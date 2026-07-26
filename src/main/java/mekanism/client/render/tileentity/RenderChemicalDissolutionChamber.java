package mekanism.client.render.tileentity;

import mekanism.api.gas.GasStack;
import mekanism.client.model.ModelChemicalDissolutionChamber;
import mekanism.client.render.GasRenderMap;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.DisplayInteger;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
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
public class RenderChemicalDissolutionChamber extends TileEntitySpecialRenderer<TileEntityChemicalDissolutionChamber> {

    public static final RenderChemicalDissolutionChamber INSTANCE = new RenderChemicalDissolutionChamber();

    private static GasRenderMap<DisplayInteger[]> cachedCenterGas = new GasRenderMap<>();

    private static final int stages = 500;

    private ModelChemicalDissolutionChamber model = new ModelChemicalDissolutionChamber();

    public static void resetDisplayInts() {
        cachedCenterGas.clear();
    }

    @Override
    public void render(TileEntityChemicalDissolutionChamber tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GasStack gasStack = tileEntity.outputTank.getGas();
        if (gasStack != null && gasStack.amount > 0 && gasStack.getGas() != null) {
            GlStateManager.pushMatrix();
            MekanismRenderer.GlowInfo glowInfo = null;
            try {
                GlStateManager.enableCull();
                GlStateManager.disableLighting();
                GlStateManager.shadeModel(GL11.GL_SMOOTH);
                GlStateManager.disableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                GlStateManager.translate((float) x, (float) y, (float) z);
                glowInfo = MekanismRenderer.enableGlow();
                DisplayInteger[] displayList = getListAndRender(gasStack);
                MekanismRenderer.color(gasStack);
                int stage = Math.max(0, Math.min(stages - 1, (int) (tileEntity.prevScale * (stages - 1))));
                displayList[stage].render();
            } finally {
                MekanismRenderer.resetColor();
                if (glowInfo != null) {
                    MekanismRenderer.disableGlow(glowInfo);
                }
                MekanismRenderer.resetBlockRenderState();
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
            bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "ChemicalDissolutionChamber.png"));
            MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
            GlStateManager.rotate(180, 0, 0, 1);
            model.render(0.0625F, true);
        } finally {
            MekanismRenderer.resetBlockRenderState();
            GlStateManager.popMatrix();
        }
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

        for (int i = 0; i < stages; i++) {
            displays[i] = DisplayInteger.createAndStart();
            try {
                toReturn.minZ = 0.125 + .01;
                toReturn.maxZ = 0.875 - .01;
                toReturn.minX = 0.125 + .01;
                toReturn.maxX = 0.875 - .01;
                toReturn.minY = 0.4375 + .01;
                toReturn.maxY = 0.4375 + ((float) i / stages) * 0.3125 - .01;

                MekanismRenderer.renderObject(toReturn);
            } finally {
                DisplayInteger.endList();
            }
        }
        cachedCenterGas.put(gasStack, displays);
        return displays;
    }
}
