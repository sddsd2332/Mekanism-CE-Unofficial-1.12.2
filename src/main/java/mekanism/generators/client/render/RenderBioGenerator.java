package mekanism.generators.client.render;

import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.DisplayInteger;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.common.MekanismFluids;
import mekanism.generators.client.model.ModelBioGenerator;
import mekanism.generators.common.tile.TileEntityBioGenerator;
import mekanism.generators.common.util.MekanismGeneratorUtils;
import mekanism.generators.common.util.MekanismGeneratorUtils.ResourceType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.EnumMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class RenderBioGenerator extends TileEntitySpecialRenderer<TileEntityBioGenerator> {

    public static final RenderBioGenerator INSTANCE = new RenderBioGenerator();

    private static Map<EnumFacing, DisplayInteger[]> energyDisplays = new EnumMap<>(EnumFacing.class);

    private ModelBioGenerator model = new ModelBioGenerator();

    private static final int stages = TileEntityBioGenerator.RENDER_STAGES;

    public static void resetDisplayInts() {
        energyDisplays.values().forEach(DisplayInteger::deleteAll);
        energyDisplays.clear();
    }
    @Override
    public void render(TileEntityBioGenerator tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismGeneratorUtils.getResource(ResourceType.RENDER, "BioGenerator.png"));
        MekanismRenderer.rotate(tileEntity.facing, 180, 0, 270, 90);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(0.0625F);
        GlStateManager.popMatrix();
        if (tileEntity.bioFuelTank.getFluidAmount() > 0) {
            GlStateManager.pushMatrix();
            GlStateManager.enableCull();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.disableLighting();
            GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
            MekanismRenderer.GlowInfo glowInfo = MekanismRenderer.enableGlow();
            GlStateManager.translate((float) x, (float) y, (float) z);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            MekanismRenderer.color(MekanismFluids.Biofuel);
            DisplayInteger[] displayList = getDisplayList(tileEntity.facing);
            displayList[tileEntity.getScaledFuelLevel(stages - 1)].render();
            MekanismRenderer.resetColor();
            MekanismRenderer.disableGlow(glowInfo);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.disableCull();
            GlStateManager.popMatrix();
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismGeneratorUtils.getResource(ResourceType.RENDER, "BioGenerator.png"));
        MekanismRenderer.rotate(tileEntity.facing, 180, 0, 270, 90);
        GlStateManager.rotate(180, 0, 0, 1);
        model.renderGlass(0.0625F);
        GlStateManager.popMatrix();
    }

    @SuppressWarnings("incomplete-switch")
    private DisplayInteger[] getDisplayList(EnumFacing side) {
        if (energyDisplays.containsKey(side)) {
            return energyDisplays.get(side);
        }
        Model3D model3D = new Model3D();
        model3D.baseBlock = Blocks.WATER;
        model3D.setTexture(MekanismFluids.Biofuel.getSprite());
        DisplayInteger[] displays = new DisplayInteger[stages];
        energyDisplays.put(side,displays);
        for (int i = 0; i < stages; i++) {
            displays[i] = DisplayInteger.createAndStart();
            switch (side) {
                case NORTH:
                    model3D.minZ = 0.5;
                    model3D.maxZ = 0.875;

                    model3D.minX = 0.1875;
                    model3D.maxX = 0.8215;
                    break;
                case SOUTH:
                    model3D.minZ = 0.125;
                    model3D.maxZ = 0.5;

                    model3D.minX = 0.1875;
                    model3D.maxX = 0.8215;
                    break;
                case WEST:
                    model3D.minX = 0.5;
                    model3D.maxX = 0.875;

                    model3D.minZ = 0.1875;
                    model3D.maxZ = 0.8215;
                    break;
                case EAST:
                    model3D.minX = 0.125;
                    model3D.maxX = 0.5;

                    model3D.minZ = 0.1875;
                    model3D.maxZ = 0.8215;
                    break;
            }
            model3D.minY = 0.4375 + 0.001;  //prevent z fighting at low fuel levels
            model3D.maxY = 0.4375 + ((float) i / stages) * 0.4375 + 0.001;
            MekanismRenderer.renderObject(model3D);
            DisplayInteger.endList();
        }
        return displays;
    }
}
