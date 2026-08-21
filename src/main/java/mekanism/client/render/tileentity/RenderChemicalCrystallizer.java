package mekanism.client.render.tileentity;

import mekanism.api.gas.GasStack;
import mekanism.client.model.ModelChemicalCrystallizer;
import mekanism.client.render.GasRenderMap;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.DisplayInteger;
import mekanism.client.render.MekanismRenderer.GlowInfo;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.common.tile.machine.TileEntityChemicalCrystallizer;
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
public class RenderChemicalCrystallizer extends TileEntitySpecialRenderer<TileEntityChemicalCrystallizer> {

    public static final RenderChemicalCrystallizer INSTANCE = new RenderChemicalCrystallizer();

    private static GasRenderMap<DisplayInteger[]> cachedCenterGas = new GasRenderMap<>();

    private static final int stages = 64;
    private ModelChemicalCrystallizer model = new ModelChemicalCrystallizer();

    public static void resetDisplayInts() {
        cachedCenterGas.values().forEach(DisplayInteger::deleteAll);
        cachedCenterGas.clear();
    }

    @Override
    public void render(TileEntityChemicalCrystallizer tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {

        if (tileEntity.inputTank.getStored() > 0) {
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
            DisplayInteger[] displayList = getListAndRender(tileEntity.inputTank.getGas());
            MekanismRenderer.color(tileEntity.inputTank.getGas());
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
        bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "ChemicalCrystallizer.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(0.0625F,true);
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
            toReturn.minZ = 0.125 + .01;
            toReturn.maxZ = 0.875 - .01;
            toReturn.minX = 0.125 + .01;
            toReturn.maxX = 0.875 - .01;
            toReturn.minY = 0.4375 + .01;  //prevent z fighting at low fuel levels
            toReturn.maxY = 0.4375 + ((float) i / stages) * 0.25 - .01;

            MekanismRenderer.renderObject(toReturn);
            DisplayInteger.endList();
        }
        return displays;
    }
}
