package mekanism.common.lib;

import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.common.content.transporter.TransporterPathfinder.Destination;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class SidedBlockPos {

    private final Coord4D pos;
    private final EnumFacing side;

    public SidedBlockPos(Coord4D pos, EnumFacing side) {
        this.pos = pos;
        this.side = side;
    }

    public static SidedBlockPos get(Destination destination) {
        List<Coord4D> path = destination.getPath();
        Coord4D pos = path.get(0);
        EnumFacing side = path.size() > 1 ? path.get(1).sideDifference(pos) : EnumFacing.DOWN;
        return new SidedBlockPos(pos, side == null ? EnumFacing.DOWN : side);
    }

    @Nullable
    public static SidedBlockPos deserialize(NBTTagCompound tag) {
        if (!tag.hasKey(NBTConstants.POSITION) || !tag.hasKey(NBTConstants.SIDE)) {
            return null;
        }
        Coord4D pos = Coord4D.read(tag.getCompoundTag(NBTConstants.POSITION));
        EnumFacing side = EnumFacing.byIndex(tag.getInteger(NBTConstants.SIDE));
        return new SidedBlockPos(pos, side);
    }

    public NBTTagCompound serialize() {
        NBTTagCompound target = new NBTTagCompound();
        target.setTag(NBTConstants.POSITION, pos.write(new NBTTagCompound()));
        target.setInteger(NBTConstants.SIDE, side.ordinal());
        return target;
    }

    public Coord4D pos() {
        return pos;
    }

    public EnumFacing side() {
        return side;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SidedBlockPos other)) {
            return false;
        }
        return pos.equals(other.pos) && side == other.side;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, side);
    }
}
