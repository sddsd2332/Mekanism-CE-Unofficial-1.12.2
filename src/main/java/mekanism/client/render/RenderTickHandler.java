package mekanism.client.render;

import mekanism.api.Coord4D;
import mekanism.api.MekanismAPI;
import mekanism.api.Pos3D;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.client.render.lib.effect.BoltRenderer;
import mekanism.client.render.particle.EntityJetpackFlameFX;
import mekanism.client.render.particle.EntityJetpackSmokeFX;
import mekanism.client.render.particle.EntityScubaBubbleFX;
import mekanism.common.CommonPlayerTickHandler;
import mekanism.common.Mekanism;
import mekanism.common.block.BlockBounding;
import mekanism.common.block.interfaces.IHighlightBoxProvider;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class RenderTickHandler {
    private static final int[][] OUTLINE_FACE_CORNERS = new int[][]{
            {0, 1, 3, 2}, // x-
            {4, 6, 7, 5}, // x+
            {0, 4, 5, 1}, // y-
            {2, 3, 7, 6}, // y+
            {0, 2, 6, 4}, // z-
            {1, 5, 7, 3}  // z+
    };
    private static final double WIREFRAME_OVERLAY_FACE_OFFSET = 0.0030D;
    private static final double AXIS_ALIGNED_OVERLAY_FACE_OFFSET = 0.0030D;
    private static final double MIN_NORMAL_LENGTH_SQ = 1.0E-12D;
    private static final double OVERLAY_AXIS_ALIGN_QUANTIZE_SCALE = 1_000_000D;

    public Random rand = new Random();
    public Minecraft mc = Minecraft.getMinecraft();
    private static final BoltRenderer BOLT_RENDERER = new BoltRenderer();

    public static double prevRadiation = 0;

    public static void renderBolt(Object renderer, BoltEffect bolt) {
        BOLT_RENDERER.update(renderer, bolt, MekanismRenderer.getPartialTick());
    }

    @SubscribeEvent
    public void filterTooltips(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof IModuleContainerItem containerItem) {
            containerItem.filterTooltips(stack, event.getToolTip());
        }
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Entity renderViewEntity = mc.getRenderViewEntity();
        if (renderViewEntity == null) {
            return;
        }
        double viewX = renderViewEntity.lastTickPosX + (renderViewEntity.posX - renderViewEntity.lastTickPosX) * event.getPartialTicks();
        double viewY = renderViewEntity.lastTickPosY + (renderViewEntity.posY - renderViewEntity.lastTickPosY) * event.getPartialTicks();
        double viewZ = renderViewEntity.lastTickPosZ + (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * event.getPartialTicks();
        if (BOLT_RENDERER.hasBoltsToRender()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-viewX, -viewY, -viewZ);
            BOLT_RENDERER.render(event.getPartialTicks());
            GlStateManager.popMatrix();
        }
        DimensionalStabilizerOverlayRenderer.render(mc, renderViewEntity, viewX, viewY, viewZ);
    }

    @SubscribeEvent
    public void tickEnd(RenderTickEvent event) {
        if (event.phase == Phase.END) {
            if (mc.player != null && mc.world != null && !mc.isGamePaused() && mc.playerController != null) {
                FontRenderer font = mc.fontRenderer;
                if (font == null) {
                    return;
                }

                EntityPlayer player = mc.player;
                World world = mc.player.world;
                RayTraceResult pos = player.rayTrace(40.0D, 1.0F);
                if (pos != null) {
                    Coord4D obj = new Coord4D(pos.getBlockPos(), world);
                    Block block = obj.getBlock(world);

                    if (block != null && MekanismAPI.debug && mc.currentScreen == null
                            && !mc.gameSettings.showDebugInfo) {
                        String tileDisplay = "";

                        if (obj.getTileEntity(world) != null) {
                            if (obj.getTileEntity(world).getClass() != null) {
                                tileDisplay = obj.getTileEntity(world).getClass().getSimpleName();
                            }
                        }

                        font.drawStringWithShadow("Block: " + block.getTranslationKey(), 1, 1, 0x404040);
                        font.drawStringWithShadow("Metadata: " + obj.getBlockState(world), 1, 10, 0x404040);
                        font.drawStringWithShadow("Location: " + MekanismUtils.getCoordDisplay(obj), 1, 19, 0x404040);
                        font.drawStringWithShadow("TileEntity: " + tileDisplay, 1, 28, 0x404040);
                        font.drawStringWithShadow("Side: " + pos.sideHit, 1, 37, 0x404040);
                    }
                }

                // Traverse a copy of jetpack state and do animations
                Mekanism.playerState.getActiveJetpacks().forEach(uuid -> {
                    EntityPlayer p = mc.world.getPlayerEntityByUUID(uuid);
                    if (p != null) {
                        Pos3D playerPos = new Pos3D(p).translate(0, p.getEyeHeight(), 0);
                        Vec3d playerMotion = new Vec3d(player.motionX, player.motionY, player.motionZ);
                        float random = (world.rand.nextFloat() - 0.5F) * 0.1F;
                        //This positioning code is somewhat cursed, but it seems to be mostly working and entity pose code seems cursed in general
                        float xRot;
                        if (p.isSneaking()) {
                            xRot = 20;
                            playerPos = playerPos.translate(0, 0.125, 0);
                        } else {
                            float f = p.getSwingProgress(event.renderTickTime);
                            if (p.isElytraFlying()) {
                                float f1 = (float) p.getTicksElytraFlying() + event.renderTickTime;
                                float f2 = clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
                                xRot = f2 * (-90.0F - p.rotationPitch);
                            } else {
                                float f3 = p.isInWater() ? -90.0F - p.rotationPitch : -90.0F;
                                xRot = lerp(f, 0.0F, f3);
                            }
                            xRot = -xRot;
                            Pos3D eyeAdjustments;
                            if (p.isElytraFlying() && (p != player || mc.gameSettings.thirdPersonView != 0)) {
                                eyeAdjustments = new Pos3D(0, p.getEyeHeight(), 0).rotatePitch(xRot).rotateYaw(p.rotationYaw);
                            }  /*else if (p.getSwingProgress()) {
                                eyeAdjustments = new Pos3D(0, p.getEyeHeight(), 0).rotatePitch(xRot).rotateYaw(p.rotationYaw).translate(0, 0.5, 0);
                            } */ else {
                                eyeAdjustments = new Pos3D(0, p.getEyeHeight(), 0).rotatePitch(xRot).rotateYaw(p.rotationYaw);
                            }
                            playerPos = new Pos3D(p.posX + eyeAdjustments.x, p.posY + eyeAdjustments.y, p.posZ + eyeAdjustments.z);
                        }
                        Pos3D vLeft = new Pos3D(-0.43, -0.55, -0.54).rotatePitch(xRot).rotateYaw(p.rotationYaw);
                        renderJetpackSmoke(world, playerPos.translate(vLeft).translate(playerMotion), vLeft.scale(0.2).translate(playerMotion).translate(vLeft.scale(random)));
                        Pos3D vRight = new Pos3D(0.43, -0.55, -0.54).rotatePitch(xRot).rotateYaw(p.rotationYaw);
                        renderJetpackSmoke(world, playerPos.translate(vRight).translate(playerMotion), vRight.scale(0.2).translate(playerMotion).translate(vRight.scale(random)));
                        Pos3D vCenter = new Pos3D((world.rand.nextFloat() - 0.5) * 0.4, -0.86, -0.30).rotatePitch(xRot).rotateYaw(p.rotationYaw);
                        renderJetpackSmoke(world, playerPos.translate(vCenter).translate(playerMotion), vCenter.scale(0.2).translate(playerMotion));
                    }
                });

                // Traverse a copy of gasmask state and do animations
                if (world.getTotalWorldTime() % 4 == 0) {
                    Mekanism.playerState.getActiveScubaMask().forEach(uuid -> {
                        EntityPlayer p = mc.world.getPlayerEntityByUUID(uuid);
                        if (p != null && p.isInWater()) {
                            Pos3D vec = new Pos3D(0.4, 0.4, 0.4).multiply(p.getLook(1)).translate(0, -0.2, 0);
                            Pos3D motion = vec.scale(0.2).translate(new Pos3D(p.motionX, p.motionY, p.motionZ));
                            Pos3D v = new Pos3D(p).translate(0, p.getEyeHeight(), 0).translate(vec);
                            spawnAndSetParticle(EnumParticleTypes.WATER_BUBBLE, world, v.x, v.y, v.z, motion.x, motion.y + 0.2, motion.z);
                        }
                    });

                    world.playerEntities.forEach(p -> {
                        if (!Mekanism.playerState.isFlamethrowerOn(p) && !p.isSwingInProgress) {
                            ItemStack currentItem = p.getHeldItemMainhand();
                            if (CommonPlayerTickHandler.hasFlamethrowerGas(currentItem)) {
                                Pos3D flameVec;
                                boolean rightHanded = p.getPrimaryHand() == EnumHandSide.RIGHT;
                                if (player == p && mc.gameSettings.thirdPersonView == 0) {
                                    flameVec = new Pos3D(1, 1, 1)
                                            .multiply(p.getLook(event.renderTickTime))
                                            .rotateYaw(rightHanded ? 15 : -15)
                                            .translate(0, p.getEyeHeight() - 0.1, 0);
                                } else {
                                    double flameXCoord = rightHanded ? -0.2 : 0.2;
                                    double flameYCoord = 1;
                                    double flameZCoord = 1.2;
                                    if (p.isSneaking()) {
                                        flameYCoord -= 0.65;
                                        flameZCoord -= 0.15;
                                    }
                                    flameVec = new Pos3D(flameXCoord, flameYCoord, flameZCoord).rotateYaw(p.rotationYaw);
                                }
                                Vec3d motion = new Vec3d(p.motionX, p.motionY, p.motionZ);
                                Vec3d flameMotion = new Vec3d(motion.x, p.onGround ? 0 : motion.y, motion.z);
                                Vec3d mergedVec = new Vec3d(p.posX, p.posY, p.posZ).add(flameVec);
                                spawnAndSetParticle(EnumParticleTypes.FLAME, world, mergedVec.x, mergedVec.y, mergedVec.z, flameMotion.x, flameMotion.y, flameMotion.z);
                            }
                        }
                    });
                }

                if (MekanismUtils.isPlayingMode(player)) {
                    if (player.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
                        IRadiationEntity c = player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
                        double radiation = c.getRadiation();
                        double severity = RadiationManager.RadiationScale.getScaledDoseSeverity(radiation) * 0.8;
                        if (prevRadiation < severity) {
                            prevRadiation = Math.min(severity, prevRadiation + 0.01);
                        }
                        if (prevRadiation > severity) {
                            prevRadiation = Math.max(severity, prevRadiation - 0.01);
                        }
                        if (severity > RadiationManager.BASELINE) {
                            int effect = (int) (prevRadiation * 255);
                            int color = (0x701E1E << 8) + effect;
                            MekanismRenderer.renderColorOverlay(0, 0, mc.displayWidth, mc.displayHeight, color);
                        }
                    }
                }
            }
        }
    }


    private void renderJetpackSmoke(World world, Vec3d pos, Vec3d motion) {
        spawnAndSetParticle(EnumParticleTypes.FLAME, world, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z);
        spawnAndSetParticle(EnumParticleTypes.SMOKE_NORMAL, world, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z);
    }


    public static float clamp(float a, float min, float max) {
        return a < min ? min : (Math.min(a, max));
    }

    public static float lerp(float pDelta, float pStart, float pEnd) {
        return pStart + pDelta * (pEnd - pStart);
    }

    public void spawnAndSetParticle(EnumParticleTypes s, World world, double x, double y, double z, double velX, double velY, double velZ) {
        Particle fx = null;
        if (s.equals(EnumParticleTypes.FLAME)) {
            fx = new EntityJetpackFlameFX(world, x, y, z, velX, velY, velZ);
        } else if (s.equals(EnumParticleTypes.SMOKE_NORMAL)) {
            fx = new EntityJetpackSmokeFX(world, x, y, z, velX, velY, velZ);
        } else if (s.equals(EnumParticleTypes.WATER_BUBBLE)) {
            fx = new EntityScubaBubbleFX(world, x, y, z, velX, velY, velZ);
        }
        mc.effectRenderer.addEffect(fx);
    }


    @SubscribeEvent(priority = EventPriority.HIGH)
    public void renderExtraBlockBreak(DrawBlockHighlightEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        ItemStack stack = player.getHeldItemMainhand();
        RayTraceResult rayTraceResult = event.getTarget();
        if (!stack.isEmpty() && stack.getItem() instanceof IBlastingItem tool) {
            event.setCanceled(true);
            if (event.getSubID() == 0 && rayTraceResult.typeOfHit.equals(RayTraceResult.Type.BLOCK)) {
                World world = player.getEntityWorld();
                BlockPos pos = rayTraceResult.getBlockPos();
                IBlockState blockState = world.getBlockState(pos);
                if (blockState.getBlock() instanceof BlockBounding) {
                    BlockPos mainPos = BlockBounding.getMainBlockPos(world, pos);
                    if (mainPos != null) {
                        pos = mainPos;
                        blockState = world.getBlockState(mainPos);
                    }
                }
                Map<BlockPos, IBlockState> blocks = tool.getBlastedBlocksForRendering(world, player, stack, pos, blockState);
                if (!blocks.isEmpty()) {
                    blocks.forEach((key, value) -> drawSelectionBox(player, rayTraceResult, key, value, event.getSubID(), event.getPartialTicks()));
                } else {
                    drawSelectionBox(player, rayTraceResult, pos, blockState, event.getSubID(), event.getPartialTicks());
                }
            }
        }
    }


    public void drawSelectionBox(EntityPlayer player, RayTraceResult movingObjectPositionIn, BlockPos blockpos, IBlockState iblockstate, int execute, float partialTicks) {
        if (execute == 0 && movingObjectPositionIn.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos renderPos = blockpos;
            IBlockState renderState = iblockstate;
            if (renderState.getBlock() instanceof BlockBounding) {
                BlockPos mainPos = BlockBounding.getMainBlockPos(player.world, renderPos);
                if (mainPos != null) {
                    renderPos = mainPos;
                    renderState = player.world.getBlockState(mainPos);
                }
            }
            if (renderState.getMaterial() == Material.AIR || !player.world.getWorldBorder().contains(renderPos)) {
                return;
            }
            boolean depthDisabled = false;
            SelectionWireframeRenderer.begin(SelectionWireframeRenderer.getConfiguredLineWidth(), depthDisabled);
            try {
                double d3 = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
                double d4 = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks;
                double d5 = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;
                // Blasting preview always uses animated color regardless of config.
                int rgb = SelectionWireframeRenderer.getAutoCycleWireframeColorRGB();
                float red = SelectionWireframeRenderer.redFromRGB(rgb);
                float green = SelectionWireframeRenderer.greenFromRGB(rgb);
                float blue = SelectionWireframeRenderer.blueFromRGB(rgb);
                AxisAlignedBB[] boxes = new AxisAlignedBB[0];
                JsonModelSelectionBoxCache.OutlineBox[] wireframes = new JsonModelSelectionBoxCache.OutlineBox[0];
                if (renderState.getBlock() instanceof IHighlightBoxProvider provider) {
                    boxes = provider.getHighlightBoxes(renderState, player.world, renderPos);
                } else {
                    wireframes = SpecialSelectionWireframeRegistry.getWireframes(renderState, player.world, renderPos);
                    if (wireframes.length == 0) {
                        wireframes = JsonModelSelectionBoxCache.getWireframes(renderState, player.world, renderPos);
                    }
                    if (wireframes.length == 0) {
                        boxes = JsonModelSelectionBoxCache.getBoxes(renderState, player.world, renderPos);
                    }
                }
                if (wireframes.length > 0) {
                    drawWireframeFilledOverlay(wireframes, renderPos, d3, d4, d5, red, green, blue, 0.390625F);
                    boolean keepVisibleInternalEdges = SelectionWireframeRenderer.keepVisibleInternalEdgesForState(renderState);
                    SelectionWireframeRenderer.drawWireframes(wireframes, renderPos, d3, d4, d5, red, green, blue, 0.4F, keepVisibleInternalEdges);
                    return;
                }
                if (boxes != null && boxes.length > 0) {
                    for (AxisAlignedBB box : boxes) {
                        AxisAlignedBB renderBox = box.offset(renderPos).grow(0.0020000000949949026D).offset(-d3, -d4, -d5);
                        drawSelectionBoundingBox(renderBox, red, green, blue, 0.390625F); //draw Blinking Block
                        RenderGlobal.drawSelectionBoundingBox(renderBox, red, green, blue, 0.4F); //draw Outlined Bounding Box
                    }
                    return;
                }
                AxisAlignedBB selectedBox = renderState.getSelectedBoundingBox(player.world, renderPos).grow(0.0020000000949949026D).offset(-d3, -d4, -d5);
                drawSelectionBoundingBox(selectedBox, red, green, blue, 0.390625F); //draw Blinking Block
                RenderGlobal.drawSelectionBoundingBox(selectedBox, red, green, blue, 0.4F); //draw Outlined Bounding Box
            } finally {
                SelectionWireframeRenderer.end(depthDisabled);
            }
        }
    }

    private void drawWireframeFilledOverlay(JsonModelSelectionBoxCache.OutlineBox[] wireframes, BlockPos blockPos,
                                            double cameraX, double cameraY, double cameraZ,
                                            float red, float green, float blue, float alpha) {
        if (wireframes == null || wireframes.length == 0) {
            return;
        }
        float pulsedAlpha = alpha * (float) Math.abs(Math.sin((double) Minecraft.getSystemTime() / 100.0D * 0.3D));
        if (pulsedAlpha <= 0.0F) {
            return;
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = null;
        boolean startedQuadBatch = false;
        for (JsonModelSelectionBoxCache.OutlineBox wireframe : wireframes) {
            if (wireframe == null) {
                continue;
            }
            Vec3d[] corners = wireframe.getCorners();
            if (corners == null || corners.length != 8) {
                continue;
            }
            if (isAxisAlignedOutline(corners)) {
                AxisAlignedBB bounds = wireframe.getBounds();
                if (bounds != null) {
                    if (!startedQuadBatch) {
                        buffer = tessellator.getBuffer();
                        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                        startedQuadBatch = true;
                    }
                    addAxisAlignedOverlayFaces(buffer, bounds, blockPos, cameraX, cameraY, cameraZ, red, green, blue, pulsedAlpha);
                    continue;
                }
            }
            if (!startedQuadBatch) {
                buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                startedQuadBatch = true;
            }
            Vec3d boxCenter = getBoxCenter(corners);
            for (int[] face : OUTLINE_FACE_CORNERS) {
                Vec3d v0 = corners[face[0]];
                Vec3d v1 = corners[face[1]];
                Vec3d v2 = corners[face[2]];
                Vec3d v3 = corners[face[3]];
                Vec3d normal = getOutwardFaceNormal(v0, v1, v2, v3, boxCenter);
                for (int cornerIndex : face) {
                    addFilledOverlayVertex(buffer, corners[cornerIndex], normal, blockPos, cameraX, cameraY, cameraZ, red, green, blue, pulsedAlpha);
                }
            }
        }
        if (startedQuadBatch) {
            tessellator.draw();
        }
    }

    private boolean isAxisAlignedOutline(Vec3d[] corners) {
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
            uniqueX.add(quantizeOverlayCoordinate(corner.x));
            uniqueY.add(quantizeOverlayCoordinate(corner.y));
            uniqueZ.add(quantizeOverlayCoordinate(corner.z));
        }
        return uniqueX.size() == 2 && uniqueY.size() == 2 && uniqueZ.size() == 2;
    }

    private long quantizeOverlayCoordinate(double value) {
        return Math.round(value * OVERLAY_AXIS_ALIGN_QUANTIZE_SCALE);
    }

    private void addAxisAlignedOverlayFaces(BufferBuilder buffer, AxisAlignedBB bounds, BlockPos blockPos,
                                            double cameraX, double cameraY, double cameraZ,
                                            float red, float green, float blue, float alpha) {
        double minX = bounds.minX - AXIS_ALIGNED_OVERLAY_FACE_OFFSET;
        double minY = bounds.minY - AXIS_ALIGNED_OVERLAY_FACE_OFFSET;
        double minZ = bounds.minZ - AXIS_ALIGNED_OVERLAY_FACE_OFFSET;
        double maxX = bounds.maxX + AXIS_ALIGNED_OVERLAY_FACE_OFFSET;
        double maxY = bounds.maxY + AXIS_ALIGNED_OVERLAY_FACE_OFFSET;
        double maxZ = bounds.maxZ + AXIS_ALIGNED_OVERLAY_FACE_OFFSET;

        // x-
        addFilledOverlayVertex(buffer, minX, minY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, minX, minY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, minX, maxY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, minX, maxY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);

        // x+
        addFilledOverlayVertex(buffer, maxX, minY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, maxY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, maxY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, minY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);

        // y-
        addFilledOverlayVertex(buffer, minX, minY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, minY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, minY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, minX, minY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);

        // y+
        addFilledOverlayVertex(buffer, minX, maxY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, minX, maxY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, maxY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, maxY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);

        // z-
        addFilledOverlayVertex(buffer, minX, minY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, minX, maxY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, maxY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, minY, minZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);

        // z+
        addFilledOverlayVertex(buffer, minX, minY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, minY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, maxX, maxY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
        addFilledOverlayVertex(buffer, minX, maxY, maxZ, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha);
    }

    private Vec3d getBoxCenter(Vec3d[] corners) {
        double centerX = 0.0D;
        double centerY = 0.0D;
        double centerZ = 0.0D;
        for (Vec3d corner : corners) {
            centerX += corner.x;
            centerY += corner.y;
            centerZ += corner.z;
        }
        return new Vec3d(centerX / corners.length, centerY / corners.length, centerZ / corners.length);
    }

    private Vec3d getOutwardFaceNormal(Vec3d v0, Vec3d v1, Vec3d v2, Vec3d v3, Vec3d boxCenter) {
        Vec3d edge1 = v1.subtract(v0);
        Vec3d edge2 = v2.subtract(v0);
        Vec3d normal = edge1.crossProduct(edge2);
        double lengthSq = normal.x * normal.x + normal.y * normal.y + normal.z * normal.z;
        if (lengthSq < MIN_NORMAL_LENGTH_SQ) {
            return Vec3d.ZERO;
        }
        normal = normal.scale(1.0D / Math.sqrt(lengthSq));
        Vec3d faceCenter = new Vec3d(
                (v0.x + v1.x + v2.x + v3.x) * 0.25D,
                (v0.y + v1.y + v2.y + v3.y) * 0.25D,
                (v0.z + v1.z + v2.z + v3.z) * 0.25D
        );
        Vec3d toFace = faceCenter.subtract(boxCenter);
        if (normal.dotProduct(toFace) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        return normal;
    }

    private void addFilledOverlayVertex(BufferBuilder buffer, Vec3d vertex, Vec3d normal, BlockPos blockPos,
                                        double cameraX, double cameraY, double cameraZ,
                                        float red, float green, float blue, float alpha) {
        double x = vertex.x + normal.x * WIREFRAME_OVERLAY_FACE_OFFSET + blockPos.getX() - cameraX;
        double y = vertex.y + normal.y * WIREFRAME_OVERLAY_FACE_OFFSET + blockPos.getY() - cameraY;
        double z = vertex.z + normal.z * WIREFRAME_OVERLAY_FACE_OFFSET + blockPos.getZ() - cameraZ;
        buffer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private void addFilledOverlayVertex(BufferBuilder buffer, double x, double y, double z, BlockPos blockPos,
                                        double cameraX, double cameraY, double cameraZ,
                                        float red, float green, float blue, float alpha) {
        double px = x + blockPos.getX() - cameraX;
        double py = y + blockPos.getY() - cameraY;
        double pz = z + blockPos.getZ() - cameraZ;
        buffer.pos(px, py, pz).color(red, green, blue, alpha).endVertex();
    }

    private void drawSelectionBoundingBox(AxisAlignedBB box, float red, float green, float blue, float alpha) {
        drawBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
    }

    public static void drawBoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float red, float green, float blue, float alpha) {
        Tessellator tessellator = Tessellator.getInstance();
        alpha *= (float) Math.abs(Math.sin((double) Minecraft.getSystemTime() / 100.0 * 0.3D));
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(5, DefaultVertexFormats.POSITION_COLOR);
        drawBoundingBox(bufferbuilder, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        tessellator.draw();
    }

    public static void drawBoundingBox(BufferBuilder buffer, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float red, float green, float blue, float alpha) {
        buffer.pos(minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
    }

}
