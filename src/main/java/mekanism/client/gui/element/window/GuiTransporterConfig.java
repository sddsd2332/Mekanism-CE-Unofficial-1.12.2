package mekanism.client.gui.element.window;

import mekanism.api.Coord4D;
import mekanism.api.RelativeSide;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.ColorButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.SideDataButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.network.PacketConfigurationUpdate.ConfigurationPacket;
import mekanism.common.network.PacketConfigurationUpdate.ConfigurationUpdateMessage;
import mekanism.common.network.PacketGuiInteract.GuiInteractMessage;
import mekanism.common.network.PacketGuiInteract.GuiInteraction;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuiTransporterConfig<TILE extends TileEntityContainerBlock & ISideConfiguration> extends GuiWindow {

    private final List<SideDataButton> sideDataButtons = new ArrayList<>();
    private final TILE tile;

    public GuiTransporterConfig(IGuiWrapper gui, int x, int y, TILE tile, SelectedWindowData windowData) {
        super(gui, x, y, 156, 119, windowData);
        if (windowData.type != WindowType.TRANSPORTER_CONFIG) {
            throw new IllegalArgumentException("Transporter configs must have a transporter config window type");
        }
        this.tile = tile;
        interactionStrategy = InteractionStrategy.ALL;
        addChild(new GuiInnerScreen(gui, relativeX + 38, relativeY + 15, 80, 12,
              () -> Collections.singletonList(new TextComponentString(LangUtils.localize("gui.strictInput") + ": " +
                    LangUtils.transOnOff(tile.getEjector().hasStrictInput())))));
        addChild(new MekanismImageButton(gui, relativeX + 136, relativeY + 6, 14, 16, getButtonLocation("exclamation"),
              () -> Mekanism.packetHandler.sendToServer(new ConfigurationUpdateMessage(ConfigurationPacket.STRICT_INPUT, Coord4D.get(tile), 0, 0, null)),
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.configuration.strictInput")))));
        addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + 111, relativeY + 48));
        addChild(new ColorButton(gui, relativeX + 112, relativeY + 49, 16, 16, () -> tile.getEjector().getOutputColor(),
              () -> Mekanism.packetHandler.sendToServer(new ConfigurationUpdateMessage(ConfigurationPacket.EJECT_COLOR, Coord4D.get(tile),
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 2 : 0, 0, null)),
              () -> Mekanism.packetHandler.sendToServer(new ConfigurationUpdateMessage(ConfigurationPacket.EJECT_COLOR, Coord4D.get(tile), 1, 0, null))));
        addSideDataButton(RelativeSide.BOTTOM, 41, 80);
        addSideDataButton(RelativeSide.TOP, 41, 34);
        addSideDataButton(RelativeSide.FRONT, 41, 57);
        addSideDataButton(RelativeSide.BACK, 18, 80);
        addSideDataButton(RelativeSide.LEFT, 18, 57);
        addSideDataButton(RelativeSide.RIGHT, 64, 57);
        updateEnabledButtons();
        if (gui instanceof GuiMekanism<?> mekanismGui && mekanismGui.inventorySlots instanceof MekanismContainer container) {
            container.startTracking(MekanismContainer.TRANSPORTER_CONFIG_WINDOW, this.tile.getEjector());
            Mekanism.packetHandler.sendToServer(new GuiInteractMessage(GuiInteraction.CONTAINER_TRACK_EJECTOR, Coord4D.get(tile), MekanismContainer.TRANSPORTER_CONFIG_WINDOW));
        }
    }

    private void addSideDataButton(RelativeSide side, int xPos, int yPos) {
        EnumFacing globalSide = side.getDirection(tile.facing);
        SideDataButton button = addChild(new SideDataButton(gui(), relativeX + xPos, relativeY + yPos, side.ordinal(), side,
              () -> tile.getConfig().getDataType(TransmissionType.ITEM, globalSide, tile.facing),
              () -> tile.getEjector().getInputColor(globalSide), tile,
              () -> Mekanism.packetHandler.sendToServer(new ConfigurationUpdateMessage(ConfigurationPacket.INPUT_COLOR, Coord4D.get(tile),
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 2 : 0, globalSide.ordinal(), null)),
              () -> Mekanism.packetHandler.sendToServer(new ConfigurationUpdateMessage(ConfigurationPacket.INPUT_COLOR, Coord4D.get(tile),
                    1, globalSide.ordinal(), null)), false));
        sideDataButtons.add(button);
    }

    private void updateEnabledButtons() {
        for (SideDataButton sideDataButton : sideDataButtons) {
            sideDataButton.active = isInputSideEnabled(RelativeSide.bydex(sideDataButton.getSideIndex()));
        }
    }

    private boolean isInputSideEnabled(RelativeSide side) {
        ConfigInfo info = tile.getConfig().getConfigInfo(TransmissionType.ITEM);
        return info == null || info.isSideEnabled(side);
    }

    @Override
    public void close() {
        super.close();
        Mekanism.packetHandler.sendToServer(new GuiInteractMessage(GuiInteraction.CONTAINER_STOP_TRACKING, Coord4D.get(tile), MekanismContainer.TRANSPORTER_CONFIG_WINDOW));
        if (gui() instanceof GuiMekanism<?> mekanismGui && mekanismGui.inventorySlots instanceof MekanismContainer container) {
            container.stopTracking(MekanismContainer.TRANSPORTER_CONFIG_WINDOW);
        }
    }

    @Override
    public void tick() {
        super.tick();
        TileEntity worldTile = minecraft.world == null ? null : minecraft.world.getTileEntity(tile.getPos());
        if (worldTile == null || worldTile.isInvalid()) {
            close();
            return;
        }
        updateEnabledButtons();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentString(LangUtils.localize("gui.configuration.transporter")), 5);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.input")), 18, 105, TextAlignment.CENTER,
              subheadingTextColor(), 68, 0, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.output")), 86, 68, TextAlignment.CENTER,
              subheadingTextColor(), width - 86, 4, false);
    }

    @Override
    protected int getTitlePadEnd() {
        return super.getTitlePadEnd() + 18;
    }
}
