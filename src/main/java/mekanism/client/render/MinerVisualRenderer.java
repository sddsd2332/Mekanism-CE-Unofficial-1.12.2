package mekanism.client.render;

import mekanism.client.render.MekanismRenderer.DisplayInteger;
import mekanism.client.render.MekanismRenderer.GlowInfo;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.init.Blocks;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MinerVisualRenderer {

    private static final double offset = 0.01;
    private static final int MAX_CACHED_VISUALS = 256;
    private static Minecraft mc = Minecraft.getMinecraft();
    private static Map<MinerRenderData, DisplayInteger> cachedVisuals = new LinkedHashMap<MinerRenderData, DisplayInteger>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MinerRenderData, DisplayInteger> eldest) {
            if (size() > MAX_CACHED_VISUALS) {
                eldest.getValue().delete();
                return true;
            }
            return false;
        }
    };

    public static void resetDisplayInts() {
        cachedVisuals.values().forEach(DisplayInteger::delete);
        cachedVisuals.clear();
    }

    public static void render(TileEntityDigitalMiner miner) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) getX(miner.getPos().getX()), (float) getY(miner.getPos().getY()), (float) getZ(miner.getPos().getZ()));
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
        GlowInfo glowInfo = MekanismRenderer.enableGlow();
        GlStateManager.enableCull();
        GlStateManager.color(1, 1, 1, 0.8F);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        getList(new MinerRenderData(miner)).render();
        MekanismRenderer.resetColor();
        GlStateManager.disableCull();
        MekanismRenderer.disableGlow(glowInfo);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.popMatrix();
    }

    private static DisplayInteger getList(MinerRenderData data) {
        if (cachedVisuals.containsKey(data)) {
            return cachedVisuals.get(data);
        }
        DisplayInteger display = DisplayInteger.createAndStart();

        Model3D toReturn = new Model3D();
        if (data.radius <= 64) {
            toReturn.setBlockBounds(-data.radius + 0.01, data.minY - data.yCoord + 0.01, -data.radius + 0.01, data.radius + 1 - 0.01, data.maxY - data.yCoord + 1 - 0.01, data.radius + 1 - 0.01);
            toReturn.baseBlock = Blocks.WATER;
            toReturn.setTexture(MekanismRenderer.whiteIcon);
            MekanismRenderer.renderObject(toReturn);
        }

        DisplayInteger.endList();
        cachedVisuals.put(data, display);

        return display;
    }


    private static double getX(int x) {
        return x - TileEntityRendererDispatcher.staticPlayerX;
    }

    private static double getY(int y) {
        return y - TileEntityRendererDispatcher.staticPlayerY;
    }

    private static double getZ(int z) {
        return z - TileEntityRendererDispatcher.staticPlayerZ;
    }

    public static class MinerRenderData {

        public int minY;
        public int maxY;
        public int radius;
        public int yCoord;

        public MinerRenderData(int min, int max, int rad, int y) {
            minY = min;
            maxY = max;
            radius = rad;
            yCoord = y;
        }

        public MinerRenderData(TileEntityDigitalMiner miner) {
            this(miner.minY, miner.maxY, miner.getRadius(), miner.getPos().getY());
        }

        @Override
        public boolean equals(Object data) {
            return data instanceof MinerRenderData && ((MinerRenderData) data).minY == minY && ((MinerRenderData) data).maxY == maxY &&
                    ((MinerRenderData) data).radius == radius && ((MinerRenderData) data).yCoord == yCoord;
        }

        @Override
        public int hashCode() {
            int code = 1;
            code = 31 * code + minY;
            code = 31 * code + maxY;
            code = 31 * code + radius;
            code = 31 * code + yCoord;
            return code;
        }
    }
}
