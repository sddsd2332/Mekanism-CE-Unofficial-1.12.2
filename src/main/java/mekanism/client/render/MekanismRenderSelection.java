package mekanism.client.render;

import mekanism.common.block.BlockBounding;
import mekanism.common.block.interfaces.IHighlightBoxProvider;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class MekanismRenderSelection {

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public void renderSelection(DrawBlockHighlightEvent event) {
        if (!SelectionWireframeRenderer.isSelectionWireframeRenderingEnabled() || event.getSubID() != 0) {
            return;
        }
        RayTraceResult target = event.getTarget();
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        World world = player.world;
        BlockPos pos = target.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof BlockBounding) {
            BlockPos mainPos = BlockBounding.getMainBlockPos(world, pos);
            if (mainPos != null) {
                pos = mainPos;
                state = world.getBlockState(mainPos);
                block = state.getBlock();
            }
        }
        AxisAlignedBB[] boxes = new AxisAlignedBB[0];
        JsonModelSelectionBoxCache.OutlineBox[] wireframes = new JsonModelSelectionBoxCache.OutlineBox[0];
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;
        float alpha = 0.4F;
        if (block instanceof IHighlightBoxProvider provider) {
            boxes = provider.getHighlightBoxes(state, world, pos);
            red = provider.getHighlightRed(state, world, pos);
            green = provider.getHighlightGreen(state, world, pos);
            blue = provider.getHighlightBlue(state, world, pos);
            alpha = provider.getHighlightAlpha(state, world, pos);
        } else {
            wireframes = SpecialSelectionWireframeRegistry.getWireframes(state, world, pos);
            if (wireframes.length == 0) {
                wireframes = JsonModelSelectionBoxCache.getWireframes(state, world, pos);
            }
            if (wireframes.length == 0) {
                return;
            }
        }

        if (!event.isCanceled()) {
            event.setCanceled(true);
        }
        boolean keepVisibleInternalEdges = SelectionWireframeRenderer.keepVisibleInternalEdgesForState(state);
        drawAllSelectionBoxes(player, pos, boxes, wireframes, event.getPartialTicks(), red, green, blue, alpha, keepVisibleInternalEdges);
    }

    private void drawAllSelectionBoxes(EntityPlayer player, BlockPos blockPos, AxisAlignedBB[] boxes,
                                       JsonModelSelectionBoxCache.OutlineBox[] wireframes, float partialTicks,
                                       float red, float green, float blue, float alpha, boolean keepVisibleInternalEdges) {
        boolean depthDisabled = false;
        SelectionWireframeRenderer.begin(SelectionWireframeRenderer.getConfiguredLineWidth(), depthDisabled);
        try {
            double cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
            double cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks;
            double cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;

            if (wireframes != null && wireframes.length > 0) {
                if (SelectionWireframeRenderer.shouldAutoCycleWireframeColor()) {
                    int rgb = SelectionWireframeRenderer.getAutoCycleWireframeColorRGB();
                    red = SelectionWireframeRenderer.redFromRGB(rgb);
                    green = SelectionWireframeRenderer.greenFromRGB(rgb);
                    blue = SelectionWireframeRenderer.blueFromRGB(rgb);
                }
                SelectionWireframeRenderer.drawWireframes(wireframes, blockPos, cameraX, cameraY, cameraZ, red, green, blue, alpha, keepVisibleInternalEdges);
            } else {
                for (AxisAlignedBB box : boxes) {
                    AxisAlignedBB renderBox = box.offset(blockPos).grow(0.0020000000949949026D).offset(-cameraX, -cameraY, -cameraZ);
                    RenderGlobal.drawSelectionBoundingBox(renderBox, red, green, blue, alpha);
                }
            }
        } finally {
            SelectionWireframeRenderer.end(depthDisabled);
        }
    }
}
