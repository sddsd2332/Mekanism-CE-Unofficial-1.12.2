package mekanism.qioprocessing.common.inventory;

import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData;
import mekanism.qioprocessing.common.terminal.QIOProcessingFrequencyAccess;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Item-backed three-window holder used only by the portable smart processing terminal. */
public final class PortableQIOProcessingTerminalInventory
      implements IQIOCraftingWindowHolder {

    private final Supplier<ItemStack> stackSupplier;
    private final World world;
    private final BiConsumer<Long, Long> generationListener;
    private final QIOCraftingWindow[] craftingWindows =
          new QIOCraftingWindow[MAX_CRAFTING_WINDOWS];
    private boolean writing;
    private boolean dirty;

    public PortableQIOProcessingTerminalInventory(
          @Nonnull Supplier<ItemStack> stackSupplier, @Nonnull World world,
          @Nonnull BiConsumer<Long, Long> generationListener) {
        this.stackSupplier = Objects.requireNonNull(stackSupplier, "stackSupplier");
        this.world = Objects.requireNonNull(world, "world");
        this.generationListener = Objects.requireNonNull(generationListener,
              "generationListener");
        for (byte index = 0; index < craftingWindows.length; index++) {
            craftingWindows[index] = new QIOCraftingWindow(this, index);
        }
        read();
        if (!world.isRemote) {
            invalidateRecipes();
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
        if (!isSmartTerminal(stack)) {
            return null;
        }
        ItemPortableQIOProcessingTerminal item =
              (ItemPortableQIOProcessingTerminal) stack.getItem();
        return QIOProcessingFrequencyAccess.resolve(item.getFrequencyReference(stack));
    }

    @Override
    public void onCraftingWindowContentsChanged() {
        if (!world.isRemote && !writing) {
            dirty = true;
            flush();
        }
    }

    public void invalidateRecipes() {
        for (QIOCraftingWindow craftingWindow : craftingWindows) {
            craftingWindow.invalidateRecipe();
        }
    }

    public void flush() {
        if (!dirty || writing || world.isRemote) {
            return;
        }
        writing = true;
        try {
            ItemStack stack = stackSupplier.get();
            if (!isSmartTerminal(stack)) {
                return;
            }
            PortableQIOProcessingTerminalData data =
                  PortableQIOProcessingTerminalData.read(stack);
            if (data == null) {
                return;
            }
            NBTTagList windows = new NBTTagList();
            for (byte index = 0; index < craftingWindows.length; index++) {
                NBTTagCompound window = new NBTTagCompound();
                window.setByte("Window", index);
                craftingWindows[index].write(window);
                windows.appendTag(window);
            }
            NBTTagCompound root = stack.getTagCompound().getCompoundTag(
                  PortableQIOProcessingTerminalData.ROOT_KEY);
            root.setTag("craftingWindows", windows);
            PortableQIOProcessingTerminalData updated = data.advanceGeneration();
            updated.writeTo(stack);
            dirty = false;
            generationListener.accept(data.getGeneration(), updated.getGeneration());
        } catch (QIOProcessingDataException ignored) {
        } finally {
            writing = false;
        }
    }

    private void read() {
        ItemStack stack = stackSupplier.get();
        if (!isSmartTerminal(stack) || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY);
        NBTTagList windows = root.getTagList("craftingWindows", NBT.TAG_COMPOUND);
        boolean[] seen = new boolean[craftingWindows.length];
        for (int index = 0; index < windows.tagCount() &&
             index < craftingWindows.length; index++) {
            NBTTagCompound window = windows.getCompoundTagAt(index);
            int windowIndex = window.getByte("Window") & 0xFF;
            if (windowIndex < craftingWindows.length && !seen[windowIndex]) {
                seen[windowIndex] = true;
                craftingWindows[windowIndex].read(window);
            }
        }
    }

    private static boolean isSmartTerminal(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() &&
              stack.getItem() instanceof ItemPortableQIOProcessingTerminal item &&
              item.getTerminalType() == QIOProcessingTerminalType.SMART_PROCESSING;
    }
}
