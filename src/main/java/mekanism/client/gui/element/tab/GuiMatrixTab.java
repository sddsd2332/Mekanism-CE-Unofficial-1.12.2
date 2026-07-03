package mekanism.client.gui.element.tab;

import mekanism.api.Coord4D;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.lib.ColorAtlas.ColorRegistryObject;
import mekanism.common.Mekanism;
import mekanism.common.network.PacketSimpleGui.SimpleGuiMessage;
import mekanism.common.tile.multiblock.TileEntityInductionCasing;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class GuiMatrixTab extends GuiTabElementType<TileEntityInductionCasing, GuiMatrixTab.MatrixTab> {

    private static final ResourceLocation ENERGY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "energy.png");
    private static final ResourceLocation STATS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "stats.png");

    public GuiMatrixTab(IGuiWrapper gui, TileEntityInductionCasing tile, MatrixTab type) {
        super(gui, tile, type);
    }

    public enum MatrixTab implements TabType<TileEntityInductionCasing> {
        MAIN(ENERGY, 49, "gui.main", SpecialColors.TAB_MULTIBLOCK_MAIN),
        STAT(STATS, 50, "gui.matrixStats", SpecialColors.TAB_MULTIBLOCK_STATS);

        private final ResourceLocation resource;
        private final int guiId;
        private final String description;
        private final ColorRegistryObject color;

        MatrixTab(ResourceLocation resource, int guiId, String description, ColorRegistryObject color) {
            this.resource = resource;
            this.guiId = guiId;
            this.description = description;
            this.color = color;
        }

        @Override
        public ResourceLocation getResource() {
            return resource;
        }

        @Override
        public void onClick(TileEntityInductionCasing tile) {
            Mekanism.packetHandler.sendToServer(new SimpleGuiMessage(Coord4D.get(tile), 0, guiId));
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentString(LangUtils.localize(description));
        }

        @Override
        public ColorRegistryObject getTabColor() {
            return color;
        }
    }
}
