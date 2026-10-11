package mekanism.common.lib.effect;

import mekanism.common.lib.Color;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class BoltEffect {

    private final Random random = new Random();

    private final BoltRenderInfo renderInfo;

    private final Vec3d start;
    private final Vec3d end;

    private final int segments;

    private int count = 1;
    private float size = 0.1F;

    private int lifespan = 30;

    private SpawnFunction spawnFunction = SpawnFunction.delay(60);
    private FadeFunction fadeFunction = FadeFunction.fade(0.5F);

    public BoltEffect(Vec3d start, Vec3d end) {
        this(BoltRenderInfo.DEFAULT, start, end, (int) (Math.sqrt(start.distanceTo(end) * 100)));
    }

    public BoltEffect(BoltRenderInfo info, Vec3d start, Vec3d end, int segments) {
        this.renderInfo = info;
        this.start = start;
        this.end = end;
        this.segments = segments;
    }

    public BoltEffect count(int count) {
        this.count = count;
        return this;
    }

    public BoltEffect size(float size) {
        this.size = size;
        return this;
    }

    public BoltEffect spawn(SpawnFunction spawnFunction) {
        this.spawnFunction = spawnFunction;
        return this;
    }

    public BoltEffect fade(FadeFunction fadeFunction) {
        this.fadeFunction = fadeFunction;
        return this;
    }

    public BoltEffect lifespan(int lifespan) {
        this.lifespan = lifespan;
        return this;
    }

    public int getLifespan() {
        return lifespan;
    }

    public SpawnFunction getSpawnFunction() {
        return spawnFunction;
    }

    public FadeFunction getFadeFunction() {
        return fadeFunction;
    }

    public Color getColor() {
        return renderInfo.color;
    }

    public List<BoltQuads> generate() {
        List<BoltQuads> quads = new ArrayList<>();
        Vec3d diff = end.subtract(start);
        float totalDistance = (float) Math.sqrt(diff.lengthSquared());
        for (int i = 0; i < count; i++) {
            Queue<BoltInstructions> drawQueue = new LinkedList<>();
            drawQueue.add(new BoltInstructions(start, 0, new Vec3d(0, 0, 0), null, false));
            while (!drawQueue.isEmpty()) {
                BoltInstructions data = drawQueue.poll();
                Vec3d perpendicularDist = data.perpendicularDist;
                float progress = data.progress + (1F / segments) * (1 - renderInfo.parallelNoise + random.nextFloat() * renderInfo.parallelNoise * 2);
                Vec3d segmentEnd;
                float segmentDiffScale = renderInfo.spreadFunction.getMaxSpread(progress);
                if (progress >= 1 && segmentDiffScale <= 0) {
                    segmentEnd = end;
                } else {
                    float maxDiff = renderInfo.spreadFactor * segmentDiffScale * totalDistance;
                    Vec3d randVec = findRandomOrthogonalVector(diff, random);
                    double rand = renderInfo.randomFunction.getRandom(random);
                    perpendicularDist = renderInfo.segmentSpreader.getSegmentAdd(perpendicularDist, randVec, maxDiff, segmentDiffScale, progress, rand);
                    segmentEnd = start.add(diff.scale(progress)).add(perpendicularDist);
                }
                float boltSize = size * (0.5F + (1 - progress) * 0.5F);
                Pair<BoltQuads, QuadCache> quadData = createQuads(data.cache, data.start, segmentEnd, boltSize);
                quads.add(quadData.getLeft());

                if (progress >= 1) {
                    break;
                } else if (!data.isBranch) {
                    drawQueue.add(new BoltInstructions(segmentEnd, progress, perpendicularDist, quadData.getRight(), false));
                } else if (random.nextFloat() < renderInfo.branchContinuationFactor) {
                    drawQueue.add(new BoltInstructions(segmentEnd, progress, perpendicularDist, quadData.getRight(), true));
                }

                while (random.nextFloat() < renderInfo.branchInitiationFactor * (1 - progress)) {
                    drawQueue.add(new BoltInstructions(segmentEnd, progress, perpendicularDist, quadData.getRight(), true));
                }
            }
        }
        return quads;
    }

    private static Vec3d findRandomOrthogonalVector(Vec3d vec, Random rand) {
        Vec3d newVec = new Vec3d(-0.5D + rand.nextDouble(), -0.5D + rand.nextDouble(), -0.5D + rand.nextDouble());
        Vec3d cross = vec.crossProduct(newVec);
        if (cross.lengthSquared() < 1.0E-5D) {
            cross = vec.crossProduct(new Vec3d(0, 1, 0));
        }
        if (cross.lengthSquared() < 1.0E-5D) {
            cross = new Vec3d(1, 0, 0);
        }
        return cross.normalize();
    }

    private Pair<BoltQuads, QuadCache> createQuads(QuadCache cache, Vec3d startPos, Vec3d end, float size) {
        Vec3d diff = end.subtract(startPos);
        Vec3d rightAdd = diff.crossProduct(new Vec3d(0.5D, 0.5D, 0.5D));
        if (rightAdd.lengthSquared() < 1.0E-5D) {
            rightAdd = diff.crossProduct(new Vec3d(0, 1, 0));
        }
        if (rightAdd.lengthSquared() < 1.0E-5D) {
            rightAdd = new Vec3d(1, 0, 0);
        }
        rightAdd = rightAdd.normalize().scale(size);
        Vec3d backAdd = diff.crossProduct(rightAdd);
        if (backAdd.lengthSquared() < 1.0E-5D) {
            backAdd = new Vec3d(0, 0, 1);
        } else {
            backAdd = backAdd.normalize();
        }
        backAdd = backAdd.scale(size);
        Vec3d rightAddSplit = rightAdd.scale(0.5F);

        Vec3d start = cache != null ? cache.prevEnd : startPos;
        Vec3d startRight = cache != null ? cache.prevEndRight : start.add(rightAdd);
        Vec3d startBack = cache != null ? cache.prevEndBack : start.add(rightAddSplit).add(backAdd);
        Vec3d endRight = end.add(rightAdd);
        Vec3d endBack = end.add(rightAddSplit).add(backAdd);

        BoltQuads quads = new BoltQuads();
        quads.addQuad(start, end, endRight, startRight);
        quads.addQuad(startRight, endRight, end, start);

        quads.addQuad(startRight, endRight, endBack, startBack);
        quads.addQuad(startBack, endBack, endRight, startRight);

        return Pair.of(quads, new QuadCache(end, endRight, endBack));
    }

    private static class QuadCache {

        private final Vec3d prevEnd;
        private final Vec3d prevEndRight;
        private final Vec3d prevEndBack;

        private QuadCache(Vec3d prevEnd, Vec3d prevEndRight, Vec3d prevEndBack) {
            this.prevEnd = prevEnd;
            this.prevEndRight = prevEndRight;
            this.prevEndBack = prevEndBack;
        }
    }

    protected static class BoltInstructions {

        private final Vec3d start;
        private final Vec3d perpendicularDist;
        private final QuadCache cache;
        private final float progress;
        private final boolean isBranch;

        private BoltInstructions(Vec3d start, float progress, Vec3d perpendicularDist, QuadCache cache, boolean isBranch) {
            this.start = start;
            this.perpendicularDist = perpendicularDist;
            this.progress = progress;
            this.cache = cache;
            this.isBranch = isBranch;
        }
    }

    public static class BoltQuads {

        private final List<Vec3d> vecs = new ArrayList<>();

        protected void addQuad(Vec3d... quadVecs) {
            vecs.addAll(Arrays.asList(quadVecs));
        }

        public List<Vec3d> getVecs() {
            return vecs;
        }
    }

    public interface SpreadFunction {

        SpreadFunction LINEAR_ASCENT = progress -> progress;
        SpreadFunction LINEAR_ASCENT_DESCENT = progress -> (progress - Math.max(0, 2 * progress - 1)) / 0.5F;
        SpreadFunction SINE = progress -> (float) Math.sin(Math.PI * progress);

        float getMaxSpread(float progress);
    }

    public interface RandomFunction {

        RandomFunction UNIFORM = Random::nextFloat;
        RandomFunction GAUSSIAN = rand -> (float) rand.nextGaussian();

        float getRandom(Random rand);
    }

    public interface SegmentSpreader {

        SegmentSpreader NO_MEMORY = (perpendicularDist, randVec, maxDiff, scale, progress, rand) -> randVec.scale(maxDiff * rand);

        static SegmentSpreader memory(float memoryFactor) {
            return (perpendicularDist, randVec, maxDiff, spreadScale, progress, rand) -> {
                double nextDiff = maxDiff * (1 - memoryFactor) * rand;
                Vec3d cur = randVec.scale(nextDiff);
                perpendicularDist = perpendicularDist.add(cur);
                double length = Math.sqrt(perpendicularDist.lengthSquared());
                if (length > maxDiff) {
                    perpendicularDist = perpendicularDist.scale(maxDiff / length);
                }
                return perpendicularDist.add(cur);
            };
        }

        Vec3d getSegmentAdd(Vec3d perpendicularDist, Vec3d randVec, float maxDiff, float scale, float progress, double rand);
    }

    public interface SpawnFunction {

        SpawnFunction NO_DELAY = rand -> Pair.of(0F, 0F);
        SpawnFunction CONSECUTIVE = new SpawnFunction() {
            @Override
            public Pair<Float, Float> getSpawnDelayBounds(Random rand) {
                return Pair.of(0F, 0F);
            }

            @Override
            public boolean isConsecutive() {
                return true;
            }
        };

        static SpawnFunction delay(float delay) {
            return rand -> Pair.of(delay, delay);
        }

        static SpawnFunction noise(float delay, float noise) {
            return rand -> Pair.of(delay - noise, delay + noise);
        }

        Pair<Float, Float> getSpawnDelayBounds(Random rand);

        default float getSpawnDelay(Random rand) {
            Pair<Float, Float> bounds = getSpawnDelayBounds(rand);
            return bounds.getLeft() + (bounds.getRight() - bounds.getLeft()) * rand.nextFloat();
        }

        default boolean isConsecutive() {
            return false;
        }
    }

    public interface FadeFunction {

        FadeFunction NONE = (totalBolts, lifeScale) -> Pair.of(0, totalBolts);

        static FadeFunction fade(float fade) {
            return (totalBolts, lifeScale) -> {
                int start = lifeScale > (1 - fade) ? (int) (totalBolts * (lifeScale - (1 - fade)) / fade) : 0;
                int end = lifeScale < fade ? (int) (totalBolts * (lifeScale / fade)) : totalBolts;
                return Pair.of(start, end);
            };
        }

        Pair<Integer, Integer> getRenderBounds(int totalBolts, float lifeScale);
    }

    public static class BoltRenderInfo {

        public static final BoltRenderInfo DEFAULT = new BoltRenderInfo();
        public static final BoltRenderInfo ELECTRICITY = electricity();

        private float parallelNoise = 0.1F;
        private float spreadFactor = 0.1F;

        private float branchInitiationFactor = 0.0F;
        private float branchContinuationFactor = 0.0F;

        private Color color = Color.rgbad(0.45F, 0.45F, 0.5F, 0.8F);

        private RandomFunction randomFunction = RandomFunction.GAUSSIAN;
        private SpreadFunction spreadFunction = SpreadFunction.SINE;
        private SegmentSpreader segmentSpreader = SegmentSpreader.NO_MEMORY;

        public static BoltRenderInfo electricity() {
            return new BoltRenderInfo().color(Color.rgbad(0.54F, 0.91F, 1F, 0.8F))
                    .noise(0.2F, 0.2F)
                    .branching(0.1F, 0.6F)
                    .spreader(SegmentSpreader.memory(0.9F));
        }

        public BoltRenderInfo noise(float parallelNoise, float spreadFactor) {
            this.parallelNoise = parallelNoise;
            this.spreadFactor = spreadFactor;
            return this;
        }

        public BoltRenderInfo branching(float branchInitiationFactor, float branchContinuationFactor) {
            this.branchInitiationFactor = branchInitiationFactor;
            this.branchContinuationFactor = branchContinuationFactor;
            return this;
        }

        public BoltRenderInfo spreader(SegmentSpreader segmentSpreader) {
            this.segmentSpreader = segmentSpreader;
            return this;
        }

        public BoltRenderInfo randomFunction(RandomFunction randomFunction) {
            this.randomFunction = randomFunction;
            return this;
        }

        public BoltRenderInfo spreadFunction(SpreadFunction spreadFunction) {
            this.spreadFunction = spreadFunction;
            return this;
        }

        public BoltRenderInfo color(Color color) {
            this.color = color;
            return this;
        }
    }
}
