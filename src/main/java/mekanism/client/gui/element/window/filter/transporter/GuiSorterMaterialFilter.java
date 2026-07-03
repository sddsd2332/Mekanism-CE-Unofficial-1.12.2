package mekanism.client.gui.element.window.filter.transporter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.filter.GuiMaterialFilter;
import mekanism.common.content.transporter.TMaterialFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.tile.TileEntityLogisticalSorter;

import javax.annotation.Nullable;

public class GuiSorterMaterialFilter extends GuiMaterialFilter<TMaterialFilter, TileEntityLogisticalSorter> implements GuiSorterFilterHelper {

    public static GuiSorterMaterialFilter create(IGuiWrapper gui, TileEntityLogisticalSorter tile) {
        return new GuiSorterMaterialFilter(gui, (gui.getWidth() - SORTER_FILTER_WIDTH) / 2, 30, tile, null);
    }

    public static GuiSorterMaterialFilter edit(IGuiWrapper gui, TileEntityLogisticalSorter tile, TMaterialFilter filter) {
        return new GuiSorterMaterialFilter(gui, (gui.getWidth() - SORTER_FILTER_WIDTH) / 2, 30, tile, filter);
    }

    private GuiSorterMaterialFilter(IGuiWrapper gui, int x, int y, TileEntityLogisticalSorter tile, @Nullable TMaterialFilter origFilter) {
        super(gui, x, y, SORTER_FILTER_WIDTH, 90, tile, origFilter);
    }

    @Override
    protected void init() {
        super.init();
        addSorterCommonControls(getSlotOffsetY());
    }

    @Override
    protected TMaterialFilter createNewFilter() {
        return new TMaterialFilter();
    }

    @Override
    protected TMaterialFilter cloneFilter(TMaterialFilter filter) {
        return filter.clone();
    }

    @Override
    public TransporterFilter getSorterFilter() {
        return filter;
    }

    @Override
    public TileEntityLogisticalSorter getSorterTile() {
        return tile;
    }

    @Override
    public <ELEMENT extends GuiElement> ELEMENT addSorterChild(ELEMENT element) {
        return addChild(element);
    }
}
