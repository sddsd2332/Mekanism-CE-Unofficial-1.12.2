package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.MekanismSounds;
import net.minecraft.util.text.ITextComponent;

import java.util.Collections;
import java.util.function.BooleanSupplier;

public class GuiScreenSwitch extends GuiInnerScreen {

    private static final int BUTTON_SIZE_X = 15;
    private static final int BUTTON_SIZE_Y = 8;

    private final BooleanSupplier stateSupplier;
    private final GuiElement.IClickable onToggle;

    public GuiScreenSwitch(IGuiWrapper gui, int x, int y, int width, ITextComponent buttonName, BooleanSupplier stateSupplier,
          GuiElement.IClickable onToggle) {
        super(gui, x, y, width, BUTTON_SIZE_Y * 2 + 6, () -> Collections.singletonList(buttonName));
        this.stateSupplier = stateSupplier;
        this.onToggle = onToggle;
        this.active = true;
        customClickSound = () -> MekanismSounds.BEEP;
        clickSoundVolume = 1.0F;
        padding(4);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        int buttonXOffset = width - 2 - BUTTON_SIZE_X;
        MekanismRenderer.bindTexture(GuiDigitalSwitch.SWITCH);
        GuiUtils.blit(relativeX + buttonXOffset, relativeY + 2, 0, stateSupplier.getAsBoolean() ? 0 : BUTTON_SIZE_Y, BUTTON_SIZE_X, BUTTON_SIZE_Y,
              BUTTON_SIZE_X, BUTTON_SIZE_Y * 2);
        GuiUtils.blit(relativeX + buttonXOffset, relativeY + 2 + BUTTON_SIZE_Y + 1, 0, stateSupplier.getAsBoolean() ? BUTTON_SIZE_Y : 0, BUTTON_SIZE_X,
              BUTTON_SIZE_Y, BUTTON_SIZE_X, BUTTON_SIZE_Y * 2);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        int buttonXOffset = width - 2 - BUTTON_SIZE_X;
        drawScaledScrollingString(MekanismLang.ON.translate(), buttonXOffset, 2, TextAlignment.CENTER, 0x101010, BUTTON_SIZE_X, 1, false, 0.5F,
              GuiElement.getMillis());
        drawScaledScrollingString(MekanismLang.OFF.translate(), buttonXOffset, 11, TextAlignment.CENTER, 0x101010, BUTTON_SIZE_X, 1, false, 0.5F,
              GuiElement.getMillis());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onToggle.onClick(this, mouseX, mouseY);
    }

    @Override
    protected int getMaxTextWidth(int row) {
        return super.getMaxTextWidth(row) - 2 - BUTTON_SIZE_X;
    }
}
