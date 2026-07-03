package mekanism.client.gui.element.window.filter.transporter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.filter.GuiModIDFilter;
import mekanism.common.content.transporter.TModIDFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.tile.TileEntityLogisticalSorter;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.List;

public class GuiSorterModIDFilter extends GuiModIDFilter<TModIDFilter, TileEntityLogisticalSorter> implements GuiSorterFilterHelper {

    public static GuiSorterModIDFilter create(IGuiWrapper gui, TileEntityLogisticalSorter tile) {
        return new GuiSorterModIDFilter(gui, (gui.getWidth() - SORTER_FILTER_WIDTH) / 2, 30, tile, null);
    }

    public static GuiSorterModIDFilter edit(IGuiWrapper gui, TileEntityLogisticalSorter tile, TModIDFilter filter) {
        return new GuiSorterModIDFilter(gui, (gui.getWidth() - SORTER_FILTER_WIDTH) / 2, 30, tile, filter);
    }

    private GuiSorterModIDFilter(IGuiWrapper gui, int x, int y, TileEntityLogisticalSorter tile, @Nullable TModIDFilter origFilter) {
        super(gui, x, y, SORTER_FILTER_WIDTH, 90, tile, origFilter);
    }

    @Override
    protected int getLeftButtonX() {
        return relativeX + 24;
    }

    @Override
    protected void init() {
        super.init();
        addSorterCommonControls(getSlotOffsetY());
    }

    @Override
    protected void validateAndSave() {
        if (text == null || text.getText().isEmpty() || setText()) {
            super.validateAndSave();
        }
    }

    @Override
    protected TModIDFilter createNewFilter() {
        return new TModIDFilter();
    }

    @Override
    protected TModIDFilter cloneFilter(TModIDFilter filter) {
        return filter.clone();
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        return addSorterCommonScreenText(super.getScreenText());
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
