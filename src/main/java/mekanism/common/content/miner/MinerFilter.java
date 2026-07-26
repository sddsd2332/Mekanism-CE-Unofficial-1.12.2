package mekanism.common.content.miner;

import io.netty.buffer.ByteBuf;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.content.filter.IFilter;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

public abstract class MinerFilter implements IFilter {

    public ItemStack replaceStack = ItemStack.EMPTY;

    public boolean requireStack;
    private boolean enabled = true;

    @Nullable
    public static MinerFilter readFromNBT(NBTTagCompound nbtTags) {
        MinerFilter filter = getType(nbtTags.getInteger("type"));
        if (filter != null) {
            filter.read(nbtTags);
        }
        return filter;
    }

    @Nullable
    public static MinerFilter readFromPacket(ByteBuf dataStream) {
        MinerFilter filter = getType(dataStream.readInt());
        if (filter != null) {
            filter.read(dataStream);
        }
        return filter;
    }

    @Nullable
    private static MinerFilter getType(int type) {
        MinerFilter filter = null;
        if (type == 0) {
            filter = new MItemStackFilter();
        } else if (type == 1) {
            filter = new MOreDictFilter();
        } else if (type == 2) {
            filter = new MMaterialFilter();
        } else if (type == 3) {
            filter = new MModIDFilter();
        }
        return filter;
    }

    public abstract boolean canFilter(ItemStack itemStack);

    public abstract boolean hasBlacklistedElement();

    @Override
    public MinerFilter clone() {
        MinerFilter copy = readFromNBT(write(new NBTTagCompound()));
        // Third-party filter types may not be registered with the legacy numeric decoder.
        // They remain usable, but cannot be isolated from live edits unless they override clone().
        return copy == null ? this : copy;
    }

    public NBTTagCompound write(NBTTagCompound nbtTags) {
        nbtTags.setBoolean(NBTConstants.ENABLED, enabled);
        nbtTags.setBoolean("requireStack", requireStack);
        if (!replaceStack.isEmpty()) {
            nbtTags.setTag("replaceStack", replaceStack.writeToNBT(new NBTTagCompound()));
        }
        return nbtTags;
    }

    protected void read(NBTTagCompound nbtTags) {
        enabled = !nbtTags.hasKey(NBTConstants.ENABLED) || nbtTags.getBoolean(NBTConstants.ENABLED);
        requireStack = nbtTags.getBoolean("requireStack");
        if (nbtTags.hasKey("replaceStack")) {
            replaceStack = new ItemStack(nbtTags.getCompoundTag("replaceStack"));
        }
    }

    public void write(TileNetworkList data) {
        data.add(enabled);
        data.add(requireStack);
        if (!replaceStack.isEmpty()) {
            data.add(true);
            data.add(MekanismUtils.getID(replaceStack));
            data.add(replaceStack.getItemDamage());
        } else {
            data.add(false);
        }
    }

    protected void read(ByteBuf dataStream) {
        enabled = dataStream.readBoolean();
        requireStack = dataStream.readBoolean();
        if (dataStream.readBoolean()) {
            replaceStack = new ItemStack(Item.getItemById(dataStream.readInt()), 1, dataStream.readInt());
        } else {
            replaceStack = ItemStack.EMPTY;
        }
    }

    protected void copyBaseData(MinerFilter filter) {
        filter.enabled = enabled;
        filter.replaceStack = replaceStack.copy();
        filter.requireStack = requireStack;
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
        code = 31 * code + (requireStack ? 1 : 0);
        code = 31 * code + MekanismUtils.getID(replaceStack);
        code = 31 * code + replaceStack.getItemDamage();
        return code;
    }

    @Override
    public boolean equals(Object filter) {
        return filter instanceof MinerFilter minerFilter && minerFilter.enabled == enabled && minerFilter.requireStack == requireStack
                && ItemStack.areItemStacksEqual(minerFilter.replaceStack, replaceStack);
    }
}
