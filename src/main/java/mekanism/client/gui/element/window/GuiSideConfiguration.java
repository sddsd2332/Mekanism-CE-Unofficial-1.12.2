package mekanism.client.gui.element.window;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mekanism.api.Coord4D;
import mekanism.api.RelativeSide;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.SideDataButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.tab.GuiConfigTypeTab;
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
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class GuiSideConfiguration<TILE extends TileEntityContainerBlock & ISideConfiguration> extends GuiWindow {

    private final Int2ObjectMap<GuiPos> slotPosMap = new Int2ObjectOpenHashMap<>();
    private final List<GuiConfigTypeTab> configTabs = new ArrayList<>();
    private final List<SideDataButton> sideDataButtons = new ArrayList<>();
    private final TILE tile;
    private MekanismButton autoEjectButton;
    private TransmissionType currentType;

    public GuiSideConfiguration(IGuiWrapper gui, int x, int y, TILE tile, SelectedWindowData windowData) {
        super(gui, x, y, 156, 135, windowData);
        if (windowData.type != WindowType.SIDE_CONFIG) {
            throw new IllegalArgumentException("Side configs must have a side config window type");
        }
        this.tile = tile;
        interactionStrategy = InteractionStrategy.ALL;
        currentType = getTopTransmission();
        slotPosMap.put(0, new GuiPos(67, 92));
        slotPosMap.put(1, new GuiPos(67, 46));
        slotPosMap.put(2, new GuiPos(67, 69));
        slotPosMap.put(3, new GuiPos(44, 92));
        slotPosMap.put(4, new GuiPos(44, 69));
        slotPosMap.put(5, new GuiPos(90, 69));
        addChild(new GuiInnerScreen(gui, relativeX + 38, relativeY + 25, 80, 12, () -> Collections.singletonList(new TextComponentString(getEjectText())))
              .tooltip(() -> tile.getConfig().canEject(currentType) ? Collections.emptyList() :
                    Collections.singletonList(new TextComponentString(LangUtils.localize("gui.noEject")))));
        autoEjectButton = addChild(new MekanismImageButton(gui, relativeX + 136, relativeY + 6, 14, getButtonLocation("auto_eject"),
              () -> Mekanism.packetHandler.sendToServer(new ConfigurationUpdateMessage(ConfigurationPacket.EJECT, Coord4D.get(tile), 0, 0, currentType)),
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.autoEject")))));
        addChild(new TooltipToggleButton(gui, relativeX + 136, relativeY + 95, 14, getButtonLocation("clear_sides"),
              () -> getTargetType(DataType::getNext) == DataType.NONE, this::clearSides, this::decrementSides,
              new TextComponentString(LangUtils.localize("configuration.mekanism.side.clear") + "\n" + LangUtils.localize("configuration.mekanism.side.clear.all")),
              new TextComponentString(LangUtils.localize("configuration.mekanism.side.increment"))));
        List<TransmissionType> transmissions = tile.getConfig().getTransmissions();
        for (int i = 0; i < transmissions.size(); i++) {
            TransmissionType type = transmissions.get(i);
            GuiConfigTypeTab tab = addChild(new GuiConfigTypeTab(gui, type, relativeX + (i < 4 ? -26 : width), relativeY + 2 + 28 * (i % 4), this, i < 4));
            configTabs.add(tab);
        }
        for (int i = 0; i < slotPosMap.size(); i++) {
            GuiPos guiPos = slotPosMap.get(i);
            EnumFacing facing = EnumFacing.byIndex(i);
            RelativeSide side = RelativeSide.bydex(i);
            SideDataButton button = addChild(new SideDataButton(gui, relativeX + guiPos.xPos, relativeY + guiPos.yPos, i, side,
                  () -> tile.getConfig().getDataType(currentType, facing),
                  () -> tile.getConfig().getDataType(currentType, facing).getColor(), tile,
                  () -> sendSideData(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 2 : 0, facing),
                  () -> sendSideData(1, facing), true));
            sideDataButtons.add(button);
        }
        updateTabs();
        if (gui instanceof GuiMekanism<?> mekanismGui && mekanismGui.inventorySlots instanceof MekanismContainer container) {
            container.startTracking(MekanismContainer.SIDE_CONFIG_WINDOW, this.tile.getConfig());
            Mekanism.packetHandler.sendToServer(new GuiInteractMessage(GuiInteraction.CONTAINER_TRACK_SIDE_CONFIG, Coord4D.get(tile), MekanismContainer.SIDE_CONFIG_WINDOW));
        }
    }

    private void clearSides() {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            sendBatchForAll(DataType.NONE);
        } else {
            sendBatch(currentType, getTargetType(DataType::getNext));
        }
    }

    private void decrementSides() {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            sendBatchForAll(DataType.NONE);
        } else {
            sendBatch(currentType, getTargetType(DataType::getPrevious));
        }
    }

    private DataType getTargetType(BiFunction<DataType, Predicate<DataType>, DataType> shift) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            return DataType.NONE;
        }
        ConfigInfo info = tile.getConfig().getConfigInfo(currentType);
        if (info == null) {
            return DataType.NONE;
        }
        DataType commonType = null;
        for (RelativeSide side : RelativeSide.SIDES) {
            if (info.isSideEnabled(side)) {
                DataType current = info.getDataType(side);
                if (commonType == null) {
                    commonType = current;
                } else if (commonType != current) {
                    return DataType.NONE;
                }
            }
        }
        return commonType == null ? DataType.NONE : shift.apply(commonType, info::supports);
    }

    private void sendBatchForAll(DataType target) {
        for (TransmissionType type : tile.getConfig().getTransmissions()) {
            sendBatch(type, target);
        }
    }

    private void sendBatch(TransmissionType type, DataType target) {
        ConfigInfo info = tile.getConfig().getConfigInfo(type);
        if (info == null) {
            return;
        }
        for (int i = 0; i < slotPosMap.size(); i++) {
            EnumFacing facing = EnumFacing.byIndex(i);
            RelativeSide side = RelativeSide.bydex(i);
            if (!info.isSideEnabled(side)) {
                continue;
            }
            DataType current = info.getDataType(side);
            if (target == DataType.NONE) {
                sendSideData(2, facing, type);
            } else if (current != target) {
                int clickType = getForwardDistance(current, target, info) <= getBackwardDistance(current, target, info) ? 0 : 1;
                int guard = 0;
                DataType cursor = current;
                while (cursor != target && guard++ < DataType.values().length) {
                    sendSideData(clickType, facing, type);
                    cursor = clickType == 0 ? cursor.getNext(info::supports) : cursor.getPrevious(info::supports);
                }
            }
        }
    }

    private int getForwardDistance(DataType current, DataType target, ConfigInfo info) {
        return getDistance(current, target, info, true);
    }

    private int getBackwardDistance(DataType current, DataType target, ConfigInfo info) {
        return getDistance(current, target, info, false);
    }

    private int getDistance(DataType current, DataType target, ConfigInfo info, boolean forward) {
        int distance = 0;
        DataType cursor = current;
        while (cursor != target && distance <= DataType.values().length) {
            cursor = forward ? cursor.getNext(info::supports) : cursor.getPrevious(info::supports);
            distance++;
        }
        return distance;
    }

    private void sendSideData(int clickType, EnumFacing facing) {
        sendSideData(clickType, facing, currentType);
    }

    private void sendSideData(int clickType, EnumFacing facing, TransmissionType type) {
        Mekanism.packetHandler.sendToServer(new ConfigurationUpdateMessage(ConfigurationPacket.SIDE_DATA, Coord4D.get(tile), clickType, facing.ordinal(), type));
    }

    private TransmissionType getTopTransmission() {
        return tile.getConfig().getTransmissions().get(0);
    }

    public void setCurrentType(TransmissionType type) {
        currentType = type;
    }

    public void updateTabs() {
        for (GuiConfigTypeTab tab : configTabs) {
            tab.visible = currentType != tab.getTransmissionType();
        }
        updateEnabledButtons();
    }

    @Override
    public void close() {
        super.close();
        Mekanism.packetHandler.sendToServer(new GuiInteractMessage(GuiInteraction.CONTAINER_STOP_TRACKING, Coord4D.get(tile), MekanismContainer.SIDE_CONFIG_WINDOW));
        if (gui() instanceof GuiMekanism<?> mekanismGui && mekanismGui.inventorySlots instanceof MekanismContainer container) {
            container.stopTracking(MekanismContainer.SIDE_CONFIG_WINDOW);
        }
    }

    private void updateEnabledButtons() {
        if (autoEjectButton != null) {
            autoEjectButton.active = tile.getConfig().canEject(currentType);
        }
        for (SideDataButton sideDataButton : sideDataButtons) {
            sideDataButton.active = tile.getConfig().isSideEnabled(currentType, EnumFacing.byIndex(sideDataButton.getSideIndex()));
        }
    }

    private String getEjectText() {
        if (tile.getConfig().canEject(currentType)) {
            return LangUtils.localize("gui.eject") + ": " + LangUtils.transOnOff(tile.getConfig().isEjecting(currentType));
        }
        return LangUtils.localize("gui.noEject");
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
        drawTitleText(new TextComponentString(String.format(LangUtils.localize("configuration.mekanism.config_type"), currentType.localize())), 5);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.slots")), 0, 120, TextAlignment.CENTER, subheadingTextColor(), 4, false);
    }

    @Override
    protected int getTitlePadEnd() {
        return super.getTitlePadEnd() + 18;
    }

    private static class GuiPos {

        private final int xPos;
        private final int yPos;
        private GuiPos(int xPos, int yPos) {
            this.xPos = xPos;
            this.yPos = yPos;
        }
    }
}
