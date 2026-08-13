package mekanism.client.gui;

import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;


public interface IGuiWrapper {

    double DEFAULT_MARQUEE_PIXELS_PER_SECOND = 12D;
    double DEFAULT_MARQUEE_EDGE_PAUSE_SECONDS = 0.5D;

    default void displayTooltip(String component, int x, int y, int maxWidth) {
        this.displayTooltips(Collections.singletonList(component), x, y, maxWidth);
    }

    default void displayTooltip(String component, int x, int y) {
        this.displayTooltips(Collections.singletonList(component), x, y);
    }

    default void displayTooltips(List<String> components, int xAxis, int yAxis) {
        displayTooltips(components, xAxis, yAxis, -1);
    }

    default void displayTooltips(List<String> components, int xAxis, int yAxis, int maxWidth) {
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        if (this instanceof GuiContainer container) {
            screenWidth = container.width;
            screenHeight = container.height;
        }
        net.minecraftforge.fml.client.config.GuiUtils.drawHoveringText(components, xAxis, yAxis, screenWidth, screenHeight, maxWidth, getFont());
    }

    default int getLeft() {
        if (this instanceof GuiContainer container) {
            return container.getGuiLeft();
        }
        return 0;
    }

    default int getTop() {
        if (this instanceof GuiContainer container) {
            return container.getGuiTop();
        }
        return 0;
    }

    default int getWidth() {
        if (this instanceof GuiContainer container) {
            return container.getXSize();
        }
        return 0;
    }

    default int getHeight() {
        if (this instanceof GuiContainer container) {
            return container.getYSize();
        }
        return 0;
    }

    default long getTimeOpened() {
        return GuiElement.getMillis();
    }

    @Nonnull
    default ItemStack getCarriedItem() {
        return ItemStack.EMPTY;
    }

    @Nullable
    default Slot getSlotUnderMouse(int mouseX, int mouseY) {
        return null;
    }

    default void addWindow(GuiWindow window) {
        Mekanism.logger.error("Tried to call 'addWindow' but unsupported in {}", getClass().getName());
    }

    default void removeWindow(GuiWindow window) {
        Mekanism.logger.error("Tried to call 'removeWindow' but unsupported in {}", getClass().getName());
    }

    default boolean currentlyQuickCrafting() {
        return false;
    }

    @Nullable
    default GuiWindow getWindowHovering(double mouseX, double mouseY) {
        Mekanism.logger.error("Tried to call 'getWindowHovering' but unsupported in {}", getClass().getName());
        return null;
    }

