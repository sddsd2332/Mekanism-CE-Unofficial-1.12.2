package mekanism.common.tile.base;

import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.config.MekanismConfig;
import mekanism.common.interfaces.IOcclusionCulling;
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

/**
 * 可异步类型tile方块。
 */
//TODO：需要让它正确的运行，虽然可以运行
public class TileEntitySynchronized extends TileEntity implements IOcclusionCulling {

    private boolean inUpdateTask = false;
    private boolean inMarkTask = false;

    private boolean requireUpdateLight = false;
    private boolean requireUpdateComparatorOutputLevel = false;

    private long lastUpdateTick = 0;

    public final void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        readCustomNBT(compound);
    }

    public void readCustomNBT(NBTTagCompound compound) {
    }

    public void readNetNBT(NBTTagCompound compound) {
    }

    @Nonnull
    public final NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        NBTTagCompound writeToNBT = super.writeToNBT(compound);
        writeCustomNBT(writeToNBT);
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

    /** Reads the bounded counterpart written by {@link #writeUpdateNBT(NBTTagCompound)}. */
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
        super.getUpdateTag();
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
        World world = getWorld();
        if (world == null) {
            inMarkTask = false;
            return;
        }
        markDirty();
        updateLight();
        inMarkTask = false;
        lastUpdateTick = world.getTotalWorldTime();
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
        World world = getWorld();
        if (world == null) { //防止世界是无
            inUpdateTask = false;
            return;
        }
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
        inUpdateTask = false;
    }

    public void markNoUpdateSync() {
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
        if (inMarkTask) {
            return;
        }
        Mekanism.EXECUTE_MANAGER.addTEMarkNoUpdateTask(this);
        inMarkTask = true;
    }

    public void markChunkDirty() {
        if (world == null) {
            return;
        }
        // Prevent extra chunk loading and checks.
        Chunk loadedChunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (loadedChunk != null) {
            loadedChunk.markDirty();
        }
    }

    /**
     * <p>markForUpdate 的同步实现，防止意料之外的 CME。</p>
     * <p>*** 只能保证 mekceu 自身对世界的线程安全 ***</p>
     */
    public void markForUpdateSync() {
        if (getWorld() == null) {
            return;
        }
        if (inUpdateTask) {
            return;
        }
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            Mekanism.EXECUTE_MANAGER.addTEUpdateTask(this);
            inUpdateTask = true;
            inMarkTask = true;
        } else {
            markForUpdate();
        }
    }

    public void updateComparatorOutputLevelSync() {
        if (getWorld() == null) {
            return;
        }
        if (inMarkTask) {
            return;
        }
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            requireUpdateComparatorOutputLevel = true;
            Mekanism.EXECUTE_MANAGER.addUpdateComparatorOutputLevelTask(this);
        } else {
            updateComparatorOutputLevel();
        }
    }

    public void updateComparatorOutputLevel() {
        if (requireUpdateComparatorOutputLevel) {
            requireUpdateComparatorOutputLevel = false;
            World world = getWorld();
            if (world != null) {
                world.updateComparatorOutputLevel(pos, getBlockType());
            }
        }
    }

    public boolean isInUpdateTask() {
        return inUpdateTask;
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
