package mekanism.client.render.tileentity;

import mekanism.api.Coord4D;
import mekanism.client.render.lib.effect.BillboardingEffectRenderer;
import mekanism.client.render.lib.effect.BoltRenderer;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.content.sps.SynchronizedSPSData;
import mekanism.common.lib.Color;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.lib.effect.BoltEffect.BoltRenderInfo;
import mekanism.common.lib.effect.BoltEffect.SpawnFunction;
import mekanism.common.lib.effect.CustomEffect;
import mekanism.common.particle.SPSOrbitEffect;
import mekanism.common.tile.TileEntitySuperchargedCoil;
import mekanism.common.tile.multiblock.TileEntitySPSCasing;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class RenderSPS extends TileEntitySpecialRenderer<TileEntitySPSCasing> {

    private static final CustomEffect CORE = new CustomEffect(MekanismUtils.getResource(ResourceType.RENDER, "energy_effect.png"));
    private static final Map<Object, BoltRenderer> BOLT_RENDERERS = new HashMap<>();
    private static final float MIN_SCALE = 0.1F;
    private static final float MAX_SCALE = 4F;
    private static final Random RAND = new Random();

    static {
        CORE.setColor(Color.rgbai(255, 255, 255, 240));
    }

    private final Minecraft minecraft = Minecraft.getMinecraft();

    public static boolean hasBoltsToRender(TileEntitySPSCasing tile) {
        if (tile == null || tile.structure == null || !tile.clientHasStructure || !tile.isRendering || tile.structure.renderLocation == null) {
            return false;
        }
        BoltRenderer bolts = BOLT_RENDERERS.get(getRendererKey(tile, tile.structure));
        return bolts != null && bolts.hasBoltsToRender();
    }

    public static void renderBloomBolts(TileEntitySPSCasing tile, float partialTick) {
        if (!hasBoltsToRender(tile)) {
            return;
        }
        BoltRenderer bolts = BOLT_RENDERERS.get(getRendererKey(tile, tile.structure));
        if (bolts != null) {
            bolts.render(partialTick);
        }
    }

    @Override
    public void render(TileEntitySPSCasing tile, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        if (tile == null || tile.structure == null || !tile.clientHasStructure || !tile.isRendering || tile.structure.renderLocation == null) {
            return;
        }
        SynchronizedSPSData multiblock = tile.structure;
        RenderBounds bounds = resolveRenderBounds(multiblock);
        if (bounds == null) {
            return;
        }

        Vec3d center = bounds.getCenter();
        Vec3d renderCenter = center.subtract(tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ());
        Object rendererKey = getRendererKey(tile, multiblock);
        BoltRenderer bolts = BOLT_RENDERERS.computeIfAbsent(rendererKey, mb -> new BoltRenderer());
        double energyScale = getEnergyScale(multiblock.lastProcessed);
        int targetEffectCount = 0;

        if (!minecraft.isGamePaused() && multiblock.lastReceivedEnergy > 0) {
            for (ArcStart start : getCoilArcStarts(tile, multiblock, bounds)) {
                if (start.level <= 0) {
                    continue;
                }
                int count = 1 + Math.max(0, (start.level - 1) / 2);
                float size = 0.01F * start.level;
                BoltEffect bolt = new BoltEffect(BoltRenderInfo.ELECTRICITY, start.localStart, renderCenter, 15)
                        .count(count)
                        .size(size)
                        .lifespan(8)
                        .spawn(SpawnFunction.delay(4));
                bolts.update(start.ownerKey, bolt, partialTick);
            }

            if (RAND.nextDouble() < getBoundedScale((float) energyScale, 0.01F, 0.4F)) {
                EnumFacing side = EnumFacing.VALUES[RAND.nextInt(EnumFacing.VALUES.length)];
                Vec3d end = getRandomInnerWallPoint(bounds, side);
                Vec3d localEnd = end.subtract(tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ());
                BoltEffect bolt = new BoltEffect(BoltRenderInfo.ELECTRICITY, renderCenter, localEnd, 15)
                        .size(0.01F * getBoundedScale((float) energyScale, 0.5F, 5F))
                        .lifespan(8)
                        .spawn(SpawnFunction.NO_DELAY);
                bolts.update(Objects.hash(side, end), bolt, partialTick);
            }
            targetEffectCount = (int) getBoundedScale((float) energyScale, 10, 120);
        }

        while (tile.orbitEffects.size() > targetEffectCount) {
            tile.orbitEffects.poll();
        }
        if (tile.orbitEffects.size() < targetEffectCount && RAND.nextDouble() < 0.5D) {
            tile.orbitEffects.add(new SPSOrbitEffect(multiblock, center));
        }

        if (!minecraft.isGamePaused()) {
            tile.orbitEffects.removeIf(effect -> {
                effect.updateMultiblock(multiblock);
                return effect.tick();
            });
        } else {
            tile.orbitEffects.forEach(effect -> effect.updateMultiblock(multiblock));
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        bolts.render(partialTick);
        GlStateManager.popMatrix();

        long worldTime = tile.getWorld() == null ? 0 : tile.getWorld().getTotalWorldTime();
        if (multiblock.lastProcessed > 0) {
            CORE.setPos(center);
            CORE.setScale(getBoundedScale((float) energyScale, MIN_SCALE, MAX_SCALE));
            BillboardingEffectRenderer.render(CORE, tile.getPos(), x, y, z, worldTime, partialTick);
        }
        tile.orbitEffects.forEach(effect -> BillboardingEffectRenderer.render(effect, tile.getPos(), x, y, z, worldTime, partialTick));

        if (tile.orbitEffects.isEmpty() && !bolts.hasBoltsToRender() && multiblock.lastProcessed <= 0) {
            BOLT_RENDERERS.remove(rendererKey);
        }
    }

    @Override
    public boolean isGlobalRenderer(TileEntitySPSCasing tile) {
        return tile != null && tile.clientHasStructure && tile.isRendering && tile.structure != null && tile.structure.renderLocation != null;
    }

    private static double getEnergyScale(double lastProcessed) {
        return Math.min(1D, Math.max(0D, (Math.log10(lastProcessed) + 2D) / 4D));
    }

    private static float getBoundedScale(float scale, float min, float max) {
        return min + scale * (max - min);
    }

    private static RenderBounds resolveRenderBounds(SynchronizedSPSData structure) {
        if (structure == null || structure.renderLocation == null || structure.volLength <= 0 || structure.volWidth <= 0 || structure.volHeight <= 0) {
            return null;
        }
        int minX = structure.renderLocation.x;
        int minY = structure.renderLocation.y - 1;
        int minZ = structure.renderLocation.z;
        int maxX = minX + structure.volLength - 1;
        int maxY = minY + structure.volHeight - 1;
        int maxZ = minZ + structure.volWidth - 1;
        return new RenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<ArcStart> getCoilArcStarts(TileEntitySPSCasing tile, SynchronizedSPSData multiblock, RenderBounds bounds) {
        List<ArcStart> starts = new ArrayList<>();
        if (!multiblock.portToCoilMap.isEmpty() && multiblock.minLocation != null && multiblock.maxLocation != null) {
            for (Map.Entry<Coord4D, Coord4D> entry : multiblock.portToCoilMap.entrySet()) {
                Coord4D portPos = entry.getKey();
                Coord4D coilPos = entry.getValue();
                EnumFacing inner = multiblock.getPortInnerSide(portPos);
                if (inner == null) {
                    continue;
                }
                Vec3d start = new Vec3d(
                        coilPos.x + 0.5D + inner.getXOffset() * 0.5D,
                        coilPos.y + 0.5D + inner.getYOffset() * 0.5D,
                        coilPos.z + 0.5D + inner.getZOffset() * 0.5D);
                int level = multiblock.getRenderCoilLevel(coilPos);
                starts.add(new ArcStart(start.subtract(tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ()), coilPos.hashCode(), level));
            }
            return starts;
        }

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    if (!isBoundary(x, y, z, bounds)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (BasicBlockType.get(tile.getWorld().getBlockState(pos)) != BasicBlockType.SPS_PORT) {
                        continue;
                    }
                    EnumFacing inner = getPortInnerSide(x, y, z, bounds);
                    if (inner == null) {
                        continue;
                    }
                    BlockPos coilPos = pos.offset(inner);
                    TileEntity coilTile = tile.getWorld().getTileEntity(coilPos);
                    if (!(coilTile instanceof TileEntitySuperchargedCoil)) {
                        continue;
                    }
                    Vec3d start = new Vec3d(
                            coilPos.getX() + 0.5D + inner.getXOffset() * 0.5D,
                            coilPos.getY() + 0.5D + inner.getYOffset() * 0.5D,
                            coilPos.getZ() + 0.5D + inner.getZOffset() * 0.5D);
                    Coord4D coord = new Coord4D(coilPos, tile.getWorld());
                    int level = multiblock.getRenderCoilLevel(coord);
                    starts.add(new ArcStart(start.subtract(tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ()), coord.hashCode(), level));
                }
            }
        }
        return starts;
    }

    private static Vec3d getRandomInnerWallPoint(RenderBounds bounds, EnumFacing side) {
        double x = randomBetween(bounds.minX + 1.05D, bounds.maxX - 0.05D);
        double y = randomBetween(bounds.minY + 1.05D, bounds.maxY - 0.05D);
        double z = randomBetween(bounds.minZ + 1.05D, bounds.maxZ - 0.05D);
        if (side.getAxis() == EnumFacing.Axis.X) {
            x = side == EnumFacing.WEST ? bounds.minX + 1.02D : bounds.maxX - 0.02D;
        } else if (side.getAxis() == EnumFacing.Axis.Y) {
            y = side == EnumFacing.DOWN ? bounds.minY + 1.02D : bounds.maxY - 0.02D;
        } else {
            z = side == EnumFacing.NORTH ? bounds.minZ + 1.02D : bounds.maxZ - 0.02D;
        }
        return new Vec3d(x, y, z);
    }

    private static boolean isBoundary(int x, int y, int z, RenderBounds bounds) {
        return x == bounds.minX || x == bounds.maxX || y == bounds.minY || y == bounds.maxY || z == bounds.minZ || z == bounds.maxZ;
    }

    private static EnumFacing getPortInnerSide(int x, int y, int z, RenderBounds bounds) {
        if (x == bounds.minX) {
            return EnumFacing.EAST;
        } else if (x == bounds.maxX) {
            return EnumFacing.WEST;
        } else if (y == bounds.minY) {
            return EnumFacing.UP;
        } else if (y == bounds.maxY) {
            return EnumFacing.DOWN;
        } else if (z == bounds.minZ) {
            return EnumFacing.SOUTH;
        } else if (z == bounds.maxZ) {
            return EnumFacing.NORTH;
        }
        return null;
    }

    private static double randomBetween(double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + RAND.nextDouble() * (max - min);
    }

    private static Object getRendererKey(TileEntitySPSCasing tile, SynchronizedSPSData multiblock) {
        return multiblock.inventoryID != null ? multiblock.inventoryID : tile.getPos();
    }

    private static class RenderBounds {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private RenderBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private Vec3d getCenter() {
            return new Vec3d((minX + maxX + 1) / 2D, (minY + maxY + 1) / 2D, (minZ + maxZ + 1) / 2D);
        }
    }

    private static class ArcStart {

        private final Vec3d localStart;
        private final Object ownerKey;
        private final int level;

        private ArcStart(Vec3d localStart, Object ownerKey, int level) {
            this.localStart = localStart;
            this.ownerKey = ownerKey;
            this.level = level;
        }
    }
}
