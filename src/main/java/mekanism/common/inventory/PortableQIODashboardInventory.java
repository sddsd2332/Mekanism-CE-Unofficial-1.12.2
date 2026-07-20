package mekanism.common.inventory;

import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.security.ISecurityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Item-backed persistent owner for the portable dashboard crafting windows. */
public final class PortableQIODashboardInventory implements IQIOCraftingWindowHolder {

    private final Supplier<ItemStack> stackSupplier;
    private final World world;
    private final QIOCraftingWindow[] craftingWindows = new QIOCraftingWindow[MAX_CRAFTING_WINDOWS];
    private boolean writing;

    public PortableQIODashboardInventory(@Nonnull ItemStack stack, @Nonnull World world) {
        this(() -> stack, world);
    }

    public PortableQIODashboardInventory(@Nonnull Supplier<ItemStack> stackSupplier, @Nonnull World world) {
        this.stackSupplier = stackSupplier;
        this.world = world;
        for (byte i = 0; i < craftingWindows.length; i++) {
            craftingWindows[i] = new QIOCraftingWindow(this, i);
        }
        read();
        if (!world.isRemote) {
            invalidateRecipes();
        }
    }

    private void read() {
        ItemStack stack = stackSupplier.get();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!stack.hasTagCompound()) {
            return;
        }
        NBTTagList windows = stack.getTagCompound().getTagList("craftingWindows", Constants.NBT.TAG_COMPOUND);
        if (windows.tagCount() == 0) {
            windows = stack.getTagCompound().getTagList("qioCraftingWindows", Constants.NBT.TAG_COMPOUND);
        }
        for (int i = 0; i < windows.tagCount(); i++) {
            NBTTagCompound window = windows.getCompoundTagAt(i);
            int index = window.getByte("Window") & 0xFF;
            if (index < craftingWindows.length) {
                craftingWindows[index].read(window);
            }
        }
    }

    public void flush() {
        write();
    }

    public void invalidateRecipes() {
        for (QIOCraftingWindow craftingWindow : craftingWindows) {
            craftingWindow.invalidateRecipe();
        }
    }

    private void write() {
        if (writing || world.isRemote) {
            return;
        }
        writing = true;
        try {
            ItemStack stack = stackSupplier.get();
            if (stack == null || stack.isEmpty()) {
                return;
            }
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            NBTTagList windows = new NBTTagList();
            for (byte i = 0; i < craftingWindows.length; i++) {
                NBTTagCompound window = new NBTTagCompound();
                window.setByte("Window", i);
                craftingWindows[i].write(window);
                windows.appendTag(window);
            }
            tag.setTag("craftingWindows", windows);
            tag.setTag("qioCraftingWindows", windows.copy());
        } finally {
            writing = false;
        }
    }

    @Override
    public World getHolderWorld() {
        return world;
    }

    @Override
    public QIOCraftingWindow[] getCraftingWindows() {
        return craftingWindows;
    }

    @Override
    @Nullable
    public QIOFrequency getFrequency() {
        ItemStack stack = stackSupplier.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!(stack.getItem() instanceof IFrequencyItem)) {
            return null;
        }
        IFrequencyItem item = (IFrequencyItem) stack.getItem();
        mekanism.common.frequency.Frequency.FrequencyIdentity identity = item.getFrequency(stack);
        java.util.UUID owner = stack.getItem() instanceof ISecurityItem ? ((ISecurityItem) stack.getItem()).getOwnerUUID(stack) : null;
        return identity == null ? null : FrequencyType.QIO.getFrequency(identity, owner);
    }

    @Override
    public void onCraftingWindowContentsChanged() {
        write();
    }
}
