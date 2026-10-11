package mekanism.generators.client.render.bloom;

import mekanism.common.util.BloomEffect;
import mekanism.generators.client.model.ModelHeatGenerator;
import mekanism.generators.client.render.RenderHeatGenerator;
import mekanism.generators.common.tile.TileEntityHeatGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BloomRenderHeatGenerator extends BloomEffect<TileEntityHeatGenerator> {

    private final TileEntityHeatGenerator tile;

    public BloomRenderHeatGenerator(TileEntityHeatGenerator tile) {
        super(tile, 180, 0, 270, 90);
        this.tile = tile;
    }

    @Override
    protected void RenderModelBloom() {
        RenderHeatGenerator renderer = (RenderHeatGenerator) TileEntityRendererDispatcher.instance.renderers.get(TileEntityHeatGenerator.class);
        ModelHeatGenerator model = renderer.getModel();
        model.renderBloom(0.0625F, tile.getActive(), Minecraft.getMinecraft().renderEngine);
    }
}
