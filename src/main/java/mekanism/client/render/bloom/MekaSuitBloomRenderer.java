package mekanism.client.render.bloom;

import gregtech.client.renderer.IRenderSetup;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.client.utils.EffectRenderContext;
import gregtech.client.utils.IBloomEffect;
import mekanism.api.gear.IModule;
import mekanism.client.model.mekasuitarmour.IMekaSuitBloomModel;
import mekanism.client.model.mekasuitarmour.ModuleElytraWing;
import mekanism.common.Mekanism;
import mekanism.common.MekanismModules;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.content.gear.mekasuit.ModuleElytraUnit;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.item.armor.ItemMekaSuitBodyArmor;
import mekanism.common.lib.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

@SideOnly(Side.CLIENT)
public class MekaSuitBloomRenderer implements IBloomEffect {

    private static final float MODEL_SCALE = 0.0625F;
    private static final ModelBiped ARMOR_MODEL = new ModelBiped(1.0F);
    private static final ModelBiped LEGGINGS_MODEL = new ModelBiped(0.5F);
    private static final ModuleElytraWing ELYTRA_MODEL = new ModuleElytraWing();
    private static final IRenderSetup RENDER_SETUP = new IRenderSetup() {
        @Override
        public void preDraw(@NotNull BufferBuilder bufferBuilder) {
        }

        @Override
        public void postDraw(@NotNull BufferBuilder bufferBuilder) {
            resetBloomRenderState();
        }
    };

    public MekaSuitBloomRenderer() {
        if (Mekanism.hooks.Bloom) {
            BloomEffectUtil.registerBloomRender(RENDER_SETUP, getBloomType(), this, ticket -> true);
        }
    }

