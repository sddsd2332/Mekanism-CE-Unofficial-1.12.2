package mekanism.coremod;



import mekanism.common.event.ItemGUIRenderEvent;
import mekanism.common.config.MekanismConfig;
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
    private static boolean openGlOcclusionWasEnabled;

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
        if (!(tileEntity instanceof IOcclusionCulling cullingTile)) {
            return false;
        }
        try {
            // Keep the injected dispatcher hook fail-open so the master option always disables
            // culling, even if an individual tile has an incorrect override.
            if (!MekanismConfig.current().client.GazeCullingTracking.val()) {
                if (occlusionCullingWasEnabled || openGlOcclusionWasEnabled) {
                    IOcclusionCulling.cullingClearClientCaches();
                }
                occlusionCullingWasEnabled = false;
                openGlOcclusionWasEnabled = false;
                return false;
            }
            occlusionCullingWasEnabled = true;
            TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
            if (dispatcher != null && IOcclusionCulling.cullingIsBeyondRenderDistance(
                  tileEntity.getDistanceSq(dispatcher.entityX, dispatcher.entityY, dispatcher.entityZ),
                  tileEntity.getMaxRenderDistanceSquared())) {
                // Vanilla will reject this tile later in the same dispatcher method. Avoid doing
                // CPU rays or GPU queries before that distance check runs.
                return false;
            }
            boolean openGlEnabled = MekanismConfig.current().client.GazeCullingOpenGLTracking.val();
            if (openGlOcclusionWasEnabled && !openGlEnabled) {
                cullingTile.cullingClearGpuQueryCache();
            }
            openGlOcclusionWasEnabled = openGlEnabled;
            return cullingTile.shouldCullForOcclusion();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
