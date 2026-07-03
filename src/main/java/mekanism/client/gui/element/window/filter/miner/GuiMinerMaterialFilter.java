package mekanism.client.gui.element.window.filter.miner;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.filter.GuiMaterialFilter;
import mekanism.common.content.miner.MMaterialFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.tile.machine.TileEntityDigitalMiner;

import javax.annotation.Nullable;

public class GuiMinerMaterialFilter extends GuiMaterialFilter<MMaterialFilter, TileEntityDigitalMiner> implements GuiMinerFilterHelper {

    public static GuiMinerMaterialFilter create(IGuiWrapper gui, TileEntityDigitalMiner tile) {
        return new GuiMinerMaterialFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, null);
    }

    public static GuiMinerMaterialFilter edit(IGuiWrapper gui, TileEntityDigitalMiner tile, MMaterialFilter filter) {
        return new GuiMinerMaterialFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, filter);
    }

    private GuiMinerMaterialFilter(IGuiWrapper gui, int x, int y, TileEntityDigitalMiner tile, @Nullable MMaterialFilter origFilter) {
        super(gui, x, y, MINER_FILTER_WIDTH, 90, tile, origFilter);
    }

    @Override
    protected void init() {
        super.init();
        addMinerDefaults(getSlotOffsetY());
    }

    @Override
    protected MMaterialFilter createNewFilter() {
        return new MMaterialFilter();
    }

    @Override
    protected MMaterialFilter cloneFilter(MMaterialFilter filter) {
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
