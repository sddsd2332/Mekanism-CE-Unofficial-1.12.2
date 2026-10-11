package mekanism.common.tile.base;

import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.IActiveState;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.config.MekanismConfig;
import mekanism.common.interfaces.IOcclusionCulling;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可异步类型tile方块。
 */
//TODO：需要让它正确的运行，虽然可以运行
public class TileEntitySynchronized extends TileEntity implements IOcclusionCulling {

    private final AtomicBoolean inUpdateTask = new AtomicBoolean();
    private final AtomicBoolean inMarkTask = new AtomicBoolean();
    private final AtomicBoolean inSaveTask = new AtomicBoolean();

    private volatile boolean requireUpdateLight = false;
    private final AtomicBoolean requireUpdateComparatorOutputLevel = new AtomicBoolean();
    private long lastSaveTick = Long.MIN_VALUE;
    private World lastSaveWorld;
    private net.minecraft.util.math.BlockPos lastSavePosition;
    private int lastNotifiedComparatorLevel = -1;
    private volatile int currentRedstoneLevel;
    // Comparator participation is static for a tile class. Cache the interface lookup and
    // support decision so markDirty() does not repeat it on every content update.
    private volatile byte comparatorSupportState;
    private volatile IComparatorSupport comparatorSupport;
    /**
     * 0 = unknown, 1 = disabled, 2 = enabled; invalidated only by support/upgrade changes.
     */
    private volatile byte comparatorChainState;
    private static final ClassValue<Boolean> LOCAL_COMPARATOR = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Class<?> owner = type.getMethod("getRedstoneLevel").getDeclaringClass();
                boolean inventorySignal = owner == mekanism.common.tile.factory.TileEntityFactory.class ||
                        owner == mekanism.common.tile.prefab.TileEntityOperationalMachine.class ||
                        owner == mekanism.common.tile.laser.TileEntityLaserTractorBeam.class;
                return inventorySignal && type.getMethod("getInventoryComparatorLevel").getDeclaringClass() ==
                        mekanism.common.tile.prefab.TileEntityContainerBlock.class &&
                        type.getMethod("getInventorySlots", net.minecraft.util.EnumFacing.class).getDeclaringClass() ==
                                mekanism.common.tile.prefab.TileEntityContainerBlock.class;
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return false;
            }
        }
    };
    private byte localComparatorKind;
    private boolean applyingQueuedMark;

    private long lastUpdateTick = 0;

    public final void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        readCustomNBT(compound);
        comparatorChainState = 0;
        lastNotifiedComparatorLevel = -1;
        if (hasEnabledComparator()) {
            currentRedstoneLevel = Math.max(0, Math.min(15, compound.getInteger("currentRedstoneLevel")));
        } else {
            currentRedstoneLevel = 0;
        }
    }

    public void readCustomNBT(NBTTagCompound compound) {
    }

    public void readNetNBT(NBTTagCompound compound) {
    }

    @Nonnull
    public final NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        NBTTagCompound writeToNBT = super.writeToNBT(compound);
        writeCustomNBT(writeToNBT);
        if (hasEnabledComparator()) {
            writeToNBT.setInteger("currentRedstoneLevel", currentRedstoneLevel);
        }
        return writeToNBT;
    }

    public void writeCustomNBT(NBTTagCompound compound) {
    }

    public void writeNetNBT(NBTTagCompound compound) {
    }

    /**
     * Writes the data sent by vanilla chunk/update-tag synchronization. The default preserves the
     * legacy behavior; tiles with large persistent state may override this with a bounded view.
     */
    protected void writeUpdateNBT(NBTTagCompound compound) {
        writeCustomNBT(compound);
    }

    /**
     * Reads the bounded counterpart written by {@link #writeUpdateNBT(NBTTagCompound)}.
     */
    protected void readUpdateNBT(NBTTagCompound compound) {
        readCustomNBT(compound);
    }


    @Nonnull
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound compound = new NBTTagCompound();
        super.writeToNBT(compound);
        writeUpdateNBT(compound);
        writeNetNBT(compound);
        return new SPacketUpdateTileEntity(getPos(), 255, compound);
    }


    @Nonnull
    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound compound = new NBTTagCompound();
        super.writeToNBT(compound);
        writeUpdateNBT(compound);
        return compound;
    }

