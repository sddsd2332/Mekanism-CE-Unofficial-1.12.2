package mekanism.common.content.transporter;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.content.filter.IFilter;
import mekanism.common.lib.inventory.Finder;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TransporterUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public abstract class TransporterFilter implements IFilter {

    public static final int MAX_LENGTH = 24;

    public static final List<Character> SPECIAL_CHARS = Arrays.asList('*', '-', ' ', '|', '_', '\'');

    public EnumColor color;

    public boolean allowDefault;
    private boolean enabled = true;

    @Nullable
    public static TransporterFilter readFromNBT(NBTTagCompound nbtTags) {
        TransporterFilter filter = getType(nbtTags.getInteger("type"));
        if (filter != null) {
            filter.read(nbtTags);
        }
        return filter;
    }

    @Nullable
    public static TransporterFilter readFromPacket(ByteBuf dataStream) {
        TransporterFilter filter = getType(dataStream.readInt());
        if (filter != null) {
            filter.read(dataStream);
        }
        return filter;
    }

    @Nullable
    private static TransporterFilter getType(int type) {
        TransporterFilter filter = null;
        if (type == 0) {
            filter = new TItemStackFilter();
        } else if (type == 1) {
            filter = new TOreDictFilter();
        } else if (type == 2) {
            filter = new TMaterialFilter();
        } else if (type == 3) {
            filter = new TModIDFilter();
        }
        return filter;
    }

    public boolean canFilter(ItemStack itemStack, boolean strict) {
        return !itemStack.isEmpty();
    }

    public abstract Finder getFinder();

    public TransitRequest mapInventory(TileEntity tile, EnumFacing side, boolean singleItem) {
        return TransitRequest.definedItem(tile, side, singleItem ? 1 : 64, getFinder());
    }

    public void write(NBTTagCompound nbtTags) {
        nbtTags.setBoolean(NBTConstants.ENABLED, enabled);
        nbtTags.setBoolean("allowDefault", allowDefault);
        if (color != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(color));
        }
    }

    protected void read(NBTTagCompound nbtTags) {
        enabled = !nbtTags.hasKey(NBTConstants.ENABLED) || nbtTags.getBoolean(NBTConstants.ENABLED);
        allowDefault = nbtTags.getBoolean("allowDefault");
        if (nbtTags.hasKey("color")) {
            color = MekanismUtils.getByIndex(TransporterUtils.colors, nbtTags.getInteger("color"), null);
        }
    }

    public void write(TileNetworkList data) {
        data.add(enabled);
        data.add(allowDefault);
        if (color != null) {
            data.add(TransporterUtils.colors.indexOf(color));
        } else {
            data.add(-1);
        }
    }

    protected void read(ByteBuf dataStream) {
        enabled = dataStream.readBoolean();
        allowDefault = dataStream.readBoolean();
        int c = dataStream.readInt();
        if (c != -1) {
            color = MekanismUtils.getByIndex(TransporterUtils.colors, c, null);
        } else {
            color = null;
        }
    }

    protected void copyBaseData(TransporterFilter filter) {
        filter.enabled = enabled;
        filter.allowDefault = allowDefault;
        filter.color = color;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int hashCode() {
        int code = 1;
        code = 31 * code + (enabled ? 1 : 0);
        code = 31 * code + (allowDefault ? 1 : 0);
        code = 31 * code + (color != null ? color.ordinal() : -1);
        return code;
    }

    @Override
    public boolean equals(Object filter) {
        return filter instanceof TransporterFilter transporterFilter && transporterFilter.enabled == enabled && transporterFilter.allowDefault == allowDefault
                && transporterFilter.color == color;
    }
}
