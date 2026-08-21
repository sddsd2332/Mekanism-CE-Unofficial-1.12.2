package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.element.GuiElement;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.awt.Color;

/** Supergiant-style animated selection frame for QIO resource and recipe grids. */
/**
 * QIO 处理模块中的 QIOGuiSelectionRenderer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOGuiSelectionRenderer {

    static final long CYCLE_MILLIS = 2_400L;
    static final int BORDER_ALPHA = 220;
    private static final float SATURATION = 0.95F;
    private static final float BRIGHTNESS = 1F;

    private QIOGuiSelectionRenderer() {
    }

    static void draw(int x, int y, int width, int height) {
        int pixels = borderPixels(width, height);
        if (pixels == 0) return;
        float phase = phase(GuiElement.getMillis());
        RenderState previous = RenderState.capture();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,
                  GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.translate(0F, 0F, 180F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int index = 0; index < pixels; index++) {
                int color = Color.HSBtoRGB(hue(index, pixels, phase), SATURATION,
                      BRIGHTNESS);
                addBorderPixel(buffer, x, y, width, height, index,
                      color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF);
            }
            tessellator.draw();
        } finally {
            GlStateManager.popMatrix();
            previous.restore();
        }
    }

    static int borderPixels(int width, int height) {
        return width < 2 || height < 2 ? 0 : 2 * (width + height);
    }

    static float phase(long elapsedMillis) {
        return Math.floorMod(elapsedMillis, CYCLE_MILLIS) / (float) CYCLE_MILLIS;
    }

    static float hue(int index, int pixels, float phase) {
        if (pixels <= 0) return 0F;
        float hue = index / (float) pixels + phase;
        return hue - (float) Math.floor(hue);
    }

    static int pixelX(int width, int height, int index) {
        if (index < width) return index;
        index -= width;
        if (index < height) return width - 1;
        index -= height;
        if (index < width) return width - 1 - index;
        return 0;
    }

    static int pixelY(int width, int height, int index) {
        if (index < width) return 0;
        index -= width;
        if (index < height) return index;
        index -= height;
        if (index < width) return height - 1;
        index -= width;
        return height - 1 - index;
    }

    private static void addBorderPixel(BufferBuilder buffer, int x, int y, int width,
          int height, int index, int red, int green, int blue) {
        int left = x + pixelX(width, height, index);
        int top = y + pixelY(width, height, index);
        buffer.pos(left, top + 1, 0).color(red, green, blue, BORDER_ALPHA).endVertex();
        buffer.pos(left + 1, top + 1, 0).color(red, green, blue, BORDER_ALPHA).endVertex();
        buffer.pos(left + 1, top, 0).color(red, green, blue, BORDER_ALPHA).endVertex();
        buffer.pos(left, top, 0).color(red, green, blue, BORDER_ALPHA).endVertex();
    }

    private static void setTexture(boolean enabled) {
        if (enabled) GlStateManager.enableTexture2D();
        else GlStateManager.disableTexture2D();
    }

    private static void setLighting(boolean enabled) {
        if (enabled) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
    }

    private static void setDepth(boolean enabled) {
        if (enabled) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
    }

    private static void setBlend(boolean enabled) {
        if (enabled) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
    }

    private static final class RenderState {

        private final boolean texture;
        private final boolean lighting;
        private final boolean depth;
        private final boolean blend;
        private final int blendSourceRgb;
        private final int blendDestinationRgb;
        private final int blendSourceAlpha;
        private final int blendDestinationAlpha;

        private RenderState(boolean texture, boolean lighting, boolean depth, boolean blend,
              int blendSourceRgb, int blendDestinationRgb, int blendSourceAlpha,
              int blendDestinationAlpha) {
            this.texture = texture;
            this.lighting = lighting;
            this.depth = depth;
            this.blend = blend;
            this.blendSourceRgb = blendSourceRgb;
            this.blendDestinationRgb = blendDestinationRgb;
            this.blendSourceAlpha = blendSourceAlpha;
            this.blendDestinationAlpha = blendDestinationAlpha;
        }

        private static RenderState capture() {
            return new RenderState(GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                  GL11.glIsEnabled(GL11.GL_LIGHTING), GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                  GL11.glIsEnabled(GL11.GL_BLEND), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                  GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                  GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                  GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        }

        private void restore() {
            GlStateManager.tryBlendFuncSeparate(blendSourceRgb, blendDestinationRgb,
                  blendSourceAlpha, blendDestinationAlpha);
            setTexture(texture);
            setLighting(lighting);
            setDepth(depth);
            setBlend(blend);
        }
    }
}
