package mekanism.generators.client.gui;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.LangUtils;
import mekanism.generators.client.gui.element.button.ReactorLogicButton;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorLogicAdapter;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorLogicAdapter.LogicMode;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Mouse;

public class GuiFissionReactorLogicAdapter extends GuiMekanismTile<TileEntityFissionReactorLogicAdapter, ContainerNull> {

    private static final int DISPLAY_COUNT = 4;

    private GuiScrollBar scrollBar;

    public GuiFissionReactorLogicAdapter(InventoryPlayer inventory, TileEntityFissionReactorLogicAdapter tile) {
        super(tile, new ContainerNull(inventory.player, tile));
        xSize += 20;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiElementHolder(this, 26, 31, 130, 90));
        scrollBar = addButton(new GuiScrollBar(this, 156, 31, 90, () -> LogicMode.values().length, () -> DISPLAY_COUNT));
        for (int i = 0; i < DISPLAY_COUNT; i++) {
            addButton(new ReactorLogicButton<>(this, 27, 32 + 22 * i, i, scrollBar::getCurrentSelection, LogicMode::values,
                  tileEntity::getLogicMode, mode -> new TextComponentString(EnumColor.WHITE + mode.getLabel()), LogicMode::getDescription, LogicMode::getRenderStack,
                  mode -> mode == LogicMode.DISABLED ? EnumColor.RED : EnumColor.DARK_GREEN, this::changeLogic));
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.fissionLogicAdapter")), 6);
        drawCenteredTextScaledBound(new TextComponentString(LangUtils.localize("fission.logic.mode") + ": " + EnumColor.RED + tileEntity.getLogicMode().getLabel()),
              xSize - 8, 123, titleTextColor());
        drawCenteredTextScaledBound(new TextComponentString(LangUtils.localize("gui.status") + ": " + EnumColor.RED + LangUtils.localize(tileEntity.getStatusTranslationKey())),
              xSize - 8, 136, titleTextColor());
        super.drawForegroundText(mouseX, mouseY);
    }

    private void changeLogic(LogicMode type) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0, type.ordinal())));
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0 && scrollBar != null) {
            scrollBar.adjustScroll(delta);
        }
    }
}
