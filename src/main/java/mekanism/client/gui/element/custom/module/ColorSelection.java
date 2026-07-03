package mekanism.client.gui.element.custom.module;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiColorWindow;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.MekanismLang;
import mekanism.common.util.text.TextUtils;

class ColorSelection extends MiniElement {

    private static final int SWATCH_SIZE = 18;

    private final GuiModuleScreen.SelectedColorConfig selection;
    private final int offsetX;

    ColorSelection(GuiModuleScreen parent, GuiModuleScreen.SelectedColorConfig selection, int xPos, int yPos, int dataIndex) {
        super(parent, xPos, yPos, dataIndex);
        this.selection = selection;
        offsetX = this.parent.getScreenWidth() - 26;
    }

    @Override
    int getNeededHeight() {
        return 20;
    }

    @Override
    void renderBackground(int mouseX, int mouseY) {
        int swatchX = getRelativeX() + offsetX;
        int swatchY = getRelativeY() + 1;
        GuiUtils.drawOutline(swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE, GuiTextField.SCREEN_COLOR);
        GuiElement.minecraft.renderEngine.bindTexture(GuiColorWindow.TRANSPARENCY_GRID);
        GuiUtils.blit(swatchX + 1, swatchY + 1, 0, 0, 16, 16, 16, 16);
        GuiUtils.fill(swatchX + 1, swatchY + 1, swatchX + SWATCH_SIZE - 1, swatchY + SWATCH_SIZE - 1, selection.getColor());
    }

    @Override
    void renderForeground(int mouseX, int mouseY) {
        int textColor = parent.screenTextColor();
        parent.drawScaledScrollingString(selection.getDescription(), xPos, yPos, TextAlignment.LEFT, textColor, offsetX, 3, false, 0.8F,
              GuiElement.getMillis());
        int color = selection.getColor();
        String hex = selection.handlesAlpha() ? TextUtils.hex(false, 4, color) : TextUtils.hex(false, 3, color & 0xFFFFFF);
        parent.drawScrollingString(MekanismLang.GENERIC_HEX.translate(hex), xPos, yPos + 11, TextAlignment.LEFT, textColor, offsetX, 3, false,
              GuiElement.getMillis());
    }

    @Override
    void click(double mouseX, double mouseY) {
        if (mouseOver(mouseX, mouseY, offsetX, 1, SWATCH_SIZE, SWATCH_SIZE)) {
            int windowWidth = GuiColorWindow.getWindowWidth(selection.handlesAlpha(), parent.getArmorPreview() != null);
            int windowHeight = GuiColorWindow.getWindowHeight(selection.handlesAlpha());
            int windowX = parent.getGuiWidth() / 2 - windowWidth / 2;
            int windowY = parent.getGuiHeight() / 2 - windowHeight / 2;
            Runnable previewUpdater = parent.getArmorPreview() != null && parent.getCurrentModule() != null ?
                  () -> parent.getArmorPreview().tryUpdateFull(parent.getCurrentModule().getContainer()) : null;
            parent.gui().addWindow(new GuiColorWindow(parent.gui(), windowX, windowY, selection, previewUpdater, parent.getArmorPreview()));
        }
    }

}
