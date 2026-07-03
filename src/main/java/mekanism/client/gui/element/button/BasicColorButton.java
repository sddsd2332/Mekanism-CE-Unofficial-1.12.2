package mekanism.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.lib.Color;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class BasicColorButton extends MekanismButton {

    public static BasicColorButton toggle(IGuiWrapper gui, int x, int y, int size, EnumColor color, BooleanSupplier toggled, Runnable onLeftClick) {
        return new BasicColorButton(gui, x, y, size, () -> toggled.getAsBoolean() ? color : null, onLeftClick, onLeftClick);
    }

    private final Supplier<EnumColor> colorSupplier;

    public BasicColorButton(IGuiWrapper gui, int x, int y, int size, Supplier<EnumColor> colorSupplier, @Nullable Runnable onLeftClick,
          @Nullable Runnable onRightClick) {
        super(gui, x, y, size, size, new TextComponentGroup(), onLeftClick, onRightClick, null);
        this.colorSupplier = colorSupplier;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        EnumColor color = getColor();
        if (color != null) {
            Color adjustedColor = Color.rgb(color.rgbCode);
            double[] hsv = adjustedColor.hsvArray();
            hsv[1] = Math.max(0, hsv[1] - 0.1);
            hsv[2] = Math.min(1, hsv[2] + 0.1);
            MekanismRenderer.color(Color.hsv(hsv[0], hsv[1], hsv[2]));
        } else {
            MekanismRenderer.resetColor();
        }
        super.drawBackground(mouseX, mouseY, partialTicks);
        if (color != null) {
            MekanismRenderer.resetColor();
        }
    }

    @Override
    protected boolean resetColorBeforeRender() {
        return false;
    }

    public EnumColor getColor() {
        return colorSupplier.get();
    }
}
