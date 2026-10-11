package mekanism.client.render.bloom;

import mekanism.client.model.ModelDigitalMiner;
import mekanism.client.render.tileentity.RenderDigitalMiner;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.BloomEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BloomRenderDigitalMiner extends BloomEffect<TileEntityDigitalMiner> {
    private final TileEntityDigitalMiner tile;

    public BloomRenderDigitalMiner(TileEntityDigitalMiner tile) {
        super(tile, 0, 180, 90, 270);
        this.tile = tile;
    }

    @Override
    protected void RenderModelBloom() {
        GlStateManager.translate(0, 0, -1.0F);
        RenderDigitalMiner renderer = (RenderDigitalMiner) TileEntityRendererDispatcher.instance.renderers.get(TileEntityDigitalMiner.class);
        ModelDigitalMiner model = renderer.getModel();
        model.renderBloom(renderer.getTime(), 0.0625F, tile.getActive(), Minecraft.getMinecraft().renderEngine);
    }
}
