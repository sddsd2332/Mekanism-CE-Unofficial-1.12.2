package mekanism.common.util;

import mekanism.client.gui.GuiUtils;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public final class ItemOverlayUtils {

    public static final int DEFAULT_BAR_X_OFFSET = 2;
    public static final int DEFAULT_BAR_Y_OFFSET = 12;
    public static final int DEFAULT_BAR_WIDTH = 13;
    public static final int DEFAULT_BAR_HEIGHT = 1;

    private ItemOverlayUtils() {
    }

    @SideOnly(Side.CLIENT)
    public static boolean renderItemBarOverlayIntoGUI(int xPosition, int yPosition, List<BarSegment> segments) {
        return renderBarOverlay(xPosition + DEFAULT_BAR_X_OFFSET, yPosition + DEFAULT_BAR_Y_OFFSET,
              DEFAULT_BAR_WIDTH, DEFAULT_BAR_HEIGHT, segments);
    }

    @SideOnly(Side.CLIENT)
    public static boolean renderBarOverlay(int x, int y, int width, int height, List<BarSegment> segments) {
        if (width <= 0 || height <= 0 || segments.isEmpty()) {
            return false;
        }
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        GuiUtils.draw(bufferbuilder, x, y, width, height, 0, 0, 0, 255);
        for (int i = 0; i < segments.size(); i++) {
            BarSegment segment = segments.get(i);
            int segmentStart = width * i / segments.size();
            int segmentEnd = width * (i + 1) / segments.size();
            int segmentWidth = segmentEnd - segmentStart;
            int fillWidth = getFillWidth(segment.fillRatio, segmentWidth);
            if (fillWidth > 0) {
                int color = segment.color;
                GuiUtils.draw(bufferbuilder, x + segmentStart, y, fillWidth, height,
                      color >> 16 & 255, color >> 8 & 255, color & 255, 255);
            }
        }
        MekanismRenderer.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        return true;
    }

    private static int getFillWidth(float fillRatio, int segmentWidth) {
        if (fillRatio <= 0 || segmentWidth <= 0) {
            return 0;
        }
        int width = Math.round(segmentWidth * Math.min(1F, fillRatio));
        return MathHelper.clamp(width, 1, segmentWidth);
    }

    public static class BarSegment {

        private final float fillRatio;
        private final int color;

        public static BarSegment fromAmount(int stored, int capacity, int color) {
            float fillRatio = stored <= 0 || capacity <= 0 ? 0 : (float) stored / capacity;
            return new BarSegment(fillRatio, color);
        }

        public BarSegment(float fillRatio, int color) {
            this.fillRatio = fillRatio;
            this.color = color;
        }
    }
}
