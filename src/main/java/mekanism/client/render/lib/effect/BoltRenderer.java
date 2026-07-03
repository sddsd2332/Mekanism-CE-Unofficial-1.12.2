package mekanism.client.render.lib.effect;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.RenderState;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.lib.effect.BoltEffect.BoltQuads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.*;

public class BoltRenderer {

    private static final float REFRESH_TIME = 3F;
    private static final double MAX_OWNER_TRACK_TIME = 100;

    private Timestamp refreshTimestamp = new Timestamp();

    private final Random random = new Random();
    private final Minecraft minecraft = Minecraft.getMinecraft();

    private final Map<Object, BoltOwnerData> boltOwners = new Object2ObjectOpenHashMap<>();

    public boolean hasBoltsToRender() {
        synchronized (boltOwners) {
            return boltOwners.values().stream().anyMatch(data -> !data.bolts.isEmpty());
        }
    }

    public void renderGui(float partialTicks) {
        render(partialTicks);
    }

    public void render(float partialTicks) {
        if (minecraft.world == null) {
            return;
        }
        Timestamp timestamp = new Timestamp(minecraft.world.getTotalWorldTime(), partialTicks);
        boolean refresh = timestamp.isPassed(refreshTimestamp, 1 / REFRESH_TIME);
        if (refresh) {
            refreshTimestamp = timestamp;
        }

        Tessellator tessellator = Tessellator.getInstance();
        RenderState previousState = MekanismRenderer.pauseRenderer(tessellator);
        BufferBuilder buffer = tessellator.getBuffer();
        GLState previousGlState = GLState.capture();

        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            synchronized (boltOwners) {
                for (Iterator<Map.Entry<Object, BoltOwnerData>> iter = boltOwners.entrySet().iterator(); iter.hasNext(); ) {
                    Map.Entry<Object, BoltOwnerData> entry = iter.next();
                    BoltOwnerData data = entry.getValue();
                    if (refresh) {
                        data.bolts.removeIf(bolt -> bolt.tick(timestamp));
                    }
                    if (data.bolts.isEmpty() && data.lastBolt != null && data.lastBolt.getSpawnFunction().isConsecutive()) {
                        data.addBolt(new BoltInstance(data.lastBolt, timestamp), timestamp);
                    }
                    data.bolts.forEach(bolt -> bolt.render(buffer, timestamp));

                    if (data.bolts.isEmpty() && timestamp.isPassed(data.lastUpdateTimestamp, MAX_OWNER_TRACK_TIME)) {
                        iter.remove();
                    }
                }
            }
            tessellator.draw();
        } finally {
            previousGlState.restore();
            MekanismRenderer.resumeRenderer(tessellator, previousState);
        }
    }

    public void update(Object owner, BoltEffect newBoltData, float partialTicks) {
        if (minecraft.world == null) {
            return;
        }
        synchronized (boltOwners) {
            BoltOwnerData data = boltOwners.computeIfAbsent(owner, o -> new BoltOwnerData());
            data.lastBolt = newBoltData;
            Timestamp timestamp = new Timestamp(minecraft.world.getTotalWorldTime(), partialTicks);
            if ((!data.lastBolt.getSpawnFunction().isConsecutive() || data.bolts.isEmpty()) && timestamp.isPassed(data.lastBoltTimestamp, data.lastBoltDelay)) {
                data.addBolt(new BoltInstance(newBoltData, timestamp), timestamp);
            }
            data.lastUpdateTimestamp = timestamp;
        }
    }

    private static class GLState {

        private final boolean texture2D;
        private final boolean lighting;
        private final boolean blend;
        private final boolean cull;
        private final boolean depthMask;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;

        private GLState(boolean texture2D, boolean lighting, boolean blend, boolean cull, boolean depthMask, int blendSrcRgb, int blendDstRgb,
              int blendSrcAlpha, int blendDstAlpha) {
            this.texture2D = texture2D;
            this.lighting = lighting;
            this.blend = blend;
            this.cull = cull;
            this.depthMask = depthMask;
            this.blendSrcRgb = blendSrcRgb;
            this.blendDstRgb = blendDstRgb;
            this.blendSrcAlpha = blendSrcAlpha;
            this.blendDstAlpha = blendDstAlpha;
        }

        private static GLState capture() {
            return new GLState(GL11.glIsEnabled(GL11.GL_TEXTURE_2D), GL11.glIsEnabled(GL11.GL_LIGHTING), GL11.glIsEnabled(GL11.GL_BLEND),
                  GL11.glIsEnabled(GL11.GL_CULL_FACE), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                  GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        }

        private void restore() {
            setTexture2D(texture2D);
            setLighting(lighting);
            setBlend(blend);
            setCull(cull);
            GlStateManager.depthMask(depthMask);
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            MekanismRenderer.resetColor();
        }

        private static void setTexture2D(boolean enabled) {
            if (enabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
        }

        private static void setLighting(boolean enabled) {
            if (enabled) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        }

        private static void setBlend(boolean enabled) {
            if (enabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
        }

        private static void setCull(boolean enabled) {
            if (enabled) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
        }
    }

    public static class Timestamp {

        private final long ticks;
        private final float partial;

        public Timestamp() {
            this(0, 0);
        }

        public Timestamp(long ticks, float partial) {
            this.ticks = ticks;
            this.partial = partial;
        }

        public Timestamp subtract(Timestamp other) {
            long newTicks = ticks - other.ticks;
            float newPartial = partial - other.partial;
            if (newPartial < 0) {
                newPartial += 1;
                newTicks -= 1;
            }
            return new Timestamp(newTicks, newPartial);
        }

        public float value() {
            return ticks + partial;
        }

        public boolean isPassed(Timestamp prev, double duration) {
            long ticksPassed = ticks - prev.ticks;
            if (ticksPassed > duration) {
                return true;
            }
            duration -= ticksPassed;
            if (duration >= 1) {
                return false;
            }
            return (partial - prev.partial) >= duration;
        }
    }

    public class BoltOwnerData {

        private final Set<BoltInstance> bolts = new ObjectOpenHashSet<>();
        private BoltEffect lastBolt;
        private Timestamp lastBoltTimestamp = new Timestamp();
        private Timestamp lastUpdateTimestamp = new Timestamp();
        private double lastBoltDelay;

        private void addBolt(BoltInstance instance, Timestamp timestamp) {
            bolts.add(instance);
            lastBoltDelay = instance.bolt.getSpawnFunction().getSpawnDelay(random);
            lastBoltTimestamp = timestamp;
        }
    }

    public static class BoltInstance {

        private final BoltEffect bolt;
        private final List<BoltQuads> renderQuads;
        private final Timestamp createdTimestamp;

        public BoltInstance(BoltEffect bolt, Timestamp timestamp) {
            this.bolt = bolt;
            this.renderQuads = bolt.generate();
            this.createdTimestamp = timestamp;
        }

        public void render(BufferBuilder buffer, Timestamp timestamp) {
            float lifeScale = timestamp.subtract(createdTimestamp).value() / bolt.getLifespan();
            Pair<Integer, Integer> bounds = bolt.getFadeFunction().getRenderBounds(renderQuads.size(), lifeScale);
            for (int i = bounds.getLeft(); i < bounds.getRight(); i++) {
                renderQuads.get(i).getVecs().forEach(v -> buffer.pos(v.x, v.y, v.z)
                      .color(bolt.getColor().r(), bolt.getColor().g(), bolt.getColor().b(), bolt.getColor().a())
                      .endVertex());
            }
        }

        public boolean tick(Timestamp timestamp) {
            return timestamp.isPassed(createdTimestamp, bolt.getLifespan());
        }
    }
}
