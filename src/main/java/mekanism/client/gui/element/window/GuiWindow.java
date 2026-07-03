package mekanism.client.gui.element.window;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.gui.element.button.GuiCloseButton;
import mekanism.client.gui.element.button.GuiPinButton;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.IEmptyContainer;
import mekanism.common.inventory.container.IGUIWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowPosition;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.lib.Color;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiWindow extends GuiTexturedElement implements IGUIWindow {

    private static final Color OVERLAY_COLOR = Color.rgbai(60, 60, 60, 128);

    private final SelectedWindowData windowData;
    private double dragX;
    private double dragY;
    private int prevDX;
    private int prevDY;
    private boolean pinned;

    private Consumer<GuiWindow> closeListener;
    private Consumer<GuiWindow> reattachListener;
    private final long msOpened;

    protected InteractionStrategy interactionStrategy = InteractionStrategy.CONTAINER;

    private static WindowPosition calculateOpenPosition(IGuiWrapper gui, SelectedWindowData windowData, int x, int y, int width, int height) {
        WindowPosition lastPosition = windowData.getLastPosition();
        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        int lastX = lastPosition.x;
        if (lastX != Integer.MAX_VALUE) {
            int guiLeft = gui.getLeft();
            if (guiLeft + lastX < 0) {
                lastX = -guiLeft;
            } else if (guiLeft + lastX + width > scaledResolution.getScaledWidth()) {
                lastX = scaledResolution.getScaledWidth() - guiLeft - width;
            }
        }
        int lastY = lastPosition.y;
        if (lastY != Integer.MAX_VALUE) {
            int guiTop = gui.getTop();
            if (guiTop + lastY < 0) {
                lastY = -guiTop;
            } else if (guiTop + lastY + height > scaledResolution.getScaledHeight()) {
                lastY = scaledResolution.getScaledHeight() - guiTop - height;
            }
        }
        return new WindowPosition(lastX == Integer.MAX_VALUE ? x : lastX, lastY == Integer.MAX_VALUE ? y : lastY, lastPosition.pinned);
    }

    public GuiWindow(IGuiWrapper gui, int x, int y, int width, int height, WindowType windowType) {
        this(gui, x, y, width, height, windowType == WindowType.UNSPECIFIED ? SelectedWindowData.UNSPECIFIED : new SelectedWindowData(windowType));
    }

    public GuiWindow(IGuiWrapper gui, int x, int y, int width, int height, SelectedWindowData windowData) {
        this(gui, calculateOpenPosition(gui, windowData, x, y, width, height), width, height, windowData);
    }

    private GuiWindow(IGuiWrapper gui, WindowPosition calculatedPosition, int width, int height, SelectedWindowData windowData) {
        super(GuiMekanism.BASE_BACKGROUND, gui, calculatedPosition.x, calculatedPosition.y, width, height);
        this.windowData = windowData;
        this.pinned = calculatedPosition.pinned;
        isOverlay = true;
        active = true;
        msOpened = GuiElement.getMillis();
        if (!isFocusOverlay()) {
            addCloseButton();
            if (this.windowData.type.canPin()) {
                addChild(new GuiPinButton(gui(), relativeX + 16, relativeY + 6, this));
            }
        }
    }

    @Override
    public long getTimeOpened() {
        return msOpened;
    }

    public void onFocusLost() {
    }

    public void onFocused() {
        gui().setSelectedWindow(windowData);
    }

    public final InteractionStrategy getInteractionStrategy() {
        return interactionStrategy;
    }

    protected void addCloseButton() {
        addChild(new GuiCloseButton(gui(), relativeX + 6, relativeY + 6, this));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean ret = super.mouseClicked(mouseX, mouseY, button);
        if (isMouseOver(mouseX, mouseY)) {
            if (mouseY < getY() + 18) {
                setDragging(true);
                dragX = mouseX;
                dragY = mouseY;
                prevDX = 0;
                prevDY = 0;
            }
        } else if (!ret && interactionStrategy.allowContainer()) {
            if (gui() instanceof GuiMekanism<?> gui) {
                Container container = gui.inventorySlots;
                if (!(container instanceof IEmptyContainer)) {
                    if (mouseX >= getGuiLeft() && mouseX < getGuiLeft() + getGuiWidth() && mouseY >= getGuiTop() + getGuiHeight() - 90) {
                        return false;
                    }
                }
            }
        }
        return ret || !interactionStrategy.allowAll();
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
        super.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
        if (isDragging()) {
            int newDX = (int) Math.round(mouseX - dragX);
            int newDY = (int) Math.round(mouseY - dragY);
            ScaledResolution scaledResolution = new ScaledResolution(minecraft);
            int changeX = Math.max(-getX(), Math.min(scaledResolution.getScaledWidth() - getRight(), newDX - prevDX));
            int changeY = Math.max(-getY(), Math.min(scaledResolution.getScaledHeight() - getBottom(), newDY - prevDY));
            prevDX = newDX;
            prevDY = newDY;
            move(changeX, changeY);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public void renderBackgroundOverlay(int mouseX, int mouseY) {
        if (isFocusOverlay()) {
            ScaledResolution scaledResolution = new ScaledResolution(minecraft);
            MekanismRenderer.renderColorOverlay(-getGuiLeft(), -getGuiTop(), scaledResolution.getScaledWidth() - getGuiLeft(), scaledResolution.getScaledHeight() - getGuiTop(),
                  OVERLAY_COLOR.rgba());
        } else {
            GlStateManager.color(1, 1, 1, 0.75F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                  GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GuiUtils.renderBackgroundTexture(GuiMekanism.SHADOW, 4, 4, getButtonX() - 3, getButtonY() - 3, getButtonWidth() + 6, getButtonHeight() + 6, 256, 256);
            MekanismRenderer.resetColor();
        }
        minecraft.renderEngine.bindTexture(getResource());
        renderBackgroundTexture(getResource(), 4, 4);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE && !isPinned()) {
            close();
            return true;
        }
        return false;
    }

    public void setListenerTab(Supplier<? extends GuiElement> elementSupplier) {
        setTabListeners(window -> elementSupplier.get().active = true, window -> elementSupplier.get().active = false);
    }

    public void setTabListeners(Consumer<GuiWindow> closeListener, Consumer<GuiWindow> reattachListener) {
        this.closeListener = closeListener;
        this.reattachListener = reattachListener;
    }

    @Override
    public void resize(int prevLeft, int prevTop, int left, int top) {
        super.resize(prevLeft, prevTop, left, top);
        if (reattachListener != null) {
            reattachListener.accept(this);
        }
    }

    public void renderBlur() {
        GlStateManager.color(1, 1, 1, 0.3F);
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GuiUtils.renderBackgroundTexture(GuiMekanism.BLUR, 4, 4, relativeX, relativeY, width, height, 256, 256);
        MekanismRenderer.resetColor();
        GlStateManager.enableDepth();
    }

    public void togglePinned() {
        pinned = !pinned;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void close() {
        gui().removeWindow(this);
        children.forEach(GuiElement::onWindowClose);
        if (closeListener != null) {
            closeListener.accept(this);
        }
        windowData.updateLastPosition(relativeX, relativeY, pinned);
    }

    protected boolean isFocusOverlay() {
        return false;
    }

    @Override
    public void drawTitleText(ITextComponent text, float y) {
        if (isFocusOverlay()) {
            super.drawTitleText(text, y);
        } else {
            drawTitleTextTextWithOffset(text, getTitlePadStart(), y, getXSize() - getTitlePadEnd());
        }
    }

    protected int getTitlePadStart() {
        if (windowData.type.canPin()) {
            return 14 + GuiPinButton.WIDTH;
        }
        return 12;
    }

    protected int getTitlePadEnd() {
        return 0;
    }

    public enum InteractionStrategy {
        NONE,
        CONTAINER,
        ALL;

        public boolean allowContainer() {
            return this != NONE;
        }

        public boolean allowAll() {
            return this == ALL;
        }
    }
}
