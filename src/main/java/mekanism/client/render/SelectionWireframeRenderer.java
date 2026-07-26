package mekanism.client.render;

import mekanism.common.config.MekanismConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class SelectionWireframeRenderer {

    private static final int AXIS_X = 0;
    private static final int AXIS_Y = 1;
    private static final int AXIS_Z = 2;
    private static final int NORMAL_X_NEG = 1 << 0;
    private static final int NORMAL_X_POS = 1 << 1;
    private static final int NORMAL_Y_NEG = 1 << 2;
    private static final int NORMAL_Y_POS = 1 << 3;
    private static final int NORMAL_Z_NEG = 1 << 4;
    private static final int NORMAL_Z_POS = 1 << 5;
    private static final double MIN_SEGMENT_LENGTH_SQ = 1.0E-12D;
    private static final double QUANTIZE_SCALE = 1_000_000D;
    private static final double MODEL_GRID_SCALE = 16D;
    // Many block models use small anti-z-fighting offsets like 0.005. Treat those as grid-aligned.
    private static final double MODEL_SNAP_EPSILON = 0.006D;
    private static final PreparedWireframe EMPTY_PREPARED_WIREFRAME = new PreparedWireframe(new LineSegment[0]);
    private static final Map<JsonModelSelectionBoxCache.OutlineBox[], PreparedWireframe> PREPARED_WIREFRAME_CACHE = new WeakHashMap<>();

    private SelectionWireframeRenderer() {
    }

    public static boolean isSelectionWireframeRenderingEnabled() {
        try {
            return MekanismConfig.current().client.enableSelectionWireframeRendering.val();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static float getConfiguredLineWidth() {
        float width = 2.0F;
        try {
            width = MekanismConfig.current().client.jsonSelectionBoxLineWidth.val();
        } catch (Exception ignored) {
        }
        if (width < 1.0F) {
            return 1.0F;
        }
        return Math.min(width, 8.0F);
    }

    public static boolean shouldAutoCycleWireframeColor() {
        try {
            return MekanismConfig.current().client.jsonSelectionBoxAutoColorCycle.val();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int getAutoCycleWireframeColorRGB() {
        float progress = (float) (System.currentTimeMillis() % 10000L) / 10000.0F;
        return Color.HSBtoRGB(progress, 0.8F, 0.8F) & 0xFFFFFF;
    }

    public static float redFromRGB(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public static float greenFromRGB(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public static float blueFromRGB(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }

    public static boolean keepVisibleInternalEdgesForState(IBlockState state) {
        if (state == null) {
            return false;
        }
        ResourceLocation registryName = state.getBlock().getRegistryName();
        if (registryName == null) {
            return false;
        }
        String namespace = registryName.getNamespace().toLowerCase(Locale.ROOT);
        String[] whitelist;
        try {
            whitelist = MekanismConfig.current().client.jsonSelectionBoxKeepVisibleInternalEdgesModIdWhitelist.get();
        } catch (Exception ignored) {
            whitelist = null;
        }
        if (whitelist == null || whitelist.length == 0) {
            return false;
        }
        for (String raw : whitelist) {
            if (raw == null) {
                continue;
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                continue;
            }
            if ("*".equals(value) || namespace.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static void begin(float lineWidth, boolean disableDepth) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        if (disableDepth) {
            GlStateManager.disableDepth();
        }
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(lineWidth);
    }

    public static void end(boolean depthDisabled) {
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        if (depthDisabled) {
            GlStateManager.enableDepth();
        }
        GlStateManager.disableBlend();
    }

    public static void drawWireframes(JsonModelSelectionBoxCache.OutlineBox[] wireframes, BlockPos blockPos,
                                      double cameraX, double cameraY, double cameraZ,
                                      float red, float green, float blue, float alpha) {
        drawWireframes(wireframes, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha, false);
    }

    public static void drawWireframes(JsonModelSelectionBoxCache.OutlineBox[] wireframes, BlockPos blockPos,
                                      double cameraX, double cameraY, double cameraZ,
                                      float red, float green, float blue, float alpha,
                                      boolean keepVisibleInternalEdges) {
        if (wireframes == null || wireframes.length == 0) {
            return;
        }

        PreparedWireframe preparedWireframe = getOrPrepareWireframe(wireframes);
        if (preparedWireframe.isEmpty()) {
            return;
        }

        double localCameraX = cameraX - blockPos.getX();
        double localCameraY = cameraY - blockPos.getY();
        double localCameraZ = cameraZ - blockPos.getZ();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (LineSegment segment : preparedWireframe.segments) {
            if (!keepVisibleInternalEdges && !shouldRenderSegment(segment, localCameraX, localCameraY, localCameraZ)) {
                continue;
            }
            addVertex(buffer, segment.start(), blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
            addVertex(buffer, segment.end(), blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        }
        tessellator.draw();
    }

    static PreparedWireframe getOrPrepareWireframe(JsonModelSelectionBoxCache.OutlineBox[] wireframes) {
        if (wireframes == null || wireframes.length == 0) {
            return EMPTY_PREPARED_WIREFRAME;
        }
        synchronized (PREPARED_WIREFRAME_CACHE) {
            PreparedWireframe cached = PREPARED_WIREFRAME_CACHE.get(wireframes);
            if (cached != null) {
                return cached;
            }
            PreparedWireframe prepared = prepareWireframe(wireframes);
            PREPARED_WIREFRAME_CACHE.put(wireframes, prepared);
            return prepared;
        }
    }

    private static PreparedWireframe prepareWireframe(JsonModelSelectionBoxCache.OutlineBox[] wireframes) {
        Map<PlaneKey, PlaneEdgeCollector> coplanarPlanes = new HashMap<>();
        Map<LineKey, LineAccumulator> fallbackSegments = new HashMap<>();

        for (JsonModelSelectionBoxCache.OutlineBox wireframe : wireframes) {
            if (wireframe == null) {
                continue;
            }
            if (isAxisAligned(wireframe)) {
                collectCoplanarFaceSegments(wireframe.getBounds(), coplanarPlanes);
            } else {
                collectFallbackSegments(wireframe.getLineVertices(), fallbackSegments);
            }
        }

        Map<LineKey, LineSegment> finalSegments = new LinkedHashMap<>();
        // Keep only merged outer boundaries from coplanar faces and unique fallback segments.
        for (Map.Entry<PlaneKey, PlaneEdgeCollector> planeEntry : coplanarPlanes.entrySet()) {
            PlaneKey plane = planeEntry.getKey();
            planeEntry.getValue().emitSegments(plane, finalSegments);
        }
        for (LineAccumulator segment : fallbackSegments.values()) {
            if (segment.isEvenOverlap()) {
                continue;
            }
            addOrUpdateSegment(finalSegments, segment.start(), segment.end(), 0, true);
        }
        if (finalSegments.isEmpty()) {
            return EMPTY_PREPARED_WIREFRAME;
        }
        return new PreparedWireframe(finalSegments.values().toArray(new LineSegment[0]));
    }

    private static boolean isAxisAligned(JsonModelSelectionBoxCache.OutlineBox wireframe) {
        Vec3d[] corners = wireframe.getCorners();
        if (corners == null || corners.length != 8) {
            return false;
        }
        Set<Long> uniqueX = new HashSet<>(4);
        Set<Long> uniqueY = new HashSet<>(4);
        Set<Long> uniqueZ = new HashSet<>(4);
        for (Vec3d corner : corners) {
            if (corner == null) {
                return false;
            }
            uniqueX.add(quantize(corner.x));
            uniqueY.add(quantize(corner.y));
            uniqueZ.add(quantize(corner.z));
        }
        return uniqueX.size() == 2 && uniqueY.size() == 2 && uniqueZ.size() == 2;
    }

    private static void collectCoplanarFaceSegments(AxisAlignedBB bounds, Map<PlaneKey, PlaneEdgeCollector> coplanarPlanes) {
        if (bounds == null) {
            return;
        }
        double minX = bounds.minX;
        double minY = bounds.minY;
        double minZ = bounds.minZ;
        double maxX = bounds.maxX;
        double maxY = bounds.maxY;
        double maxZ = bounds.maxZ;

        // X faces (u = y, v = z)
        addPlaneRect(coplanarPlanes, AXIS_X, minX, minY, minZ, maxY, maxZ, -1);
        addPlaneRect(coplanarPlanes, AXIS_X, maxX, minY, minZ, maxY, maxZ, 1);
        // Y faces (u = x, v = z)
        addPlaneRect(coplanarPlanes, AXIS_Y, minY, minX, minZ, maxX, maxZ, -1);
        addPlaneRect(coplanarPlanes, AXIS_Y, maxY, minX, minZ, maxX, maxZ, 1);
        // Z faces (u = x, v = y)
        addPlaneRect(coplanarPlanes, AXIS_Z, minZ, minX, minY, maxX, maxY, -1);
        addPlaneRect(coplanarPlanes, AXIS_Z, maxZ, minX, minY, maxX, maxY, 1);
    }

    private static void addPlaneRect(Map<PlaneKey, PlaneEdgeCollector> coplanarPlanes, int axis,
                                     double planeCoord, double minU, double minV, double maxU, double maxV, int normalSign) {
        PlaneKey planeKey = new PlaneKey(axis, quantize(planeCoord));
        PlaneEdgeCollector collector = coplanarPlanes.computeIfAbsent(planeKey, key -> new PlaneEdgeCollector());
        collector.addRect(minU, minV, maxU, maxV, normalSign);
    }

    private static void collectFallbackSegments(Vec3d[] lineVertices, Map<LineKey, LineAccumulator> fallbackSegments) {
        if (lineVertices == null || lineVertices.length < 2) {
            return;
        }
        for (int i = 0; i + 1 < lineVertices.length; i += 2) {
            Vec3d start = lineVertices[i];
            Vec3d end = lineVertices[i + 1];
            if (start == null || end == null || start.squareDistanceTo(end) <= MIN_SEGMENT_LENGTH_SQ) {
                continue;
            }
            LineKey key = LineKey.from(start, end);
            LineAccumulator accumulator = fallbackSegments.get(key);
            if (accumulator == null) {
                fallbackSegments.put(key, new LineAccumulator(start, end));
            } else {
                accumulator.increment();
            }
        }
    }

    private static void addOrUpdateSegment(Map<LineKey, LineSegment> finalSegments, Vec3d start, Vec3d end, int normalMask, boolean hasUnknownNormal) {
        if (start == null || end == null || start.squareDistanceTo(end) <= MIN_SEGMENT_LENGTH_SQ) {
            return;
        }
        LineKey key = LineKey.from(start, end);
        LineSegment existing = finalSegments.get(key);
        if (existing == null) {
            finalSegments.put(key, new LineSegment(start, end, normalMask, hasUnknownNormal));
        } else {
            existing.mergeNormals(normalMask, hasUnknownNormal);
        }
    }

    private static boolean shouldRenderSegment(LineSegment segment, double localCameraX, double localCameraY, double localCameraZ) {
        if (segment == null) {
            return false;
        }
        if (segment.hasUnknownNormal()) {
            return true;
        }
        int mask = segment.normalMask();
        if (mask == 0) {
            return true;
        }

        Vec3d start = segment.start();
        Vec3d end = segment.end();
        double midX = (start.x + end.x) * 0.5D;
        double midY = (start.y + end.y) * 0.5D;
        double midZ = (start.z + end.z) * 0.5D;
        double viewX = localCameraX - midX;
        double viewY = localCameraY - midY;
        double viewZ = localCameraZ - midZ;

        boolean front = false;
        boolean back = false;
        for (int i = 0; i < 6; i++) {
            int bit = 1 << i;
            if ((mask & bit) == 0) {
                continue;
            }
            double dot = normalDot(bit, viewX, viewY, viewZ);
            if (dot > 1.0E-7D) {
                front = true;
            } else if (dot < -1.0E-7D) {
                back = true;
            } else {
                front = true;
                back = true;
            }
            if (front && back) {
                return true;
            }
        }
        // Keep edges belonging to visible faces as well; strict silhouette-only removes too many creases.
        return front;
    }

    private static double normalDot(int normalBit, double viewX, double viewY, double viewZ) {
        return switch (normalBit) {
            case NORMAL_X_NEG -> -viewX;
            case NORMAL_X_POS -> viewX;
            case NORMAL_Y_NEG -> -viewY;
            case NORMAL_Y_POS -> viewY;
            case NORMAL_Z_NEG -> -viewZ;
            case NORMAL_Z_POS -> viewZ;
            default -> 0D;
        };
    }

    private static long quantize(double value) {
        return Math.round(normalizeCoordinate(value) * QUANTIZE_SCALE);
    }

    private static double dequantize(long value) {
        return value / QUANTIZE_SCALE;
    }

    private static double normalizeCoordinate(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        double nearestModelGrid = Math.rint(value * MODEL_GRID_SCALE) / MODEL_GRID_SCALE;
        if (Math.abs(value - nearestModelGrid) <= MODEL_SNAP_EPSILON) {
            return nearestModelGrid;
        }
        return value;
    }

    private static void addVertex(BufferBuilder buffer, Vec3d lineVertex, BlockPos blockPos, double cameraX, double cameraY, double cameraZ,
                                  float red, float green, float blue, float alpha) {
        double x = lineVertex.x + blockPos.getX() - cameraX;
        double y = lineVertex.y + blockPos.getY() - cameraY;
        double z = lineVertex.z + blockPos.getZ() - cameraZ;
        buffer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
    }

    static final class PreparedWireframe {

        private final LineSegment[] segments;

        private PreparedWireframe(LineSegment[] segments) {
            this.segments = segments;
        }

        int segmentCount() {
            return segments.length;
        }

        private boolean isEmpty() {
            return segments.length == 0;
        }
    }

    private static final class LineAccumulator {

        private final Vec3d start;
        private final Vec3d end;
        private int overlapCount = 1;

        private LineAccumulator(Vec3d start, Vec3d end) {
            this.start = start;
            this.end = end;
        }

        private void increment() {
            overlapCount++;
        }

        private boolean isEvenOverlap() {
            return (overlapCount & 1) == 0;
        }

        private Vec3d start() {
            return start;
        }

        private Vec3d end() {
            return end;
        }
    }

    private static final class LineSegment {

        private final Vec3d start;
        private final Vec3d end;
        private int normalMask;
        private boolean unknownNormal;

        private LineSegment(Vec3d start, Vec3d end, int normalMask, boolean unknownNormal) {
            this.start = start;
            this.end = end;
            this.normalMask = normalMask;
            this.unknownNormal = unknownNormal;
        }

        private Vec3d start() {
            return start;
        }

        private Vec3d end() {
            return end;
        }

        private int normalMask() {
            return normalMask;
        }

        private boolean hasUnknownNormal() {
            return unknownNormal;
        }

        private void mergeNormals(int otherMask, boolean otherUnknown) {
            normalMask |= otherMask;
            unknownNormal |= otherUnknown;
        }
    }

    private static final class LineKey {

        private final long ax;
        private final long ay;
        private final long az;
        private final long bx;
        private final long by;
        private final long bz;

        private LineKey(long ax, long ay, long az, long bx, long by, long bz) {
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
        }

        private static LineKey from(Vec3d start, Vec3d end) {
            long ax = quantize(start.x);
            long ay = quantize(start.y);
            long az = quantize(start.z);
            long bx = quantize(end.x);
            long by = quantize(end.y);
            long bz = quantize(end.z);
            if (compare(ax, ay, az, bx, by, bz) <= 0) {
                return new LineKey(ax, ay, az, bx, by, bz);
            }
            return new LineKey(bx, by, bz, ax, ay, az);
        }

        private static int compare(long ax, long ay, long az, long bx, long by, long bz) {
            int c = Long.compare(ax, bx);
            if (c != 0) {
                return c;
            }
            c = Long.compare(ay, by);
            if (c != 0) {
                return c;
            }
            return Long.compare(az, bz);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LineKey other)) {
                return false;
            }
            return ax == other.ax && ay == other.ay && az == other.az
                    && bx == other.bx && by == other.by && bz == other.bz;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(ax);
            result = 31 * result + Long.hashCode(ay);
            result = 31 * result + Long.hashCode(az);
            result = 31 * result + Long.hashCode(bx);
            result = 31 * result + Long.hashCode(by);
            result = 31 * result + Long.hashCode(bz);
            return result;
        }
    }

    private static final class PlaneKey {

        private final int axis;
        private final long plane;

        private PlaneKey(int axis, long plane) {
            this.axis = axis;
            this.plane = plane;
        }

        private Vec3d[] toWorldSegment(boolean alongU, long fixedCoord, long start, long end) {
            double p = dequantize(plane);
            double c = dequantize(fixedCoord);
            double from = dequantize(start);
            double to = dequantize(end);
            Vec3d startVec;
            Vec3d endVec;
            if (axis == AXIS_X) {
                if (alongU) {
                    startVec = new Vec3d(p, from, c);
                    endVec = new Vec3d(p, to, c);
                } else {
                    startVec = new Vec3d(p, c, from);
                    endVec = new Vec3d(p, c, to);
                }
            } else if (axis == AXIS_Y) {
                if (alongU) {
                    startVec = new Vec3d(from, p, c);
                    endVec = new Vec3d(to, p, c);
                } else {
                    startVec = new Vec3d(c, p, from);
                    endVec = new Vec3d(c, p, to);
                }
            } else {
                if (alongU) {
                    startVec = new Vec3d(from, c, p);
                    endVec = new Vec3d(to, c, p);
                } else {
                    startVec = new Vec3d(c, from, p);
                    endVec = new Vec3d(c, to, p);
                }
            }
            return new Vec3d[]{startVec, endVec};
        }

        private int normalMask(int normalSign) {
            int sign = normalSign >= 0 ? 1 : -1;
            if (axis == AXIS_X) {
                return sign > 0 ? NORMAL_X_POS : NORMAL_X_NEG;
            } else if (axis == AXIS_Y) {
                return sign > 0 ? NORMAL_Y_POS : NORMAL_Y_NEG;
            }
            return sign > 0 ? NORMAL_Z_POS : NORMAL_Z_NEG;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PlaneKey other)) {
                return false;
            }
            return axis == other.axis && plane == other.plane;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(axis);
            result = 31 * result + Long.hashCode(plane);
            return result;
        }
    }

    private static final class PlaneEdgeCollector {

        private final List<FaceRect> faces = new ArrayList<>();

        private void addRect(double minU, double minV, double maxU, double maxV, int normalSign) {
            long qMinU = quantize(Math.min(minU, maxU));
            long qMaxU = quantize(Math.max(minU, maxU));
            long qMinV = quantize(Math.min(minV, maxV));
            long qMaxV = quantize(Math.max(minV, maxV));
            if (qMinU == qMaxU || qMinV == qMaxV) {
                return;
            }
            int sign = normalSign >= 0 ? 1 : -1;
            faces.add(new FaceRect(qMinU, qMaxU, qMinV, qMaxV, sign));
        }

        private void emitSegments(PlaneKey plane, Map<LineKey, LineSegment> finalSegments) {
            if (faces.isEmpty()) {
                return;
            }

            Set<Long> uSet = new HashSet<>();
            Set<Long> vSet = new HashSet<>();
            for (FaceRect face : faces) {
                uSet.add(face.minU);
                uSet.add(face.maxU);
                vSet.add(face.minV);
                vSet.add(face.maxV);
            }
            if (uSet.size() < 2 || vSet.size() < 2) {
                return;
            }

            List<Long> uCoords = new ArrayList<>(uSet);
            List<Long> vCoords = new ArrayList<>(vSet);
            Collections.sort(uCoords);
            Collections.sort(vCoords);

            Map<Long, Integer> uIndex = new HashMap<>(uCoords.size() * 2);
            Map<Long, Integer> vIndex = new HashMap<>(vCoords.size() * 2);
            for (int i = 0; i < uCoords.size(); i++) {
                uIndex.put(uCoords.get(i), i);
            }
            for (int i = 0; i < vCoords.size(); i++) {
                vIndex.put(vCoords.get(i), i);
            }

            int uCells = uCoords.size() - 1;
            int vCells = vCoords.size() - 1;
            int[][] coverage = new int[uCells][vCells];

            for (FaceRect face : faces) {
                Integer u0 = uIndex.get(face.minU);
                Integer u1 = uIndex.get(face.maxU);
                Integer v0 = vIndex.get(face.minV);
                Integer v1 = vIndex.get(face.maxV);
                if (u0 == null || u1 == null || v0 == null || v1 == null) {
                    continue;
                }
                for (int u = u0; u < u1; u++) {
                    for (int v = v0; v < v1; v++) {
                        coverage[u][v] += face.sign;
                    }
                }
            }

            Map<LineBucketKey, List<SignedLongRange>> boundaryBuckets = new HashMap<>();
            for (int u = 0; u < uCells; u++) {
                long uStart = uCoords.get(u);
                long uEnd = uCoords.get(u + 1);
                for (int v = 0; v < vCells; v++) {
                    if (coverage[u][v] == 0) {
                        continue;
                    }
                    int normalSign = coverage[u][v] > 0 ? 1 : -1;
                    long vStart = vCoords.get(v);
                    long vEnd = vCoords.get(v + 1);

                    if (v == 0 || coverage[u][v - 1] == 0) {
                        addRange(boundaryBuckets, true, vStart, uStart, uEnd, normalSign);
                    }
                    if (v == vCells - 1 || coverage[u][v + 1] == 0) {
                        addRange(boundaryBuckets, true, vEnd, uStart, uEnd, normalSign);
                    }
                    if (u == 0 || coverage[u - 1][v] == 0) {
                        addRange(boundaryBuckets, false, uStart, vStart, vEnd, normalSign);
                    }
                    if (u == uCells - 1 || coverage[u + 1][v] == 0) {
                        addRange(boundaryBuckets, false, uEnd, vStart, vEnd, normalSign);
                    }
                }
            }

            for (Map.Entry<LineBucketKey, List<SignedLongRange>> entry : boundaryBuckets.entrySet()) {
                LineBucketKey bucket = entry.getKey();
                List<SignedLongRange> ranges = entry.getValue();
                if (ranges == null || ranges.isEmpty()) {
                    continue;
                }
                ranges.sort((a, b) -> {
                    int c = Long.compare(a.start, b.start);
                    if (c != 0) {
                        return c;
                    }
                    c = Long.compare(a.end, b.end);
                    if (c != 0) {
                        return c;
                    }
                    return Integer.compare(a.normalSign, b.normalSign);
                });

                long currentStart = ranges.get(0).start;
                long currentEnd = ranges.get(0).end;
                int currentSign = ranges.get(0).normalSign;
                for (int i = 1; i < ranges.size(); i++) {
                    SignedLongRange range = ranges.get(i);
                    if (range.start <= currentEnd && range.normalSign == currentSign) {
                        currentEnd = Math.max(currentEnd, range.end);
                    } else {
                        emitWorldSegment(plane, bucket, currentStart, currentEnd, currentSign, finalSegments);
                        currentStart = range.start;
                        currentEnd = range.end;
                        currentSign = range.normalSign;
                    }
                }
                emitWorldSegment(plane, bucket, currentStart, currentEnd, currentSign, finalSegments);
            }
        }

        private void addRange(Map<LineBucketKey, List<SignedLongRange>> buckets, boolean alongU, long fixedCoord, long start, long end, int normalSign) {
            long from = start;
            long to = end;
            if (from == to) {
                return;
            }
            if (from > to) {
                long tmp = from;
                from = to;
                to = tmp;
            }
            LineBucketKey key = new LineBucketKey(alongU, fixedCoord);
            int sign = normalSign >= 0 ? 1 : -1;
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new SignedLongRange(from, to, sign));
        }

        private void emitWorldSegment(PlaneKey plane, LineBucketKey bucket, long start, long end, int normalSign,
                                      Map<LineKey, LineSegment> finalSegments) {
            if (start == end) {
                return;
            }
            Vec3d[] worldSegment = plane.toWorldSegment(bucket.alongU, bucket.fixedCoord, start, end);
            int normalMask = plane.normalMask(normalSign);
            addOrUpdateSegment(finalSegments, worldSegment[0], worldSegment[1], normalMask, false);
        }
    }

    private static final class FaceRect {

        private final long minU;
        private final long maxU;
        private final long minV;
        private final long maxV;
        private final int sign;

        private FaceRect(long minU, long maxU, long minV, long maxV, int sign) {
            this.minU = minU;
            this.maxU = maxU;
            this.minV = minV;
            this.maxV = maxV;
            this.sign = sign;
        }
    }

    private static final class LineBucketKey {

        private final boolean alongU;
        private final long fixedCoord;

        private LineBucketKey(boolean alongU, long fixedCoord) {
            this.alongU = alongU;
            this.fixedCoord = fixedCoord;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LineBucketKey other)) {
                return false;
            }
            return alongU == other.alongU && fixedCoord == other.fixedCoord;
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(alongU);
            result = 31 * result + Long.hashCode(fixedCoord);
            return result;
        }
    }

    private static final class SignedLongRange {

        private final long start;
        private final long end;
        private final int normalSign;

        private SignedLongRange(long start, long end, int normalSign) {
            this.start = start;
            this.end = end;
            this.normalSign = normalSign;
        }
    }
}
