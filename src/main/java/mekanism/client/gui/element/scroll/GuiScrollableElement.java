package mekanism.client.gui.element.scroll;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiTexturedElement;
import net.minecraft.util.ResourceLocation;

public abstract class GuiScrollableElement extends GuiTexturedElement {

    protected double scroll;
    private int dragOffset;
    protected final int maxBarHeight;
    protected final int barWidth;
    protected final int barHeight;
    protected final int barXShift;
    protected int barX;
    protected int barY;

    protected GuiScrollableElement(ResourceLocation resource, IGuiWrapper gui, int x, int y, int width, int height, int barXShift, int barYShift, int barWidth, int barHeight, int maxBarHeight) {
        super(resource, gui, x, y, width, height);
        this.barXShift = barXShift;
        this.barX = this.relativeX + barXShift;
        this.barY = this.relativeY + barYShift;
        this.barWidth = barWidth;
        this.barHeight = barHeight;
        this.maxBarHeight = maxBarHeight;
    }

    @Override
    public void move(int changeX, int changeY) {
        super.move(changeX, changeY);
        barX += changeX;
        barY += changeY;
    }

    protected abstract int getMaxElements();

    protected abstract int getFocusedElements();

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        int scroll = getScroll();
        int x = getGuiLeft() + barX;
        int y = getGuiTop() + barY;
        if (mouseX >= x && mouseX <= x + barWidth && mouseY >= y + scroll && mouseY <= y + scroll + barHeight) {
            if (needsScrollBars()) {
                double yAxis = mouseY - getGuiTop();
                dragOffset = (int) (yAxis - (scroll + barY));
                //Mark that we are dragging so that we can continue to "drag" even if our mouse goes off of being over the element
                setDragging(true);
            } else {
                this.scroll = 0;
            }
        }
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
        super.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
        if (isDragging() && needsScrollBars()) {
            double yAxis = mouseY - getGuiTop();
            this.scroll = Math.min(Math.max((yAxis - barY - dragOffset) / getMax(), 0), 1);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        dragOffset = 0;
    }

    protected boolean needsScrollBars() {
        return getMaxElements() > getFocusedElements();
    }

    protected final int getElements() {
        return getMaxElements() - getFocusedElements();
    }

    protected int getScrollElementScaler() {
        return 1;
    }

    private int getMax() {
        return maxBarHeight - barHeight;
    }

    protected int getScroll() {
        //Calculate thumb position along scrollbar
        int max = getMax();
        return Math.max(Math.min((int) (scroll * max), max), 0);
    }

    public int getCurrentSelection() {
        clampScroll();
        if (needsScrollBars()) {
            return (int) ((getElements() + 0.5) * scroll);
        }
        return 0;
    }

    public void setCurrentSelection(int selection) {
        int elements = getElements();
        if (elements <= 0) {
            scroll = 0;
            return;
        }
        int clamped = Math.max(0, Math.min(elements, selection));
        scroll = clamped / (double) elements;
    }

    public boolean adjustScroll(double delta) {
        if (delta != 0 && needsScrollBars()) {
            int elements = (int) Math.ceil(getElements() / (double) getScrollElementScaler());
            if (elements > 0) {
                if (delta > 0) {
                    delta = 1;
                } else {
                    delta = -1;
                }
                scroll = (float) (scroll - delta / elements);
                if (scroll < 0.0F) {
                    scroll = 0.0F;
                } else if (scroll > 1.0F) {
                    scroll = 1.0F;
                }
                return true;
            }
        }
        return false;
    }

    public void resetScroll() {
        scroll = 0;
    }

    protected void clampScroll() {
        if (needsScrollBars()) {
            scroll = Math.min(Math.max(scroll, 0), 1);
        } else {
            scroll = 0;
        }
    }

    protected void drawScrollBar(int textureWidth, int textureHeight) {
        GuiElement.minecraft.renderEngine.bindTexture(getResource());
        //Top border
        GuiUtils.blit(barX - 1, barY - 1, 0, 0, textureWidth, 1, textureWidth, textureHeight);
        //Middle border
        GuiUtils.blit(barX - 1, barY, textureWidth, maxBarHeight, 0, 1, textureWidth, 1, textureWidth, textureHeight);
        //Bottom border
        GuiUtils.blit(barX - 1, relativeY + maxBarHeight + 2, 0, 0, textureWidth, 1, textureWidth, textureHeight);
        //Scroll bar
        GuiUtils.blit(barX, barY + getScroll(), 0, 2, barWidth, barHeight, textureWidth, textureHeight);
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        GuiScrollableElement old = (GuiScrollableElement) element;
        if (needsScrollBars() && old.needsScrollBars()) {
            //Only copy scrolling if we need scroll bars and used to also need scroll bars
            scroll = old.scroll;
        }
        //Note: We don't care about dragging as there is no way for the user while continuing to have MC focussed can change the window size
        // switching into full screen makes MC lose focus briefly anyway so dragging events don't continue to fire so that is not a case
        // that we need to worry about
    }
}
