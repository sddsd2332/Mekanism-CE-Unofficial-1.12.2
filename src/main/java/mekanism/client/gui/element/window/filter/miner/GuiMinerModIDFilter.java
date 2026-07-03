package mekanism.client.gui.element.window.filter.miner;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.filter.GuiModIDFilter;
import mekanism.common.OreDictCache;
import mekanism.common.content.miner.MModIDFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class GuiMinerModIDFilter extends GuiModIDFilter<MModIDFilter, TileEntityDigitalMiner> implements GuiMinerFilterHelper {

    public static GuiMinerModIDFilter create(IGuiWrapper gui, TileEntityDigitalMiner tile) {
        return new GuiMinerModIDFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, null);
    }

    public static GuiMinerModIDFilter edit(IGuiWrapper gui, TileEntityDigitalMiner tile, MModIDFilter filter) {
        return new GuiMinerModIDFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, filter);
    }

    private GuiMinerModIDFilter(IGuiWrapper gui, int x, int y, TileEntityDigitalMiner tile, @Nullable MModIDFilter origFilter) {
        super(gui, x, y, MINER_FILTER_WIDTH, 90, tile, origFilter);
    }

    @Override
    protected void init() {
        super.init();
        addMinerDefaults(getSlotOffsetY());
    }

    @Nonnull
    @Override
    protected List<ItemStack> getRenderStacks() {
        return filter.getModID() == null || filter.getModID().isEmpty() ? Collections.emptyList() : OreDictCache.getModIDStacks(filter.getModID(), true);
    }

    @Override
    protected boolean hasMatchingTargets(String modName) {
        return !OreDictCache.getModIDStacks(modName, true).isEmpty();
    }

    @Override
    protected MModIDFilter createNewFilter() {
        return new MModIDFilter();
    }

    @Override
    protected MModIDFilter cloneFilter(MModIDFilter filter) {
        return filter.clone();
    }

    @Override
    public MinerFilter getMinerFilter() {
        return filter;
    }

    @Override
    public TileEntityDigitalMiner getMinerTile() {
        return tile;
    }

    @Override
    public <ELEMENT extends GuiElement> ELEMENT addMinerChild(ELEMENT element) {
        return addChild(element);
    }
}
