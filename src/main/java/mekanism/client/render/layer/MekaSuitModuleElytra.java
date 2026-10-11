package mekanism.client.render.layer;

import mekanism.api.gear.IModule;
import mekanism.client.model.mekasuitarmour.IMekaSuitBloomModel;
import mekanism.client.model.mekasuitarmour.ModuleElytraWing;
import mekanism.common.MekanismModules;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.content.gear.mekasuit.ModuleElytraUnit;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class MekaSuitModuleElytra implements LayerRenderer<EntityLivingBase> {

    /**
     * The basic Elytra texture.
     */
    private static final ResourceLocation MODULE_ELYTRA = MekanismUtils.getResource(MekanismUtils.ResourceType.RENDER, "MekAsuit.png");
    protected final RenderLivingBase<?> renderPlayer;
    private final ModuleElytraWing modelElytra = new ModuleElytraWing();

    public MekaSuitModuleElytra(RenderLivingBase<?> renderPlayer) {
        this.renderPlayer = renderPlayer;
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        IModule<ModuleElytraUnit> module = ModuleHelper.get().load(entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST), MekanismModules.ELYTRA_UNIT);
        if (entity.isElytraFlying() && module != null && module.isEnabled()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            if (entity instanceof AbstractClientPlayer player) {
                if (player.isPlayerInfoSet() && player.getLocationElytra() != null) {
                    renderPlayer.bindTexture(player.getLocationElytra());
                } else if (player.hasPlayerInfo() && player.getLocationCape() != null && player.isWearing(EnumPlayerModelParts.CAPE)) {
                    renderPlayer.bindTexture(player.getLocationCape());
                } else {
                    renderPlayer.bindTexture(MODULE_ELYTRA);
                }
            } else {
                renderPlayer.bindTexture(MODULE_ELYTRA);
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(0F, -0.25F, 0.125F);
            GlStateManager.rotate(-90, 1, 0, 0);
            modelElytra.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
            modelElytra.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            if (IMekaSuitBloomModel.shouldRenderDirectGlow()) {
                modelElytra.renderGlow(entity, scale);
            }
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }

    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
