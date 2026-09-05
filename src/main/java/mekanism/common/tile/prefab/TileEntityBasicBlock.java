package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.IContainerTransaction;
import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import mekanism.api.IProcessingStateVersion;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.ITileComponent;
import mekanism.common.base.ITileNetwork;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.concurrent.TaskExecutor;
import mekanism.common.concurrent.AsyncPlanSafetyValidator;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.inventory.container.ITrackableContainer;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.RecipeExecutionPlan;
import mekanism.common.recipe.cache.RecipeRunSnapshot;
import mekanism.common.recipe.cache.AsyncMachinePlanSupport;
import mekanism.common.recipe.cache.RecipeRandomContext;
import mekanism.common.tile.base.TileEntityRestrictedTick;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * 基本方块类型
 */
@Interface(iface = "ic2.api.tile.IWrenchable", modid = MekanismHooks.IC2_MOD_ID)
public abstract class TileEntityBasicBlock extends TileEntityRestrictedTick implements ITileNetwork, ITrackableContainer,
      IContainerTransaction, IProcessingStateVersion {

    private static volatile Consumer<TileEntityBasicBlock> serverPreComponentTickListener = tile -> {
    };
    private static volatile ToLongFunction<TileEntityBasicBlock> asyncLeaseVersionProvider = tile -> 0;
    private static volatile ToLongFunction<TileEntityBasicBlock> asyncPortOwnershipVersionProvider = tile -> 0;

    private final ReentrantLock containerTransactionLock = new ReentrantLock();
    /** Optimistic-concurrency version for snapshots captured from this tile. */
    private final AtomicLong processingStateVersion = new AtomicLong();
    /** At most one calculation may be waiting for a commit for this tile. */
    private final AtomicReference<PendingAsyncPlan> pendingAsyncPlan = new AtomicReference<>();
    private boolean serverEjectionSuppressedForCurrentTick;

    private static final class PendingAsyncPlan {
        private final Object snapshot;
        private final long stateVersion;
        private final long recipeGeneration;
        private final long commitSequence;
        private volatile Object plan;
        private volatile Throwable failure;

        private PendingAsyncPlan(Object snapshot, long stateVersion,
              long recipeGeneration, long commitSequence) {
            this.snapshot = snapshot;
            this.stateVersion = stateVersion;
            this.recipeGeneration = recipeGeneration;
            this.commitSequence = commitSequence;
        }
    }

    /**
     * Installs the optional-module callback which runs immediately before tile components.
     * The callback is server-only and therefore observes the stable result of the previous
     * tick before ejectors and asynchronous machine work begin for the current tick.
     */
    public static void setServerPreComponentTickListener(Consumer<TileEntityBasicBlock> listener) {
        serverPreComponentTickListener = Objects.requireNonNull(listener,
              "Server pre-component tick listener cannot be null");
    }

    public static void setAsyncLeaseVersionProvider(ToLongFunction<TileEntityBasicBlock> provider) {
        asyncLeaseVersionProvider = Objects.requireNonNull(provider, "Async lease version provider cannot be null");
    }

    public static void setAsyncPortOwnershipVersionProvider(ToLongFunction<TileEntityBasicBlock> provider) {
        asyncPortOwnershipVersionProvider = Objects.requireNonNull(provider,
              "Async port ownership version provider cannot be null");
    }

    public final long getAsyncLeaseVersion() {
        return Math.max(0, asyncLeaseVersionProvider.applyAsLong(this));
    }

    public final long getAsyncPortOwnershipVersion() {
        return Math.max(0, asyncPortOwnershipVersionProvider.applyAsLong(this));
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

    public List<ITileComponent> components = new ArrayList<>();

    @Override
    public void onLoad() {
        super.onLoad();
        markProcessingStateChanged();
        if (isRemote()) {
            Mekanism.packetHandler.sendToServer(new DataRequestMessage(Coord4D.get(this)));
        }
    }

    @Override
    public void doRestrictedTick() {
        beginServerTick();
        if (!isRemote()) {
            // Establish a new optimistic-concurrency boundary for every server tick.
            // Container listeners may advance it further when a real mutation occurs.
            markProcessingStateChanged();
        }
        if (checkInvalidBlock()) {
            return;
        }

        if (!isRemote()) {
            tickServerPreComponents();
        }
        tickComponents();
        //TODO：切换为四种状态：同时更新,客户端更新,服务端更新，服务端异步更新
        if (!isRemote()) {
            onUpdateServer(); //服务端更新
            IAsyncMachinePlanner<?, ?> planner = getAsyncMachinePlanner();
            if (planner != null) {
                scheduleAsyncPlan(planner);
            } else {
                // Compatibility bridge for legacy tiles. Their old async callback is
                // deliberately executed on the server thread until that tile is
                // migrated to an explicit planner. This preserves processing while
                // ensuring legacy code cannot race a live container.
                onAsyncUpdateServer();
            }
        } else {
            onUpdateClient(); //进行客户端更新
        }
        onUpdate(); //最后同时更新

        if (!isRemote() && this instanceof TileEntityElectricBlock electricBlock) {
            electricBlock.trackEnergyInputRate();
        }

        if (!isRemote() && doAutoSync && !playersUsing.isEmpty()) {
            if (getAsyncMachinePlanner() != null) {
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

    protected void tickComponents() {
        components.forEach(ITileComponent::tick);
    }

    /** Runs optional-module machine work before ordinary ejector/configuration components. */
    void tickServerPreComponents() {
        serverPreComponentTickListener.accept(this);
        onUpdateServerPreComponents();
    }

    /**
     * Server-side extension point for tile-owned output work which must run after optional
     * modules have inspected the previous tick's result, but before ordinary components eject.
     */
    protected void onUpdateServerPreComponents() {
    }

    /** Clears transient component guards before the current server tick is evaluated. */
    void beginServerTick() {
        serverEjectionSuppressedForCurrentTick = false;
    }

    /** Prevents this tile's ordinary ejector from racing an owned server-side transfer. */
    public final void suppressServerEjectionForCurrentTick() {
        serverEjectionSuppressedForCurrentTick = true;
    }

    /** Returns whether an earlier server pre-component hook retained output ownership. */
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
        markProcessingStateChanged();
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
        markProcessingStateChanged();
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
        markProcessingStateChanged();
        cancelPendingAsyncPlan(null);
        AsyncMachinePlanSupport.invalidateCompiledSource(this);
        super.invalidate();
        if (components != null) {
            components.forEach(ITileComponent::invalidate);
        }
    }

    /**
     * A chunk unload does not necessarily invalidate a tile immediately. Treat it
     * as an asynchronous ownership boundary so a completed worker plan cannot be
     * committed after the tile has left the loaded world.
     */
    @Override
    public void onChunkUnload() {
        markProcessingStateChanged();
        cancelPendingAsyncPlan(null);
        AsyncMachinePlanSupport.invalidateCompiledSource(this);
        super.onChunkUnload();
    }

    /**
     * Explicit planner selection. A tile only enters the worker pool when it
     * implements the three-stage planner contract (or overrides this method to
     * return a standalone planner object). There is intentionally no reflection
     * based opt-in.
     */
    @Nullable
    protected IAsyncMachinePlanner<?, ?> getAsyncMachinePlanner() {
        return this instanceof IAsyncMachinePlanner ? (IAsyncMachinePlanner<?, ?>) this : null;
    }

    /** Returns whether this tile currently has a calculation awaiting commit. */
    public final boolean hasPendingAsyncPlan() {
        return pendingAsyncPlan.get() != null;
    }

    /** Cancels a pending calculation, normally from invalidation or replacement. */
    public final void cancelPendingAsyncPlan(@Nullable Throwable cause) {
        PendingAsyncPlan pending = pendingAsyncPlan.getAndSet(null);
        if (pending != null) {
            invokeDiscarded(pending, cause);
        }
    }

    private void scheduleAsyncPlan(IAsyncMachinePlanner<?, ?> planner) {
        if (pendingAsyncPlan.get() != null || isInvalid()) {
            return;
        }
        IAsyncPlanCalculator calculator = planner.getAsyncPlanCalculator();
        if (!AsyncPlanSafetyValidator.isDetached(calculator)) {
            runPlannerSynchronously(planner);
            return;
        }
        Object snapshot;
        try {
            snapshot = planner.captureSnapshot();
        } catch (Throwable error) {
            Mekanism.logger.warn("Unable to capture async machine snapshot for {}", getClass().getName(), error);
            return;
        }
        if (snapshot == null) {
            return;
        }
        if (!AsyncPlanSafetyValidator.isDetachedValue(snapshot)) {
            runPlannerSynchronously(planner, snapshot);
            return;
        }
        PendingAsyncPlan pending = new PendingAsyncPlan(snapshot,
              getProcessingStateVersion(), RecipeHandler.getGlobalRecipeGeneration(),
              Mekanism.EXECUTE_MANAGER.reservePlanCommitSequence());
        if (!pendingAsyncPlan.compareAndSet(null, pending)) {
            return;
        }
        try {
            Mekanism.EXECUTE_MANAGER.addPlanCommitTask(pending.commitSequence,
                  () -> finishAsyncPlan(pending), () -> pendingAsyncPlan.compareAndSet(pending, null));
            Mekanism.EXECUTE_MANAGER.addTask(() -> calculateAsyncPlan(pending, calculator));
        } catch (Throwable error) {
            if (pendingAsyncPlan.compareAndSet(pending, null)) {
                invokeDiscarded(pending, error);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void calculateAsyncPlan(PendingAsyncPlan pending, IAsyncPlanCalculator calculator) {
        try {
            pending.plan = calculator.calculate(pending.snapshot);
        } catch (Throwable error) {
            pending.failure = error;
        }
    }

    @SuppressWarnings("unchecked")
    private void finishAsyncPlan(PendingAsyncPlan pending) {
        if (pendingAsyncPlan.get() != pending) {
            return;
        }
        String phase = "validate";
        try {
            IAsyncMachinePlanner planner = (IAsyncMachinePlanner) getAsyncMachinePlanner();
            if (planner == null) return;
            if (pending.failure != null) {
                Mekanism.logger.warn("Async machine calculation failed for {} at {}", getClass().getName(), getPos(), pending.failure);
                invokeDiscarded(pending, pending.failure);
                return;
            }
            if (pending.plan == null || !AsyncPlanSafetyValidator.isDetachedValue(pending.plan) ||
                  !isAsyncPlanEnvironmentValid(pending) || !planner.isPlanStillValid(pending.snapshot, pending.plan) ||
                  !isAsyncPlanVersionValid(pending)) {
                invokeDiscarded(pending, null);
                return;
            }
            phase = "commit";
            if (pending.snapshot instanceof RecipeRunSnapshot) {
                RecipeRandomContext.run(((RecipeRunSnapshot) pending.snapshot).getRandomSeed(),
                      () -> planner.commitPlan(pending.snapshot, pending.plan));
            } else {
                planner.commitPlan(pending.snapshot, pending.plan);
            }
        } catch (Throwable error) {
            Mekanism.logger.warn("Async machine {} failed for {} at {}", phase, getClass().getName(), getPos(), error);
            invokeDiscarded(pending, error);
        } finally {
            pendingAsyncPlan.compareAndSet(pending, null);
        }
    }

    private boolean isAsyncPlanEnvironmentValid(PendingAsyncPlan pending) {
        if (!isAsyncPlanVersionValid(pending)) {
            return false;
        }
        if (pending.snapshot instanceof RecipeRunSnapshot && pending.plan instanceof RecipeExecutionPlan &&
            !((RecipeExecutionPlan) pending.plan).isValidFor((RecipeRunSnapshot) pending.snapshot)) {
            return false;
        }
        if (world != null) {
            if (world.isRemote || !world.isBlockLoaded(getPos(), false) || world.getTileEntity(getPos()) != this) {
                return false;
            }
        }
        return true;
    }

    private boolean isAsyncPlanVersionValid(PendingAsyncPlan pending) {
        if (isInvalid() || getProcessingStateVersion() != pending.stateVersion ||
              RecipeHandler.getGlobalRecipeGeneration() != pending.recipeGeneration) {
            return false;
        }
        if (pending.snapshot instanceof RecipeRunSnapshot && pending.plan instanceof RecipeExecutionPlan) {
            return ((RecipeExecutionPlan) pending.plan).isValidFor((RecipeRunSnapshot) pending.snapshot);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void invokeDiscarded(PendingAsyncPlan pending, @Nullable Throwable cause) {
        IAsyncMachinePlanner planner = (IAsyncMachinePlanner) getAsyncMachinePlanner();
        if (planner == null) return;
        try {
            planner.onPlanDiscarded(pending.snapshot, pending.plan, cause);
        } catch (Throwable error) {
            Mekanism.logger.warn("Async machine discard callback failed for {}", getClass().getName(), error);
        }
    }

    /**
     * Legacy entry point retained for binary compatibility. It is never scheduled
     * by the base class and therefore never acquires the container lock on a worker.
     */
    public boolean supportsAsync() {
        IAsyncMachinePlanner<?, ?> planner = getAsyncMachinePlanner();
        return planner != null && AsyncPlanSafetyValidator.isDetached(planner.getAsyncPlanCalculator());
    }

    @SuppressWarnings("unchecked")
    private void runPlannerSynchronously(IAsyncMachinePlanner planner) {
        try {
            Object snapshot = planner.captureSnapshot();
            if (snapshot == null) return;
            runPlannerSynchronously(planner, snapshot);
        } catch (Throwable error) {
            Mekanism.logger.warn("Synchronous machine planner failed for {}", getClass().getName(), error);
        }
    }

    @SuppressWarnings("unchecked")
    private void runPlannerSynchronously(IAsyncMachinePlanner planner, Object snapshot) {
        try {
            Object plan = planner.calculatePlan(snapshot);
            if (plan != null && planner.isPlanStillValid(snapshot, plan)) {
                if (snapshot instanceof RecipeRunSnapshot) {
                    RecipeRandomContext.run(((RecipeRunSnapshot) snapshot).getRandomSeed(), () -> planner.commitPlan(snapshot, plan));
                } else {
                    planner.commitPlan(snapshot, plan);
                }
            } else {
                planner.onPlanDiscarded(snapshot, plan, null);
            }
        } catch (Throwable error) {
            Mekanism.logger.warn("Synchronous machine planner failed for {}", getClass().getName(), error);
        }
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
     * Legacy processing hook. Despite the historical name it is now invoked only on
     * the server thread, either directly for non-planned tiles or during plan commit.
     */
    @Deprecated
    protected void onAsyncUpdateServer() {
        if (TaskExecutor.isWorkerThread()) {
            throw new IllegalStateException("Legacy machine update cannot run on a worker thread");
        }
    }


    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        markProcessingStateChanged();
        if (nbtTags.hasKey("facing")) {
            facing = EnumFacing.byIndex(nbtTags.getInteger("facing"));
        }
        redstone = nbtTags.getBoolean("redstone");
        if (nbtTags.hasKey("asyncProcessingStateVersion")) {
            long savedVersion = nbtTags.getLong("asyncProcessingStateVersion");
            if (savedVersion >= 0) {
                processingStateVersion.set(Math.max(processingStateVersion.get(), savedVersion));
            }
        }
        components.forEach(component -> component.read(nbtTags));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        if (facing != null) {
            nbtTags.setInteger("facing", facing.ordinal());
        }
        nbtTags.setBoolean("redstone", redstone);
        nbtTags.setLong("asyncProcessingStateVersion", getProcessingStateVersion());
        components.forEach(component -> component.write(nbtTags));
    }

    @Override
    public long getProcessingStateVersion() {
        return processingStateVersion.get();
    }

    /** Returns the current version without exposing the mutable counter. */
    public final long captureProcessingStateVersion() {
        return getProcessingStateVersion();
    }

    /** Alias retained for integrations which use a shorter name. */
    public final long getStateVersion() {
        return getProcessingStateVersion();
    }

    /**
     * Invalidates all plans captured before this call. This method is safe to call
     * from container listeners and other server-side mutation hooks.
     */
    protected final long markProcessingStateChanged() {
        long current = processingStateVersion.get();
        while (true) {
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("Processing state version exhausted");
            }
            if (processingStateVersion.compareAndSet(current, current + 1)) {
                return current + 1;
            }
            current = processingStateVersion.get();
        }
    }

    /** Public bridge for capability/integration code which cannot subclass the tile. */
    public final long invalidateProcessingState() {
        return markProcessingStateChanged();
    }

    public final boolean isProcessingStateCurrent(long version) {
        return getProcessingStateVersion() == version;
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
        EnumFacing previous = facing;
        if (canSetFacing(direction)) {
            facing = direction;
        }
        if (previous != facing) {
            markProcessingStateChanged();
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
            updatePower();
        }
    }

    private void updatePower() {
        boolean power = world.getRedstonePowerFromNeighbors(getPos()) > 0;
        if (redstone != power) {
            redstone = power;
            markProcessingStateChanged();
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
