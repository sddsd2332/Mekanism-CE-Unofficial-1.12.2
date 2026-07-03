package mekanism.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.client.gui.IGuiWrapper;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public class TooltipColorButton extends BasicColorButton {

    private final BooleanSupplier toggled;
    private final List<String> enabledTooltip;
    private final List<String> disabledTooltip;

    public TooltipColorButton(IGuiWrapper gui, int x, int y, int size, EnumColor color, BooleanSupplier toggled, Runnable onLeftClick,
          String enabledTooltip, String disabledTooltip) {
        this(gui, x, y, size, color, toggled, onLeftClick, Collections.singletonList(enabledTooltip), Collections.singletonList(disabledTooltip));
    }

    public TooltipColorButton(IGuiWrapper gui, int x, int y, int size, EnumColor color, BooleanSupplier toggled, Runnable onLeftClick,
          List<String> enabledTooltip, List<String> disabledTooltip) {
        super(gui, x, y, size, () -> toggled.getAsBoolean() ? color : null, onLeftClick, onLeftClick);
        this.toggled = toggled;
        this.enabledTooltip = enabledTooltip;
        this.disabledTooltip = disabledTooltip;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltips(toggled.getAsBoolean() ? enabledTooltip : disabledTooltip, mouseX, mouseY);
    }
}
