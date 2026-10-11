package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.IContainerTransaction;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.IGasHandler;
import mekanism.common.Mekanism;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.ITileComponent;
import mekanism.common.base.ITileNetwork;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.frequency.TileComponentFrequency;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.inventory.container.ITrackableContainer;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.base.TileEntityRestrictedTick;
import mekanism.common.tile.component.TileComponentChunkLoader;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 基本方块类型
 */
@Interface(iface = "ic2.api.tile.IWrenchable", modid = MekanismHooks.IC2_MOD_ID)
public abstract class TileEntityBasicBlock extends TileEntityRestrictedTick implements ITileNetwork, ITrackableContainer, IContainerTransaction {

    private volatile mekanism.common.tile.component.UpgradeComponentController upgradeFeatures;
    private boolean upgradeFeaturesPrepared;
    private final TileEntity[] cachedEnergyAcceptorTiles = new TileEntity[6];
    private final EnergyAcceptorWrapper[] cachedEnergyAcceptors = new EnergyAcceptorWrapper[6];
    private final boolean[] cachedEnergyAcceptorResolved = new boolean[6];
    private final TileEntity[] cachedGasHandlerTiles = new TileEntity[6];
    private final IGasHandler[] cachedGasHandlers = new IGasHandler[6];
    private final boolean[] cachedGasHandlerResolved = new boolean[6];
    private final TileEntity[] cachedFluidHandlerTiles = new TileEntity[6];
    private final IFluidHandler[] cachedFluidHandlers = new IFluidHandler[6];
    private final boolean[] cachedFluidHandlerResolved = new boolean[6];
    private final TileEntity[] cachedItemHandlerTiles = new TileEntity[6];
    private final IItemHandler[] cachedItemHandlers = new IItemHandler[6];
    private final boolean[] cachedItemHandlerResolved = new boolean[6];

    private static final ClassValue<Class<?>> ASYNC_UPDATE_DECLARING_CLASS = new ClassValue<Class<?>>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            Class<?> current = type;
            while (current != null && TileEntityBasicBlock.class.isAssignableFrom(current)) {
                try {
                    current.getDeclaredMethod("onAsyncUpdateServer");
                    return current;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
            return TileEntityBasicBlock.class;
        }
    };

    private final ReentrantLock containerTransactionLock = new ReentrantLock();
    private boolean serverEjectionSuppressedForCurrentTick;
    private boolean asyncUpdateSubmitted;

    /**
     * Called only after construction. Feature factories may consult attached capability facades once.
     */
    public final void prepareUpgradeComponents() {
        if (!upgradeFeaturesPrepared) {
            upgradeFeaturesPrepared = true;
            if (registeredUpgradeComponent != null) {
                mekanism.common.tile.component.UpgradeComponentController controller =
                        new mekanism.common.tile.component.UpgradeComponentController(this, registeredUpgradeComponent);
                upgradeFeatures = controller.isEmpty() ? null : controller;
            }
        }
    }

    public final mekanism.common.base.IUpgradeTileComponent getUpgradeFeature(
            mekanism.common.tile.component.UpgradeComponentRegistry.Slot slot) {
        return upgradeFeatures == null ? null : upgradeFeatures.get(slot);
    }

    /**
     * Pure refresh; server side effects are drained at the next safe pre-component phase.
     */
    public final void refreshUpgradeComponents(TileComponentUpgrade upgrades, boolean installedChange) {
        if (upgradeFeatures != null) upgradeFeatures.refresh(upgrades, installedChange);
    }

    public final void notifyUpgradeFeatureContentsChanged() {
        if (upgradeFeatures != null) upgradeFeatures.contentsChanged();
    }

    /**
     * Parameter/layout batches invalidate runtime feature state without inventing install/remove events.
     */
    public final void notifyUpgradeParametersRecalculated() {
        if (upgradeFeatures != null) upgradeFeatures.restored();
    }

    /**
     * The direction this block is facing.
     */
    public EnumFacing facing = EnumFacing.NORTH;

    public EnumFacing clientFacing = facing;

    @Nullable
    private AxisAlignedBB cachedBoundingBlockRenderBounds;
    @Nullable
    private BlockPos cachedBoundingBlockRenderPos;
    @Nullable
    private EnumFacing cachedBoundingBlockRenderFacing;
    private boolean boundingBlockRenderBoundsResolved;

    /**
     * The players currently using this block.
     */
    public Set<EntityPlayer> playersUsing = new ReferenceOpenHashSet<>();

    /**
     * A timer used to send packets to clients.
     */
    public int ticker;

