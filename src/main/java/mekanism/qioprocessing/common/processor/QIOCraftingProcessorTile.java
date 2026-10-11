package mekanism.qioprocessing.common.processor;

import mekanism.api.processing.MachineOperationToken;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistry;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Server-side Processor host carrying durable state, energy and lane scheduling.
 */
public abstract class QIOCraftingProcessorTile extends TileEntityElectricBlock
        implements IUpgradeTile, IFrequencyHandler, ISecurityTile {
    private static final String STATE_KEY = "qioProcessorState";
    private static final String RUNTIME_KEY = "qioProcessorRuntime";
    private QIOCraftingProcessorState processorState;
    private String loadDiagnostic = "";
    private int speedUpgrades;
    private int energyUpgrades;
    private int stackingUpgrades;
    private final QIOProcessorUpgradeComponent upgradeComponent;
    private QIOFrequencyReference endpointReference;
    private long endpointRevision;
    private String endpointDiagnostic = "";
    private final TileComponentSecurity securityComponent;

    protected QIOCraftingProcessorTile() {
        super("qio_crafting_processor", 100_000D);
        upgradeComponent = new QIOProcessorUpgradeComponent(this);
        getFrequencyComponent().track(FrequencyType.QIO, true, true, true);
        securityComponent = new TileComponentSecurity(this);
    }

    public final QIOFrequency qioFrequency() {
        return getFrequencyComponent().getFrequency(FrequencyType.QIO);
    }

    @Override
    public final TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    protected abstract ResourceLocation processorHostId();

    protected abstract ResourceLocation processorDefinitionId();

    public final QIOCraftingProcessorState processorState() {
        if (processorState == null) {
            QIOCraftingProcessorDefinition definition = definition();
            processorState = QIOCraftingProcessorState.create(UUID.randomUUID(), processorHostId(), definition);
        }
        return processorState;
    }

    public final QIOCraftingProcessorDefinition definition() {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(processorDefinitionId());
        if (definition == null)
            throw new IllegalStateException("Processor definition is not registered: " + processorDefinitionId());
        return definition;
    }

    @Override
    public double getMaxEnergy() {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(processorDefinitionId());
        return definition == null ? super.getMaxEnergy() :
                QIOCraftingProcessorRuntime.maxEnergy(definition, energyUpgrades());
    }

    @Override
    public void setEnergy(double energy) {
        double sanitized = Double.isFinite(energy) ? Math.max(0D, Math.min(energy, getMaxEnergy())) : 0D;
        electricityStored.set(sanitized);
        markDirty();
    }

    @Override
    protected double getMainEnergyPerTick() {
        return QIOCraftingProcessorRuntime.energyPerTick(definition(), speedUpgrades(), energyUpgrades());
    }

    public final int speedUpgrades() {
        return upgradeComponent == null ? speedUpgrades : upgradeComponent.getUpgrades(Upgrade.SPEED);
    }

    public final int energyUpgrades() {
        return upgradeComponent == null ? energyUpgrades : upgradeComponent.getUpgrades(Upgrade.ENERGY);
    }

    public final int stackingUpgrades() {
        return upgradeComponent == null ? stackingUpgrades : upgradeComponent.getUpgrades(mekanism.qioprocessing.common.QIOProcessingUpgrades.QIO_STACKING);
    }

    public final void setUpgrades(int speed, int energy, int stacking) {
        speedUpgrades = boundedUpgrade(speed, definition().getSpeedUpgradeLimit());
        energyUpgrades = boundedUpgrade(energy, definition().getEnergyUpgradeLimit());
        stackingUpgrades = boundedUpgrade(stacking, definition().getStackingUpgradeLimit());
        upgradeComponent.setUpgrades(Upgrade.SPEED, speedUpgrades);
        upgradeComponent.setUpgrades(Upgrade.ENERGY, energyUpgrades);
        upgradeComponent.setUpgrades(mekanism.qioprocessing.common.QIOProcessingUpgrades.QIO_STACKING, stackingUpgrades);
        setEnergy(Math.min(getEnergy(), getMaxEnergy()));
        markDirty();
    }

    @Override
    public QIOProcessorUpgradeComponent getComponent() {
        return upgradeComponent;
    }

    @Override
    public int getBlockGuiID(net.minecraft.block.Block block, int metadata) {
        return -1;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        speedUpgrades = upgradeComponent.getUpgrades(Upgrade.SPEED);
        energyUpgrades = upgradeComponent.getUpgrades(Upgrade.ENERGY);
        stackingUpgrades = upgradeComponent.getUpgrades(mekanism.qioprocessing.common.QIOProcessingUpgrades.QIO_STACKING);
        if (upgrade == Upgrade.ENERGY) setEnergy(Math.min(getEnergy(), getMaxEnergy()));
    }

    public final long operationsPerBatch() {
        return QIOCraftingProcessorRuntime.operationsPerBatch(stackingUpgrades(), definition());
    }

    public final MachineOperationToken allocateOperation(UUID operationId, String sourceId,
                                                         String routeId, String recipeKey, int configurationRevision, long baseTicks) {
        if (baseTicks <= 0) throw new IllegalArgumentException("Base processing ticks must be positive");
        QIOCraftingProcessorState current = processorState().state() == QIOCraftingProcessorState.State.UNBOUND
                ? processorState().activate() : processorState();
        java.util.OptionalLong lane = current.nextAvailableLane(definition());
        if (!lane.isPresent()) throw new IllegalStateException("All processor lanes are occupied");
        MachineOperationToken token = MachineOperationToken.allocate(operationId, sourceId, routeId,
                recipeKey, configurationRevision, lane.getAsLong());
        long ticks = QIOCraftingProcessorRuntime.processingTicks(baseTicks, speedUpgrades(), definition());
        QIOCraftingProcessorState next = current.startLane(lane.getAsLong(), token, ticks);
        processorState = next;
        markDirty();
        return token;
    }

    public final void completeOperation(long laneId) {
        processorState = QIOCraftingProcessorRuntime.completeLane(processorState(), laneId);
        markDirty();
    }

    public final void cancelOperation(long laneId, String reason) {
        processorState = QIOCraftingProcessorRuntime.cancelLane(processorState(), laneId, reason);
        markDirty();
    }

    public final void releaseOperation(long laneId) {
        processorState = processorState().releaseLane(laneId);
        markDirty();
    }

    public final QIOCraftingProcessorRuntime.TickStatus tickProcessor(long tick) {
        QIOCraftingProcessorRuntime.TickResult result = QIOCraftingProcessorRuntime.tick(processorState(),
                definition(), getEnergy(), speedUpgrades(), energyUpgrades(), tick);
        if (result.state() != processorState() || Double.compare(result.energy(), getEnergy()) != 0) {
            processorState = result.state();
            setEnergy(result.energy());
            markDirty();
        }
        return result.status();
    }

    @Override
    protected void onUpdateServer() {
        if (processorState().state() == QIOCraftingProcessorState.State.UNBOUND) {
            processorState = processorState().activate();
            markDirty();
        }
        if (world != null) tickProcessor(world.getTotalWorldTime());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            QIOCraftingProcessorEndpointDirectory.INSTANCE.register(this);
        }
    }

    @Override
    public void onChunkUnload() {
        QIOCraftingProcessorEndpointDirectory.INSTANCE.unregister(this);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        QIOCraftingProcessorEndpointDirectory.INSTANCE.unregister(this);
        super.invalidate();
    }

    public final String loadDiagnostic() {
        return loadDiagnostic;
    }

    /**
     * Returns the exact frequency identity retained by this processor, if one is bound.
     */
    public final QIOFrequencyReference endpointReference() {
        return endpointReference;
    }

    public final long endpointRevision() {
        return endpointRevision;
    }

    public final String endpointDiagnostic() {
        return endpointDiagnostic;
    }

    /**
     * Stores an already-authorized endpoint identity. Authorization and frequency lookup remain
     * the caller's responsibility; this method only enforces that the reference is bound to the
     * same player and never silently replaces a different endpoint.
     */
    public final void bindEndpoint(QIOFrequencyReference reference, UUID authorizedPlayer) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(authorizedPlayer, "authorizedPlayer");
        if (!authorizedPlayer.equals(reference.getBindingPlayerUUID())) {
            throw new IllegalArgumentException("Processor endpoint player does not match frequency reference");
        }
        if (endpointReference != null && !endpointReference.equals(reference)) {
            throw new IllegalStateException("Processor endpoint is already bound to another frequency");
        }
        endpointReference = reference;
        endpointDiagnostic = "";
        endpointRevision++;
        markDirty();
    }

    public final void unbindEndpoint(QIOFrequencyReference expected, UUID authorizedPlayer) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(authorizedPlayer, "authorizedPlayer");
        if (endpointReference == null || !endpointReference.equals(expected)
                || !authorizedPlayer.equals(expected.getBindingPlayerUUID())) {
            throw new IllegalStateException("Processor endpoint binding does not match the requested release");
        }
        endpointReference = null;
        endpointRevision++;
        markDirty();
    }

    public final void updateProcessorState(UnaryOperator<QIOCraftingProcessorState> update) {
        if (world != null && world.isRemote) throw new IllegalStateException("Processor state is server-side only");
        processorState = Objects.requireNonNull(update.apply(processorState()), "Processor state update returned null");
        markDirty();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound tag) {
        super.writeCustomNBT(tag);
        writeProcessorState(tag);
        NBTTagCompound runtime = new NBTTagCompound();
        runtime.setInteger("speedUpgrades", speedUpgrades());
        runtime.setInteger("energyUpgrades", energyUpgrades());
        runtime.setInteger("stackingUpgrades", stackingUpgrades());
        tag.setTag(RUNTIME_KEY, runtime);
        if (endpointReference != null) {
            tag.setTag("qioProcessorEndpoint", endpointReference.write());
            tag.setLong("qioProcessorEndpointRevision", endpointRevision);
        }
        if (!endpointDiagnostic.isEmpty()) tag.setString("qioProcessorEndpointDiagnostic", endpointDiagnostic);
    }

    @Override
    public void readCustomNBT(NBTTagCompound tag) {
        super.readCustomNBT(tag);
        if (tag.hasKey(RUNTIME_KEY, 10)) {
            NBTTagCompound runtime = tag.getCompoundTag(RUNTIME_KEY);
            int storedSpeed = boundedUpgrade(runtime.getInteger("speedUpgrades"), definition().getSpeedUpgradeLimit());
            int storedEnergy = boundedUpgrade(runtime.getInteger("energyUpgrades"), definition().getEnergyUpgradeLimit());
            int storedStacking = boundedUpgrade(runtime.getInteger("stackingUpgrades"), definition().getStackingUpgradeLimit());
            if (!tag.hasKey("componentUpgrade", 10)) {
                upgradeComponent.setUpgrades(Upgrade.SPEED, storedSpeed);
                upgradeComponent.setUpgrades(Upgrade.ENERGY, storedEnergy);
                upgradeComponent.setUpgrades(mekanism.qioprocessing.common.QIOProcessingUpgrades.QIO_STACKING, storedStacking);
            }
        }
        recalculateUpgradables(Upgrade.ENERGY);
        setEnergy(Math.min(getEnergy(), getMaxEnergy()));
        endpointDiagnostic = tag.getString("qioProcessorEndpointDiagnostic");
        try {
            endpointReference = tag.hasKey("qioProcessorEndpoint", 10)
                    ? QIOFrequencyReference.read(tag.getCompoundTag("qioProcessorEndpoint")) : null;
            endpointRevision = Math.max(0, tag.getLong("qioProcessorEndpointRevision"));
        } catch (RuntimeException error) {
            endpointReference = null;
            endpointRevision = Math.max(0, tag.getLong("qioProcessorEndpointRevision"));
            endpointDiagnostic = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        }
        readProcessorState(tag);
        if (!endpointDiagnostic.isEmpty()) {
            loadDiagnostic = endpointDiagnostic;
            processorState = processorState().quarantine(endpointDiagnostic);
        }
    }

    /**
     * Writes only the module-owned state payload, without requiring a Forge TileEntity mapping.
     */
    public final NBTTagCompound writeProcessorStateToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        writeProcessorState(tag);
        return tag;
    }

    /**
     * Reads only the module-owned state payload, without invoking vanilla TileEntity metadata.
     */
    public final void readProcessorStateFromNBT(NBTTagCompound tag) {
        readProcessorState(tag);
    }

    private void writeProcessorState(NBTTagCompound tag) {
        tag.setTag(STATE_KEY, processorState().write());
        if (!loadDiagnostic.isEmpty()) tag.setString("qioProcessorDiagnostic", loadDiagnostic);
    }

    private void readProcessorState(NBTTagCompound tag) {
        loadDiagnostic = tag.getString("qioProcessorDiagnostic");
        try {
            processorState = tag.hasKey(STATE_KEY, 10)
                    ? QIOCraftingProcessorState.read(tag.getCompoundTag(STATE_KEY)) :
                    QIOCraftingProcessorState.create(UUID.randomUUID(), processorHostId(), definition());
            QIOCraftingProcessorHostRegistry.ResolvedHost resolved = QIOCraftingProcessorHostRegistry.resolve(this);
            QIOCraftingProcessorStateValidator.Result validation =
                    QIOCraftingProcessorStateValidator.validate(processorState, resolved);
            if (!validation.valid()) {
                loadDiagnostic = validation.diagnostic();
                processorState = processorState.quarantine(loadDiagnostic);
            }
        } catch (RuntimeException error) {
            loadDiagnostic = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            processorState = QIOCraftingProcessorState.create(UUID.randomUUID(), processorHostId(), definition())
                    .quarantine(loadDiagnostic);
        }
    }

    private static int boundedUpgrade(int value, int limit) {
        if (value < 0 || value > QIOCraftingProcessorRuntime.MAX_UPGRADES) {
            throw new IllegalArgumentException("Invalid processor upgrade count");
        }
        return Math.min(value, limit);
    }

    public static final class Ordinary extends QIOCraftingProcessorTile {
        @Override
        protected ResourceLocation processorHostId() {
            return QIOCraftingProcessorTileBootstrap.ORDINARY_HOST;
        }

        @Override
        protected ResourceLocation processorDefinitionId() {
            return mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry.ORDINARY_ID;
        }
    }

    public static final class Basic extends QIOCraftingProcessorTile {
        @Override
        protected ResourceLocation processorHostId() {
            return QIOCraftingProcessorTileBootstrap.BASIC_HOST;
        }

        @Override
        protected ResourceLocation processorDefinitionId() {
            return mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry.BASIC_ID;
        }
    }

    public static final class Advanced extends QIOCraftingProcessorTile {
        @Override
        protected ResourceLocation processorHostId() {
            return QIOCraftingProcessorTileBootstrap.ADVANCED_HOST;
        }

        @Override
        protected ResourceLocation processorDefinitionId() {
            return mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry.ADVANCED_ID;
        }
    }

    public static final class Elite extends QIOCraftingProcessorTile {
        @Override
        protected ResourceLocation processorHostId() {
            return QIOCraftingProcessorTileBootstrap.ELITE_HOST;
        }

        @Override
        protected ResourceLocation processorDefinitionId() {
            return mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry.ELITE_ID;
        }
    }

    public static final class Ultimate extends QIOCraftingProcessorTile {
        @Override
        protected ResourceLocation processorHostId() {
            return QIOCraftingProcessorTileBootstrap.ULTIMATE_HOST;
        }

        @Override
        protected ResourceLocation processorDefinitionId() {
            return mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry.ULTIMATE_ID;
        }
    }
}
