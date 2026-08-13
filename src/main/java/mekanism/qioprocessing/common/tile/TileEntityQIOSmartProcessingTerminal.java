package mekanism.qioprocessing.common.tile;

import mekanism.common.CommonWorldTickHandler;
import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;

/** Smart request terminal block with the same three manual crafting windows as a QIO dashboard. */
public final class TileEntityQIOSmartProcessingTerminal extends QIOProcessingTerminal
      implements IQIOCraftingWindowHolder {

    private final QIOCraftingWindow[] craftingWindows =
          new QIOCraftingWindow[MAX_CRAFTING_WINDOWS];
    private boolean craftingWindowsLoaded;
    private boolean shiftClickIntoFrequency = true;

    public TileEntityQIOSmartProcessingTerminal() {
        super("QIOSmartProcessingTerminal",
              QIOProcessingTerminalType.SMART_PROCESSING);
        for (byte index = 0; index < craftingWindows.length; index++) {
            craftingWindows[index] = new QIOCraftingWindow(this, index);
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
        return getQIOFrequency();
    }

    @Override
    public void onCraftingWindowContentsChanged() {
        if (world == null || !world.isRemote) {
            markNoUpdateSync();
        }
    }

    public boolean shiftClickIntoFrequency() {
        return shiftClickIntoFrequency;
    }

    public boolean toggleShiftClickIntoFrequency() {
        shiftClickIntoFrequency = !shiftClickIntoFrequency;
        markConfigurationChanged();
        return shiftClickIntoFrequency;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::shiftClickIntoFrequency,
              value -> shiftClickIntoFrequency = value));
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (world != null && (!craftingWindowsLoaded ||
            CommonWorldTickHandler.flushTagAndRecipeCaches)) {
            craftingWindowsLoaded = true;
            for (QIOCraftingWindow craftingWindow : craftingWindows) {
                craftingWindow.invalidateRecipe();
            }
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound data) {
        super.writeCustomNBT(data);
        writeCraftingWindows(data);
    }

    @Override
    public void readCustomNBT(NBTTagCompound data) {
        super.readCustomNBT(data);
        readCraftingWindows(data);
    }

    @Override
    public void writeSustainedQIOData(NBTTagCompound data) {
        super.writeSustainedQIOData(data);
        writeCraftingWindows(data);
    }

    @Override
    public void readSustainedQIOData(NBTTagCompound data) {
        super.readSustainedQIOData(data);
        readCraftingWindows(data);
    }

    private void writeCraftingWindows(NBTTagCompound data) {
        NBTTagList windows = new NBTTagList();
        for (byte index = 0; index < craftingWindows.length; index++) {
            NBTTagCompound window = new NBTTagCompound();
            window.setByte("Window", index);
            craftingWindows[index].write(window);
            windows.appendTag(window);
        }
        data.setTag("qioProcessingCraftingWindows", windows);
        data.setBoolean("qioProcessingShiftClickIntoFrequency",
              shiftClickIntoFrequency);
    }

    private void readCraftingWindows(NBTTagCompound data) {
        NBTTagList windows = data.getTagList("qioProcessingCraftingWindows",
              NBT.TAG_COMPOUND);
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
        craftingWindowsLoaded = false;
        shiftClickIntoFrequency = !data.hasKey(
              "qioProcessingShiftClickIntoFrequency") || data.getBoolean(
              "qioProcessingShiftClickIntoFrequency");
    }
}
