package mekanism.client.gui.qio;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.custom.GuiQIOFilterList;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.client.gui.element.window.GuiQIOFilterSelectWindow;
import mekanism.client.gui.element.window.GuiQIOResourceFilterWindow;
import mekanism.client.gui.element.window.GuiQIOTextFilterWindow;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOModIDFilter;
import mekanism.common.content.qio.filter.QIOOreDictFilter;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import mekanism.common.util.text.TextUtils;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Shared 26.2-style three-row filter layout for QIO automation components. */
public abstract class GuiQIOFilterHandler<TILE extends TileEntityQIOFilterHandler, CONTAINER extends MekanismTileContainer<TILE>>
      extends GuiMekanismTile<TILE, CONTAINER> {

    private GuiQIOFrequencyTab<?> frequencyTab;

    protected GuiQIOFilterHandler(TILE tile, CONTAINER container) {
        super(tile, container);
        xSize = 236;
        ySize = 240;
        dynamicSlots = true;
        inventoryLabelX = 38;
        inventoryLabelY = ySize - 94;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOFrequencyTab.Tile(this, tileEntity, () -> frequencyTab));
        addButton(new GuiInnerScreen(this, 9, 16, xSize - 18, 12, this::getFrequencyText)
              .tooltip(this::getFrequencyTooltip));
        addButton(new GuiElementHolder(this, 9, 30, 204, 68));
        addButton(new GuiElementHolder(this, 9, 98, 204, 22));
        addButton(new GuiQIOFilterList(this, 10, 31, 202, 3, 22, 90, tileEntity,
              this::editFilter));
        addButton(new TranslationButton(this, 10, 99, 202, 20, MekanismLang.BUTTON_NEW_FILTER,
              () -> addWindow(new GuiQIOFilterSelectWindow(this, tileEntity))));
    }

    private void editFilter(QIOFilter filter, Integer index) {
        if (filter instanceof QIOOreDictFilter || filter instanceof QIOModIDFilter) {
            addWindow(new GuiQIOTextFilterWindow(this, tileEntity, filter, index));
        } else {
            addWindow(new GuiQIOResourceFilterWindow(this, tileEntity, filter, index));
        }
    }

    private List<ITextComponent> getFrequencyText() {
        QIOFrequency frequency = tileEntity.getQIOFrequency();
        return Collections.singletonList(frequency == null ? new TextComponentTranslation("frequency.mekanism.none") :
              MekanismLang.FREQUENCY.translate(frequency.getName()));
    }

    private List<ITextComponent> getFrequencyTooltip() {
        QIOFrequency frequency = tileEntity.getQIOFrequency();
        if (frequency == null) {
            return Collections.emptyList();
        }
        return QIOGuiCapacityText.forFrequency(frequency);
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
