package mekanism.multiblockmachine.client.render.block.machine;

import mekanism.client.Utils.RenderTileEntityTime;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.multiblockmachine.client.model.machine.ModelLargeChemicalInfuser;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderLargeChemicalInfuser extends RenderTileEntityTime<TileEntityLargeChemicalInfuser> {

    private ModelLargeChemicalInfuser model = new ModelLargeChemicalInfuser();

    @Override
    public void render(TileEntityLargeChemicalInfuser tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER_MACHINE, "ChemicalInfuser/ChemicalInfuser.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(getTime(), 0.0625F, tileEntity.getActive(), rendererDispatcher.renderEngine, tileEntity.getScaledGasTankLevel(), tileEntity.getScaledLeftTankGasLevel(), tileEntity.getScaledRightTankGasLevel(), true);
        GlStateManager.popMatrix();
    }

    public ModelLargeChemicalInfuser getModel() {
        return model;
    }

}
