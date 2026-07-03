package mekanism.client.gui;

import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.FilterButton;
import mekanism.client.gui.element.button.MovableFilterButton;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.common.content.filter.*;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

public abstract class GuiFilterHolder<FILTER extends IFilter, TILE extends TileEntityContainerBlock & ITileFilterHolder<FILTER>, CONTAINER extends Container>
      extends GuiMekanismTile<TILE, CONTAINER> {

    protected static final int FILTER_COUNT = 4;

    protected GuiInnerScreen leftScreen;
    protected GuiScrollBar scrollBar;

    protected GuiFilterHolder(TILE tile, CONTAINER container) {
        super(tile, container);
        ySize += 88;
        xSize += 100;
        inventoryLabelX += 50;
        inventoryLabelY = ySize - 94;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        leftScreen = addButton(new GuiInnerScreen(this, 9, 17, 85, 140));
        addButton(new GuiElementHolder(this, 95, 17, 158, 118));
        addButton(new GuiElementHolder(this, 95, 135, 158, 22));
        FilterManager<FILTER> filterManager = getFilterManager();
        scrollBar = addButton(new GuiScrollBar(this, 253, 17, 140, filterManager::count, () -> FILTER_COUNT));
        for (int i = 0; i < FILTER_COUNT; i++) {
            addFilterButton(new MovableFilterButton(this, 96, 18 + i * 29, i, () -> scrollBar.getCurrentSelection(), filterManager,
                  getMoveUpSender(), getMoveDownSender(), this::onClick, getToggleSender(), this::getRenderStacks));
        }
    }

    protected void drawScreenText(ITextComponent text, int y) {
        drawScreenText(text, 0, y);
    }

    protected void drawScreenText(ITextComponent text, int x, int y) {
        if (leftScreen != null) {
            leftScreen.drawScaledScrollingString(text, x, y, TextAlignment.LEFT, screenTextColor(), leftScreen.getWidth() - x, 5, false, 0.8F, GuiElement.getMillis());
        }
    }

    private List<ItemStack> getRenderStacks(@Nullable IFilter filter) {
        if (filter instanceof IItemStackFilter itemFilter) {
            return Collections.singletonList(itemFilter.getItemStack());
        } else if (filter instanceof IMaterialFilter materialFilter) {
            return Collections.singletonList(materialFilter.getMaterialItem());
        } else if (filter instanceof IOreDictFilter oreFilter) {
            return getOreDictStacks(oreFilter.getOreDictName());
        } else if (filter instanceof IModIDFilter modIDFilter) {
            return getModIDStacks(modIDFilter.getModID());
        }
        return Collections.emptyList();
    }

    protected FilterButton addFilterButton(FilterButton button) {
        return addButton(button);
    }

    protected FilterManager<FILTER> getFilterManager() {
        return tileEntity.getFilterManager();
    }

    protected abstract void onClick(IFilter filter, int index);

    protected abstract IntConsumer getMoveUpSender();

    protected abstract IntConsumer getMoveDownSender();

    protected abstract IntConsumer getToggleSender();

    protected abstract List<ItemStack> getOreDictStacks(String oreName);

    protected abstract List<ItemStack> getModIDStacks(String modID);

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        super.drawForegroundText(mouseX, mouseY);
        renderInventoryText();
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        if (scrollBar != null) {
            scrollBar.adjustScroll(org.lwjgl.input.Mouse.getEventDWheel());
        }
    }
}
