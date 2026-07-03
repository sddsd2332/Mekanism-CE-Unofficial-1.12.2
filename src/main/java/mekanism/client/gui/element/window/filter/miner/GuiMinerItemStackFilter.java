package mekanism.client.gui.element.window.filter.miner;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.window.filter.GuiItemStackFilter;
import mekanism.common.MekanismLang;
import mekanism.common.content.miner.MItemStackFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.LangUtils;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.List;

public class GuiMinerItemStackFilter extends GuiItemStackFilter<MItemStackFilter, TileEntityDigitalMiner> implements GuiMinerFilterHelper {

    public static GuiMinerItemStackFilter create(IGuiWrapper gui, TileEntityDigitalMiner tile) {
        return new GuiMinerItemStackFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, null);
    }

    public static GuiMinerItemStackFilter edit(IGuiWrapper gui, TileEntityDigitalMiner tile, MItemStackFilter filter) {
        return new GuiMinerItemStackFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, filter);
    }

    private GuiMinerItemStackFilter(IGuiWrapper gui, int x, int y, TileEntityDigitalMiner tile, @Nullable MItemStackFilter origFilter) {
        super(gui, x, y, MINER_FILTER_WIDTH, 90, tile, origFilter);
    }

    @Override
    protected boolean isBlockFilter() {
        return true;
    }

    @Override
    protected void init() {
        super.init();
        addMinerDefaults(getSlotOffsetY());
        addChild(new TooltipToggleButton(gui(), relativeX + 15, relativeY + 45, 14, getButtonLocation("fuzzy"),
              () -> filter.fuzzy,
              () -> filter.fuzzy = !filter.fuzzy,
              MekanismLang.FUZZY_MODE.translate(), MekanismLang.FUZZY_MODE.translate()));
    }

    @Override
    protected MItemStackFilter createNewFilter() {
        return new MItemStackFilter();
    }

    @Override
    protected MItemStackFilter cloneFilter(MItemStackFilter filter) {
        return filter.clone();
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = super.getScreenText();
        list.add(new TextComponentString(LangUtils.localize("gui.digitalMiner.fuzzyMode") + ": " + LangUtils.transYesNo(filter.fuzzy)));
        return list;
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
