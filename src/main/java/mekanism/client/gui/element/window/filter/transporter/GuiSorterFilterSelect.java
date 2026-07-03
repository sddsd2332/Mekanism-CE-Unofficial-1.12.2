package mekanism.client.gui.element.window.filter.transporter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.filter.GuiFilterSelect;
import mekanism.common.MekanismLang;
import mekanism.common.tile.TileEntityLogisticalSorter;
import mekanism.api.text.ILangEntry;

public class GuiSorterFilterSelect extends GuiFilterSelect<TileEntityLogisticalSorter> {

    public GuiSorterFilterSelect(IGuiWrapper gui, TileEntityLogisticalSorter tile) {
        super(gui, tile, 3);
    }

    @Override
    protected GuiFilterCreator<TileEntityLogisticalSorter> getItemStackFilterCreator() {
        return GuiSorterItemStackFilter::create;
    }

    @Override
    protected GuiFilterCreator<TileEntityLogisticalSorter> getOreDictFilterCreator() {
        return GuiSorterOreDictFilter::create;
    }

    @Override
    protected GuiFilterCreator<TileEntityLogisticalSorter> getMaterialFilterCreator() {
        return null;
    }

    @Override
    protected GuiFilterCreator<TileEntityLogisticalSorter> getModIDFilterCreator() {
        return GuiSorterModIDFilter::create;
    }

    @Override
    protected ILangEntry getOreDictFilterLabel() {
        return MekanismLang.BUTTON_TAG_FILTER;
    }
}
