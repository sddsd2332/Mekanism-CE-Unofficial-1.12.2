package mekanism.common.content.miner;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.content.filter.IItemStackFilter;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

public class MItemStackFilter extends MinerFilter implements IItemStackFilter {

    private ItemStack itemType = ItemStack.EMPTY;
    public boolean fuzzy;

    public MItemStackFilter(ItemStack item) {
        itemType = item;
    }

    public MItemStackFilter() {
    }

    @Override
    public boolean canFilter(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        if (itemStack.getItem() == itemType.getItem() && fuzzy) {
            return true;
        }
        return itemType.isItemEqual(itemStack);
    }

    @Override
    public boolean hasBlacklistedElement() {
        return MinerBlacklistHelper.hasBlacklistedItem(itemType);
    }

    @Override
    public NBTTagCompound write(NBTTagCompound nbtTags) {
        super.write(nbtTags);
        nbtTags.setInteger("type", 0);
        nbtTags.setBoolean("fuzzy", fuzzy);
        itemType.writeToNBT(nbtTags);
        return nbtTags;
    }

    @Override
    protected void read(NBTTagCompound nbtTags) {
        super.read(nbtTags);
        fuzzy = nbtTags.getBoolean("fuzzy");
        itemType = new ItemStack(nbtTags);
    }

    @Override
    public void write(TileNetworkList data) {
        data.add(0);
        super.write(data);
        data.add(fuzzy);
        data.add(MekanismUtils.getID(itemType));
        data.add(itemType.getCount());
        data.add(itemType.getItemDamage());
    }

    @Override
    protected void read(ByteBuf dataStream) {
        super.read(dataStream);
        fuzzy = dataStream.readBoolean();
        itemType = new ItemStack(Item.getItemById(dataStream.readInt()), dataStream.readInt(), dataStream.readInt());
    }

    @Override
    public int hashCode() {
        int code = super.hashCode();
        code = 31 * code + MekanismUtils.getID(itemType);
        code = 31 * code + itemType.getCount();
        code = 31 * code + itemType.getItemDamage();
        return code;
    }

    @Override
    public boolean equals(Object filter) {
        return super.equals(filter) && filter instanceof MItemStackFilter stackFilter && stackFilter.itemType.isItemEqual(itemType);
    }

    @Override
    public MItemStackFilter clone() {
        MItemStackFilter filter = new MItemStackFilter();
        copyBaseData(filter);
        filter.fuzzy = fuzzy;
        filter.itemType = itemType.copy();
        return filter;
    }

    @Nonnull
    @Override
    public ItemStack getItemStack() {
        return itemType;
    }

    @Override
    public void setItemStack(@Nonnull ItemStack stack) {
        itemType = stack;
    }
}
