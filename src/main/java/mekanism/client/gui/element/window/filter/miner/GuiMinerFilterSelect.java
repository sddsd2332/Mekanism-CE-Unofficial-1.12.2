package mekanism.client.gui.element.window.filter.miner;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.filter.GuiFilterSelect;
import mekanism.common.MekanismLang;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.api.text.ILangEntry;

public class GuiMinerFilterSelect extends GuiFilterSelect<TileEntityDigitalMiner> {

    public GuiMinerFilterSelect(IGuiWrapper gui, TileEntityDigitalMiner tile) {
        super(gui, tile, 3);
    }

    @Override
    protected GuiFilterCreator<TileEntityDigitalMiner> getItemStackFilterCreator() {
        return GuiMinerItemStackFilter::create;
    }

    @Override
    protected GuiFilterCreator<TileEntityDigitalMiner> getOreDictFilterCreator() {
        return GuiMinerOreDictFilter::create;
    }

    @Override
    protected GuiFilterCreator<TileEntityDigitalMiner> getMaterialFilterCreator() {
        return null;
    }

    @Override
    protected GuiFilterCreator<TileEntityDigitalMiner> getModIDFilterCreator() {
        return GuiMinerModIDFilter::create;
    }

    @Override
    protected ILangEntry getOreDictFilterLabel() {
        return MekanismLang.BUTTON_TAG_FILTER;
    }
}
