package mekanism.client.gui.element.window.filter.transporter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.filter.GuiItemStackFilter;
import mekanism.common.content.transporter.TItemStackFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.tile.TileEntityLogisticalSorter;
import mekanism.common.util.LangUtils;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.List;

public class GuiSorterItemStackFilter extends GuiItemStackFilter<TItemStackFilter, TileEntityLogisticalSorter> implements GuiSorterFilterHelper {

    public static GuiSorterItemStackFilter create(IGuiWrapper gui, TileEntityLogisticalSorter tile) {
        return new GuiSorterItemStackFilter(gui, (gui.getWidth() - SORTER_FILTER_WIDTH) / 2, 30, tile, null);
    }

    public static GuiSorterItemStackFilter edit(IGuiWrapper gui, TileEntityLogisticalSorter tile, TItemStackFilter filter) {
        return new GuiSorterItemStackFilter(gui, (gui.getWidth() - SORTER_FILTER_WIDTH) / 2, 30, tile, filter);
    }

    private GuiTextField minField;
    private GuiTextField maxField;

    private GuiSorterItemStackFilter(IGuiWrapper gui, int x, int y, TileEntityLogisticalSorter tile, @Nullable TItemStackFilter origFilter) {
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
        addSorterSizeControls((min, max) -> {
            minField = min;
            maxField = max;
        });
    }

    @Override
    protected void validateAndSave() {
        if (filter.getItemStack().isEmpty()) {
            filterSaveFailed(LangUtils.localize("gui.itemFilter.noItem"));
        } else if (validateSorterFields(minField, maxField, error -> {
            filterSaveFailed(error);
            return false;
        })) {
            super.validateAndSave();
        }
    }

    @Override
    protected TItemStackFilter createNewFilter() {
        return new TItemStackFilter();
    }

    @Override
    protected TItemStackFilter cloneFilter(TItemStackFilter filter) {
        return filter.clone();
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        return addSorterSizeScreenText(addSorterCommonScreenText(super.getScreenText()));
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

    @Override
    public int getFilterMin() {
        return filter.min;
    }

    @Override
    public int getFilterMax() {
        return filter.max;
    }

    @Override
    public void setFilterMin(int min) {
        filter.min = min;
    }

    @Override
    public void setFilterMax(int max) {
        filter.max = max;
    }

    @Override
    public boolean getFilterSizeMode() {
        return filter.sizeMode;
    }

    @Override
    public void setFilterSizeMode(boolean sizeMode) {
        filter.sizeMode = sizeMode;
    }
}
