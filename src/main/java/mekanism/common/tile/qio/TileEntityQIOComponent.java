package mekanism.common.tile.qio;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.content.qio.IQIOFrequencyHolder;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared server-side behavior for QIO blocks.  QIO components all use the
 * regular 1.12 tile inventory and frequency component rather than a second
 * networking or persistence path.
 */
public abstract class TileEntityQIOComponent extends TileEntityContainerBlock
      implements IQIOFrequencyHolder, IFrequencyHandler, ISecurityTile, IActiveState {

    protected final TileComponentSecurity securityComponent;
    private boolean active;
    @Nullable
    private EnumColor lastColor;

    protected TileEntityQIOComponent(String name) {
        super(name);
        frequencyComponent.track(FrequencyType.QIO, true, true, true);
        securityComponent = new TileComponentSecurity(this);
    }

    @Override
    @Nullable
    public QIOFrequency getQIOFrequency() {
        return getFrequency(FrequencyType.QIO);
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        QIOFrequency frequency = getQIOFrequency();
        boolean nextActive = frequency != null && frequency.isValid() && !frequency.isRemoved();
        EnumColor nextColor = frequency == null ? null : frequency.getColor();
        if (active != nextActive || lastColor != nextColor) {
            active = nextActive;
            lastColor = nextColor;
            syncVisualState();
        }
    }

    @Override
    public boolean getActive() {
        return active;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            syncVisualState();
        }
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @Nullable
    public EnumColor getQIOColor() {
        return lastColor;
    }

    public int getQIODimension() {
        return world == null || world.provider == null ? 0 : world.provider.getDimension();
    }

    private void syncVisualState() {
        markNoUpdateSync();
        if (world != null && !world.isRemote) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (world != null && world.isRemote && dataStream.readableBytes() >= 5) {
            boolean previousActive = active;
            EnumColor previousColor = lastColor;
            active = dataStream.readBoolean();
            lastColor = MekanismUtils.getByIndex(EnumColor.values(), dataStream.readInt(), null);
            if (previousActive != active || previousColor != lastColor) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(active);
        data.add(lastColor == null ? -1 : lastColor.ordinal());
        return data;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (!isRemote()) {
            // Chunk unload does not invalidate ordinary tile entities in
            // 1.12. Detach now so drive capacity and UUID mount locks cannot
            // remain active while the physical holder is unloaded.
            frequencyComponent.invalidate();
        }
    }

    @Nonnull
    public BlockPos getQIOPosition() {
        return getPos();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("qioActive", active);
        if (lastColor != null) {
            nbtTags.setInteger("qioColor", lastColor.ordinal());
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        active = nbtTags.getBoolean("qioActive");
        if (nbtTags.hasKey("qioColor")) {
            lastColor = MekanismUtils.getByIndex(EnumColor.values(), nbtTags.getInteger("qioColor"), null);
        } else {
            lastColor = null;
        }
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        boolean previousActive = active;
        EnumColor previousColor = lastColor;
        super.handleUpdateTag(tag);
        if (tag.hasKey("qioColor")) {
            lastColor = MekanismUtils.getByIndex(EnumColor.values(), tag.getInteger("qioColor"), null);
        } else {
            lastColor = null;
        }
        if (world != null && (previousActive != active || previousColor != lastColor)) {
            MekanismUtils.updateBlock(world, getPos());
        }
    }

    /** Data retained by ItemBlockQIOComponent when the block is picked up. */
    public void writeSustainedQIOData(NBTTagCompound data) {
    }

    /** Restores data retained by ItemBlockQIOComponent on placement. */
    public void readSustainedQIOData(NBTTagCompound data) {
    }
}
