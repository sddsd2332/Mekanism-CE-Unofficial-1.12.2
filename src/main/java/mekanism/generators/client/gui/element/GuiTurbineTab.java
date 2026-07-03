package mekanism.generators.client.gui.element;

import mekanism.api.Coord4D;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiTabElementType;
import mekanism.client.gui.element.tab.TabType;
import mekanism.client.render.lib.ColorAtlas.ColorRegistryObject;
import mekanism.common.Mekanism;
import mekanism.common.base.IGuiProvider;
import mekanism.common.network.PacketSimpleGui;
import mekanism.common.network.PacketSimpleGui.SimpleGuiMessage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.client.gui.element.GuiTurbineTab.TurbineTab;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

public class GuiTurbineTab extends GuiTabElementType<TileEntityTurbineCasing, TurbineTab> {

    private static final ResourceLocation CHEMICALS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "chemicals.png");
    private static final ResourceLocation STATS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "stats.png");

    public GuiTurbineTab(IGuiWrapper gui, TileEntityTurbineCasing tile, TurbineTab type) {
        super(gui, tile, type);
    }

    public enum TurbineTab implements TabType<TileEntityTurbineCasing> {
        MAIN(CHEMICALS, 6, "gui.main", SpecialColors.TAB_MULTIBLOCK_MAIN),
        STAT(STATS, 7, "gui.turbineStats", SpecialColors.TAB_MULTIBLOCK_STATS);

        private final ResourceLocation resource;
        private final int guiId;
        private final String description;
        private final ColorRegistryObject color;

        TurbineTab(ResourceLocation resource, int guiId, String description, ColorRegistryObject color) {
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
        public void onClick(TileEntityTurbineCasing tile) {
            List<IGuiProvider> handlers = PacketSimpleGui.handlers;
            int handler = handlers.indexOf(MekanismGenerators.proxy);
            Mekanism.packetHandler.sendToServer(new SimpleGuiMessage(Coord4D.get(tile), handler, guiId));
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
