package mekanism.client.render.tileentity;

import mekanism.client.render.FluidRenderer;
import mekanism.client.render.FluidRenderer.RenderData;
import mekanism.client.render.FluidRenderer.ValveRenderData;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.GlowInfo;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.tile.multiblock.TileEntityBoilerCasing;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class RenderThermoelectricBoiler extends TileEntitySpecialRenderer<TileEntityBoilerCasing> {

    private FluidStack STEAM = new FluidStack(FluidRegistry.getFluid("steam"), 1);
    private FluidStack WATER = new FluidStack(FluidRegistry.WATER, 1);

    @Override
    public void render(TileEntityBoilerCasing tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        if (tileEntity.clientHasStructure && tileEntity.isRendering && tileEntity.structure != null && tileEntity.structure.renderLocation != null && tileEntity.structure.upperRenderLocation != null) {
            FluidStack waterStored = tileEntity.structure.waterStored;
            boolean glChanged = false;
            if (waterStored != null && waterStored.amount != 0) {
                RenderData data = new RenderData();
                data.location = tileEntity.structure.renderLocation;
                data.height = tileEntity.structure.upperRenderLocation.y - 1 - tileEntity.structure.renderLocation.y;
                data.length = tileEntity.structure.volLength;
                data.width = tileEntity.structure.volWidth;
                data.fluidType = WATER;

                if (data.height >= 1 && waterStored.getFluid() != null) {
                    GlStateManager.pushMatrix();
                    glChanged = enableGL();
                    bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                    FluidRenderer.translateToOrigin(data.location);
                    GlowInfo glowInfo = MekanismRenderer.enableGlow(waterStored);
                    float waterScale = tileEntity.clientWaterCapacity <= 0 ? 0 : Math.min(1, waterStored.amount / (float) tileEntity.clientWaterCapacity);
                    MekanismRenderer.color(waterStored, waterScale);
                    if (waterStored.getFluid().isGaseous(waterStored)) {
                        FluidRenderer.getTankDisplay(data).render();
                    } else {
                        FluidRenderer.getTankDisplay(data, tileEntity.prevWaterScale).render();
                    }
                    MekanismRenderer.resetColor();
                    MekanismRenderer.disableGlow(glowInfo);
                    GlStateManager.popMatrix();

                    for (ValveData valveData : tileEntity.valveViewing) {
                        GlStateManager.pushMatrix();
                        glChanged = enableGL();
                        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                        FluidRenderer.translateToOrigin(valveData.location);
                        GlowInfo valveGlowInfo = MekanismRenderer.enableGlow(waterStored);
                        MekanismRenderer.color(waterStored);
                        FluidRenderer.getValveDisplay(ValveRenderData.get(data, valveData)).render();
                        MekanismRenderer.resetColor();
                        MekanismRenderer.disableGlow(valveGlowInfo);
                        GlStateManager.popMatrix();
                    }
                }
            }

            if (tileEntity.structure.steamStored != null && tileEntity.structure.steamStored.amount > 0) {
                RenderData data = new RenderData();
                data.location = tileEntity.structure.upperRenderLocation;
                data.height = tileEntity.structure.renderLocation.y + tileEntity.structure.volHeight - 2 - tileEntity.structure.upperRenderLocation.y;
                data.length = tileEntity.structure.volLength;
                data.width = tileEntity.structure.volWidth;
                data.fluidType = STEAM;

                if (data.height >= 1 && tileEntity.structure.steamStored.getFluid() != null) {
                    GlStateManager.pushMatrix();
                    glChanged = enableGL();
                    bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                    FluidRenderer.translateToOrigin(data.location);
                    GlowInfo glowInfo = MekanismRenderer.enableGlow(tileEntity.structure.steamStored);
                    float steamScale = tileEntity.clientSteamCapacity <= 0 ? 0 : Math.min(1, tileEntity.structure.steamStored.amount / (float) tileEntity.clientSteamCapacity);
                    MekanismRenderer.color(tileEntity.structure.steamStored, steamScale);
                    FluidRenderer.getTankDisplay(data).render();
                    MekanismRenderer.resetColor();
                    MekanismRenderer.disableGlow(glowInfo);
                    GlStateManager.popMatrix();
                }
            }

            if (glChanged) {
                setLightmapDisabled(false);
                MekanismRenderer.resetBlockRenderState();
            }
        }
    }

    private boolean enableGL() {
        GlStateManager.enableCull();
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
        setLightmapDisabled(true);
        return true;
    }
}
