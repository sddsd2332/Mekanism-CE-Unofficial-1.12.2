package mekanism.client.render.tileentity;

import mekanism.api.gas.GasStack;
import mekanism.client.model.ModelNutritionalLiquifier;
import mekanism.client.render.GasRenderMap;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.DisplayInteger;
import mekanism.client.render.MekanismRenderer.GlowInfo;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.common.tile.machine.TileEntityNutritionalLiquifier;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class RenderNutritionalLiquifier extends TileEntitySpecialRenderer<TileEntityNutritionalLiquifier> {

    private static final float BASE_SPEED = 512F;
    public static final RenderNutritionalLiquifier INSTANCE = new RenderNutritionalLiquifier();

    private static GasRenderMap<DisplayInteger[]> cachedCenterGas = new GasRenderMap<>();

    private static final int stages = 64;

    private ModelNutritionalLiquifier model = new ModelNutritionalLiquifier();

    public static void resetDisplayInts() {
        cachedCenterGas.values().forEach(DisplayInteger::deleteAll);
        cachedCenterGas.clear();
    }

    @SuppressWarnings("incomplete-switch")
    @Override
    public void render(TileEntityNutritionalLiquifier tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        ItemStack stack = tileEntity.getStackInSlot(0);

        if (!stack.isEmpty() && tileEntity.gasTank.getStored() < tileEntity.gasTank.getMaxGas() / 2) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 0.5D, y + 0.65D, z + 0.5D);
            float scale = stack.getItem() instanceof ItemBlock ? 0.425F : 0.325F;
            GlStateManager.scale(scale, scale, scale);
            double tick = Minecraft.getSystemTime() / 800.0D;
            GlStateManager.translate(0.0D, Math.sin(tick % (2 * Math.PI)) * 0.065D, 0.0D);
            GlStateManager.rotate((float) (((tick * 40.0D) % 360)), 0, 1, 0);
            GlStateManager.disableLighting();
            GlStateManager.pushAttrib();
            RenderHelper.enableStandardItemLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Minecraft.getMinecraft().getRenderItem().renderItem(stack, TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popAttrib();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }

        if (tileEntity.gasTank.getStored() > 0) {
            GlStateManager.pushMatrix();
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.translate((float) x, (float) y, (float) z);
            GlowInfo glowInfo = MekanismRenderer.enableGlow();
            DisplayInteger[] displayList = getListAndRender(tileEntity.gasTank.getGas());
            MekanismRenderer.color(tileEntity.gasTank.getGas());
            displayList[Math.min(stages - 1, (int) (tileEntity.prevScale * ((float) stages - 1)))].render();
            MekanismRenderer.resetColor();
            MekanismRenderer.disableGlow(glowInfo);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            MekanismRenderer.resetBlockRenderState();
            GlStateManager.popMatrix();
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "NutritionalLiquifier.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(0.0625F,tileEntity.getActive());
        if (tileEntity.getActive()){
            double tick = Minecraft.getSystemTime() / 800.0D;
            GlStateManager.rotate((float) (((tick * 100.0D) % 360)), 0, 1, 0);
            model.renderBlade(0.0625F);
        }
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        model.renderGlass(0.0625F,true);
        GlStateManager.popMatrix();
        MekanismRenderer.machineRenderer().render(tileEntity, x, y, z, partialTick, destroyStage, alpha);
    }


    @SuppressWarnings("incomplete-switch")
    private DisplayInteger[] getListAndRender(GasStack gasStack) {
        if (cachedCenterGas.containsKey(gasStack)) {
            return cachedCenterGas.get(gasStack);
        }

        Model3D toReturn = new Model3D();
        toReturn.baseBlock = Blocks.WATER;
        toReturn.setTexture(gasStack.getGas().getSprite());
        DisplayInteger[] displays = new DisplayInteger[stages];
        cachedCenterGas.put(gasStack, displays);

        for (int i = 0; i < stages; i++) {
            displays[i] = DisplayInteger.createAndStart();

            toReturn.minZ = 0.0625 + .01;
            toReturn.maxZ = 0.9375 - .01;

            toReturn.minX = 0.0625 + .01;
            toReturn.maxX = 0.9375 - .01;

            toReturn.minY = 0.3125 + .01;
            toReturn.maxY = 0.3125 + ((float) i / stages) * 0.625 - .01;

            MekanismRenderer.renderObject(toReturn);
            DisplayInteger.endList();
        }
        return displays;
    }
}