    @Nonnull
    default BooleanSupplier trackWarning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        Mekanism.logger.error("Tried to call 'trackWarning' but unsupported in {}", getClass().getName());
        return warningSupplier;
    }

    @Nonnull
    default BooleanSupplier trackWarning(@Nonnull mekanism.common.inventory.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        Mekanism.logger.error("Tried to call 'trackWarning' but unsupported in {}", getClass().getName());
        return warningSupplier;
    }

    @Nullable
    FontRenderer getFont();

    /** Draws text statically when it fits, or as a clipped, edge-pausing marquee otherwise. */
    default void drawMarqueeString(@Nonnull ITextComponent text, float minX, float minY,
          float maxX, float maxY, int color, boolean shadow, float textScale,
          long elapsedMillis) {
        FontRenderer font = getFont();
        float availableWidth = maxX - minX;
        if (font == null || availableWidth <= 0 || maxY <= minY || textScale <= 0) return;
        float contentWidth = font.getStringWidth(text.getFormattedText()) * textScale;
        float drawY = (minY + maxY - font.FONT_HEIGHT) / 2F;
        if (contentWidth <= availableWidth) {
            drawTextWithScale(font, text, minX, drawY, color, shadow, textScale);
            return;
        }

        float offset = marqueeOffset(contentWidth, availableWidth, elapsedMillis);
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean restoreScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer previousScissor = null;
        if (restoreScissor) {
            previousScissor = BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previousScissor);
        }
        double scaleX = minecraft.displayWidth / (double) minecraft.currentScreen.width;
        double scaleY = minecraft.displayHeight / (double) minecraft.currentScreen.height;
        int scissorX = (int) Math.floor((getLeft() + minX) * scaleX);
        int scissorY = (int) Math.floor(minecraft.displayHeight -
              (getTop() + maxY) * scaleY);
        int scissorWidth = Math.max(0, (int) Math.ceil((maxX - minX) * scaleX));
        int scissorHeight = Math.max(0, (int) Math.ceil((maxY - minY) * scaleY));
        if (previousScissor != null) {
            int left = Math.max(scissorX, previousScissor.get(0));
            int bottom = Math.max(scissorY, previousScissor.get(1));
            int right = Math.min(scissorX + scissorWidth,
                  previousScissor.get(0) + previousScissor.get(2));
            int top = Math.min(scissorY + scissorHeight,
                  previousScissor.get(1) + previousScissor.get(3));
            scissorX = left;
            scissorY = bottom;
            scissorWidth = Math.max(0, right - left);
            scissorHeight = Math.max(0, top - bottom);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        try {
            drawTextWithScale(font, text, minX - offset, drawY, color, shadow, textScale);
        } finally {
            if (previousScissor == null) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glScissor(previousScissor.get(0), previousScissor.get(1),
                      previousScissor.get(2), previousScissor.get(3));
            }
        }
    }

    /** Keeps a label fixed and scrolls only its value when the complete line does not fit. */
    default void drawPrefixedMarqueeString(@Nullable ITextComponent prefix,
          @Nonnull ITextComponent value, float minX, float minY, float maxX, float maxY,
          int color, boolean shadow, float textScale, float gap, long elapsedMillis) {
        FontRenderer font = getFont();
        if (font == null) return;
        String prefixText = prefix == null ? "" : prefix.getFormattedText();
        String valueText = value.getFormattedText();
        float prefixWidth = font.getStringWidth(prefixText) * textScale;
        float valueWidth = font.getStringWidth(valueText) * textScale;
        float actualGap = prefixText.isEmpty() || valueText.isEmpty() ? 0 : Math.max(0, gap);
        float availableWidth = maxX - minX;
        float drawY = (minY + maxY - font.FONT_HEIGHT) / 2F;
        if (prefixWidth + actualGap + valueWidth <= availableWidth) {
            if (prefix != null && !prefixText.isEmpty()) {
                drawTextWithScale(font, prefix, minX, drawY, color, shadow, textScale);
            }
            if (!valueText.isEmpty()) {
                drawTextWithScale(font, value, minX + prefixWidth + actualGap, drawY,
                      color, shadow, textScale);
            }
            return;
        }

        float valueStart = minX + prefixWidth + actualGap;
        if (maxX - valueStart <= 1) {
            drawMarqueeString(new TextComponentString(prefixText +
                        (actualGap == 0 ? "" : "  ") + valueText),
                  minX, minY, maxX, maxY, color, shadow, textScale, elapsedMillis);
            return;
        }
        if (prefix != null && !prefixText.isEmpty()) {
            drawTextWithScale(font, prefix, minX, drawY, color, shadow, textScale);
        }
        drawMarqueeString(value, valueStart, minY, maxX, maxY, color, shadow,
              textScale, elapsedMillis);
    }

    static float marqueeOffset(double contentWidth, double areaWidth, long elapsedMillis) {
        double overflow = contentWidth - areaWidth;
        if (overflow <= 0) return 0;
        double seconds = Math.max(0, elapsedMillis) / 1_000D;
        double travel = overflow / DEFAULT_MARQUEE_PIXELS_PER_SECOND;
        double cycle = DEFAULT_MARQUEE_EDGE_PAUSE_SECONDS * 2 + travel * 2;
        double position = seconds % cycle;
        if (position < DEFAULT_MARQUEE_EDGE_PAUSE_SECONDS) return 0;
        position -= DEFAULT_MARQUEE_EDGE_PAUSE_SECONDS;
        if (position < travel) {
            return (float) (position * DEFAULT_MARQUEE_PIXELS_PER_SECOND);
        }
        if (position < travel + DEFAULT_MARQUEE_EDGE_PAUSE_SECONDS) {
            return (float) overflow;
        }
        position -= travel + DEFAULT_MARQUEE_EDGE_PAUSE_SECONDS;
        return (float) (overflow - position * DEFAULT_MARQUEE_PIXELS_PER_SECOND);
    }

    static void drawTextWithScale(FontRenderer font, ITextComponent text, float x, float y,
          int color, boolean shadow, float scale) {
        float yAdd = 4 - (scale * 8) / 2F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + yAdd, 0);
        GlStateManager.scale(scale, scale, scale);
        font.drawString(text.getFormattedText(), 0, 0, color, shadow);
        GlStateManager.popMatrix();
        MekanismRenderer.resetColor();
    }

    default void renderItem(@Nonnull ItemStack stack, int xAxis, int yAxis) {
        renderItem(stack, xAxis, yAxis, 1);
    }

    default void renderItem(@Nonnull ItemStack stack, int xAxis, int yAxis, float scale) {
        GuiUtils.renderGuiElementItem(getItemRenderer(), stack, xAxis, yAxis, scale, getFont(), null, false);
    }

    RenderItem getItemRenderer();

    default void renderItemTooltip(@Nonnull ItemStack stack, int xAxis, int yAxis) {
        Mekanism.logger.error("Tried to call 'renderItemTooltip' but unsupported in {}", getClass().getName());
    }

    default void renderItemTooltipWithExtra(@Nonnull ItemStack stack, int xAxis, int yAxis, List<String> toAppend) {
        if (toAppend.isEmpty()) {
            renderItemTooltip(stack, xAxis, yAxis);
        } else {
            Mekanism.logger.error("Tried to call 'renderItemTooltipWithExtra' but unsupported in {}", getClass().getName());
        }
    }

    default void renderItemTooltipWithExtra(@Nonnull ItemStack stack, int xAxis, int yAxis, List<String> toInsert, int insertionIndex) {
        renderItemTooltipWithExtra(stack, xAxis, yAxis, toInsert);
    }

    default void renderItemWithOverlay(@Nonnull ItemStack stack, int xAxis, int yAxis, float scale, @Nullable String text) {
        GuiUtils.renderGuiElementItem(getItemRenderer(), stack, xAxis, yAxis, scale, getFont(), text, true);
    }

    default void setSelectedWindow(SelectedWindowData selectedWindow) {
        Mekanism.logger.error("Tried to call 'setSelectedWindow' but unsupported in {}", getClass().getName());
    }


    default void addFocusListener(GuiElement element) {
        Mekanism.logger.error("Tried to call 'addFocusListener' but unsupported in {}", getClass().getName());
    }

    default void removeFocusListener(GuiElement element) {
        Mekanism.logger.error("Tried to call 'removeFocusListener' but unsupported in {}", getClass().getName());
    }

    default void focusChange(GuiElement changed) {
        Mekanism.logger.error("Tried to call 'focusChange' but unsupported in {}", getClass().getName());
    }

    default void incrementFocus(GuiElement current) {
        Mekanism.logger.error("Tried to call 'incrementFocus' but unsupported in {}", getClass().getName());
    }
}