/*
    @Override
    public final void onDataPacket(@Nonnull NetworkManager manager, @Nonnull SPacketUpdateTileEntity packet) {
        super.onDataPacket(manager, packet);
        readCustomNBT(packet.getNbtCompound());
        readNetNBT(packet.getNbtCompound());
    }
 */

    @Override
    public void handleUpdateTag(@Nonnull NBTTagCompound tag) {
        // The super implementation of handleUpdateTag is to call this readFromNBT. But, the given TagCompound
        // only has x/y/z/id data, so our readFromNBT will set a bunch of default values which are wrong.
        // So simply call the super's readFromNBT, to let Forge do whatever it wants, but don't treat this like
        // a full NBT object, don't pass it to our custom read methods.
        readUpdateNBT(tag);
        readNetNBT(tag);
        super.readFromNBT(tag);
    }

    @SuppressWarnings("ConstantValue")
    public void markNoUpdate() {
        if (world != null && !world.isRemote && !isServerThread()) {
            markNoUpdateSync();
            return;
        }
        // Release before callbacks: a callback requesting another update must not be lost.
        boolean queued = inMarkTask.getAndSet(false);
        if (!queued && hasEnabledComparator()) invalidateComparatorSnapshot();
        World world = getWorld();
        if (world == null) {
            inMarkTask.set(false);
            return;
        }
        // Queued content changes have already notified their physical containers. Keep subclass
        // markDirty hooks, but do not turn every energy-item NBT update into a full inventory scan.
        boolean previous = applyingQueuedMark;
        applyingQueuedMark = true;
        try {
            markDirty();
        } finally {
            applyingQueuedMark = previous;
        }
        updateLight();
        lastUpdateTick = world.getTotalWorldTime();
    }

    /**
     * Persistence and comparator invalidation are independent; neither broadcasts neighbors here.
     */
    @Override
    public void markDirty() {
        if ((!applyingQueuedMark || !isServerThread()) && hasEnabledComparator()) invalidateComparatorSnapshot();
        if (world == null || isInvalid()) return;
        if (!world.isRemote && !isServerThread()) {
            markNoUpdateSync();
            return;
        }
        markForSave();
        if (!world.isRemote && hasEnabledComparator()) requestComparatorUpdate();
        onMarkDirty();
    }

    /**
     * Main-thread content side effects, kept separate from save-only updates.
     */
    protected void onMarkDirty() {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lastNotifiedComparatorLevel = -1;
        if (hasEnabledComparator()) updateComparatorOutputLevelSync();
    }

    private boolean isServerThread() {
        return world != null && world.getMinecraftServer() != null &&
                world.getMinecraftServer().isCallingFromMinecraftThread();
    }

    /**
     * Save-only requests deliberately do not invoke subclass markDirty recipe/render hooks.
     */
    public final void markForSaveSync() {
        if (world == null || world.isRemote) return;
        if (inSaveTask.compareAndSet(false, true)) {
            try {
                Mekanism.EXECUTE_MANAGER.addTESaveTask(this);
            } catch (RuntimeException error) {
                inSaveTask.set(false);
                throw error;
            }
        }
    }

    public final void markForSave() {
        if (world == null || world.isRemote) {
            inSaveTask.set(false);
            return;
        }
        if (!isServerThread()) {
            markForSaveSync();
            return;
        }
        inSaveTask.set(false);
        long tick = world.getTotalWorldTime();
        if (lastSaveWorld == world && pos.equals(lastSavePosition) && lastSaveTick == tick) return;
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && !isInvalid()) {
            chunk.markDirty();
            lastSaveWorld = world;
            lastSavePosition = pos.toImmutable();
            lastSaveTick = tick;
        }
    }

    @SuppressWarnings("ConstantValue")
    public void updateLight() {
        if (!requireUpdateLight) {
            return;
        }
        World world = getWorld();
        if (world == null) {
            return;
        }
        world.markBlockRangeForRenderUpdate(pos, pos);
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IActiveState state) || state.lightUpdate() && MekanismConfig.current().client.machineEffects.val()) {
            MekanismUtils.updateAllLightTypes(world, pos);
        }
        requireUpdateLight = false;
    }

    public void markForUpdate() {
        markNoUpdate();
        notifyUpdate();
    }

    public void notifyUpdate() {
        if (world != null && !world.isRemote && !isServerThread()) {
            markForUpdateSync();
            return;
        }
        inUpdateTask.set(false);
        World world = getWorld();
        if (world == null) { //防止世界是无
            inUpdateTask.set(false);
            return;
        }
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    public void markNoUpdateSync() {
        if (hasEnabledComparator()) invalidateComparatorSnapshot();
        enqueueMarkNoUpdate();
    }

    /**
     * Native slot callbacks already marked the changed physical slots in the comparator tracker.
     */
    protected final void markTrackedContentsChangedSync() {
        enqueueMarkNoUpdate();
    }

    private void enqueueMarkNoUpdate() {
        // Forge can deserialize a tile before assigning its world. Do not enqueue a
        // mark task in that phase: the queued task cannot mark anything and would
        // leave inMarkTask latched, suppressing all later updates after placement.
        if (getWorld() == null) {
            return;
        }
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            markNoUpdate();
            return;
        }
        if (!inMarkTask.compareAndSet(false, true)) {
            return;
        }
        try {
            Mekanism.EXECUTE_MANAGER.addTEMarkNoUpdateTask(this);
        } catch (RuntimeException e) {
            inMarkTask.set(false);
            throw e;
        }
    }

    public void markChunkDirty() {
        markForSave();
    }

    /**
     * <p>markForUpdate 的同步实现，防止意料之外的 CME。</p>
     * <p>*** 只能保证 mekceu 自身对世界的线程安全 ***</p>
     */
    public void markForUpdateSync() {
        if (getWorld() == null) {
            return;
        }
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            if (!inUpdateTask.compareAndSet(false, true)) {
                return;
            }
            try {
                Mekanism.EXECUTE_MANAGER.addTEUpdateTask(this);
            } catch (RuntimeException e) {
                inUpdateTask.set(false);
                throw e;
            }
        } else {
            markForUpdate();
        }
    }

    public void updateComparatorOutputLevelSync() {
        if (hasComparatorSupport() && !hasEnabledComparator()) {
            return;
        }
        // Explicit requests can follow in-place edits outside the slot API.
        invalidateComparatorSnapshot();
        requestComparatorUpdate();
    }

    /**
     * Notification-only derived state; arbitrary external reads still use getRedstoneLevel.
     */
    protected void invalidateComparatorSnapshot() {
    }

    protected int getLocalComparatorLevelForNotification() {
        return ((IComparatorSupport) this).getRedstoneLevel();
    }

    private void requestComparatorUpdate() {
        if (world == null || world.isRemote) return;
        if (hasComparatorSupport() && !hasEnabledComparator()) return;
        if (requireUpdateComparatorOutputLevel.compareAndSet(false, true)) {
            try {
                Mekanism.EXECUTE_MANAGER.addUpdateComparatorOutputLevelTask(this);
            } catch (RuntimeException error) {
                requireUpdateComparatorOutputLevel.set(false);
                throw error;
            }
        }
    }

    public void updateComparatorOutputLevel() {
        if (world != null && !world.isRemote && !isServerThread()) {
            updateComparatorOutputLevelSync();
            return;
        }
        if (!requireUpdateComparatorOutputLevel.getAndSet(false) || world == null || world.isRemote || isInvalid())
            return;
        if (hasComparatorSupport() && !hasEnabledComparator()) return;
        int level = -1;
        if (localComparatorKind == 0) localComparatorKind = (byte) (LOCAL_COMPARATOR.get(getClass()) ? 2 : 1);
        boolean local = localComparatorKind == 2;
        if (local) {
            // Computing a local signal does not notify the world. Verify the tile's identity
            // only when a notification is necessary; never cache a failed identity check.
            level = getLocalComparatorLevelForNotification();
            if (level == lastNotifiedComparatorLevel) return;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) != this) return;
        IComparatorSupport comparator = getComparatorSupport();
        if (comparator != null) {
            if (!local) {
                level = comparator.getRedstoneLevel();
                if (level == lastNotifiedComparatorLevel) return;
            }
            currentRedstoneLevel = level;
            lastNotifiedComparatorLevel = level;
            // The cached signal is persisted as derived state alongside the contents.
            chunk.markDirty();
        }
        // Explicit callers without IComparatorSupport retain their requested notification.
        world.updateComparatorOutputLevel(pos, getBlockType());
    }

    /**
     * Whether this tile implements the comparator contract, regardless of its opt-in state.
     */
    protected final boolean hasComparatorSupport() {
        return getComparatorSupport() != null;
    }

    /**
     * Cached comparator opt-in used by high-frequency update paths.
     */
    protected final boolean hasEnabledComparator() {
        byte state = comparatorChainState;
        if (state != 0) {
            return state == 2;
        }
        IComparatorSupport comparator = getComparatorSupport();
        if (comparator == null || comparatorSupportState != 3) {
            comparatorChainState = 1;
            return false;
        }
        // Upgradeable machines opt into the comparator chain explicitly. Legacy
        // comparator implementations without an upgrade component remain compatible.
        if (!(this instanceof IUpgradeTile upgradeTile)) {
            comparatorChainState = 2;
            return true;
        }
        TileComponentUpgrade component = upgradeTile.getComponent();
        if (component == null) {
            // During construction the component is not available yet. Do not
            // cache this provisional legacy-compatible answer.
            return true;
        }
        state = (byte) (component.isUpgradeInstalled(Upgrade.COMPARATOR) ? 2 : 1);
        comparatorChainState = state;
        return state == 2;
    }

    /**
     * Invalidates the cached comparator gate after an upgrade or support mutation.
     */
    public final void invalidateComparatorUpgradeState() {
        comparatorChainState = 0;
    }

    // Deliberately not annotated @Override: TileEntitySynchronized does not declare IComparatorSupport
    // (the concrete tile subclasses declare it), so this only overrides the interface default for tiles
    // that do implement it. An @Override here fails to compile.
    public int getCurrentRedstoneLevel() {
        return hasEnabledComparator() ? currentRedstoneLevel : 0;
    }

    private IComparatorSupport getComparatorSupport() {
        byte state = comparatorSupportState;
        if (state == 0) {
            IComparatorSupport support = this instanceof IComparatorSupport comparator ? comparator : null;
            comparatorSupport = support;
            comparatorSupportState = (byte) (support == null ? 1 : support.supportsComparator() ? 3 : 2);
            return support;
        }
        return comparatorSupport;
    }

    public boolean isInUpdateTask() {
        return inUpdateTask.get();
    }

    public long getLastUpdateTick() {
        return lastUpdateTick;
    }

    public boolean isRequireUpdateLight() {
        return requireUpdateLight;
    }

    public void setRequireUpdateLight(final boolean requireUpdateLight) {
        this.requireUpdateLight = requireUpdateLight;
    }

    /**
     * Like getWorld(), but for when you _know_ world won't be null
     *
     * @return The world!
     */
    @Nonnull
    protected World getWorldNN() {
        return Objects.requireNonNull(getWorld(), "getWorldNN called before world set");
    }

    public boolean isRemote() {
        return getWorldNN().isRemote;
    }

    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        int rang = MekanismConfig.current().client.terRange.val();
        return rang * rang;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(getPos()).grow(IBoundingBlock.RENDER_BOUNDS_EPSILON);
    }


    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldCullForOcclusion() {
        if (!MekanismConfig.current().client.GazeCullingTracking.val()) {
            return false;
        }
        return IOcclusionCulling.super.shouldCullForOcclusion();
    }
}
