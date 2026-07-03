package mekanism.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.api.text.ILangEntry;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.render.MekanismRenderer;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class TranslationButton extends MekanismButton {

    @Nullable
    private final Supplier<EnumColor> colorSupplier;
    @Nullable
    private final GuiElement.IClickable onPress;

    public TranslationButton(IGuiWrapper gui, int x, int y, int width, int height, ILangEntry translationHelper, Runnable onPress) {
        this(gui, x, y, width, height, translationHelper, onPress, null, null);
    }

    public TranslationButton(IGuiWrapper gui, int x, int y, int width, int height, ILangEntry translationHelper, Runnable onPress,
          @Nullable IHoverable onHover) {
        this(gui, x, y, width, height, translationHelper, onPress, onHover, null);
    }

    public TranslationButton(IGuiWrapper gui, int x, int y, int width, int height, ILangEntry translationHelper, Runnable onPress,
          @Nullable IHoverable onHover, @Nullable Supplier<EnumColor> colorSupplier) {
        super(gui, x, y, width, height, translationHelper.translate(), onPress, onHover);
        this.colorSupplier = colorSupplier;
        this.onPress = null;
    }

    public TranslationButton(IGuiWrapper gui, int x, int y, int width, int height, ILangEntry translationHelper, GuiElement.IClickable onPress) {
        this(gui, x, y, width, height, translationHelper, onPress, null);
    }

    public TranslationButton(IGuiWrapper gui, int x, int y, int width, int height, ILangEntry translationHelper, GuiElement.IClickable onPress,
          @Nullable Supplier<EnumColor> colorSupplier) {
        super(gui, x, y, width, height, translationHelper.translate(), null, null);
        this.colorSupplier = colorSupplier;
        this.onPress = onPress;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (onPress == null) {
            super.onClick(mouseX, mouseY, button);
        } else {
            onPress.onClick(this, mouseX, mouseY);
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        if (colorSupplier == null) {
            MekanismRenderer.resetColor();
            super.drawBackground(mouseX, mouseY, partialTicks);
            return;
        }
        MekanismRenderer.color(colorSupplier.get());
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.resetColor();
    }

    @Override
    protected boolean resetColorBeforeRender() {
        return false;
    }
}
