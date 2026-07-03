package mekanism.generators.client.gui;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.ToggleButton;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.LangUtils;
import mekanism.generators.client.gui.element.button.ReactorLogicButton;
import mekanism.generators.common.tile.reactor.TileEntityReactorLogicAdapter;
import mekanism.generators.common.tile.reactor.TileEntityReactorLogicAdapter.ReactorLogic;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Mouse;

public class GuiFusionReactorLogicAdapter extends GuiMekanismTile<TileEntityReactorLogicAdapter, ContainerNull> {

    private static final int DISPLAY_COUNT = 4;

    private GuiScrollBar scrollBar;

    public GuiFusionReactorLogicAdapter(InventoryPlayer inventory, TileEntityReactorLogicAdapter tile) {
        super(tile, new ContainerNull(inventory.player, tile));
        xSize += 20;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiElementHolder(this, 26, 31, 130, 90));
        addButton(new ToggleButton(this, 26, 19, 11, () -> tileEntity.activeCooled,
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0))),
              new TextComponentString(LangUtils.localize("gui.toggleCooling")), new TextComponentString(LangUtils.localize("gui.toggleCooling"))));
        scrollBar = addButton(new GuiScrollBar(this, 156, 31, 90, () -> ReactorLogic.values().length, () -> DISPLAY_COUNT));
        for (int i = 0; i < DISPLAY_COUNT; i++) {
            addButton(new ReactorLogicButton<>(this, 27, 32 + 22 * i, i, scrollBar::getCurrentSelection, ReactorLogic::values,
                  () -> tileEntity.logicType, mode -> new TextComponentString(EnumColor.WHITE + mode.getLocalizedName()), ReactorLogic::getDescription, ReactorLogic::getRenderStack,
                  mode -> EnumColor.RED, this::changeLogic));
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 6);
        drawTextScaledBound(new TextComponentString(LangUtils.localize("gui.coolingMeasurements") + ": " + EnumColor.RED + LangUtils.transOnOff(tileEntity.activeCooled)),
              40, 20, titleTextColor(), xSize - 44);
        drawCenteredTextScaledBound(new TextComponentString(LangUtils.localize("gui.redstoneOutputMode") + ": " + EnumColor.RED + tileEntity.logicType.getLocalizedName()),
              xSize - 8, 123, titleTextColor());
        drawCenteredTextScaledBound(new TextComponentString(LangUtils.localize("gui.status") + ": " + EnumColor.RED +
              LangUtils.localize("gui." + (tileEntity.checkMode() ? "outputting" : "idle"))), xSize - 8, 136, titleTextColor());
        super.drawForegroundText(mouseX, mouseY);
    }

    private void changeLogic(ReactorLogic type) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(1, type.ordinal())));
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
