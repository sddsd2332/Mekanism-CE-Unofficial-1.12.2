package mekanism.client.render.tileentity;

import mekanism.api.Coord4D;
import mekanism.common.tile.TileEntityBin;
import mekanism.common.util.LangUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderBin extends TileEntitySpecialRenderer<TileEntityBin> {

    private final RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();

    @Override
    public void render(TileEntityBin tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        Coord4D obj = Coord4D.get(tileEntity).offset(tileEntity.facing);
        if (!obj.getBlockState(tileEntity.getWorld()).isSideSolid(tileEntity.getWorld(), obj.getPos(), tileEntity.facing.getOpposite())) {
            render(tileEntity.facing, tileEntity.itemType, tileEntity.clientAmount, true, x, y, z);
        }
    }

    public void render(EnumFacing facing, ItemStack itemType, int clientAmount, boolean text, double x, double y, double z) {
        if (!itemType.isEmpty()) {
            String amount = Integer.toString(clientAmount);
            if (clientAmount == Integer.MAX_VALUE) {
                amount = LangUtils.localize("gui.infinite");
            }
            setLightmapDisabled(true);
            GlStateManager.pushMatrix();
            switch (facing) {
                case NORTH:
                    GlStateManager.translate((float) x + 0.73F, (float) y + 0.83F, (float) z - 0.0001F);
                    break;
                case SOUTH:
                    GlStateManager.translate((float) x + 0.27F, (float) y + 0.83F, (float) z + 1.0001F);
                    GlStateManager.rotate(180, 0, 1, 0);
                    break;
                case WEST:
                    GlStateManager.translate((float) x - 0.0001F, (float) y + 0.83F, (float) z + 0.27F);
                    GlStateManager.rotate(90, 0, 1, 0);
                    break;
                case EAST:
                    GlStateManager.translate((float) x + 1.0001F, (float) y + 0.83F, (float) z + 0.73F);
                    GlStateManager.rotate(-90, 0, 1, 0);
                    break;
                default:
                    break;
            }

            float scale = 0.03125F;
            float scaler = 0.9F;
            GlStateManager.scale(scale * scaler, scale * scaler, -0.0001F);
            GlStateManager.rotate(180, 0, 0, 1);
            renderItem.renderItemAndEffectIntoGUI(itemType, 0, 0);
            GlStateManager.popMatrix();
            if (text) {
                renderText(amount, facing, 0.02F, x, y - 0.3725F, z);
            }
            setLightmapDisabled(false);
        }
    }

    @SuppressWarnings("incomplete-switch")
    private void renderText(String text, EnumFacing side, float maxScale, double x, double y, double z) {
        GlStateManager.pushMatrix();
        GlStateManager.doPolygonOffset(-10, -10);
        GlStateManager.enablePolygonOffset();
        float displayWidth = 1;
        float displayHeight = 1;
        GlStateManager.translate((float) x, (float) y, (float) z);

        switch (side) {
            case SOUTH:
                GlStateManager.translate(0, 1, 0);
                GlStateManager.rotate(0, 0, 1, 0);
                GlStateManager.rotate(90, 1, 0, 0);
                break;
            case NORTH:
                GlStateManager.translate(1, 1, 1);
                GlStateManager.rotate(180, 0, 1, 0);
                GlStateManager.rotate(90, 1, 0, 0);
                break;
            case EAST:
                GlStateManager.translate(0, 1, 1);
                GlStateManager.rotate(90, 0, 1, 0);
                GlStateManager.rotate(90, 1, 0, 0);
                break;
            case WEST:
                GlStateManager.translate(1, 1, 0);
                GlStateManager.rotate(-90, 0, 1, 0);
                GlStateManager.rotate(90, 1, 0, 0);
                break;
        }

        GlStateManager.translate(displayWidth / 2, 1F, displayHeight / 2);
        GlStateManager.rotate(-90, 1, 0, 0);

        FontRenderer fontRenderer = getFontRenderer();

        int requiredWidth = Math.max(fontRenderer.getStringWidth(text), 1);
        int requiredHeight = fontRenderer.FONT_HEIGHT + 2;
        float scaler = 0.4F;
        float scaleX = displayWidth / requiredWidth;
        float scale = scaleX * scaler;
        if (maxScale > 0) {
            scale = Math.min(scale, maxScale);
        }

        GlStateManager.scale(scale, -scale, scale);
        GlStateManager.depthMask(false);
        int realHeight = (int) Math.floor(displayHeight / scale);
        int realWidth = (int) Math.floor(displayWidth / scale);
        int offsetX = (realWidth - requiredWidth) / 2;
        int offsetY = (realHeight - requiredHeight) / 2;
        GlStateManager.disableLighting();
        fontRenderer.drawString("\u00a7f" + text, offsetX - (realWidth / 2), 1 + offsetY - (realHeight / 2), 1);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.disablePolygonOffset();
        GlStateManager.popMatrix();
    }
}
