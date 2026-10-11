package mekanism.client.render.transmitter;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.ColourRGBA;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.model.Attributes;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.client.model.obj.OBJModel;
import net.minecraftforge.client.model.obj.OBJModel.OBJState;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static mekanism.common.tile.transmitter.TileEntitySidedPipe.ConnectionType.NONE;

public abstract class RenderTransmitterBase<T extends TileEntityTransmitter> extends TileEntitySpecialRenderer<T> {

    /* Credit to Eternal Energy */
    public static Function<ResourceLocation, TextureAtlasSprite> textureGetterFlipV = location -> DummyAtlasTextureFlipV.instance;
    private static OBJModel contentsModel;
    private static Map<String, IBakedModel> contentsMap = new Object2ObjectOpenHashMap<>();
    protected Minecraft mc = Minecraft.getMinecraft();

    public RenderTransmitterBase() {
        if (contentsModel == null) {
            try {
                contentsModel = (OBJModel) OBJLoader.INSTANCE.loadModel(MekanismUtils.getResource(ResourceType.MODEL, "transmitter_contents.obj"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            contentsMap = buildModelMap(contentsModel);
        }
    }

    public static Map<String, IBakedModel> buildModelMap(OBJModel objModel) {
        Map<String, IBakedModel> modelParts = new Object2ObjectOpenHashMap<>();
        Set<String> keys = objModel.getMatLib().getGroups().keySet();
        if (!keys.isEmpty()) {
            keys.forEach(key -> {
                if (!modelParts.containsKey(key)) {
                    modelParts.put(key, objModel.bake(new OBJState(Collections.singletonList(key), false), Attributes.DEFAULT_BAKED_FORMAT, textureGetterFlipV));
                }
            });
        }
        return modelParts;
    }

    public void renderTransparency(BufferBuilder renderer, TextureAtlasSprite icon, IBakedModel cc, ColourRGBA color) {
        if (!renderer.isDrawing) {
            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            cc.getQuads(null, side, 0).forEach(quad -> {
                quad = MekanismRenderer.iconTransform(quad, icon);
                LightUtil.renderQuadColor(renderer, quad, color.argb());
            });
        }
        cc.getQuads(null, null, 0).forEach(quad -> {
            quad = MekanismRenderer.iconTransform(quad, icon);
            LightUtil.renderQuadColor(renderer, quad, color.argb());
        });
    }

    public IBakedModel getModelForSide(TileEntityTransmitter part, EnumFacing side) {
        String sideName = side.name().toLowerCase(Locale.ROOT);
        String typeName = part.getConnectionType(side).name().toUpperCase();
        String name = sideName + typeName;
        return contentsMap.get(name);
    }

    protected boolean shouldRenderTransmitterInterior(T transmitter, float partialTick) {
        if (transmitter == null || transmitter.getWorld() == null || mc == null || mc.gameSettings == null) {
            return true;
        }
        // Third-person camera position differs from player eye position; skip occlusion culling to avoid false negatives.
        if (mc.gameSettings.thirdPersonView != 0) {
            return true;
        }
        Entity renderView = mc.getRenderViewEntity();
        if (renderView == null) {
            return true;
        }

        Vec3d eyePos = renderView.getPositionEyes(partialTick);
        Vec3d lookVec = renderView.getLook(partialTick);
        BlockPos pos = transmitter.getPos();
        Vec3d center = new Vec3d(pos).add(0.5D, 0.5D, 0.5D);

        Vec3d toCenter = center.subtract(eyePos);
        double lengthSq = toCenter.lengthSquared();
        int maxDistance = MekanismConfig.current().client.transmitterInteriorRenderDistance.val();
        if (maxDistance > 0 && lengthSq > (double) maxDistance * maxDistance) {
            return false;
        }
        if (lengthSq > 1.0E-8D) {
            double invLen = 1.0D / Math.sqrt(lengthSq);
            double facingDot = lookVec.dotProduct(toCenter.scale(invLen));
            if (facingDot < getFacingDotThreshold()) {
                return false;
            }
        }

        if (canSeePoint(transmitter, eyePos, center, pos)) {
            return true;
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            if (transmitter.getConnectionType(side) == NONE) {
                continue;
            }
            Vec3d sidePoint = center.add(side.getXOffset() * 0.35D, side.getYOffset() * 0.35D, side.getZOffset() * 0.35D);
            if (canSeePoint(transmitter, eyePos, sidePoint, pos)) {
                return true;
            }
        }
        return false;
    }

    private boolean canSeePoint(T transmitter, Vec3d eyePos, Vec3d target, BlockPos selfPos) {
        World world = transmitter.getWorld();
        Vec3d start = eyePos;
        Vec3d direction = target.subtract(eyePos);
        double distanceSq = direction.lengthSquared();
        if (distanceSq < 1.0E-8D) {
            return true;
        }
        Vec3d directionNorm = direction.scale(1.0D / Math.sqrt(distanceSq));

        // Skip transparent blockers (e.g. glass) by advancing the ray until we hit a solid block or the target.
        for (int i = 0; i < 16; i++) {
            RayTraceResult trace = world.rayTraceBlocks(start, target, false, true, false);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) {
                return true;
            }
            BlockPos hitPos = trace.getBlockPos();
            if (selfPos.equals(hitPos)) {
                return true;
            }
            IBlockState hitState = world.getBlockState(hitPos);
            if (!isTransparentOccluder(hitState, world, hitPos)) {
                return false;
            }
            if (trace.hitVec == null) {
                return true;
            }
            start = trace.hitVec.add(directionNorm.scale(0.01D));
            if (start.squareDistanceTo(target) < 1.0E-6D) {
                return true;
            }
        }
        return true;
    }

    private boolean isTransparentOccluder(IBlockState state, World world, BlockPos pos) {
        if (state == null) {
            return true;
        }
        if (state.getMaterial().isLiquid()) {
            return true;
        }
        if (!state.isFullCube()) {
            return true;
        }
        if (!state.isOpaqueCube()) {
            return true;
        }
        return state.getBlock().isTranslucent(state);
    }

    private double getFacingDotThreshold() {
        double fov = mc.gameSettings.fovSetting;
        if (Double.isNaN(fov) || fov <= 0.0D) {
            fov = 70.0D;
        }
        // Add a small angular margin so side-viewed pipes are still considered visible.
        double halfFovRadians = Math.toRadians(Math.min(179.0D, Math.max(30.0D, fov + 10.0D)) * 0.5D);
        return Math.cos(halfFovRadians);
    }

    private static class DummyAtlasTextureFlipV extends TextureAtlasSprite {

        public static DummyAtlasTextureFlipV instance = new DummyAtlasTextureFlipV();

        protected DummyAtlasTextureFlipV() {
            super("dummyFlipV");
        }

        @Override
        public float getInterpolatedU(double u) {
            return (float) u / 16;
        }

        @Override
        public float getInterpolatedV(double v) {
            return (float) v / -16;
        }
    }
}
