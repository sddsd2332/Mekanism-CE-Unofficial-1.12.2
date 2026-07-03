package mekanism.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.MekanismLang;
import mekanism.common.lib.Color;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ColorButton extends MekanismButton {

    private static final List<String> NONE = Collections.singletonList(MekanismLang.NONE.translate().getFormattedText());

    private final Map<EnumColor, List<String>> cachedTooltips = new EnumMap<>(EnumColor.class);
    private final Supplier<EnumColor> colorSupplier;
    private final Supplier<List<String>> tooltipSupplier;

    public ColorButton(IGuiWrapper gui, int x, int y, int width, int height, Supplier<EnumColor> colorSupplier, Runnable onLeftClick, Runnable onRightClick) {
        this(gui, x, y, width, height, colorSupplier, onLeftClick, onRightClick, null);
    }

    public ColorButton(IGuiWrapper gui, int x, int y, int width, int height, Supplier<EnumColor> colorSupplier, Runnable onLeftClick, Runnable onRightClick,
          Supplier<List<String>> tooltipSupplier) {
        super(gui, x, y, width, height, new TextComponentGroup(), onLeftClick, onRightClick, null);
        this.colorSupplier = colorSupplier;
        this.tooltipSupplier = tooltipSupplier == null ? this::getDefaultTooltip : tooltipSupplier;
        setButtonBackground(ButtonBackground.NONE);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        EnumColor color = colorSupplier.get();
        if (color != null) {
            GuiUtils.fill(getButtonX(), getButtonY(), getButtonX() + getButtonWidth(), getButtonY() + getButtonHeight(),
                  Color.rgb(color.rgbCode).argb());
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        List<String> tooltips = tooltipSupplier.get();
        displayTooltips(tooltips, mouseX, mouseY);
    }

    public EnumColor getColor() {
        return colorSupplier.get();
    }

    private List<String> getDefaultTooltip() {
        EnumColor color = colorSupplier.get();
        if (color == null) {
            return NONE;
        }
        return cachedTooltips.computeIfAbsent(color, c -> Collections.singletonList(c.getColoredName()));
    }
}
