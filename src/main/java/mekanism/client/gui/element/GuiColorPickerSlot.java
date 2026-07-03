package mekanism.client.gui.element;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.GuiColorWindow;
import mekanism.common.MekanismLang;
import mekanism.common.lib.Color;
import mekanism.common.util.text.TextUtils;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiColorPickerSlot extends GuiElement {

    private final Supplier<Color> supplier;
    private final Consumer<Color> consumer;
    private final boolean handlesAlpha;
    @Nullable
    private ITextComponent lastTooltip;
    @Nullable
    private Color lastColor;

    public GuiColorPickerSlot(IGuiWrapper gui, int x, int y, boolean handlesAlpha, Supplier<Color> supplier, Consumer<Color> consumer) {
        super(gui, x, y, 18, 18);
        this.handlesAlpha = handlesAlpha;
        this.supplier = supplier;
        this.consumer = consumer;
        addChild(new GuiElementHolder(gui, relativeX, relativeY, 18, 18));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        GuiUtils.fill(relativeX + 1, relativeY + 1, relativeX + width - 1, relativeY + height - 1, supplier.get().argb());
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        Color color = supplier.get();
        if (!color.equals(lastColor)) {
            lastColor = color;
            lastTooltip = MekanismLang.GENERIC_HEX.translateColored(EnumColor.GREY, TextUtils.hex(false, 3, color.rgb()));
        }
        displayTooltip(lastTooltip, mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        gui().addWindow(new GuiColorWindow(gui(), (getGuiWidth() - 160) / 2, (getGuiHeight() - 120) / 2, handlesAlpha, supplier.get(), consumer));
    }
}