    @Override
    public boolean shouldRenderBloomEffect(@NotNull EffectRenderContext context) {
        if (!IMekaSuitBloomModel.shouldUseBloom()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            return false;
        }
        for (AbstractClientPlayer player : minecraft.world.getPlayers(AbstractClientPlayer.class, MekaSuitBloomRenderer::shouldRenderBloom)) {
            if (shouldRenderBloomForCamera(player, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void renderBloomEffect(@NotNull BufferBuilder bufferBuilder, @NotNull EffectRenderContext context) {
        if (!IMekaSuitBloomModel.shouldUseBloom()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            return;
        }
        for (AbstractClientPlayer player : minecraft.world.getPlayers(AbstractClientPlayer.class, MekaSuitBloomRenderer::shouldRenderBloom)) {
            if (!shouldRenderBloomForCamera(player, context)) {
                continue;
            }
            Render<?> render = minecraft.getRenderManager().getEntityRenderObject(player);
            if (render instanceof RenderPlayer renderer) {
                renderPlayerBloom(player, renderer, context);
            }
        }
    }

    private static boolean shouldRenderBloom(AbstractClientPlayer player) {
        if (player == null || player.isSpectator() || player.isInvisible()) {
            return false;
        }
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (stack.getItem() instanceof ItemMekaSuitArmor) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldRenderBloomForCamera(AbstractClientPlayer player, EffectRenderContext context) {
        if (!shouldRenderBloom(player)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (player instanceof EntityPlayerSP && context.renderViewEntity() == player && minecraft.gameSettings.thirdPersonView == 0) {
            return false;
        }
        int customBloomRenderDistance = MekanismConfig.current().client.customBloomRenderDistance.val();
        return customBloomRenderDistance <= 0 || player.getDistanceSq(context.cameraX(), context.cameraY(), context.cameraZ()) < (double) customBloomRenderDistance * customBloomRenderDistance;
    }

    private static gregtech.client.shader.postprocessing.BloomType getBloomType() {
        return switch (MekanismConfig.current().client.customBloomStyle.val()) {
            case 1 -> gregtech.client.shader.postprocessing.BloomType.UNITY;
            case 2 -> gregtech.client.shader.postprocessing.BloomType.UNREAL;
            default -> gregtech.client.shader.postprocessing.BloomType.GAUSSIAN;
        };
    }

    private static void renderPlayerBloom(AbstractClientPlayer player, RenderPlayer renderer, EffectRenderContext context) {
        if (player == null || renderer == null || player.isSpectator()) {
            return;
        }
        int customBloomRenderDistance = MekanismConfig.current().client.customBloomRenderDistance.val();
        if (customBloomRenderDistance > 0 && player.getDistanceSq(context.cameraX(), context.cameraY(), context.cameraZ()) >= (double) customBloomRenderDistance * customBloomRenderDistance) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(false);

        float partialTick = context.partialTicks();
        boolean shouldSit = player.isRiding() && player.getRidingEntity() != null && player.getRidingEntity().shouldRiderSit();

        double x = interpolate(player.prevPosX, player.posX, partialTick) - context.cameraX();
        double y = interpolate(player.prevPosY, player.posY, partialTick) - context.cameraY();
        double z = interpolate(player.prevPosZ, player.posZ, partialTick) - context.cameraZ();
        if (player.isSneaking()) {
            y -= 0.125D;
        }

        float renderYawOffset = interpolateRotation(player.prevRenderYawOffset, player.renderYawOffset, partialTick);
        float rotationYawHead = interpolateRotation(player.prevRotationYawHead, player.rotationYawHead, partialTick);
        float netHeadYaw = rotationYawHead - renderYawOffset;
        if (shouldSit && player.getRidingEntity() instanceof EntityLivingBase livingBase) {
            renderYawOffset = interpolateRotation(livingBase.prevRenderYawOffset, livingBase.renderYawOffset, partialTick);
            netHeadYaw = rotationYawHead - renderYawOffset;
            float wrappedHeadYaw = MathHelper.wrapDegrees(netHeadYaw);
            if (wrappedHeadYaw < -85.0F) {
                wrappedHeadYaw = -85.0F;
            }
            if (wrappedHeadYaw >= 85.0F) {
                wrappedHeadYaw = 85.0F;
            }
            renderYawOffset = rotationYawHead - wrappedHeadYaw;
            if (wrappedHeadYaw * wrappedHeadYaw > 2500.0F) {
                renderYawOffset += wrappedHeadYaw * 0.2F;
            }
            netHeadYaw = rotationYawHead - renderYawOffset;
        }

        float headPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTick;
        float ageInTicks = player.ticksExisted + partialTick;
        float limbSwingAmount = 0.0F;
        float limbSwing = 0.0F;
        if (!player.isRiding()) {
            limbSwingAmount = player.prevLimbSwingAmount + (player.limbSwingAmount - player.prevLimbSwingAmount) * partialTick;
            limbSwing = player.limbSwing - player.limbSwingAmount * (1.0F - partialTick);
            if (player.isChild()) {
                limbSwing *= 3.0F;
            }
            if (limbSwingAmount > 1.0F) {
                limbSwingAmount = 1.0F;
            }
        }

        renderLivingAt(player, x, y, z);
        applyRotations(player, ageInTicks, renderYawOffset, partialTick);
        float scale = prepareScale();

        renderArmorBloom(player, EntityEquipmentSlot.CHEST, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, scale);
        renderElytraBloom(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        renderArmorBloom(player, EntityEquipmentSlot.LEGS, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, scale);
        renderArmorBloom(player, EntityEquipmentSlot.FEET, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, scale);
        renderArmorBloom(player, EntityEquipmentSlot.HEAD, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, scale);

        GlStateManager.disableRescaleNormal();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
        resetBloomRenderState();
    }

    private static void resetBloomRenderState() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.disableRescaleNormal();
        GlStateManager.depthMask(false);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        minecraft.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    private static void renderElytraBloom(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
          float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (!shouldRenderElytraBloom(player)) {
            return;
        }
        ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        setupBloomColor(chestStack);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, -0.25F, 0.125F);
        GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
        ELYTRA_MODEL.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, player);
        ELYTRA_MODEL.renderGlow(player, scale);
        GlStateManager.popMatrix();
        restoreBloomColor();
    }

    private static boolean shouldRenderElytraBloom(AbstractClientPlayer player) {
        if (player == null || !player.isElytraFlying()) {
            return false;
        }
        ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!(chestStack.getItem() instanceof ItemMekaSuitBodyArmor)) {
            return false;
        }
        IModule<ModuleElytraUnit> module = ModuleHelper.get().load(chestStack, MekanismModules.ELYTRA_UNIT);
        return module != null && module.isEnabled();
    }

    private static void renderArmorBloom(AbstractClientPlayer player, EntityEquipmentSlot slot, float limbSwing, float limbSwingAmount,
          float partialTick, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        ItemStack stack = player.getItemStackFromSlot(slot);
        if (!(stack.getItem() instanceof ItemArmor)) {
            return;
        }
        ModelBiped model = ForgeHooksClient.getArmorModel(player, stack, slot, getDefaultModel(slot));
        if (!(model instanceof IMekaSuitBloomModel bloomModel)) {
            return;
        }
        prepareArmorModel(player, model, partialTick);
        model.setLivingAnimations(player, limbSwing, limbSwingAmount, partialTick);
        setModelSlotVisible(model, slot);
        setupBloomColor(stack);
        bloomModel.renderBloom(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        restoreBloomColor();
    }

    private static void setupBloomColor(ItemStack stack) {
        Color color = getMekaSuitTint(stack);
        GlStateManager.disableBlend();
        GlStateManager.color(color.rf(), color.gf(), color.bf(), 1.0F);
    }

    private static void restoreBloomColor() {
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static Color getMekaSuitTint(ItemStack stack) {
        Color color = ItemMekaSuitArmor.getColorModulation(stack);
        if (color == null) {
            return Color.WHITE;
        }
        double tintStrength = color.ad();
        double tintBase = 1.0D - tintStrength;
        return Color.rgbd(color.rd() * tintStrength + tintBase, color.gd() * tintStrength + tintBase, color.bd() * tintStrength + tintBase);
    }

    private static ModelBiped getDefaultModel(EntityEquipmentSlot slot) {
        return slot == EntityEquipmentSlot.LEGS ? LEGGINGS_MODEL : ARMOR_MODEL;
    }

    private static void prepareArmorModel(AbstractClientPlayer player, ModelBiped model, float partialTick) {
        model.swingProgress = player.getSwingProgress(partialTick);
        model.isRiding = player.isRiding() && player.getRidingEntity() != null && player.getRidingEntity().shouldRiderSit();
        model.isChild = player.isChild();
        model.isSneak = player.isSneaking();
        model.rightArmPose = ModelBiped.ArmPose.EMPTY;
        model.leftArmPose = ModelBiped.ArmPose.EMPTY;

        ItemStack mainHand = player.getHeldItemMainhand();
        ItemStack offHand = player.getHeldItemOffhand();
        ModelBiped.ArmPose rightArmPose = getArmPose(player, mainHand);
        ModelBiped.ArmPose leftArmPose = getArmPose(player, offHand);
        if (player.getPrimaryHand() == EnumHandSide.RIGHT) {
            model.rightArmPose = rightArmPose;
            model.leftArmPose = leftArmPose;
        } else {
            model.rightArmPose = leftArmPose;
            model.leftArmPose = rightArmPose;
        }
    }

    private static ModelBiped.ArmPose getArmPose(AbstractClientPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return ModelBiped.ArmPose.EMPTY;
        }
        ModelBiped.ArmPose armPose = ModelBiped.ArmPose.ITEM;
        if (player.getItemInUseCount() > 0) {
            EnumAction action = stack.getItemUseAction();
            if (action == EnumAction.BLOCK) {
                armPose = ModelBiped.ArmPose.BLOCK;
            } else if (action == EnumAction.BOW) {
                armPose = ModelBiped.ArmPose.BOW_AND_ARROW;
            }
        }
        return armPose;
    }

    private static void setModelSlotVisible(ModelBiped model, EntityEquipmentSlot slot) {
        model.setVisible(false);
        switch (slot) {
            case HEAD:
                model.bipedHead.showModel = true;
                model.bipedHeadwear.showModel = true;
                break;
            case CHEST:
                model.bipedBody.showModel = true;
                model.bipedRightArm.showModel = true;
                model.bipedLeftArm.showModel = true;
                break;
            case LEGS:
                model.bipedBody.showModel = true;
                model.bipedRightLeg.showModel = true;
                model.bipedLeftLeg.showModel = true;
                break;
            case FEET:
                model.bipedRightLeg.showModel = true;
                model.bipedLeftLeg.showModel = true;
                break;
            default:
                break;
        }
    }

    private static void renderLivingAt(AbstractClientPlayer player, double x, double y, double z) {
        if (player.isEntityAlive() && player.isPlayerSleeping()) {
            GlStateManager.translate((float) (x + player.renderOffsetX), (float) (y + player.renderOffsetY), (float) (z + player.renderOffsetZ));
        } else {
            GlStateManager.translate((float) x, (float) y, (float) z);
        }
    }

    private static void applyRotations(AbstractClientPlayer player, float ageInTicks, float rotationYaw, float partialTick) {
        if (player.isEntityAlive() && player.isPlayerSleeping()) {
            GlStateManager.rotate(player.getBedOrientationInDegrees(), 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(270.0F, 0.0F, 1.0F, 0.0F);
        } else if (player.isElytraFlying()) {
            applyBaseRotations(player, rotationYaw, partialTick);
            float flightTicks = (float) player.getTicksElytraFlying() + partialTick;
            float flightProgress = MathHelper.clamp(flightTicks * flightTicks / 100.0F, 0.0F, 1.0F);
            GlStateManager.rotate(flightProgress * (-90.0F - player.rotationPitch), 1.0F, 0.0F, 0.0F);
            Vec3d look = player.getLook(partialTick);
            double horizontalMotion = player.motionX * player.motionX + player.motionZ * player.motionZ;
            double horizontalLook = look.x * look.x + look.z * look.z;
            if (horizontalMotion > 0.0D && horizontalLook > 0.0D) {
                double lookDot = (player.motionX * look.x + player.motionZ * look.z) / (Math.sqrt(horizontalMotion) * Math.sqrt(horizontalLook));
                double lookCross = player.motionX * look.z - player.motionZ * look.x;
                GlStateManager.rotate((float) (Math.signum(lookCross) * Math.acos(lookDot)) * 180.0F / (float) Math.PI, 0.0F, 1.0F, 0.0F);
            }
        } else {
            applyBaseRotations(player, rotationYaw, partialTick);
        }
    }

    private static void applyBaseRotations(AbstractClientPlayer player, float rotationYaw, float partialTick) {
        GlStateManager.rotate(180.0F - rotationYaw, 0.0F, 1.0F, 0.0F);
        if (player.deathTime > 0) {
            float deathRotation = ((float) player.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
            deathRotation = MathHelper.sqrt(deathRotation);
            if (deathRotation > 1.0F) {
                deathRotation = 1.0F;
            }
            GlStateManager.rotate(deathRotation * 90.0F, 0.0F, 0.0F, 1.0F);
        } else {
            String name = TextFormatting.getTextWithoutFormattingCodes(player.getName());
            if (name != null && ("Dinnerbone".equals(name) || "Grumm".equals(name)) && player.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.CAPE)) {
                GlStateManager.translate(0.0F, player.height + 0.1F, 0.0F);
                GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            }
        }
    }

    private static float prepareScale() {
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        GlStateManager.scale(0.9375F, 0.9375F, 0.9375F);
        GlStateManager.translate(0.0F, -1.501F, 0.0F);
        return MODEL_SCALE;
    }

    private static double interpolate(double prev, double current, float partialTick) {
        return prev + (current - prev) * partialTick;
    }

    private static float interpolateRotation(float prevYawOffset, float yawOffset, float partialTick) {
        float yaw = yawOffset - prevYawOffset;
        while (yaw < -180.0F) {
            yaw += 360.0F;
        }
        while (yaw >= 180.0F) {
            yaw -= 360.0F;
        }
        return prevYawOffset + partialTick * yaw;
    }
}
