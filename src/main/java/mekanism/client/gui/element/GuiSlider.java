package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.util.function.DoubleConsumer;

import static mekanism.client.gui.GuiUtils.blit;

public class GuiSlider extends GuiElement {

    private static final ResourceLocation SLIDER = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "smooth_slider.png");

    private final DoubleConsumer callback;

    private double value;

    public GuiSlider(IGuiWrapper gui, int x, int y, int width, DoubleConsumer callback) {
        super(gui, x, y, width, 12);
        this.callback = callback;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public void renderBackgroundOverlay(int mouseX, int mouseY) {
        super.renderBackgroundOverlay(mouseX, mouseY);
        GuiUtils.fill(relativeX + 2, relativeY + 3, relativeX + width - 2, relativeY + 9, 0xFF555555);
        minecraft.renderEngine.bindTexture(SLIDER);
        int posX = (int) (value * (width - 6));
        blit(relativeX + posX, relativeY, 0, 0, 7, 12, 12, 12);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        set(mouseX, mouseY);
        setDragging(true);
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
        super.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
        if (isDragging()) {
            set(mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        double shift;
        if (isPreviousButton(keyCode) && value > 0) {
            shift = -0.01;
        } else if (isNextButton(keyCode) && value < 1) {
            shift = 0.01;
        } else {
            return false;
        }
        value = Math.max(0, Math.min(1, value + shift));
        callback.accept(value);
        return true;
    }

    private void set(double mouseX, double mouseY) {
        double oldValue = value;
        value = Math.max(0, Math.min(1, (mouseX - getX() - 2) / (width - 6)));
        if (Double.compare(value, oldValue) != 0) {
            callback.accept(value);
        }
    }

    private boolean isPreviousButton(int keyCode) {
        return keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_LEFT;
    }

    private boolean isNextButton(int keyCode) {
        return keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_RIGHT;
    }
}
