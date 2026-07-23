package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.multiblock.TileEntityInternalMultiblock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Objects;

public class TileEntitySuperheatingElement extends TileEntityInternalMultiblock implements IActiveState {

    private boolean active;
    private boolean clientActive;

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        boolean hot = multiblockUUID != null && Boolean.TRUE.equals(SynchronizedBoilerData.hotMap.get(multiblockUUID));
        setActive(hot);
    }

    @Override
    public void setMultiblock(String id) {
        boolean changed = !Objects.equals(multiblockUUID, id);
        boolean wasActive = active;
        super.setMultiblock(id);
        if (changed) {
            setActive(id != null && Boolean.TRUE.equals(SynchronizedBoilerData.hotMap.get(id)));
            markDirty();
            if (wasActive == active && world != null && !world.isRemote) {
                //setActive only sends when the light state changes. The UUID still has to reach
                //the client when both the old and new structures have the same hot state.
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
        }
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        active = resolveClientActive(multiblockUUID, active);
        if (clientActive != active) {
            MekanismUtils.updateBlock(world, getPos());
            clientActive = active;
        }
    }

    static boolean resolveClientActive(String multiblockUUID, boolean synchronizedActive) {
        if (multiblockUUID == null) {
            return false;
        }
        Boolean mappedActive = SynchronizedBoilerData.clientHotMap.get(multiblockUUID);
        return mappedActive == null ? synchronizedActive : mappedActive;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            active = dataStream.readBoolean();
            if (clientActive != active) {
                MekanismUtils.updateBlock(world, getPos());
                clientActive = active;
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(active);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        active = clientActive = nbtTags.getBoolean("active");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("active", active);
    }

    @Override
    public boolean getActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            if (world != null) {
                MekanismUtils.updateBlock(world, getPos());
                if (!isRemote()) {
                    Mekanism.packetHandler.sendUpdatePacket(this);
                }
            }
        }
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }
}
