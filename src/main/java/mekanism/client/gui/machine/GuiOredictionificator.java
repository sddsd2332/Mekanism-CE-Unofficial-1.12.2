package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.custom.GuiOredictionificatorFilterRow;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.gui.element.window.filter.GuiOredictionificatorFilter;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.ContainerOredictionificator;
import mekanism.common.tile.machine.TileEntityOredictionificator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiOredictionificator extends GuiConfigurableTile<TileEntityOredictionificator, ContainerOredictionificator> {

    private static final int FILTER_COUNT = 3;
    private GuiScrollBar scrollBar;

    public GuiOredictionificator(InventoryPlayer inventory, TileEntityOredictionificator tile) {
        super(tile, new ContainerOredictionificator(inventory, tile));
        dynamicSlots = true;
        ySize += 64;
        inventoryLabelY = ySize - 94;
        xSize += 60;
        inventoryLabelX += 30;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiElementHolder(this, 9, 17, 204, 68));
        addButton(new GuiElementHolder(this, 9, 85, 204, 22));
        scrollBar = addButton(new GuiScrollBar(this, 213, 17, 90, () -> tileEntity.getFilterManager().count(), () -> FILTER_COUNT));
        addButton(new GuiProgress(() -> tileEntity.didProcess, ProgressType.LARGE_RIGHT, this, 94, 119));
        addButton(new TranslationButton(this, 10, 86, 202, 20, MekanismLang.BUTTON_NEW_FILTER,
              () -> addWindow(GuiOredictionificatorFilter.create(this, tileEntity))));
        for (int i = 0; i < FILTER_COUNT; i++) {
            addButton(new GuiOredictionificatorFilterRow(this, tileEntity, 10, 18 + i * 22, 202, 22, i, () -> scrollBar.getCurrentSelection()));
        }
        trackWarning(WarningType.INVALID_OREDICTIONIFICATOR_FILTER,
              () -> tileEntity.getFilterManager().anyEnabledMatch(filter -> filter.filter == null || filter.filter.isEmpty()));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int delta = org.lwjgl.input.Mouse.getEventDWheel();
        if (delta != 0 && windows.isEmpty()) {
            scrollBar.adjustScroll(delta);
        }
    }
}