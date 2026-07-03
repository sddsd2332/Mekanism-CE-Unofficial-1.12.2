package mekanism.client.gui.element.window.filter.miner;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.filter.GuiOreDictFilter;
import mekanism.common.OreDictCache;
import mekanism.common.content.miner.MOreDictFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class GuiMinerOreDictFilter extends GuiOreDictFilter<MOreDictFilter, TileEntityDigitalMiner> implements GuiMinerFilterHelper {

    public static GuiMinerOreDictFilter create(IGuiWrapper gui, TileEntityDigitalMiner tile) {
        return new GuiMinerOreDictFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, null);
    }

    public static GuiMinerOreDictFilter edit(IGuiWrapper gui, TileEntityDigitalMiner tile, MOreDictFilter filter) {
        return new GuiMinerOreDictFilter(gui, (gui.getWidth() - MINER_FILTER_WIDTH) / 2, 30, tile, filter);
    }

    private GuiMinerOreDictFilter(IGuiWrapper gui, int x, int y, TileEntityDigitalMiner tile, @Nullable MOreDictFilter origFilter) {
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
        return filter.getOreDictName() == null || filter.getOreDictName().isEmpty() ? Collections.emptyList() : OreDictCache.getOreDictStacks(filter.getOreDictName(), true);
    }

    @Override
    protected boolean hasMatchingTargets(String oreName) {
        return !OreDictCache.getOreDictStacks(oreName, true).isEmpty();
    }

    @Override
    protected MOreDictFilter createNewFilter() {
        return new MOreDictFilter();
    }

    @Override
    protected MOreDictFilter cloneFilter(MOreDictFilter filter) {
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