    public boolean redstone = false;
    public boolean redstoneLastTick = false;

    public boolean doAutoSync = true;

    private final List<ITileComponent> components = new ArrayList<>();
    @Nullable
    private TileComponentFrequency registeredFrequencyComponent;
    @Nullable
    private TileComponentUpgrade registeredUpgradeComponent;
    @Nullable
    private volatile TileComponentUpgrade tickingUpgradeComponent;
    @Nullable
    private TileComponentChunkLoader registeredChunkLoaderComponent;
    @Nullable
    private TileComponentEjector registeredEjectorComponent;

    public final void addComponent(ITileComponent component) {
        Objects.requireNonNull(component);
        if (components.contains(component)) throw new IllegalArgumentException("Component already registered");
        if (component instanceof TileComponentFrequency frequency) {
            if (registeredFrequencyComponent != null)
                throw new IllegalArgumentException("Duplicate frequency component");
            registeredFrequencyComponent = frequency;
        } else if (component instanceof TileComponentUpgrade upgrade) {
            if (registeredUpgradeComponent != null) throw new IllegalArgumentException("Duplicate upgrade component");
            registeredUpgradeComponent = upgrade;
            tickingUpgradeComponent = upgrade;
        } else if (component instanceof TileComponentChunkLoader chunkLoader) {
            if (registeredChunkLoaderComponent != null)
                throw new IllegalArgumentException("Duplicate chunk loader component");
            registeredChunkLoaderComponent = chunkLoader;
        } else if (component instanceof TileComponentEjector ejector) {
            if (registeredEjectorComponent != null) throw new IllegalArgumentException("Duplicate ejector component");
            registeredEjectorComponent = ejector;
        }
        components.add(component);
    }

    public final List<ITileComponent> getComponents() {
        return java.util.Collections.unmodifiableList(components);
    }

    /**
     * The registered component keeps its lifecycle ownership while its idle tick is suspended.
     */
    public final void setUpgradeComponentTicking(TileComponentUpgrade component, boolean needed) {
        if (registeredUpgradeComponent == component) tickingUpgradeComponent = needed ? component : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        prepareUpgradeComponents();
        if (upgradeFeatures != null) upgradeFeatures.restored();
        if (isRemote()) {
            Mekanism.packetHandler.sendToServer(new DataRequestMessage(Coord4D.get(this)));
        }
    }

