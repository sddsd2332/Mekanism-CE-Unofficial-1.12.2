package mekanism.client.gui.element.button;

import mekanism.api.text.ILangEntry;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.lib.Color;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;

public class DigitalButton extends TranslationButton {

    @Nullable
    private ITextComponent tooltip;

    public DigitalButton(IGuiWrapper gui, int x, int y, int width, int height, ILangEntry translationHelper, IClickable onPress) {
        super(gui, x, y, width, height, translationHelper, onPress, null);
        setButtonBackground(ButtonBackground.DIGITAL);
    }

    @Override
    protected int getButtonTextColor(int mouseX, int mouseY) {
        if (active) {
            if (isMouseOverCheckWindows(mouseX, mouseY)) {
                return screenTextColor();
            }
            return Color.argb(screenTextColor()).darken(0.2).argb();
        }
        return Color.argb(screenTextColor()).darken(0.4).argb();
    }

    @Override
    protected boolean displayButtonTextShadow() {
        return false;
    }

    public DigitalButton setTooltip(ITextComponent tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (tooltip != null) {
            displayTooltip(tooltip, mouseX, mouseY);
        }
    }
}
