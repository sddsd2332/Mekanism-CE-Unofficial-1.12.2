package mekanism.client.render;

import mekanism.api.text.TextComponentGroup;
import mekanism.client.SpecialColors;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.ITextComponent;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public interface IFancyFontRenderer {

    int getXSize();

    FontRenderer getFont();

    /**
     * Time the gui was opened in ms, or zero if the time is unknown.
     */
    default long getTimeOpened() {
        return 0;
    }

    default int titleTextColor() {
        return SpecialColors.TEXT_TITLE.argb();
    }

    default int headingTextColor() {
        return SpecialColors.TEXT_HEADING.argb();
    }

    default int subheadingTextColor() {
        return SpecialColors.TEXT_SUBHEADING.argb();
    }

    default int screenTextColor() {
        return SpecialColors.TEXT_SCREEN.argb();
    }

    default int activeButtonTextColor() {
        return SpecialColors.TEXT_ACTIVE_BUTTON.argb();
    }

    default int inactiveButtonTextColor() {
        return SpecialColors.TEXT_INACTIVE_BUTTON.argb();
    }

    default int drawString(ITextComponent component, int x, int y, int color) {
        return getFont().drawString(component.getFormattedText(), x, y, color);
    }

    default int getStringWidth(ITextComponent component) {
        return getFont().getStringWidth(component.getFormattedText());
    }

    default void drawCenteredText(ITextComponent component, float x, float y, int color) {
        drawCenteredText(component, x, 0, y, color);
    }

    default void drawCenteredText(ITextComponent component, float xStart, float areaWidth, float y, int color) {
        int textWidth = getStringWidth(component);
        float centerX = xStart + (areaWidth / 2F) - (textWidth / 2F);
        drawTextExact(component, centerX, y, color);
    }

    default void drawTitleText(ITextComponent text, float y) {
        drawCenteredTextScaledBound(text, getXSize() - 8, y, titleTextColor());
    }

    default void drawTitleTextWithOffset(ITextComponent text, float x, float y, float end) {
        drawTitleTextWithOffset(text, x, y, end, 4);
    }

    default void drawTitleTextWithOffset(ITextComponent text, float x, float y, float end, float maxLengthPad) {
        drawTitleTextWithOffset(text, x, y, end, maxLengthPad, TextAlignment.CENTER);
    }

    default void drawTitleTextWithOffset(ITextComponent text, float x, float y, float end, float maxLengthPad, TextAlignment alignment) {
        float maxLength = end - x - 2 * maxLengthPad;
        float textWidth = getStringWidth(text);
        float scale = Math.min(1, maxLength / textWidth);
        float scaledWidth = textWidth * scale;
        drawTextWithScale(text, alignment.getTarget(x + maxLengthPad, end - maxLengthPad, scaledWidth), y, titleTextColor(), scale);
    }

    default void drawTitleTextTextWithOffset(ITextComponent text, float x, float y, float end) {
        drawTitleTextWithOffset(text, x, y, end);
    }

    default void drawScaledCenteredTextScaledBound(ITextComponent text, float left, float y, int color, float maxX, float textScale) {
        float width = getStringWidth(text) * textScale;
        float scale = Math.min(1, maxX / width) * textScale;
        drawScaledCenteredText(text, left, y, color, scale);
    }

    default void drawScaledCenteredText(ITextComponent text, float left, float y, int color, float scale) {
        int textWidth = getStringWidth(text);
        float centerX = left - (textWidth / 2F) * scale;
        drawTextWithScale(text, centerX, y, color, scale);
    }

    default void drawCenteredTextScaledBound(ITextComponent text, float maxLength, float y, int color) {
        drawCenteredTextScaledBound(text, maxLength, 0, y, color);
    }

    default void drawCenteredTextScaledBound(ITextComponent text, float maxLength, float x, float y, int color) {
        float scale = Math.min(1, maxLength / getStringWidth(text));
        drawScaledCenteredText(text, x + getXSize() / 2F, y, color, scale);
    }

    default void drawTextExact(ITextComponent text, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        drawString(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    default float getNeededScale(ITextComponent text, float maxLength) {
        int length = getStringWidth(text);
        return length <= maxLength ? 1 : maxLength / length;
    }

    default void drawTextScaledBound(String text, float x, float y, int color, float maxLength) {
        drawTextScaledBound(new TextComponentGroup().translation(text), x, y, color, maxLength);
    }

    default void drawTextScaledBound(ITextComponent component, float x, float y, int color, float maxLength) {
        int length = getStringWidth(component);

        if (length <= maxLength) {
            drawTextExact(component, x, y, color);
        } else {
            drawTextWithScale(component, x, y, color, maxLength / length);
        }
        //Make sure the color does not leak from having drawn the string
        MekanismRenderer.resetColor();
    }

    default void drawScaledTextScaledBound(ITextComponent text, float x, float y, int color, float maxX, float textScale) {
        float width = getStringWidth(text) * textScale;
        float scale = Math.min(1, maxX / width) * textScale;
        drawTextWithScale(text, x, y, color, scale);
    }

    default void drawTextWithScale(ITextComponent text, float x, float y, int color, float scale) {
        drawTextWithScale(text, x, y, color, scale, false);
    }

    default void drawTextWithScale(ITextComponent text, float x, float y, int color, float scale, boolean shadow) {
        prepTextScale(m -> m.drawString(text.getFormattedText(), 0, 0, color, shadow), x, y, scale);
    }

    default void drawScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float maxLengthPad, boolean shadow) {
        drawScrollingString(text, x, y, alignment, color, maxLengthPad, shadow, getTimeOpened());
    }

    default void drawScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float maxLengthPad, boolean shadow, long msVisible) {
        drawScrollingString(text, x, y, alignment, color, getXSize(), maxLengthPad, shadow, msVisible);
    }

    default void drawScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float maxLengthPad, boolean shadow) {
        drawScrollingString(text, x, y, alignment, color, width, maxLengthPad, shadow, getTimeOpened());
    }

    default void drawScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float maxLengthPad, boolean shadow,
          long msVisible) {
        drawScrollingString(text, x, y, alignment, color, width, getFont().FONT_HEIGHT, maxLengthPad, shadow, msVisible);
    }

    default void drawScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float height, float maxLengthPad,
          boolean shadow, long msVisible) {
        drawScrollingString(text, x + maxLengthPad, y, x + width - maxLengthPad, y + height, alignment, color, shadow, msVisible);
    }

    default void drawScrollingString(ITextComponent text, float minX, float minY, float maxX, float maxY, TextAlignment alignment, int color, boolean shadow,
          long msVisible) {
        int textWidth = getStringWidth(text);
        float availableWidth = maxX - minX;
        if (textWidth <= 0 || availableWidth <= 0) {
            return;
        }
        float y = (minY + maxY - getFont().FONT_HEIGHT) / 2F;
        drawTextWithScale(text, alignment.getTarget(minX, maxX, Math.min(textWidth, availableWidth)), y, color, Math.min(1, availableWidth / textWidth), shadow);
    }

    default void drawScaledScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float maxLengthPad,
          boolean shadow, float textScale, long msVisible) {
        drawScaledScrollingString(text, x, y, alignment, color, width, getFont().FONT_HEIGHT, maxLengthPad, shadow, textScale, msVisible);
    }

    default void drawScaledScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float height, float maxLengthPad,
          boolean shadow, float textScale, long msVisible) {
        float minX = x + maxLengthPad;
        float maxX = x + width - maxLengthPad;
        float minY = y;
        float maxY = y + height;
        drawScaledScrollingString(text, minX, minY, maxX, maxY, alignment, color, shadow, textScale, msVisible);
    }

    default void drawScaledScrollingString(ITextComponent text, float minX, float minY, float maxX, float maxY, TextAlignment alignment, int color,
          boolean shadow, float textScale, long msVisible) {
        float availableWidth = maxX - minX;
        int textWidth = getStringWidth(text);
        if (textWidth <= 0 || availableWidth <= 0) {
            return;
        }
        float scale = textScale;
        float scaledWidth = textWidth * scale;
        if (scaledWidth > availableWidth) {
            scale = availableWidth / textWidth;
            scaledWidth = availableWidth;
        }
        float y = (minY + maxY - getFont().FONT_HEIGHT) / 2F;
        drawTextWithScale(text, alignment.getTarget(minX, maxX, scaledWidth), y, color, scale, shadow);
    }

    default void prepTextScale(Consumer<FontRenderer> runnable, float x, float y, float scale) {
        float yAdd = 4 - (scale * 8) / 2F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + yAdd, 0);
        GlStateManager.scale(scale, scale, scale);
        runnable.accept(getFont());
        GlStateManager.popMatrix();
        MekanismRenderer.resetColor();
    }

    /**
     * @apiNote Consider caching the {@link WrappedTextRenderer} instead of using this method.
     */
    default int drawWrappedTextWithScale(ITextComponent text, float x, float y, int color, float maxLength, float scale) {
        return new WrappedTextRenderer(this, text).renderWithScale(x, y, color, maxLength, scale);
    }

    /**
     * @apiNote Consider caching the {@link WrappedTextRenderer} instead of using this method.
     */
    default void drawWrappedCenteredText(ITextComponent text, float x, float y, int color, float maxLength) {
        new WrappedTextRenderer(this, text).renderCentered(x, y, color, maxLength);
    }

    // efficient tool to draw word-by-word wrapped text based on a horizontal bound. looks intimidating but runs in O(n)
    class WrappedTextRenderer {

        private final List<Pair<ITextComponent, Float>> linesToDraw = new ArrayList<>();
        private final IFancyFontRenderer font;
        private final String text;
        @Nullable
        private FontRenderer lastFont;
        private float lastMaxLength = -1;
        private float lineLength = 0;

        public WrappedTextRenderer(IFancyFontRenderer font, ITextComponent text) {
            this(font, text.getFormattedText());
        }

        public WrappedTextRenderer(IFancyFontRenderer font, String text) {
            this.font = font;
            this.text = text;
        }

        public void renderCentered(float x, float y, int color, float maxLength) {
            calculateLines(maxLength);
            float startY = y;
            for (Pair<ITextComponent, Float> p : linesToDraw) {
                font.drawTextExact(p.getLeft(), x - p.getRight() / 2, startY, color);
                startY += 9;
            }
        }

        public int renderWithScale(float x, float y, int color, float maxLength, float scale) {
            //Divide by scale for calculating actual max length so that when the text is scaled it has the proper total space available
            calculateLines(maxLength / scale);
            font.prepTextScale(m -> {
                int startY = 0;
                for (Pair<ITextComponent, Float> p : linesToDraw) {
                    font.drawString(p.getLeft(), 0, startY, color);
                    startY += 9;
                }
            }, x, y, scale);
            return linesToDraw.size();
        }

        void calculateLines(float maxLength) {
            //If something changed since the last time we calculated it
            FontRenderer font = this.font.getFont();
            if (font != null && (lastFont != font || lastMaxLength != maxLength)) {
                lastFont = font;
                lastMaxLength = maxLength;
                linesToDraw.clear();
                lineLength = 0;
                StringBuilder lineBuilder = new StringBuilder();
                StringBuilder wordBuilder = new StringBuilder();
                int spaceLength = lastFont.getStringWidth(" ");
                int wordLength = 0;
                for (char c : text.toCharArray()) {
                    if (c == ' ') {
                        lineBuilder = addWord(lineBuilder, wordBuilder, maxLength, spaceLength, wordLength);
                        wordBuilder = new StringBuilder();
                        wordLength = 0;
                        continue;
                    }
                    wordBuilder.append(c);
                    wordLength += lastFont.getStringWidth(Character.toString(c));
                }
                if (wordBuilder.length() > 0) {
                    lineBuilder = addWord(lineBuilder, wordBuilder, maxLength, spaceLength, wordLength);
                }
                if (lineBuilder.length() > 0) {
                    linesToDraw.add(Pair.of(new TextComponentGroup().getString(lineBuilder.toString()), lineLength));
                }
            }
        }

        StringBuilder addWord(StringBuilder lineBuilder, StringBuilder wordBuilder, float maxLength, int spaceLength, int wordLength) {
            // ignore spacing if this is the first word of the line
            float spacingLength = lineBuilder.length() == 0 ? 0 : spaceLength;
            if (lineBuilder.length() > 0 && lineLength + spacingLength + wordLength > maxLength) {
                linesToDraw.add(Pair.of(new TextComponentGroup().getString(lineBuilder.toString()), lineLength));
                lineBuilder = new StringBuilder(wordBuilder);
                lineLength = wordLength;
            } else {
                if (spacingLength > 0) {
                    lineBuilder.append(" ");
                }
                lineBuilder.append(wordBuilder);
                lineLength += spacingLength + wordLength;
            }
            return lineBuilder;
        }

        public static int calculateHeightRequired(FontRenderer font, ITextComponent text, int width, float maxLength) {
            return calculateHeightRequired(font, text.getFormattedText(), width, maxLength);
        }

        public static int calculateHeightRequired(FontRenderer font, String text, int width, float maxLength) {
            //TODO: Come up with a better way of doing this
            WrappedTextRenderer wrappedTextRenderer = new WrappedTextRenderer(new IFancyFontRenderer() {
                @Override
                public int getXSize() {
                    return width;
                }

                @Override
                public FontRenderer getFont() {
                    return font;
                }
            }, text);
            wrappedTextRenderer.calculateLines(maxLength);
            return 9 * wrappedTextRenderer.linesToDraw.size();
        }
    }

    enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT;

        public float getTarget(float minX, float maxX, float textWidth) {
            switch (this) {
                case LEFT:
                    return minX;
                case RIGHT:
                    return maxX - textWidth;
                case CENTER:
                default:
                    return minX + ((maxX - minX) - textWidth) / 2F;
            }
        }
    }
}
