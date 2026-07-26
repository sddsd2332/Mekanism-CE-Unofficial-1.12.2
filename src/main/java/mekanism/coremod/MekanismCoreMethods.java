package mekanism.coremod;



import mekanism.common.Mekanism;
import mekanism.client.render.OptifineRenderCompat;
import mekanism.common.config.MekanismConfig;
import mekanism.common.event.ItemGUIRenderEvent;
import mekanism.common.interfaces.IOcclusionCulling;
import mekanism.common.interfaces.IOverlayRenderAware;
import mekanism.common.interfaces.IRenderEffectIntoGUI;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nonnull;

public class MekanismCoreMethods {

    private static boolean occlusionCullingWasEnabled;
    private static boolean legacyOpenGlWarningLogged;

    public static void renderItemOverlayIntoGUI(@Nonnull ItemStack stack, int xPosition, int yPosition) {
        if (!stack.isEmpty()) {
            resetRenderState();

            if (stack.getItem() instanceof IOverlayRenderAware overlayRenderAware) {
                if (overlayRenderAware.renderItemOverlayIntoGUI(stack, xPosition, yPosition)){
                    resetRenderState();
                }
            }

            MinecraftForge.EVENT_BUS.post(new ItemGUIRenderEvent.Post(stack, xPosition, yPosition));
        }
    }

    private static void resetRenderState(){
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    public static void renderItemAndEffectIntoGUI(@Nonnull ItemStack stack, int xPosition, int yPosition) {
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof IRenderEffectIntoGUI effect) {
                effect.renderItemAndEffectIntoGUI(stack, xPosition, yPosition);
            }
            MinecraftForge.EVENT_BUS.post(new ItemGUIRenderEvent.Pre(stack, xPosition, yPosition));
        }
    }

    public static boolean shouldCullTileEntityForOcclusion(TileEntity tileEntity) {
        // The shadow map is a separate camera/pass. Culling it with the player's
        // view can remove the tile from the shadow atlas and produce missing shadows.
        // Keep this guard here as well as in the interface so overrides which do not
        // call IOcclusionCulling.super cannot bypass it.
        if (OptifineRenderCompat.isShadowPass()) {
            return false;
        }
        if (!(tileEntity instanceof IOcclusionCulling cullingTile)) {
            return false;
        }
        try {
            // Keep the injected dispatcher hook fail-open so the master option always disables
            // culling, even if an individual tile has an incorrect override.
            if (!MekanismConfig.current().client.GazeCullingTracking.val()) {
                if (occlusionCullingWasEnabled) {
                    IOcclusionCulling.cullingClearClientCaches();
                }
                occlusionCullingWasEnabled = false;
                return false;
            }
            occlusionCullingWasEnabled = true;
            if (MekanismConfig.current().client.GazeCullingOpenGLTracking.val() && !legacyOpenGlWarningLogged) {
                legacyOpenGlWarningLogged = true;
                Mekanism.logger.warn("GazeCullingOpenGLTracking is obsolete; tile occlusion culling now uses the CPU-only compatibility path.");
            }
            TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
            if (dispatcher != null && IOcclusionCulling.cullingIsBeyondRenderDistance(
                  tileEntity.getDistanceSq(dispatcher.entityX, dispatcher.entityY, dispatcher.entityZ),
                  tileEntity.getMaxRenderDistanceSquared())) {
                // Vanilla will reject this tile later in the same dispatcher method. Avoid doing
                // CPU rays before that distance check runs.
                return false;
            }
            return cullingTile.shouldCullForOcclusion();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
