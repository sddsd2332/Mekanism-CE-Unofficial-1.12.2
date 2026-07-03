package mekanism.client.gui.element.custom.module;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.RadioButton;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.MekanismLang;
import mekanism.common.MekanismSounds;
import mekanism.common.content.gear.ModuleConfigItem;
import net.minecraft.client.audio.PositionedSoundRecord;

class BooleanToggle extends MiniElement {

    private static final int RADIO_SIZE = RadioButton.RADIO_SIZE;

    private final ModuleConfigItem<Boolean> data;

    BooleanToggle(GuiModuleScreen parent, ModuleConfigItem<Boolean> data, int xPos, int yPos, int dataIndex) {
        super(parent, xPos, yPos, dataIndex);
        this.data = data;
    }

    @Override
    int getNeededHeight() {
        return 20;
    }

    @Override
    void renderBackground(int mouseX, int mouseY) {
        GuiElement.minecraft.renderEngine.bindTexture(RadioButton.RADIO);
        drawRadio(mouseX, mouseY, data.get(), 4, 11, 0);
        drawRadio(mouseX, mouseY, !data.get(), 50, 11, RADIO_SIZE);
    }

    private void drawRadio(int mouseX, int mouseY, boolean selected, int relativeX, int relativeY, int selectedU) {
        if (selected) {
            GuiUtils.blit(getRelativeX() + relativeX, getRelativeY() + relativeY, selectedU, RADIO_SIZE, RADIO_SIZE, RADIO_SIZE,
                  2 * RADIO_SIZE, 2 * RADIO_SIZE);
        } else {
            boolean hovered = mouseOver(mouseX, mouseY, relativeX, relativeY, RADIO_SIZE, RADIO_SIZE);
            GuiUtils.blit(getRelativeX() + relativeX, getRelativeY() + relativeY, hovered ? RADIO_SIZE : 0, 0, RADIO_SIZE, RADIO_SIZE,
                  2 * RADIO_SIZE, 2 * RADIO_SIZE);
        }
    }

    @Override
    void renderForeground(int mouseX, int mouseY) {
        int textColor = parent.screenTextColor();
        parent.drawScaledScrollingString(data.getDescription(), xPos, yPos, TextAlignment.LEFT, textColor, parent.getScreenWidth() - GuiScrollList.TEXTURE_WIDTH,
              2, false, 0.8F, GuiElement.getMillis());

        int trueShift = 4 + RADIO_SIZE;
        int falseShift = 50 + RADIO_SIZE;
        parent.drawScaledScrollingString(MekanismLang.TRUE.translate(), xPos + trueShift, yPos + 11, TextAlignment.LEFT, textColor,
              50 - trueShift, 3, false, 0.8F, GuiElement.getMillis());
        parent.drawScaledScrollingString(MekanismLang.FALSE.translate(), xPos + falseShift, yPos + 11, TextAlignment.LEFT, textColor,
              parent.getScreenWidth() - GuiScrollList.TEXTURE_WIDTH - falseShift, 3, false, 0.8F, GuiElement.getMillis());
    }

    @Override
    void click(double mouseX, double mouseY) {
        if (data.get()) {
            if (mouseOver(mouseX, mouseY, 50, 11, RADIO_SIZE, RADIO_SIZE)) {
                setDataFromClick(false);
            }
        } else if (mouseOver(mouseX, mouseY, 4, 11, RADIO_SIZE, RADIO_SIZE)) {
            setDataFromClick(true);
        }
    }

    private void setDataFromClick(boolean value) {
        data.set(value, () -> parent.saveCallback.accept(dataIndex, data.getData()));
        GuiElement.minecraft.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(MekanismSounds.BEEP, 1.0F));
    }
}
