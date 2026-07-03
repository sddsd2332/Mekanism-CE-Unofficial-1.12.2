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
import mekanism.generators.client.gui.element.GuiFusionReactorTab.FusionReactorTab;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

public class GuiFusionReactorTab extends GuiTabElementType<TileEntityReactorController, FusionReactorTab> {

    private static final ResourceLocation HEAT_ICON = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "heat.png");
    private static final ResourceLocation FUEL_ICON = new ResourceLocation("mekanismgenerators", "gui/fuel.png");
    private static final ResourceLocation STATS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "stats.png");

    public GuiFusionReactorTab(IGuiWrapper gui, TileEntityReactorController tile, FusionReactorTab type) {
        super(gui, tile, type);
    }

    public enum FusionReactorTab implements TabType<TileEntityReactorController> {
        HEAT(HEAT_ICON, 11, "gui.heat", 6, SpecialColors.TAB_HEAT_CONFIG),
        FUEL(FUEL_ICON, 12, "gui.fuel", 34, SpecialColors.TAB_GAS_CONFIG),
        STAT(STATS, 13, "gui.stats", 62, SpecialColors.TAB_MULTIBLOCK_STATS);

        private final ResourceLocation resource;
        private final int guiId;
        private final String description;
        private final int yPos;
        private final ColorRegistryObject color;

        FusionReactorTab(ResourceLocation resource, int guiId, String description, int yPos, ColorRegistryObject color) {
            this.resource = resource;
            this.guiId = guiId;
            this.description = description;
            this.yPos = yPos;
            this.color = color;
        }

        @Override
        public ResourceLocation getResource() {
            return resource;
        }

        @Override
        public void onClick(TileEntityReactorController tile) {
            List<IGuiProvider> handlers = PacketSimpleGui.handlers;
            int handler = handlers.indexOf(MekanismGenerators.proxy);
            Mekanism.packetHandler.sendToServer(new SimpleGuiMessage(Coord4D.get(tile), handler, guiId));
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentString(LangUtils.localize(description));
        }

        @Override
        public int getYPos() {
            return yPos;
        }

        @Override
        public ColorRegistryObject getTabColor() {
            return color;
        }
    }
}
