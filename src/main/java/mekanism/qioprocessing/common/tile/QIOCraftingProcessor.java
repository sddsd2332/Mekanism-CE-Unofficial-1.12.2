package mekanism.qioprocessing.common.tile;

import com.google.common.util.concurrent.AtomicDouble;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergyEmitter;
import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.TileNetworkList;
import mekanism.api.heat.HeatAPI;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.base.IGuiProvider;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableNBT;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.lib.LastEnergyTracker;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.CapabilityUtils;
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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.Optional.Method;

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
public abstract class QIOCraftingProcessor extends TileEntityQIOComponent implements IUpgradeTile,
      IEnergyWrapper {

    private static final int WORKBENCH_BASE_PROCESSING_TICKS = 200;
    private static final String PROCESSOR_STATE = "qioCraftingProcessorState";
    private static final String MANAGEMENT_PAUSED = "qioManagementPaused";
    private static final String MANAGEMENT_REVISION = "qioManagementRevision";
    private static final String FREQUENCY_REFERENCE = "qioProcessingFrequencyReference";

    private final AtomicDouble electricityStored = new AtomicDouble();
    private final double baseMaxEnergy;
    private double maxEnergy;
    private boolean ic2Registered;
    private final CapabilityWrapperManager<IEnergyWrapper, TeslaIntegration> teslaManager =
          new CapabilityWrapperManager<>(IEnergyWrapper.class, TeslaIntegration.class);
    private final CapabilityWrapperManager<IEnergyWrapper, ForgeEnergyIntegration> forgeEnergyManager =
          new CapabilityWrapperManager<>(IEnergyWrapper.class, ForgeEnergyIntegration.class);
    private final LastEnergyTracker lastEnergyTracker = new LastEnergyTracker();
    @Nullable
    private MachineEnergyContainer mainEnergyContainer;
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
        super(Objects.requireNonNull(name, "name"));
        expectedHostId = Objects.requireNonNull(hostId, "hostId");
        expectedDefinitionId = Objects.requireNonNull(definitionId, "definitionId");
        baseMaxEnergy = definitionEnergyCapacity(expectedDefinitionId);
        maxEnergy = baseMaxEnergy;
        processorState = QIOCraftingProcessorState.create(expectedHostId, requireDefinition());
        upgradeComponent = new QIOProcessorUpgradeComponent(this);
        initializeInventorySlots();
        bindState();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return ProxiedEnergyContainerHolder.create(
              side -> side != null && sideIsConsumer(side),
              side -> side != null && sideIsOutput(side),
              side -> side == null || sideIsConsumer(side) || sideIsOutput(side) ?
                    Collections.singletonList(getMainEnergyContainer(listener)) :
                    Collections.emptyList());
    }

    @Nonnull
    public final MachineEnergyContainer getMainEnergyContainer() {
        return getMainEnergyContainer(this);
    }

    @Nonnull
    private MachineEnergyContainer getMainEnergyContainer(@Nullable IContentsListener listener) {
        if (mainEnergyContainer == null) {
            mainEnergyContainer = MachineEnergyContainer.create(this::getEnergy, this::setEnergy,
                  this::getMaxEnergy, this::getMainEnergyPerTick, ignored -> true,
                  ignored -> true, listener);
        }
        return mainEnergyContainer;
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
    public boolean sideIsOutput(EnumFacing side) {
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return true;
    }

    @Override
    public double getMaxOutput() {
        return 0;
    }

    @Override
    public double getEnergy() {
        return electricityStored.get();
    }

    @Override
    public void setEnergy(double energy) {
        runContainerTransaction(() -> {
            double sanitized = HeatAPI.isFinite(energy) ?
                  Math.max(0, Math.min(energy, getMaxEnergy())) : 0;
            electricityStored.set(sanitized);
            MekanismUtils.saveChunk(this);
        });
    }

    @Override
    public double getMaxEnergy() {
        return HeatAPI.isFinite(maxEnergy) ?
              Math.max(0, Math.min(HeatAPI.MAX_HEAT, maxEnergy)) : 0;
    }

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
            maxEnergy = isUpgradeInstalled(Upgrade.ENERGY) ?
                  MekanismUtils.getMaxEnergy(this, baseMaxEnergy) : baseMaxEnergy;
            setEnergy(Math.min(getEnergy(), maxEnergy));
        }
    }

    private void trackEnergyInput(double amount, Action action, double remainder) {
        if (action.execute()) {
            lastEnergyTracker.received(world == null ? 0 : world.getTotalWorldTime(),
                  amount - remainder);
        }
    }

    private double getInputRate() {
        return lastEnergyTracker.getLastEnergyReceived();
    }

    @Override
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return tryCallContainerTransaction(() -> {
            double toUse = Math.min(getMaxEnergy() - getEnergy(), amount);
            if (toUse < 0.0001 || side != null && !canInsertExternalEnergy(side)) {
                return 0D;
            }
            if (!simulate) {
                setEnergy(getEnergy() + toUse);
                lastEnergyTracker.received(world == null ? 0 : world.getTotalWorldTime(), toUse);
            }
            return toUse;
        }, () -> 0D);
    }

    @Override
    public double insertEnergy(int container, double amount, @Nullable EnumFacing side,
          Action action) {
        return tryCallContainerTransaction(() -> {
            double remainder = super.insertEnergy(container, amount, side, action);
            trackEnergyInput(amount, action, remainder);
            return remainder;
        }, () -> amount);
    }

    @Override
    public double insertEnergy(double amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            double remainder = super.insertEnergy(amount, side, action);
            trackEnergyInput(amount, action, remainder);
            return remainder;
        }, () -> amount);
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return tryCallContainerTransaction(() -> {
            double toGive = Math.min(getEnergy(), amount);
            if (toGive < 0.0001 || side != null && !canExtractExternalEnergy(side)) {
                return 0D;
            }
            if (!simulate) {
                setEnergy(getEnergy() - toGive);
            }
            return toGive;
        }, () -> 0D);
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return canInsertExternalEnergy(side);
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return canExtractExternalEnergy(side);
    }

    private boolean canInsertExternalEnergy(@Nullable EnumFacing side) {
        return canInsertEnergy(side);
    }

    private boolean canExtractExternalEnergy(@Nullable EnumFacing side) {
        return canExtractEnergy(side);
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
        return RFIntegration.toRF(acceptEnergy(from, RFIntegration.fromRF(maxReceive), simulate));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(EnumFacing from, int maxExtract, boolean simulate) {
        return RFIntegration.toRF(pullEnergy(from, RFIntegration.fromRF(maxExtract), simulate));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public boolean canConnectEnergy(EnumFacing from) {
        return canInsertExternalEnergy(from) || canExtractExternalEnergy(from);
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(EnumFacing from) {
        return RFIntegration.toRF(getEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(EnumFacing from) {
        return RFIntegration.toRF(getMaxEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getSinkTier() {
        return MekanismConfig.current().general.blacklistIC2.val() ? 0 :
              IC2Integration.getConfiguredInputTier();
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getSourceTier() {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int addEnergy(int amount) {
        if (MekanismConfig.current().general.blacklistIC2.val()) {
            return 0;
        }
        return tryCallContainerTransaction(() -> {
            setEnergy(getEnergy() + IC2Integration.fromEU(amount));
            return IC2Integration.toEUAsInt(getEnergy());
        }, () -> IC2Integration.toEUAsInt(getEnergy()));
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean isTeleporterCompatible(EnumFacing side) {
        return false;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing direction) {
        return !MekanismConfig.current().general.blacklistIC2.val() &&
              canInsertExternalEnergy(direction);
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, EnumFacing direction) {
        return false;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getStored() {
        return IC2Integration.toEUAsInt(getEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void setStored(int energy) {
        if (!MekanismConfig.current().general.blacklistIC2.val()) {
            setEnergy(IC2Integration.fromEU(energy));
        }
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getCapacity() {
        return IC2Integration.toEUAsInt(getMaxEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getOutput() {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getDemandedEnergy() {
        return MekanismConfig.current().general.blacklistIC2.val() ? 0 :
              IC2Integration.toEU(getMaxEnergy() - getEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getOfferedEnergy() {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getOutputEnergyUnitsPerTick() {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double injectEnergy(EnumFacing pushDirection, double amount, double voltage) {
        TileEntity tile = MekanismUtils.getTileEntity(world,
              getPos().offset(pushDirection.getOpposite()));
        if (MekanismConfig.current().general.blacklistIC2.val() ||
            CapabilityUtils.hasCapability(tile, Capabilities.GRID_TRANSMITTER_CAPABILITY,
                  pushDirection)) {
            return amount;
        }
        return amount - IC2Integration.toEU(acceptEnergy(pushDirection.getOpposite(),
              IC2Integration.fromEU(amount), false));
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void drawEnergy(double amount) {
    }

    @Override
    public void invalidateCapability(@Nullable Capability<?> capability,
          @Nullable EnumFacing side) {
        super.invalidateCapability(capability, side);
        if (capability == CapabilityEnergy.ENERGY) {
            forgeEnergyManager.invalidate(side);
        } else if (isTeslaCapability(capability)) {
            teslaManager.invalidate(side);
        } else if (capability == null) {
            forgeEnergyManager.invalidateAll();
            teslaManager.invalidateAll();
        }
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == CapabilityEnergy.ENERGY || isTeslaCapability(capability) &&
              canResolveTesla(capability, side) || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability,
          @Nullable EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(forgeEnergyManager.getWrapper(this, side));
        } else if (isTeslaCapability(capability) && canResolveTesla(capability, side)) {
            return (T) teslaManager.getWrapper(this, side);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability,
          @Nullable EnumFacing side) {
        if (capability == CapabilityEnergy.ENERGY || isTeslaCapability(capability)) {
            return side != null && !canInsertExternalEnergy(side) &&
                  !canExtractExternalEnergy(side);
        }
        return super.isCapabilityDisabled(capability, side);
    }

    private boolean canResolveTesla(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == Capabilities.TESLA_HOLDER_CAPABILITY ||
              capability == Capabilities.TESLA_CONSUMER_CAPABILITY &&
                    canInsertExternalEnergy(side) ||
              capability == Capabilities.TESLA_PRODUCER_CAPABILITY &&
                    canExtractExternalEnergy(side);
    }

    private static boolean isTeslaCapability(@Nullable Capability<?> capability) {
        return capability != null &&
              (capability == Capabilities.TESLA_HOLDER_CAPABILITY ||
               capability == Capabilities.TESLA_CONSUMER_CAPABILITY ||
               capability == Capabilities.TESLA_PRODUCER_CAPABILITY);
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
        if (MekanismUtils.useIC2()) {
            registerEnergyTile();
        }
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
        if (MekanismUtils.useIC2()) {
            deregisterEnergyTile();
        }
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (MekanismUtils.useIC2()) {
            deregisterEnergyTile();
        }
        if (!isRemote()) {
            QIOCraftingProcessorDeviceRegistry.INSTANCE.unregisterRemoved(this);
        }
    }

    @Override
    public void validate() {
        boolean wasInvalid = tileEntityInvalid;
        super.validate();
        if (wasInvalid && MekanismUtils.useIC2()) {
            registerEnergyTile();
        }
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    private void registerEnergyTile() {
        if (world != null && !world.isRemote && !ic2Registered) {
            MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
            ic2Registered = true;
        }
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    private void deregisterEnergyTile() {
        if (world != null && !world.isRemote && ic2Registered) {
            MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
            ic2Registered = false;
        }
    }

    @Override
    public boolean supportsAsync() {
        // Lane execution and every QIO transaction are committed on the server thread.
        return false;
    }

    @Override
    protected void onUpdateServer() {
        lastEnergyTracker.received(world == null ? 0 : world.getTotalWorldTime(), 0);
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
        data.setDouble("electricityStored", getEnergy());
        data.setTag(PROCESSOR_STATE, processorState.write());
        data.setBoolean(MANAGEMENT_PAUSED, managementPaused);
        data.setLong(MANAGEMENT_REVISION, managementConfigurationRevision);
        writeFrequencyReference(data);
    }

    @Override
    public void readCustomNBT(NBTTagCompound data) {
        super.readCustomNBT(data);
        electricityStored.set(Math.max(0, Math.min(data.getDouble("electricityStored"),
              getMaxEnergy())));
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
            setEnergy(dataStream.readDouble());
            lastEnergyTracker.setLastEnergyReceived(dataStream.readDouble());
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
        data.add(getEnergy());
        data.add(getInputRate());
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
