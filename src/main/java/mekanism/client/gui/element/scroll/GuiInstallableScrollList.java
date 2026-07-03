package mekanism.client.gui.element.scroll;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.List;

public abstract class GuiInstallableScrollList<TYPE> extends GuiScrollList {

    private final ResourceLocation texture;
    private final int textureWidth;
    private final int textureHeight;
    @Nullable
    protected TYPE selectedType;

    protected GuiInstallableScrollList(IGuiWrapper gui, int x, int y, int width, int height, ResourceLocation background, int backgroundSideSize,
          ResourceLocation texture, int textureWidth, int textureHeight) {
        super(gui, x, y, width, height, textureHeight / 3, background, backgroundSideSize);
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    protected GuiInstallableScrollList(IGuiWrapper gui, int x, int y, int height, ResourceLocation background, int backgroundSideSize,
          ResourceLocation texture, int textureWidth, int textureHeight) {
        this(gui, x, y, textureWidth + 8, height, background, backgroundSideSize, texture, textureWidth, textureHeight);
    }

    protected abstract List<TYPE> getCurrentInstalled();

    protected abstract void drawName(TYPE type, int y);

    protected abstract ItemStack getRenderStack(TYPE type);

    @Nullable
    public TYPE getSelection() {
        return selectedType;
    }

    @Override
    public boolean hasSelection() {
        return selectedType != null;
    }

    @Override
    protected int getMaxElements() {
        return getCurrentInstalled().size();
    }

    @Override
    protected void setSelected(int index) {
        if (index >= 0) {
            List<TYPE> currentInstalled = getCurrentInstalled();
            if (index < currentInstalled.size()) {
                setSelected(currentInstalled.get(index));
            }
        }
    }

    protected abstract void setSelected(@Nullable TYPE newType);

    @Override
    public void clearSelection() {
        setSelected(null);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        int currentSelection = getCurrentSelection();
        List<TYPE> currentInstalled = getCurrentInstalled();
        int max = Math.max(0, Math.min(getFocusedElements(), currentInstalled.size() - currentSelection));
        for (int i = 0; i < max; i++) {
            drawName(currentInstalled.get(currentSelection + i), 3 + i * elementHeight);
        }
    }

    protected void drawNameText(int y, ITextComponent name, int color, float scale) {
        drawScaledScrollingString(name, 13, y, TextAlignment.LEFT, color, barXShift - 16, 0, false, scale, GuiElement.getMillis());
    }

    @Nullable
    protected EnumColor getColor(TYPE type) {
        return null;
    }

    @Override
    protected void renderElements(int mouseX, int mouseY, float partialTicks) {
        List<TYPE> currentInstalled = getCurrentInstalled();
        int currentSelection = getCurrentSelection();
        int max = Math.max(0, Math.min(getFocusedElements(), currentInstalled.size() - currentSelection));
        minecraft.renderEngine.bindTexture(texture);
        for (int i = 0; i < max; i++) {
            TYPE type = currentInstalled.get(currentSelection + i);
            int multipliedElement = i * elementHeight;
            int shiftedY = getY() + 1 + multipliedElement;
            int textureRow = 1;
            if (type == getSelection()) {
                textureRow = 2;
            } else if (mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 && mouseY >= shiftedY && mouseY < shiftedY + elementHeight) {
                textureRow = 0;
            }
            EnumColor color = getColor(type);
            if (color != null) {
                MekanismRenderer.color(color);
            }
            GuiUtils.blit(relativeX + 1, relativeY + 1 + multipliedElement, 0, elementHeight * textureRow, textureWidth, elementHeight, textureWidth, textureHeight);
            if (color != null) {
                MekanismRenderer.resetColor();
            }
        }
        
        for (int i = 0; i < max; i++) {
            TYPE type = currentInstalled.get(currentSelection + i);
            gui().renderItem(getRenderStack(type), relativeX + 3, relativeY + 3 + i * elementHeight, 0.5F);
        }
    }
}
