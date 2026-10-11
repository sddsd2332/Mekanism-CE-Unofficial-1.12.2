package mekanism.multiblockmachine.client.render.block.generator.bloom;

import mekanism.common.util.BloomEffect;
import mekanism.multiblockmachine.client.model.generator.ModelLargeGasGenerator;
import mekanism.multiblockmachine.client.render.block.generator.RenderLargeGasGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeGasGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BloomRendererLargeGasGenerator extends BloomEffect<TileEntityLargeGasGenerator> {


    private final TileEntityLargeGasGenerator gasGenerator;

    public BloomRendererLargeGasGenerator(TileEntityLargeGasGenerator gasGenerator) {
        super(gasGenerator, 0, 180, 90, 270);
        this.gasGenerator = gasGenerator;
    }

    @Override
    protected void RenderModelBloom() {
        RenderLargeGasGenerator renderer = (RenderLargeGasGenerator) TileEntityRendererDispatcher.instance.renderers.get(TileEntityLargeGasGenerator.class);
        ModelLargeGasGenerator model = renderer.getModel();
        model.renderBloom(renderer.getTime(), 0.0625F, tile.getActive(), Minecraft.getMinecraft().renderEngine);
    }


}