    @Override
    public void doRestrictedTick() {
        asyncUpdateSubmitted = false;
        beginServerTick();
        if (checkInvalidBlock()) {
            return;
        }

        if (!isRemote()) {
            tickServerPreComponents();
            tickServerComponents();
        }
        //TODO：切换为四种状态：同时更新,客户端更新,服务端更新，服务端异步更新
        if (!isRemote()) {
            tickServerEjector();
            onUpdateServer(); //服务端更新
            if (supportsAsync() && !trySkipAsyncUpdate()) { //如果支持异步
                Mekanism.EXECUTE_MANAGER.addMachineTask(this::runAsyncUpdateServer); //进行服务端异步更新
                asyncUpdateSubmitted = true;
            }
        } else {
            onUpdateClient(); //进行客户端更新
        }
        onUpdate(); //最后同时更新

        if (!isRemote() && this instanceof TileEntityElectricBlock electricBlock) {
            electricBlock.trackEnergyInputRate();
        }

        if (!isRemote() && doAutoSync && !playersUsing.isEmpty()) {
            if (supportsAsync()) {
                Mekanism.EXECUTE_MANAGER.addSyncTask(() -> playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this), (EntityPlayerMP) player)));
            } else {
                playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this), (EntityPlayerMP) player));
            }
        }

        ticker++;
        redstoneLastTick = redstone;
    }


    private boolean checkInvalidBlock() {
        if (!isRemote() && MekanismConfig.current().general.destroyDisabledBlocks.val()) {
            MachineType type = MachineType.get(getBlockType(), getBlockMetadata());
            if (type != null && !type.isEnabled()) {
                Mekanism.logger.info("Destroying machine of type '{}' at coords {} as according to config.", type.getBlockName(), Coord4D.get(this));
                world.setBlockToAir(getPos());
                return true;
            }
        }
        return false;
    }

    /**
     * Modern component ownership: the lifecycle list is never a generic ticking list.
     */
    protected void tickServerComponents() {
        if (registeredFrequencyComponent != null) registeredFrequencyComponent.tickServer();
        TileComponentUpgrade upgrade = tickingUpgradeComponent;
        if (upgrade != null) upgrade.tickServer();
        if (registeredChunkLoaderComponent != null) registeredChunkLoaderComponent.tickServer();
    }

    /**
     * Machine I/O follows core components and precedes this port's asynchronous machine work.
     */
    protected void tickServerEjector() {
        if (registeredEjectorComponent != null) registeredEjectorComponent.tickServer();
    }

    /**
     * Runs optional-module machine work before ordinary ejector/configuration components.
     */
    final void tickServerPreComponents() {
        prepareUpgradeComponents();
        if (upgradeFeatures != null) upgradeFeatures.tickServer();
        onUpdateServerPreComponents();
    }

    /**
     * Server-side extension point for tile-owned output work which must run after optional
     * modules have inspected the previous tick's result, but before ordinary components eject.
     */
    protected void onUpdateServerPreComponents() {
    }

    /**
     * Clears transient component guards before the current server tick is evaluated.
     */
    void beginServerTick() {
        serverEjectionSuppressedForCurrentTick = false;
    }

    /**
     * Prevents this tile's ordinary ejector from racing an owned server-side transfer.
     */
    public final void suppressServerEjectionForCurrentTick() {
        serverEjectionSuppressedForCurrentTick = true;
    }

    /**
     * Returns whether an earlier server pre-component hook retained output ownership.
     */
    public final boolean isServerEjectionSuppressedForCurrentTick() {
        return serverEjectionSuppressedForCurrentTick;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        components.forEach(component -> component.trackForMainContainer(container));
        components.stream().filter(TileComponentUpgrade.class::isInstance)
                .map(TileComponentUpgrade.class::cast)
                .forEach(component -> container.startTracking(component, component));
    }


    @Override
    public void updateContainingBlockInfo() {
        super.updateContainingBlockInfo();
        onAdded();
    }

    public void open(EntityPlayer player) {
        playersUsing.add(player);
    }

    public void close(EntityPlayer player) {
        playersUsing.remove(player);
    }

    public boolean canPlayerOpenGui(EntityPlayer player) {
        if (MekanismConfig.current().mekce.AllowMultiplePlayersOpenSameMachineGui.val()) {
            return true;
        }
        playersUsing.removeIf(this::isStaleGuiUser);
        return playersUsing.isEmpty() || playersUsing.contains(player);
    }

    private boolean isStaleGuiUser(EntityPlayer player) {
        if (player == null || player.isDead || player.world != world || !(player.openContainer instanceof MekanismTileContainer)) {
            return true;
        }
        MekanismTileContainer<?> container = (MekanismTileContainer<?>) player.openContainer;
        return container.getTileEntity() != this;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            facing = EnumFacing.byIndex(dataStream.readInt());
            redstone = dataStream.readBoolean();
            if (clientFacing != facing) {
                MekanismUtils.updateBlock(world, getPos());
                world.notifyNeighborsOfStateChange(getPos(), world.getBlockState(getPos()).getBlock(), true);
                clientFacing = facing;
            }
            components.forEach(components -> components.read(dataStream));
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(facing == null ? -1 : facing.ordinal());
        data.add(redstone);
        components.forEach(component -> component.write(data));
        return data;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (upgradeFeatures != null) upgradeFeatures.invalidated();
        clearCachedEnergyAcceptors();
        components.forEach(ITileComponent::invalidate);
    }

    @Override
    public void onChunkUnload() {
        if (upgradeFeatures != null) upgradeFeatures.unload();
        super.onChunkUnload();
    }

    public boolean supportsAsync() {
        Class<?> declaringClass = ASYNC_UPDATE_DECLARING_CLASS.get(getClass());
        if (declaringClass == TileEntityBasicBlock.class) {
            return false;
        }
        return declaringClass != TileEntityElectricBlock.class || ((TileEntityElectricBlock) this).hasTileSyncTask();
    }

    public final boolean wasAsyncUpdateSubmitted() {
        return asyncUpdateSubmitted;
    }

    protected boolean supportsAsyncIdleSkipping() {
        return false;
    }

    /**
     * Called with the local container lock; no recipe lookup or capability discovery belongs here.
     */
    protected boolean isAsyncUpdateIdle() {
        return false;
    }

    /**
     * Small tile-owned bookkeeping which must continue even without a worker task.
     */
    protected void onAsyncUpdateSkipped() {
    }

    private boolean trySkipAsyncUpdate() {
        if (!MekanismConfig.current().mekce.SkipIdleMachineAsyncTasks.val() || !supportsAsyncIdleSkipping() ||
                !containerTransactionLock.tryLock()) {
            return false;
        }
        try {
            if (!isAsyncUpdateIdle()) {
                return false;
            }
            onAsyncUpdateSkipped();
            return true;
        } finally {
            containerTransactionLock.unlock();
        }
    }

    private void runAsyncUpdateServer() {
        if (hasCrossMachineAsyncOperations()) {
            onAsyncUpdateServer();
        } else {
            runContainerTransaction(this::onAsyncUpdateServer);
        }
    }

    /**
     * Marks legacy async updates which perform cross-machine or network calls and therefore cannot hold the local
     * container transaction around the entire update. These updates are intentionally excluded from the automatic
     * whole-update transaction until they can be split into local and cross-machine phases.
     */
    protected boolean hasCrossMachineAsyncOperations() {
        return false;
    }

    @Override
    public final void runContainerTransaction(Runnable action) {
        Objects.requireNonNull(action, "Container transaction action cannot be null");
        containerTransactionLock.lock();
        try {
            action.run();
        } finally {
            containerTransactionLock.unlock();
        }
    }

    @Override
    public final <T> T callContainerTransaction(Supplier<T> action) {
        Objects.requireNonNull(action, "Container transaction action cannot be null");
        containerTransactionLock.lock();
        try {
            return action.get();
        } finally {
            containerTransactionLock.unlock();
        }
    }

    /**
     * Attempts a local container operation without waiting for another machine transaction. This is used by external
     * handlers so that two machines transferring to each other cannot deadlock while both are asynchronously updating.
     */
    protected final boolean tryRunContainerTransaction(Runnable action) {
        Objects.requireNonNull(action, "Container transaction action cannot be null");
        if (!containerTransactionLock.tryLock()) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            containerTransactionLock.unlock();
        }
    }

    /**
     * Attempts a local container operation and returns the supplied busy value if another thread owns the transaction.
     */
    protected final <T> T tryCallContainerTransaction(Supplier<T> action, Supplier<T> busyValue) {
        Objects.requireNonNull(action, "Container transaction action cannot be null");
        Objects.requireNonNull(busyValue, "Container transaction busy value cannot be null");
        if (!containerTransactionLock.tryLock()) {
            return busyValue.get();
        }
        try {
            return action.get();
        } finally {
            containerTransactionLock.unlock();
        }
    }

    /**
     * Update call for machines. Use instead of updateEntity -- it's called every tick.
     */
    public void onUpdate() {
    }


    /**
     * Update call for machines. Use instead of updateEntity -- it's called every tick on the client side.
     */
    protected void onUpdateClient() {
    }

    /**
     * Update call for machines. Use instead of updateEntity -- it's called every tick on the server side.
     */
    protected void onUpdateServer() {
    }

    /**
     * Async Update call for machines. Use instead of updateEntity -- it's called every tick on the server side.
     */
    protected void onAsyncUpdateServer() {
    }


    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        prepareUpgradeComponents();
        if (nbtTags.hasKey("facing")) {
            facing = EnumFacing.byIndex(nbtTags.getInteger("facing"));
        }
        redstone = nbtTags.getBoolean("redstone");
        components.forEach(component -> component.read(nbtTags));
        if (upgradeFeatures != null) {
            upgradeFeatures.refresh(registeredUpgradeComponent, false);
            upgradeFeatures.restored();
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        prepareUpgradeComponents();
        if (facing != null) {
            nbtTags.setInteger("facing", facing.ordinal());
        }
        nbtTags.setBoolean("redstone", redstone);
        components.forEach(component -> component.write(nbtTags));
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return capability == Capabilities.TILE_NETWORK_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (capability == Capabilities.TILE_NETWORK_CAPABILITY) {
            return Capabilities.TILE_NETWORK_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    public void setFacing(@Nonnull EnumFacing direction) {
        if (canSetFacing(direction)) {
            facing = direction;
        }
        if (facing != clientFacing && !isRemote()) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
            clientFacing = facing;
        }
    }

    /**
     * Whether or not this block's orientation can be changed to a specific direction. True by default.
     *
     * @param facing - facing to check
     * @return if the block's orientation can be changed
     */
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return true;
    }

    public boolean isPowered() {
        return redstone;
    }

    public boolean wasPowered() {
        return redstoneLastTick;
    }

    public void onPowerChange() {
    }

    public void onNeighborChange(Block block) {
        if (!isRemote()) {
            if (upgradeFeatures != null) upgradeFeatures.neighborChanged();
            updatePower();
            clearCachedEnergyAcceptors();
        }
    }

    public final EnergyAcceptorWrapper getCachedEnergyAcceptor(TileEntity tile, EnumFacing side) {
        int index = side.ordinal();
        if (!cachedEnergyAcceptorResolved[index] || cachedEnergyAcceptorTiles[index] != tile) {
            cachedEnergyAcceptorTiles[index] = tile;
            cachedEnergyAcceptors[index] = EnergyAcceptorWrapper.get(tile, side);
            cachedEnergyAcceptorResolved[index] = true;
        }
        return cachedEnergyAcceptors[index];
    }

    public final IGasHandler getCachedGasHandler(TileEntity tile, EnumFacing side) {
        int index = side.ordinal();
        if (!cachedGasHandlerResolved[index] || cachedGasHandlerTiles[index] != tile) {
            cachedGasHandlerTiles[index] = tile;
            cachedGasHandlers[index] = CapabilityUtils.getCapability(tile, Capabilities.GAS_HANDLER_CAPABILITY, side);
            cachedGasHandlerResolved[index] = true;
        }
        return cachedGasHandlers[index];
    }

    public final IFluidHandler getCachedFluidHandler(TileEntity tile, EnumFacing side) {
        int index = side.ordinal();
        if (!cachedFluidHandlerResolved[index] || cachedFluidHandlerTiles[index] != tile) {
            cachedFluidHandlerTiles[index] = tile;
            cachedFluidHandlers[index] = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
            cachedFluidHandlerResolved[index] = true;
        }
        return cachedFluidHandlers[index];
    }

    public final IItemHandler getCachedNeighborItemHandler(EnumFacing side) {
        int index = side.ordinal();
        TileEntity tile = MekanismUtils.getTileEntity(world, getPos().offset(side));
        if (!cachedItemHandlerResolved[index] || cachedItemHandlerTiles[index] != tile) {
            cachedItemHandlerTiles[index] = tile;
            cachedItemHandlers[index] = CapabilityUtils.getCapability(tile, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
            cachedItemHandlerResolved[index] = true;
        }
        return cachedItemHandlers[index];
    }

    private void clearCachedEnergyAcceptors() {
        java.util.Arrays.fill(cachedEnergyAcceptorTiles, null);
        java.util.Arrays.fill(cachedEnergyAcceptors, null);
        java.util.Arrays.fill(cachedEnergyAcceptorResolved, false);
        java.util.Arrays.fill(cachedGasHandlerTiles, null);
        java.util.Arrays.fill(cachedGasHandlers, null);
        java.util.Arrays.fill(cachedGasHandlerResolved, false);
        java.util.Arrays.fill(cachedFluidHandlerTiles, null);
        java.util.Arrays.fill(cachedFluidHandlers, null);
        java.util.Arrays.fill(cachedFluidHandlerResolved, false);
        java.util.Arrays.fill(cachedItemHandlerTiles, null);
        java.util.Arrays.fill(cachedItemHandlers, null);
        java.util.Arrays.fill(cachedItemHandlerResolved, false);
    }

    private void updatePower() {
        boolean power = world.getRedstonePowerFromNeighbors(getPos()) > 0;
        if (redstone != power) {
            redstone = power;
            Mekanism.packetHandler.sendUpdatePacket(this);
            onPowerChange();
        }
    }

    /**
     * Called when block is placed in world
     */
    public void onAdded() {
        if (!isRemote()) {
            updatePower();
        }
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (this instanceof IBoundingBlock boundingBlock) {
            BlockPos currentPos = getPos();
            if (!boundingBlockRenderBoundsResolved || !currentPos.equals(cachedBoundingBlockRenderPos) || facing != cachedBoundingBlockRenderFacing) {
                AxisAlignedBB declaredBounds;
                try {
                    declaredBounds = boundingBlock.getBoundingBlockRenderBounds(currentPos);
                } catch (RuntimeException ignored) {
                    declaredBounds = null;
                }
                cachedBoundingBlockRenderBounds = declaredBounds == null ? INFINITE_EXTENT_AABB : declaredBounds;
                cachedBoundingBlockRenderPos = currentPos.toImmutable();
                cachedBoundingBlockRenderFacing = facing;
                boundingBlockRenderBoundsResolved = true;
            }
            return cachedBoundingBlockRenderBounds == null ? INFINITE_EXTENT_AABB : cachedBoundingBlockRenderBounds;
        }
        return super.getRenderBoundingBox();
    }

    @SideOnly(Side.CLIENT)
    protected void invalidateBoundingBlockRenderBounds() {
        boundingBlockRenderBoundsResolved = false;
        cachedBoundingBlockRenderBounds = null;
        cachedBoundingBlockRenderPos = null;
        cachedBoundingBlockRenderFacing = null;
    }

}
