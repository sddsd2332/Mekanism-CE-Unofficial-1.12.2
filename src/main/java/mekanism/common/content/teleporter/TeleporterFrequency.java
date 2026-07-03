package mekanism.common.content.teleporter;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IColorableFrequency;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

public class TeleporterFrequency extends Frequency implements IColorableFrequency {

    private EnumColor color;
    public final Set<Coord4D> activeCoords = new ObjectOpenHashSet<>();

    public TeleporterFrequency(String name, @Nullable UUID ownerUUID) {
        this(name, ownerUUID, SecurityMode.PUBLIC);
    }

    public TeleporterFrequency(String name, @Nullable UUID ownerUUID, SecurityMode securityMode) {
        super(FrequencyType.TELEPORTER, name, ownerUUID, securityMode);
        color = EnumColor.PURPLE;
    }

    public TeleporterFrequency(NBTTagCompound nbtTags) {
        super(FrequencyType.TELEPORTER, nbtTags);
    }

    public TeleporterFrequency(ByteBuf dataStream) {
        super(FrequencyType.TELEPORTER, dataStream);
    }

    @Override
    public int getSyncHash() {
        int code = super.getSyncHash();
        code = 31 * code + color.ordinal();
        return code;
    }

    @Override
    public EnumColor getColor() {
        return color;
    }

    @Override
    public void setColor(EnumColor color) {
        EnumColor resolved = color == null ? EnumColor.PURPLE : color;
        if (this.color != resolved) {
            this.color = resolved;
            dirty = true;
        }
    }

    @Override
    public boolean onDeactivate(Object source) {
        Coord4D coord = getCoord(source);
        return coord != null && activeCoords.remove(coord);
    }

    @Override
    public boolean update(Object source) {
        Coord4D coord = getCoord(source);
        return coord != null && activeCoords.add(coord);
    }

    @Nullable
    private Coord4D getCoord(Object source) {
        if (source instanceof Coord4D coord) {
            return coord;
        } else if (source instanceof TileEntity tile) {
            return Coord4D.get(tile);
        }
        return null;
    }

    public Coord4D getClosestCoords(Coord4D coord) {
        Coord4D closest = null;
        for (Coord4D iterCoord : activeCoords) {
            if (iterCoord.equals(coord)) {
                continue;
            }
            if (closest == null) {
                closest = iterCoord;
                continue;
            }

            if (coord.dimensionId != closest.dimensionId && coord.dimensionId == iterCoord.dimensionId) {
                closest = iterCoord;
            } else if (coord.dimensionId != closest.dimensionId || coord.dimensionId == iterCoord.dimensionId) {
                if (coord.distanceTo(closest) > coord.distanceTo(iterCoord)) {
                    closest = iterCoord;
                }
            }
        }
        return closest;
    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        super.write(nbtTags);
        nbtTags.setInteger(NBTConstants.COLOR, color.ordinal());
    }

    @Override
    protected void read(NBTTagCompound nbtTags) {
        super.read(nbtTags);
        color = nbtTags.hasKey(NBTConstants.COLOR) ? MekanismUtils.getByIndex(EnumColor.values(), nbtTags.getInteger(NBTConstants.COLOR), EnumColor.PURPLE) : EnumColor.PURPLE;
    }

    @Override
    public void write(TileNetworkList data) {
        super.write(data);
        data.add(color.ordinal());
    }

    @Override
    protected void read(ByteBuf dataStream) {
        super.read(dataStream);
        color = MekanismUtils.getByIndex(EnumColor.values(), dataStream.readInt(), EnumColor.PURPLE);
    }
}
