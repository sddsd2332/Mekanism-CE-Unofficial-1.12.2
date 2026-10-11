package mekanism.client.render;

import mekanism.common.tile.machine.TileEntityDimensionalStabilizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class DimensionalStabilizerOverlayRenderer {

    private static final double LINE_OFFSET = 0.0025D;
    private static final double MAX_RENDER_DISTANCE_SQ = 512D * 512D;
    private static final int NORTH = 0;
    private static final int EAST = 1;
    private static final int SOUTH = 2;
    private static final int WEST = 3;
    private static final int LOADED = 4;
    // 1.21.1 style: white translucent vertical walls only.
    private static final float COLOR_RED = 1.0F;
    private static final float COLOR_GREEN = 1.0F;
    private static final float COLOR_BLUE = 1.0F;
    private static final float NORTH_SOUTH_ALPHA = 0.82F;
    private static final float EAST_WEST_ALPHA = 0.78F;

    private DimensionalStabilizerOverlayRenderer() {
    }

    public static void render(Minecraft mc, Entity renderViewEntity, double viewX, double viewY, double viewZ) {
        if (mc.world == null || mc.world.loadedTileEntityList.isEmpty()) {
            return;
        }
        List<TileEntityDimensionalStabilizer> visibleStabilizers = new ArrayList<>();
        List<TileEntity> loadedTiles = new ArrayList<>(mc.world.loadedTileEntityList);
        for (TileEntity tileEntity : loadedTiles) {
            if (!(tileEntity instanceof TileEntityDimensionalStabilizer stabilizer) || stabilizer.isInvalid()) {
                continue;
            }
            if (!stabilizer.isClientRendering() || !stabilizer.canDisplayVisuals() || stabilizer.getWorld() == null) {
                continue;
            }
            if (renderViewEntity.getDistanceSq(stabilizer.getPos()) > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }
            visibleStabilizers.add(stabilizer);
        }
        if (visibleStabilizers.isEmpty()) {
            return;
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (TileEntityDimensionalStabilizer stabilizer : visibleStabilizers) {
            drawMergedFaces(buffer, stabilizer, renderViewEntity, viewX, viewY, viewZ);
        }
        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void drawMergedFaces(BufferBuilder buffer, TileEntityDimensionalStabilizer stabilizer, Entity renderViewEntity,
                                        double viewX, double viewY, double viewZ) {
        boolean[][][] allRenderSides = getRenderSides(stabilizer);
        List<RenderPiece> pieces = calculateRenderPieces(allRenderSides);
        if (pieces.isEmpty()) {
            return;
        }
        int worldHeight = stabilizer.getWorld().getHeight();
        int tileChunkX = stabilizer.getPos().getX() >> 4;
        int tileChunkZ = stabilizer.getPos().getZ() >> 4;
        for (RenderPiece piece : pieces) {
            int xChunkOffset = piece.x - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS;
            int zChunkOffset = piece.z - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS;
            int startChunkX = tileChunkX + xChunkOffset;
            int startChunkZ = tileChunkZ + zChunkOffset;
            int startMinX = startChunkX << 4;
            int startMinZ = startChunkZ << 4;
            int endMaxX = startMinX + 16 * piece.xLength;
            int endMaxZ = startMinZ + 16 * piece.zLength;

            // Keep slight offset/scale tweaks to avoid z-fighting at boundaries, matching 1.21.1 logic.
            double xShift = 0.01D;
            double zShift = 0.01D;
            double xScaleShift = 0.02D;
            double zScaleShift = 0.02D;
            if (piece.renderEast && piece.renderWest && !piece.renderNorth) {
                zShift = -0.01D;
                zScaleShift = piece.renderSouth ? 0D : -0.02D;
            } else if (piece.renderNorth && !piece.renderSouth) {
                zScaleShift = 0D;
            } else if (piece.renderNorth && piece.renderWest && !piece.renderEast) {
                xScaleShift = 0D;
            } else if (piece.renderNorth && !piece.renderWest) {
                xShift = -0.01D;
                xScaleShift = piece.renderEast ? 0D : -0.02D;
            } else if (piece.renderSouth && piece.renderEast != piece.renderWest) {
                zShift = -0.01D;
                zScaleShift = 0D;
            }

            double minX = startMinX + xShift + LINE_OFFSET;
            double minZ = startMinZ + zShift + LINE_OFFSET;
            double maxX = startMinX + 16D * piece.xLength - xScaleShift - LINE_OFFSET;
            double maxZ = startMinZ + 16D * piece.zLength - zScaleShift - LINE_OFFSET;
            double renderMinX = minX - viewX;
            double renderMaxX = maxX - viewX;
            double renderMinY = LINE_OFFSET - viewY;
            double renderMaxY = worldHeight - LINE_OFFSET - viewY;
            double renderMinZ = minZ - viewZ;
            double renderMaxZ = maxZ - viewZ;

            boolean insidePiece = isInsideBounds(renderViewEntity, startMinX, startMinZ, endMaxX, endMaxZ);
            if (piece.renderNorth) {
                addFace(buffer, renderMinX, renderMinY, renderMinZ, renderMinX, renderMaxY, renderMinZ, renderMaxX, renderMaxY, renderMinZ, renderMaxX, renderMinY, renderMinZ,
                        NORTH_SOUTH_ALPHA, insidePiece);
            }
            if (piece.renderSouth) {
                addFace(buffer, renderMinX, renderMinY, renderMaxZ, renderMaxX, renderMinY, renderMaxZ, renderMaxX, renderMaxY, renderMaxZ, renderMinX, renderMaxY, renderMaxZ,
                        NORTH_SOUTH_ALPHA, insidePiece);
            }
            if (piece.renderWest) {
                addFace(buffer, renderMinX, renderMinY, renderMinZ, renderMinX, renderMinY, renderMaxZ, renderMinX, renderMaxY, renderMaxZ, renderMinX, renderMaxY, renderMinZ,
                        EAST_WEST_ALPHA, insidePiece);
            }
            if (piece.renderEast) {
                addFace(buffer, renderMaxX, renderMinY, renderMinZ, renderMaxX, renderMaxY, renderMinZ, renderMaxX, renderMaxY, renderMaxZ, renderMaxX, renderMinY, renderMaxZ,
                        EAST_WEST_ALPHA, insidePiece);
            }
        }
    }

    private static boolean isInsideBounds(Entity cameraEntity, int minX, int minZ, int maxXExclusive, int maxZExclusive) {
        double x = cameraEntity.posX;
        double z = cameraEntity.posZ;
        return x >= minX && x < maxXExclusive && z >= minZ && z < maxZExclusive;
    }

    private static boolean[][][] getRenderSides(TileEntityDimensionalStabilizer stabilizer) {
        int diameter = TileEntityDimensionalStabilizer.MAX_LOAD_DIAMETER;
        boolean[][][] allRenderSides = new boolean[diameter][diameter][5];
        for (int x = 0; x < diameter; x++) {
            boolean[][] rowRenderSides = allRenderSides[x];
            for (int z = 0; z < diameter; z++) {
                if (stabilizer.isChunkLoadingAt(x, z)) {
                    boolean[] renderSides = rowRenderSides[z];
                    for (int i = 0; i < renderSides.length; i++) {
                        renderSides[i] = true;
                    }
                    if (x > 0) {
                        boolean[] previousRenderSides = allRenderSides[x - 1][z];
                        if (previousRenderSides[EAST]) {
                            renderSides[WEST] = false;
                            previousRenderSides[EAST] = false;
                        }
                    }
                    if (z > 0) {
                        boolean[] previousRenderSides = rowRenderSides[z - 1];
                        if (previousRenderSides[SOUTH]) {
                            renderSides[NORTH] = false;
                            previousRenderSides[SOUTH] = false;
                        }
                    }
                }
            }
        }
        return allRenderSides;
    }

    private static List<RenderPiece> calculateRenderPieces(boolean[][][] allRenderSides) {
        Map<ColumnKey, List<RowData>> columnData = new HashMap<>();
        for (int x = 0; x < allRenderSides.length; x++) {
            boolean[][] rowRenderSides = allRenderSides[x];
            for (int z = 0; z < rowRenderSides.length; ) {
                int zLength = 1;
                boolean[] renderSides = rowRenderSides[z];
                if (renderSides[LOADED]) {
                    boolean renderNorth = renderSides[NORTH];
                    boolean renderSouth = renderSides[SOUTH];
                    boolean renderEast = renderSides[EAST];
                    boolean renderWest = renderSides[WEST];
                    while (!renderSouth && z + zLength < rowRenderSides.length) {
                        boolean[] nextColumnRenderSides = rowRenderSides[z + zLength];
                        if (renderEast == nextColumnRenderSides[EAST] &&
                                renderWest == nextColumnRenderSides[WEST]) {
                            zLength++;
                            renderSouth = nextColumnRenderSides[SOUTH];
                        } else {
                            break;
                        }
                    }
                    ColumnKey key = new ColumnKey(z, zLength, renderNorth, renderSouth);
                    columnData.computeIfAbsent(key, k -> new ArrayList<>(TileEntityDimensionalStabilizer.MAX_LOAD_DIAMETER))
                            .add(new RowData(x, renderEast, renderWest));
                }
                z += zLength;
            }
        }
        List<RenderPiece> pieces = new ArrayList<>();
        for (Map.Entry<ColumnKey, List<RowData>> entry : columnData.entrySet()) {
            ColumnKey columnKey = entry.getKey();
            List<RowData> rows = entry.getValue();
            for (int row = 0; row < rows.size(); ) {
                int xLength = 1;
                RowData rowData = rows.get(row);
                boolean renderEast = rowData.renderEast;
                while (!renderEast && row + xLength < rows.size()) {
                    RowData nextRow = rows.get(row + xLength);
                    if (rowData.x + xLength == nextRow.x) {
                        xLength++;
                        renderEast = nextRow.renderEast;
                    } else {
                        break;
                    }
                }
                pieces.add(new RenderPiece(rowData.x, xLength, columnKey.z, columnKey.zLength, columnKey.renderNorth,
                        columnKey.renderSouth, renderEast, rowData.renderWest));
                row += xLength;
            }
        }
        return pieces;
    }

    private static void addFace(BufferBuilder buffer,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                float alpha, boolean insidePiece) {
        if (insidePiece) {
            // Equivalent to 1.21.1 FaceDisplay.BACK when camera is inside the bounds.
            addQuad(buffer, x4, y4, z4, x3, y3, z3, x2, y2, z2, x1, y1, z1, alpha);
        } else {
            // Equivalent to 1.21.1 FaceDisplay.BOTH when camera is outside the bounds.
            addQuad(buffer, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, alpha);
            addQuad(buffer, x4, y4, z4, x3, y3, z3, x2, y2, z2, x1, y1, z1, alpha);
        }
    }

    private static void addQuad(BufferBuilder buffer,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                float alpha) {
        buffer.pos(x1, y1, z1).color(COLOR_RED, COLOR_GREEN, COLOR_BLUE, alpha).endVertex();
        buffer.pos(x2, y2, z2).color(COLOR_RED, COLOR_GREEN, COLOR_BLUE, alpha).endVertex();
        buffer.pos(x3, y3, z3).color(COLOR_RED, COLOR_GREEN, COLOR_BLUE, alpha).endVertex();
        buffer.pos(x4, y4, z4).color(COLOR_RED, COLOR_GREEN, COLOR_BLUE, alpha).endVertex();
    }

    private static class ColumnKey {

        private final int z;
        private final int zLength;
        private final boolean renderNorth;
        private final boolean renderSouth;

        private ColumnKey(int z, int zLength, boolean renderNorth, boolean renderSouth) {
            this.z = z;
            this.zLength = zLength;
            this.renderNorth = renderNorth;
            this.renderSouth = renderSouth;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ColumnKey other)) {
                return false;
            }
            return z == other.z && zLength == other.zLength && renderNorth == other.renderNorth && renderSouth == other.renderSouth;
        }

        @Override
        public int hashCode() {
            int result = z;
            result = 31 * result + zLength;
            result = 31 * result + (renderNorth ? 1 : 0);
            result = 31 * result + (renderSouth ? 1 : 0);
            return result;
        }
    }

    private static class RowData {

        private final int x;
        private final boolean renderEast;
        private final boolean renderWest;

        private RowData(int x, boolean renderEast, boolean renderWest) {
            this.x = x;
            this.renderEast = renderEast;
            this.renderWest = renderWest;
        }
    }

    private static class RenderPiece {

        private final int x;
        private final int xLength;
        private final int z;
        private final int zLength;
        private final boolean renderNorth;
        private final boolean renderSouth;
        private final boolean renderEast;
        private final boolean renderWest;

        private RenderPiece(int x, int xLength, int z, int zLength, boolean renderNorth, boolean renderSouth, boolean renderEast, boolean renderWest) {
            this.x = x;
            this.xLength = xLength;
            this.z = z;
            this.zLength = zLength;
            this.renderNorth = renderNorth;
            this.renderSouth = renderSouth;
            this.renderEast = renderEast;
            this.renderWest = renderWest;
        }
    }
}
