package mekanism.common.tile.qio;

import mekanism.common.CommonWorldTickHandler;
import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/** QIO network viewer block. The actual resource state lives in its frequency. */
public class TileEntityQIODashboard extends TileEntityQIOComponent implements IQIOCraftingWindowHolder {

    private final QIOCraftingWindow[] craftingWindows = new QIOCraftingWindow[MAX_CRAFTING_WINDOWS];
    private boolean craftingWindowsLoaded;
    private boolean shiftClickIntoFrequency = true;

    public TileEntityQIODashboard() {
        super("QIODashboard");
        for (byte i = 0; i < craftingWindows.length; i++) {
            craftingWindows[i] = new QIOCraftingWindow(this, i);
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
    public QIOFrequency getFrequency() {
        return getQIOFrequency();
    }

    @Override
    public void onCraftingWindowContentsChanged() {
        if (!isRemote()) {
            markNoUpdateSync();
        }
    }

    public boolean shiftClickIntoFrequency() {
        return shiftClickIntoFrequency;
    }

    public void toggleShiftClickIntoFrequency() {
        shiftClickIntoFrequency = !shiftClickIntoFrequency;
        markNoUpdateSync();
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::shiftClickIntoFrequency, value -> shiftClickIntoFrequency = value));
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (world != null && (!craftingWindowsLoaded || CommonWorldTickHandler.flushTagAndRecipeCaches)) {
            craftingWindowsLoaded = true;
            for (QIOCraftingWindow window : craftingWindows) {
                window.invalidateRecipe();
            }
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        NBTTagList windows = new NBTTagList();
        for (byte i = 0; i < craftingWindows.length; i++) {
            NBTTagCompound window = new NBTTagCompound();
            window.setByte("Window", i);
            craftingWindows[i].write(window);
            windows.appendTag(window);
        }
        // Keep the established 1.12 key while accepting the namespaced key
        // used by early development builds.
        nbtTags.setTag("craftingWindows", windows);
        nbtTags.setTag("qioCraftingWindows", windows.copy());
        nbtTags.setBoolean("qioShiftClickIntoFrequency", shiftClickIntoFrequency);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        NBTTagList windows = nbtTags.getTagList("craftingWindows", Constants.NBT.TAG_COMPOUND);
        if (windows.tagCount() == 0) {
            windows = nbtTags.getTagList("qioCraftingWindows", Constants.NBT.TAG_COMPOUND);
        }
        for (int i = 0; i < windows.tagCount(); i++) {
            NBTTagCompound window = windows.getCompoundTagAt(i);
            int index = window.getByte("Window") & 0xFF;
            if (index < craftingWindows.length) {
                craftingWindows[index].read(window);
            }
        }
        craftingWindowsLoaded = false;
        shiftClickIntoFrequency = !nbtTags.hasKey("qioShiftClickIntoFrequency") || nbtTags.getBoolean("qioShiftClickIntoFrequency");
    }

    @Override
    public void writeSustainedQIOData(NBTTagCompound data) {
        super.writeSustainedQIOData(data);
        NBTTagList windows = new NBTTagList();
        for (byte i = 0; i < craftingWindows.length; i++) {
            NBTTagCompound window = new NBTTagCompound();
            window.setByte("Window", i);
            craftingWindows[i].write(window);
            windows.appendTag(window);
        }
        data.setTag("craftingWindows", windows);
        data.setBoolean("qioShiftClickIntoFrequency", shiftClickIntoFrequency);
    }

    @Override
    public void readSustainedQIOData(NBTTagCompound data) {
        super.readSustainedQIOData(data);
        NBTTagList windows = data.getTagList("craftingWindows", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < windows.tagCount(); i++) {
            NBTTagCompound window = windows.getCompoundTagAt(i);
            int index = window.getByte("Window") & 0xFF;
            if (index < craftingWindows.length) {
                craftingWindows[index].read(window);
            }
        }
        craftingWindowsLoaded = false;
        shiftClickIntoFrequency = !data.hasKey("qioShiftClickIntoFrequency") || data.getBoolean("qioShiftClickIntoFrequency");
    }
}
