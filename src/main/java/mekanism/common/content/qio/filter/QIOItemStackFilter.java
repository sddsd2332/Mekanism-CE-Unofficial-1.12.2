package mekanism.common.content.qio.filter;

import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemHandlerHelper;

public class QIOItemStackFilter extends QIOFilter {

    public static final String TYPE = "item";
    private ItemStack item = ItemStack.EMPTY;
    private boolean fuzzyMode;

    public QIOItemStackFilter() {
    }

    public QIOItemStackFilter(ItemStack item) {
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        this.item.setCount(1);
    }

    public ItemStack getItemStack() {
        return item.copy();
    }

    public void setItemStack(ItemStack item) {
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        this.item.setCount(1);
    }

    public boolean isFuzzyMode() {
        return fuzzyMode;
    }

    public void setFuzzyMode(boolean fuzzyMode) {
        this.fuzzyMode = fuzzyMode;
    }

    public void toggleFuzzyMode() {
        fuzzyMode = !fuzzyMode;
    }

    @Override public QIOResourceKind getKind() { return QIOResourceKind.ITEM; }

    @Override
    public boolean matches(QIOResourceEntry entry) {
        return matches(entry.getItem());
    }

    @Override
    public boolean matches(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !item.isEmpty() &&
              (fuzzyMode ? item.getItem() == stack.getItem() : ItemHandlerHelper.canItemStacksStack(item, stack));
    }

    @Override public String getType() { return TYPE; }

    @Override public boolean hasFilter() { return !item.isEmpty(); }

    @Override
    public void writePayload(NBTTagCompound data) {
        if (!item.isEmpty()) {
            item.writeToNBT(data);
        }
        data.setBoolean("fuzzy", fuzzyMode);
    }

    @Override
    protected void readPayload(NBTTagCompound data) {
        item = data.isEmpty() ? ItemStack.EMPTY : new ItemStack(data);
        fuzzyMode = data.getBoolean("fuzzy");
    }
}
