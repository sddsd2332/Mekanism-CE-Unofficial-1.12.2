package mekanism.client.gui.element.button;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

public class MekanismButton extends GuiElement {

    @Nullable
    private final IHoverable onHover;
    @Nullable
    private final Runnable onLeftClick;
    @Nullable
    private final Runnable onRightClick;
    @Nullable
    private BooleanSupplier visibilitySupplier;

    public MekanismButton(IGuiWrapper gui, int x, int y, int width, int height, ITextComponent text, @Nullable Runnable onLeftClick, @Nullable IHoverable onHover) {
        this(gui, x, y, width, height, text, onLeftClick, onLeftClick, onHover);
        //TODO: Decide if default implementation for right clicking should be do nothing, or act as left click
    }

    public MekanismButton(IGuiWrapper gui, int x, int y, int width, int height, ITextComponent text, @Nullable Runnable onLeftClick, @Nullable Runnable onRightClick,
                          @Nullable IHoverable onHover) {
        super(gui, x, y, width, height, text);
        this.onHover = onHover;
        this.onLeftClick = onLeftClick;
        this.onRightClick = onRightClick;
        playClickSound = true;
        setButtonBackground(ButtonBackground.DEFAULT);
    }

    public MekanismButton visibility(BooleanSupplier visibilitySupplier) {
        this.visibilitySupplier = visibilitySupplier;
        updateVisibility();
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        updateVisibility();
    }

    private void updateVisibility() {
        if (visibilitySupplier != null) {
            visible = visibilitySupplier.getAsBoolean();
        }
    }

    private void onLeftClick() {
        if (onLeftClick != null) {
            onLeftClick.run();
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onLeftClick();
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (button == 1) {
            onRightClick();
        } else {
            onClick(mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        //From AbstractButton
        if (this.active && this.visible && this.isFocused()) {
            if (keyCode == 257 || keyCode == 32 || keyCode == 335) {
                playDownSound(Minecraft.getMinecraft().getSoundHandler());
                onClick(getButtonX() + getButtonWidth() / 2.0, getButtonY() + getButtonHeight() / 2.0, 0);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (onHover != null) {
            onHover.onHover(this, mouseX, mouseY);
        }
    }

    @Override
    public boolean isValidClickButton(int button) {
        return button == 0 || button == 1 && onRightClick != null;
    }

    protected void onRightClick() {
        if (onRightClick != null) {
            onRightClick.run();
        }
    }
}
