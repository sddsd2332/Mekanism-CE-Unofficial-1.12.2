package mekanism.client.gui.element.scroll;

import mekanism.client.gui.IGuiWrapper;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.util.ResourceLocation;

public abstract class GuiScrollList extends GuiScrollableElement {

    public static final ResourceLocation SCROLL_LIST = MekanismUtils.getResource(ResourceType.GUI, "scroll_list.png");
    public static final int TEXTURE_WIDTH = 6;
    public static final int TEXTURE_HEIGHT = 6;

    private final ResourceLocation background;
    private final int backgroundSideSize;
    protected final int elementHeight;

    protected GuiScrollList(IGuiWrapper gui, int x, int y, int width, int height, int elementHeight, ResourceLocation background, int backgroundSideSize) {
        super(SCROLL_LIST, gui, x, y, width, height, width - 6, 2, 4, 4, height - 4);
        this.elementHeight = elementHeight;
        this.background = background;
        this.backgroundSideSize = backgroundSideSize;
    }

    @Override
    protected int getFocusedElements() {
        return (height - 2) / elementHeight;
    }

    public abstract boolean hasSelection();

    protected abstract void setSelected(int index);

    public abstract void clearSelection();

    protected abstract void renderElements(int mouseX, int mouseY, float partialTicks);

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        //Draw the background
        renderBackgroundTexture(background, backgroundSideSize, backgroundSideSize);
        //Draw Scroll
        drawScrollBar(TEXTURE_WIDTH, TEXTURE_HEIGHT);
        //Draw the elements
        renderElements(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        int absoluteX = getX();
        int absoluteY = getY();
        if (mouseX >= absoluteX + 1 && mouseX < absoluteX + barXShift - 1 && mouseY >= absoluteY + 1 && mouseY < absoluteY + height - 1) {
            int index = getCurrentSelection();
            int focused = getFocusedElements();
            int maxElements = getMaxElements();
            for (int i = 0; i < focused && index + i < maxElements; i++) {
                int shiftedY = absoluteY + 1 + elementHeight * i;
                if (mouseY >= shiftedY && mouseY <= shiftedY + elementHeight) {
                    setSelected(index + i);
                    return;
                }
            }
            clearSelection();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOver(mouseX, mouseY) && adjustScroll(delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }
}
