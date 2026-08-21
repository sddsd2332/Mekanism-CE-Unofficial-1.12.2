package mekanism.client.render.tileentity;

import mekanism.api.gas.GasStack;
import mekanism.client.model.ModelIsotopicCentrifuge;
import mekanism.client.render.GasRenderMap;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.DisplayInteger;
import mekanism.common.tile.machine.TileEntityIsotopicCentrifuge;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class RenderIsotopicCentrifuge extends TileEntitySpecialRenderer<TileEntityIsotopicCentrifuge> {

    public static final RenderIsotopicCentrifuge INSTANCE = new RenderIsotopicCentrifuge();

    private static GasRenderMap<MekanismRenderer.DisplayInteger[]> cachedCenterGas = new GasRenderMap<>();

    private ModelIsotopicCentrifuge model = new ModelIsotopicCentrifuge();

    private static final int stages = 64;

    public static void resetDisplayInts() {
        cachedCenterGas.values().forEach(DisplayInteger::deleteAll);
        cachedCenterGas.clear();
    }

    @Override
    public void render(TileEntityIsotopicCentrifuge tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        if (tileEntity.outputTank.getStored() > 0) {
            GlStateManager.pushMatrix();
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.translate((float) x, (float) y, (float) z);
            MekanismRenderer.GlowInfo glowInfo = MekanismRenderer.enableGlow();
            DisplayInteger[] displayList = getListAndRender(tileEntity.outputTank.getGas());
            MekanismRenderer.color(tileEntity.outputTank.getGas());
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
        bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "IsotopicCentrifuge.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(0.0625F,true);
        GlStateManager.popMatrix();
        MekanismRenderer.machineRenderer().render(tileEntity, x, y, z, partialTick, destroyStage, alpha);
    }

    private DisplayInteger[] getListAndRender(GasStack gasStack) {
        if (cachedCenterGas.containsKey(gasStack)) {
            return cachedCenterGas.get(gasStack);
        }

        MekanismRenderer.Model3D toReturn = new MekanismRenderer.Model3D();
        toReturn.baseBlock = Blocks.WATER;
        toReturn.setTexture(gasStack.getGas().getSprite());
        DisplayInteger[] displays = new DisplayInteger[stages];
        cachedCenterGas.put(gasStack, displays);

        for (int i = 0; i < stages; i++) {
            displays[i] = MekanismRenderer.DisplayInteger.createAndStart();
            toReturn.minZ = 0.325 + .01;;
            toReturn.maxZ = 0.7375 - .01;
            toReturn.minX = 0.325 + .01;;
            toReturn.maxX = 0.7375 - .01;
            toReturn.minY = 0.9375+ .01;;  //prevent z fighting at low fuel levels
            toReturn.maxY = 0.9375 + ((float) i / stages) * 0.75  - .01;
            MekanismRenderer.renderObject(toReturn);
            MekanismRenderer.DisplayInteger.endList();
        }
        return displays;
    }
}
