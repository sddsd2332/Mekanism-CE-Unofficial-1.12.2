package mekanism.qioprocessing.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.TileNetworkList;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.base.IGuiProvider;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableNBT;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistry;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.processor.QIOCraftingProcessorState;
import mekanism.qioprocessing.common.content.processor.QIOProcessorDisplaySnapshot;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Objects;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.content.processor.QIOProcessorLaneRuntime;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipePattern;
import mekanism.qioprocessing.common.terminal.QIOProcessingFrequencyAccess;
import mekanism.qioprocessing.common.tile.component.QIOProcessorUpgradeComponent;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingCommonProxy;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import mekanism.common.util.LangUtils;

/**
 * Reusable root TileEntity implementation for ordinary workbench processors. Tier implementations
 * only select immutable host and processor definitions; they do not replace this state machine.
 */
/**
 * QIO 处理模块中的 QIOCraftingProcessor 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public abstract class QIOCraftingProcessor extends TileEntityQIOComponent implements IUpgradeTile {

    private static final int WORKBENCH_BASE_PROCESSING_TICKS = 200;
    private static final String PROCESSOR_STATE = "qioCraftingProcessorState";
    private static final String MANAGEMENT_PAUSED = "qioManagementPaused";
    private static final String MANAGEMENT_REVISION = "qioManagementRevision";
    private static final String FREQUENCY_REFERENCE = "qioProcessingFrequencyReference";

    private final ResourceLocation expectedHostId;
    private final ResourceLocation expectedDefinitionId;
    private QIOCraftingProcessorState processorState;
    private final QIOProcessorUpgradeComponent upgradeComponent;
    private EnergyInventorySlot energySlot;
    private long clientActiveLaneCount;
    private long clientProcessingTicks;
    private long clientTotalProcessingTicks;
    private QIOProcessorDisplaySnapshot clientDisplaySnapshot =
          QIOProcessorDisplaySnapshot.empty(0);
    private final long[] clientLaneCurrentTicks =
          new long[QIOProcessorDisplaySnapshot.MAX_DISPLAY_LANES];
    private final long[] clientLaneTotalTicks =
          new long[QIOProcessorDisplaySnapshot.MAX_DISPLAY_LANES];
    private String cachedDisplaySignature = "";
    private QIOProcessorDisplaySnapshot cachedDisplaySnapshot =
          QIOProcessorDisplaySnapshot.empty(0);
    private NBTTagCompound cachedDisplayData = new NBTTagCompound();
    private boolean managementPaused;
    private long managementConfigurationRevision;
    private boolean working;
    @Nullable
    private QIOFrequencyReference frequencyReference;

    protected QIOCraftingProcessor(@Nonnull String name, @Nonnull ResourceLocation hostId,
          @Nonnull ResourceLocation definitionId) {
        super(Objects.requireNonNull(name, "name"), definitionEnergyCapacity(definitionId));
        expectedHostId = Objects.requireNonNull(hostId, "hostId");
        expectedDefinitionId = Objects.requireNonNull(definitionId, "definitionId");
        processorState = QIOCraftingProcessorState.create(expectedHostId, requireDefinition());
        upgradeComponent = new QIOProcessorUpgradeComponent(this);
        initializeInventorySlots();
        bindState();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        int slotX = isFactoryProcessor() ? 7 : 143;
        int slotY = isFactoryProcessor() ? 5 : 57;
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(),
              this::getWorld, listener, slotX, slotY), RelativeSide.BACK);
        return builder.build();
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    public long getOperationsPerBatch() {
        int upgrades = getInstalledUpgrades(QIOProcessingUpgrades.QIO_STACKING);
        return 1L << Math.min(62, Math.max(0, upgrades));
    }

    public long getLaneCount() {
        QIOCraftingProcessorDefinition definition = processorState.getResolvedDefinition();
        return definition == null ? 0 : definition.getLaneCount();
    }

    public boolean isFactoryProcessor() {
        return getLaneCount() > 1;
    }

    public int getDisplayLaneCount() {
        return (int) Math.min(QIOProcessorDisplaySnapshot.MAX_DISPLAY_LANES,
              Math.max(0, getLaneCount()));
    }

    /** Built-in factory layout has room for at most nine simultaneously visible lanes. */
    public int getVisibleLaneCount() {
        return Math.min(9, getDisplayLaneCount());
    }

    @Nullable
    public QIOProcessorDisplaySnapshot.Lane getLaneDisplay(int lane) {
        if (lane < 0 || lane >= getDisplayLaneCount()) {
            return null;
        }
        return isRemote() ? clientDisplaySnapshot.getLane(lane) :
              buildDisplaySnapshot().getLane(lane);
    }

    public long getLaneCurrentTicks(int lane) {
        if (lane < 0 || lane >= getDisplayLaneCount()) {
            return 0;
        }
        if (isRemote()) {
            return clientLaneCurrentTicks[lane];
        }
        QIOProcessorLaneRuntime runtime = processorState.getLane(lane);
        return runtime == null ? 0 : runtime.getCurrentTick();
    }

    public long getLaneTotalTicks(int lane) {
        if (lane < 0 || lane >= getDisplayLaneCount()) {
            return 0;
        }
        if (isRemote()) {
            return clientLaneTotalTicks[lane];
        }
        QIOProcessorLaneRuntime runtime = processorState.getLane(lane);
        return runtime == null ? 0 : runtime.getTotalTicks();
    }

    public double getScaledLaneProgress(int lane) {
        long total = getLaneTotalTicks(lane);
        if (total <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, getLaneCurrentTicks(lane) / (double) total));
    }

    public long getActiveLaneCount() {
        return isRemote() ? clientActiveLaneCount : processorState.getActiveLanes().size();
    }

    /** True only while at least one lane advanced its recipe processing this tick. */
    public boolean isWorking() {
        return working;
    }

    public boolean isManagementPaused() {
        return managementPaused;
    }

    public long getManagementConfigurationRevision() {
        return managementConfigurationRevision;
    }

    @Nullable
    public final QIOFrequencyReference getFrequencyReference() {
        return frequencyReference;
    }

    @Override
    @Nullable
    public final QIOFrequency getQIOFrequency() {
        QIOFrequency frequency = super.getQIOFrequency();
        return frequencyReference != null &&
              QIOProcessingFrequencyAccess.matches(frequencyReference, frequency) ?
              frequency : null;
    }

    /** Generic QIO packets cannot mutate an exact smart-processing binding. */
    @Override
    public final void setFrequency(FrequencyType<?> type, FrequencyIdentity data,
          UUID player) {
    }

    /** Frequency removal is deliberately separate from unbinding this processor. */
    @Override
    public final void removeFrequency(FrequencyType<?> type, FrequencyIdentity data,
          UUID player) {
    }

    public final boolean applyAuthorizedBinding(@Nullable QIOFrequency frequency,
          @Nonnull UUID bindingPlayerUUID) {
        Objects.requireNonNull(bindingPlayerUUID, "bindingPlayerUUID");
        boolean normalized = processorState.normalizeLegacyState(expectedHostId, expectedDefinitionId);
        if (normalized) {
            wakeExecution();
        }
        if (processorState.hasRecoveryPending() || processorState.hasBindingOwnership()) {
            return false;
        }
        QIOFrequencyReference updatedReference = frequency == null ? null :
              QIOFrequencyStorageAccess.INSTANCE.createReference(frequency,
                    bindingPlayerUUID);
        if (Objects.equals(frequencyReference, updatedReference)) {
            return false;
        }
        if (frequency == null) {
            getFrequencyComponent().unsetFrequency(FrequencyType.QIO);
        } else {
            getFrequencyComponent().setFrequencyFromData(FrequencyType.QIO,
                  frequency.getIdentity(), bindingPlayerUUID);
        }
        frequencyReference = updatedReference;
        markFrequencyBindingChanged();
        return true;
    }

    public final boolean restoreExactBinding(@Nonnull UUID bindingPlayerUUID) {
        Objects.requireNonNull(bindingPlayerUUID, "bindingPlayerUUID");
        boolean normalized = processorState.normalizeLegacyState(expectedHostId, expectedDefinitionId);
        if (normalized) {
            wakeExecution();
        }
        if (processorState.hasRecoveryPending()) {
            return false;
        }
        QIOFrequency frequency = QIOProcessingFrequencyAccess.resolveAccessible(
              frequencyReference, bindingPlayerUUID);
        if (frequency == null) {
            getFrequencyComponent().unsetFrequency(FrequencyType.QIO);
            return false;
        }
        getFrequencyComponent().setFrequencyFromData(FrequencyType.QIO,
              frequency.getIdentity(), bindingPlayerUUID);
        markFrequencyBindingChanged();
        return true;
    }

    public boolean setManagementPaused(boolean paused) {
        if (world == null || world.isRemote || managementPaused == paused) {
            return managementPaused == paused;
        }
        if (managementConfigurationRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO processor management revision exhausted");
        }
        managementPaused = paused;
        managementConfigurationRevision++;
        markDirty();
        QIOCraftingProcessorDeviceRegistry.INSTANCE.refresh(this);
        return true;
    }

    public double getScaledProcessingProgress() {
        long total = isRemote() ? clientTotalProcessingTicks : totalProcessingTicks();
        if (total <= 0) {
            return 0;
        }
        long current = isRemote() ? clientProcessingTicks : currentProcessingTicks();
        return Math.max(0, Math.min(1, current / (double) total));
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableNBT.create(this::getDisplaySnapshotData,
              this::setClientDisplaySnapshot));
        for (int lane = 0; lane < getDisplayLaneCount(); lane++) {
            final int laneId = lane;
            container.track(SyncableLong.create(() -> getLaneCurrentTicks(laneId),
                  value -> clientLaneCurrentTicks[laneId] = Math.max(0, value)));
            container.track(SyncableLong.create(() -> getLaneTotalTicks(laneId),
                  value -> clientLaneTotalTicks[laneId] = Math.max(0, value)));
        }
        container.track(SyncableLong.create(this::getActiveLaneCount,
              value -> clientActiveLaneCount = Math.max(0, value)));
        container.track(SyncableLong.create(this::currentProcessingTicks,
              value -> clientProcessingTicks = Math.max(0, value)));
        container.track(SyncableLong.create(this::totalProcessingTicks,
              value -> clientTotalProcessingTicks = Math.max(0, value)));
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return QIOProcessingCommonProxy.GUI_CRAFTING_PROCESSOR;
    }

    @Override
    public IGuiProvider guiProvider() {
        return MekanismQIOProcessing.proxy;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize(getBlockType().getTranslationKey() + ".name");
    }

    @Override
    protected double getMainEnergyPerTick() {
        QIOCraftingProcessorDefinition definition = processorState == null ?
              QIOCraftingProcessorRegistry.get(expectedDefinitionId) :
              processorState.getResolvedDefinition();
        if (definition == null) {
            return 0;
        }
        if (!isUpgradeInstalled(Upgrade.SPEED) && !isUpgradeInstalled(Upgrade.ENERGY)) {
            return definition.getBaseEnergyUsage();
        }
        return MekanismUtils.getEnergyPerTick(this, definition.getBaseEnergyUsage());
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        if (upgrade == Upgrade.ENERGY) {
            QIOCraftingProcessorDefinition definition = requireDefinition();
            maxEnergy = isUpgradeInstalled(Upgrade.ENERGY) ?
                  MekanismUtils.getMaxEnergy(this, definition.getEnergyCapacity()) :
                  definition.getEnergyCapacity();
            setEnergy(Math.min(getEnergy(), maxEnergy));
        }
    }

    @Nonnull
    public final ResourceLocation getExpectedProcessorHostId() {
        return expectedHostId;
    }

    @Nonnull
    public final ResourceLocation getExpectedProcessorDefinitionId() {
        return expectedDefinitionId;
    }

    @Nonnull
    public final QIOCraftingProcessorState getProcessorState() {
        return processorState;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote) {
            reconcileRegistration();
            QIOCraftingProcessorDeviceRegistry.INSTANCE.register(this);
        }
    }

    @Override
    public void onChunkUnload() {
        if (!isRemote()) {
            QIOCraftingProcessorDeviceRegistry.INSTANCE.unregister(this);
        }
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (!isRemote()) {
            QIOCraftingProcessorDeviceRegistry.INSTANCE.unregisterRemoved(this);
        }
    }

    @Override
    public boolean supportsAsync() {
        // Lane execution and every QIO transaction are committed on the server thread.
        return false;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        // Normalize every lossless legacy marker and single-sided lane buffer. Ambiguous lane/raw
        // ownership remains visible for the audited management-terminal recovery action.
        if (processorState.normalizeLegacyState(expectedHostId, expectedDefinitionId)) {
            QIOCraftingProcessorDeviceRegistry.INSTANCE.refresh(this);
            // A recovered single-sided lane may still need the network executor to drain its
            // RETURNING/OUTPUT_BLOCKED buffer.  Refreshing the directory alone is insufficient
            // when the executor had already placed this device on an adaptive retry backoff.
            wakeExecution();
        }
        if ((world.getTotalWorldTime() + getPos().toLong()) % 16 == 0) {
            QIOCraftingProcessorDeviceRegistry.INSTANCE.refresh(this);
        }
        energySlot.fillContainerOrConvert();
        if (!isActive() || processorState.getState() != QIOCraftingProcessorState.State.ACTIVE ||
              !QIORecipeCatalogService.INSTANCE.isReady()) {
            updateWorking(false);
            return;
        }
        int processed = 0;
        boolean workedThisTick = false;
        for (QIOProcessorLaneRuntime lane : new ArrayList<>(
              processorState.getActiveLanes().values())) {
            if (processed++ >= 64) {
                break;
            }
            workedThisTick |= tickWorkbenchLane(lane);
        }
        updateWorking(workedThisTick);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound data) {
        super.writeCustomNBT(data);
        data.setTag(PROCESSOR_STATE, processorState.write());
        data.setBoolean(MANAGEMENT_PAUSED, managementPaused);
        data.setLong(MANAGEMENT_REVISION, managementConfigurationRevision);
        writeFrequencyReference(data);
    }

    @Override
    public void readCustomNBT(NBTTagCompound data) {
        super.readCustomNBT(data);
        readFrequencyReference(data);
        managementPaused = data.getBoolean(MANAGEMENT_PAUSED);
        managementConfigurationRevision = Math.max(0, data.getLong(MANAGEMENT_REVISION));
        if (!data.hasKey(PROCESSOR_STATE, NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagCompound stored = data.getCompoundTag(PROCESSOR_STATE);
        try {
            processorState = QIOCraftingProcessorState.read(stored, expectedHostId,
                  expectedDefinitionId);
        } catch (QIOProcessingDataException | RuntimeException e) {
            processorState = QIOCraftingProcessorState.damaged(expectedHostId, requireDefinition(),
                  stored, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        bindState();
    }

    @Override
    protected void writeUpdateNBT(NBTTagCompound data) {
        writeQIOVisualUpdateNBT(data);
        data.setBoolean("qioWorking", working);
        data.setString("processorState", processorState.getState().name());
        data.setBoolean(MANAGEMENT_PAUSED, managementPaused);
        writeFrequencyReference(data);
    }

    @Override
    protected void readUpdateNBT(NBTTagCompound data) {
        readQIOVisualUpdateNBT(data);
        working = data.getBoolean("qioWorking");
        readFrequencyReference(data);
        managementPaused = data.getBoolean(MANAGEMENT_PAUSED);
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        boolean previousWorking = working;
        super.handleUpdateTag(tag);
        if (world != null && world.isRemote && previousWorking != working) {
            MekanismUtils.updateBlock(world, getPos());
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (world != null && world.isRemote) {
            boolean previousWorking = working;
            working = dataStream.readBoolean();
            readFrequencyReference(PacketHandler.readNBT(dataStream));
            if (previousWorking != working) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(working);
        NBTTagCompound frequencyData = new NBTTagCompound();
        writeFrequencyReference(frequencyData);
        data.add(frequencyData);
        return data;
    }

    @Override
    public void writeSustainedQIOData(NBTTagCompound data) {
        super.writeSustainedQIOData(data);
        data.setTag(PROCESSOR_STATE, processorState.write());
        data.setBoolean(MANAGEMENT_PAUSED, managementPaused);
        data.setLong(MANAGEMENT_REVISION, managementConfigurationRevision);
        writeFrequencyReference(data);
    }

    @Override
    public void readSustainedQIOData(NBTTagCompound data) {
        super.readSustainedQIOData(data);
        readFrequencyReference(data);
        managementPaused = data.getBoolean(MANAGEMENT_PAUSED);
        managementConfigurationRevision = Math.max(0, data.getLong(MANAGEMENT_REVISION));
        if (data.hasKey(PROCESSOR_STATE, NBT.TAG_COMPOUND)) {
            NBTTagCompound stored = data.getCompoundTag(PROCESSOR_STATE);
            try {
                processorState = QIOCraftingProcessorState.read(stored, expectedHostId,
                      expectedDefinitionId);
            } catch (QIOProcessingDataException | RuntimeException e) {
                processorState = QIOCraftingProcessorState.damaged(expectedHostId,
                      requireDefinition(), stored,
                      e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            bindState();
        }
    }

    private void writeFrequencyReference(NBTTagCompound data) {
        if (frequencyReference == null) {
            data.removeTag(FREQUENCY_REFERENCE);
        } else {
            data.setTag(FREQUENCY_REFERENCE, frequencyReference.write());
        }
    }

    private void readFrequencyReference(NBTTagCompound data) {
        try {
            frequencyReference = data.hasKey(FREQUENCY_REFERENCE, NBT.TAG_COMPOUND) ?
                  QIOFrequencyReference.read(data.getCompoundTag(FREQUENCY_REFERENCE)) : null;
        } catch (RuntimeException ignored) {
            frequencyReference = null;
        }
        if (frequencyReference == null) {
            getFrequencyComponent().unsetFrequency(FrequencyType.QIO);
        }
    }

    private void markFrequencyBindingChanged() {
        markNoUpdateSync();
        if (world != null && !world.isRemote) {
            QIOCraftingProcessorDeviceRegistry.INSTANCE.refresh(this);
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    private void reconcileRegistration() {
        try {
            QIOCraftingProcessorHostRegistry.ResolvedHost resolved =
                  QIOCraftingProcessorHostRegistry.resolve(this);
            if (resolved == null) {
                processorState.markUnresolved("No QIO processor host registration matches " +
                      getClass().getName());
            } else if (!resolved.getHostId().equals(expectedHostId) ||
                  !resolved.getDefinition().getId().equals(expectedDefinitionId)) {
                processorState.markUnresolved("QIO processor host registration resolved a different identity");
            } else {
                processorState.reconcile(expectedHostId, expectedDefinitionId);
            }
        } catch (RuntimeException e) {
            processorState.markUnresolved(e.getMessage() == null ? e.getClass().getSimpleName() :
                  e.getMessage());
        }
    }

    @Nonnull
    private QIOCraftingProcessorDefinition requireDefinition() {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(expectedDefinitionId);
        if (definition == null) {
            throw new IllegalStateException("QIO crafting processor definition is not registered: " +
                  expectedDefinitionId);
        }
        return definition;
    }

    private void bindState() {
        processorState.setDirtyListener(this::markDirty);
    }

    private boolean tickWorkbenchLane(QIOProcessorLaneRuntime lane) {
        QIOProcessingNetworkData network = frequencyReference == null ? null :
              QIOProcessingNetworkManager.INSTANCE.get(frequencyReference.getFrequencyUUID());
        if (!QIORecipeCatalogService.INSTANCE.isReady()) {
            // Keep the lane state intact and retry after the immutable catalog is published.
            return false;
        }
        QIOWorkbenchRecipePattern pattern = network == null ? null :
              QIORecipeCatalogService.INSTANCE.getSnapshot(network.getWorkbenchConfiguration())
                    .getPattern(lane.getRouteKey());
        if (lane.getState() == QIOProcessorLaneRuntime.State.READY) {
            if (pattern == null || !scaleAmounts(pattern.getExactInputs(),
                  lane.getOperationCount()).equals(lane.getInput())) {
                lane.beginReturn();
                wakeExecution();
                return false;
            }
            lane.beginProcessing(getWorkbenchProcessingTicks());
        }
        if (lane.getState() != QIOProcessorLaneRuntime.State.PROCESSING) {
            return false;
        }
        double energy = getMainEnergyPerTick() * lane.getOperationCount();
        if (energy <= 0 || Double.compare(getMainEnergyContainer().extract(energy,
              Action.SIMULATE, AutomationType.INTERNAL), energy) != 0) {
            return false;
        }
        getMainEnergyContainer().extract(energy, Action.EXECUTE, AutomationType.INTERNAL);
        lane.advanceProcessing(1);
        if (lane.getCurrentTick() < lane.getTotalTicks()) {
            return true;
        }
        Map<PortableResourceDescriptor, Long> actualOutputs = new LinkedHashMap<>();
        for (long operation = 0; operation < lane.getOperationCount(); operation++) {
            QIOWorkbenchRecipePattern.CraftResult result = pattern == null ? null :
                  pattern.craft(world);
            if (result == null) {
                lane.abortProcessing();
                wakeExecution();
                return true;
            }
            result.getOutputs().forEach((resource, amount) -> actualOutputs.merge(resource,
                  amount, Math::addExact));
        }
        if (!scaleAmounts(pattern.getExpectedOutputs(), lane.getOperationCount()).equals(
              actualOutputs)) {
            lane.abortProcessing();
            wakeExecution();
            return true;
        }
        lane.completeProcessing(actualOutputs);
        wakeExecution();
        return true;
    }

    private void updateWorking(boolean nextWorking) {
        if (working == nextWorking) {
            return;
        }
        working = nextWorking;
        markNoUpdateSync();
        if (world != null && !world.isRemote) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    private void wakeExecution() {
        if (frequencyReference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDeviceContents(
                  frequencyReference.getFrequencyUUID(), processorState.getProcessorUUID());
        }
    }

    /**
     * Resolves the workbench processing duration from the processor definition's actual speed
     * upgrade limit. The geometric curve keeps the familiar diminishing-time behavior while
     * distributing the full range over an addon-defined limit and reaching exactly one tick at
     * that limit.
     */
    static int workbenchProcessingTicks(int installedSpeedUpgrades, int speedUpgradeLimit) {
        int limit = Math.max(0, speedUpgradeLimit);
        int installed = Math.max(0, installedSpeedUpgrades);
        if (limit == 0 || installed <= 0) {
            return WORKBENCH_BASE_PROCESSING_TICKS;
        }
        if (installed >= limit) {
            return 1;
        }
        double exponent = (limit - installed) / (double) limit;
        int ticks = (int) Math.ceil(Math.pow(WORKBENCH_BASE_PROCESSING_TICKS, exponent));
        return Math.max(1, Math.min(WORKBENCH_BASE_PROCESSING_TICKS, ticks));
    }

    int getWorkbenchProcessingTicks() {
        QIOCraftingProcessorDefinition definition = processorState.getResolvedDefinition();
        int limit = definition == null ? 0 : definition.getSpeedUpgradeLimit();
        return workbenchProcessingTicks(getInstalledUpgrades(Upgrade.SPEED), limit);
    }

    private static Map<PortableResourceDescriptor, Long> scaleAmounts(
          Map<PortableResourceDescriptor, Long> amounts, long multiplier) {
        Map<PortableResourceDescriptor, Long> scaled = new LinkedHashMap<>();
        amounts.forEach((resource, amount) ->
              scaled.put(resource, Math.multiplyExact(amount, multiplier)));
        return scaled;
    }

    private static double definitionEnergyCapacity(ResourceLocation definitionId) {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(
              Objects.requireNonNull(definitionId, "definitionId"));
        if (definition == null) {
            throw new IllegalStateException("QIO crafting processor definition is not registered: " +
                  definitionId);
        }
        return definition.getEnergyCapacity();
    }

    private long currentProcessingTicks() {
        if (isRemote()) {
            return clientProcessingTicks;
        }
        long total = 0;
        for (QIOProcessorLaneRuntime lane : processorState.getActiveLanes().values()) {
            total = saturatedAdd(total, lane.getCurrentTick());
        }
        return total;
    }

    private long totalProcessingTicks() {
        if (isRemote()) {
            return clientTotalProcessingTicks;
        }
        long total = 0;
        for (QIOProcessorLaneRuntime lane : processorState.getActiveLanes().values()) {
            total = saturatedAdd(total, lane.getTotalTicks());
        }
        return total;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    @Nonnull
    private NBTTagCompound getDisplaySnapshotData() {
        if (isRemote()) {
            return clientDisplaySnapshot.write();
        }
        buildDisplaySnapshot();
        return cachedDisplayData;
    }

    private void setClientDisplaySnapshot(NBTTagCompound data) {
        try {
            clientDisplaySnapshot = QIOProcessorDisplaySnapshot.read(data);
        } catch (QIOProcessingDataException | RuntimeException ignored) {
            clientDisplaySnapshot = QIOProcessorDisplaySnapshot.empty(getDisplayLaneCount());
        }
    }

    @Nonnull
    private QIOProcessorDisplaySnapshot buildDisplaySnapshot() {
        int laneCount = getDisplayLaneCount();
        QIOProcessingNetworkData network = frequencyReference == null ? null :
              QIOProcessingNetworkManager.INSTANCE.get(frequencyReference.getFrequencyUUID());
        long patternRevision = network == null ? -1 :
              QIORecipeCatalogService.INSTANCE.getRevision(
                    network.getWorkbenchConfiguration());
        StringBuilder signature = new StringBuilder().append(laneCount).append('|')
              .append(patternRevision);
        List<QIOProcessorLaneRuntime> displayRuntimes = new ArrayList<>(laneCount);
        for (int laneId = 0; laneId < laneCount; laneId++) {
            QIOProcessorLaneRuntime lane = processorState.getLane(laneId);
            if (lane == null) {
                continue;
            }
            displayRuntimes.add(lane);
            signature.append('|').append(lane.getLaneId()).append(':')
                  .append(lane.getOperationId()).append(':').append(lane.getRouteKey())
                  .append(':').append(lane.getState()).append(':')
                  .append(lane.getOperationCount());
        }
        String currentSignature = signature.toString();
        if (currentSignature.equals(cachedDisplaySignature)) {
            return cachedDisplaySnapshot;
        }

        QIOWorkbenchRecipeCatalog.Snapshot catalog = null;
        if (network != null && QIORecipeCatalogService.INSTANCE.isInitialized()) {
            try {
                catalog = QIORecipeCatalogService.INSTANCE.getSnapshot(
                      network.getWorkbenchConfiguration());
            } catch (RuntimeException ignored) {
            }
        }
        List<QIOProcessorDisplaySnapshot.Lane> displayed = new ArrayList<>();
        for (QIOProcessorLaneRuntime lane : displayRuntimes) {
            QIOWorkbenchRecipePattern pattern = catalog == null ? null :
                  catalog.getPattern(lane.getRouteKey());
            List<ItemStack> grid = pattern == null ?
                  Collections.nCopies(9, ItemStack.EMPTY) : pattern.getGrid();
            ItemStack output = pattern == null ? ItemStack.EMPTY : pattern.getDisplayOutput();
            displayed.add(new QIOProcessorDisplaySnapshot.Lane((int) lane.getLaneId(),
                  lane.getState(), lane.getOperationCount(), lane.getRouteKey(), grid, output));
        }
        QIOProcessorDisplaySnapshot snapshot = new QIOProcessorDisplaySnapshot(laneCount,
              displayed);
        cachedDisplaySignature = currentSignature;
        cachedDisplaySnapshot = snapshot;
        cachedDisplayData = snapshot.write();
        return snapshot;
    }
}
