package mekanism.common.util;

import gregtech.client.renderer.IRenderSetup;
import gregtech.client.utils.EffectRenderContext;
import gregtech.client.utils.IBloomEffect;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.base.IBloom;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.coremod.MekanismCoreMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

public abstract class BloomEffect<T extends TileEntityBasicBlock> implements IBloomEffect, IRenderSetup, IBloom {

    private static final IRenderSetup SHARED_RENDER_SETUP = new IRenderSetup() {
        @Override
        public void preDraw(@NotNull BufferBuilder bufferBuilder) {
        }

        @Override
        public void postDraw(@NotNull BufferBuilder bufferBuilder) {
        }
    };

    public static IRenderSetup getSharedRenderSetup() {
        return SHARED_RENDER_SETUP;
    }

    public T tile;
    public int north;
    public int south;
    public int west;
    public int east;

    public BloomEffect(T tile, int north, int south, int west, int east) {
        this.tile = tile;
        this.north = north;
        this.south = south;
        this.west = west;
        this.east = east;
        Bloom(tile, getRenderSetup(), this);
    }

    protected IRenderSetup getRenderSetup() {
        return SHARED_RENDER_SETUP;
    }

    @Override
    public void preDraw(@NotNull final BufferBuilder bufferBuilder) {

    }

    @Override
    public void postDraw(@NotNull final BufferBuilder bufferBuilder) {

    }

    @Override
    public void renderBloomEffect(@NotNull final BufferBuilder bufferBuilder, @NotNull final EffectRenderContext effectRenderContext) {
        if (tile != null && !tile.isInvalid()) {
            GlStateManager.pushMatrix();
            BlockPos pos = tile.getPos();
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            double cX = effectRenderContext.cameraX();
            double cY = effectRenderContext.cameraY();
            double cZ = effectRenderContext.cameraZ();
            GlStateManager.translate((x + 0.5F) - cX, (y + 1.5F) - cY, (z + 0.5F) - cZ);
            MekanismRenderer.rotate(tile.facing, north, south, west, east);
            GlStateManager.rotate(180, 0, 0, 1);
            RenderModelBloom();
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.popMatrix();
        }
    }

    protected abstract void RenderModelBloom();

    @Override
    public boolean shouldRenderBloomEffect(@NotNull EffectRenderContext context) {
        if (tile == null || tile.isInvalid() || !MekanismConfig.current().client.enableBloom.val()) {
            return false;
        }
        double maxRenderDistanceSq = tile.getMaxRenderDistanceSquared();
        int customBloomRenderDistance = MekanismConfig.current().client.customBloomRenderDistance.val();
        if (customBloomRenderDistance > 0) {
            maxRenderDistanceSq = Math.min(maxRenderDistanceSq, (double) customBloomRenderDistance * customBloomRenderDistance);
        }
        if (tile.getDistanceSq(context.cameraX(), context.cameraY(), context.cameraZ()) >= maxRenderDistanceSq) {
            return false;
        }
        // Bloom is rendered outside TileEntityRendererDispatcher, so route it through
        // the same fail-open policy and OptiFine shadow-pass guard as the normal TESR.
        return !MekanismCoreMethods.shouldCullTileEntityForOcclusion(tile);
    }


}
