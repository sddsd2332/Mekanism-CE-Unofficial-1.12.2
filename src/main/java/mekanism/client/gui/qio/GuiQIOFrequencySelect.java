package mekanism.client.gui.qio;

import mekanism.api.Coord4D;
import mekanism.client.gui.GuiTeleporterBase;
import mekanism.client.gui.element.button.ColorButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiConfirmationDialog;
import mekanism.client.gui.element.window.GuiConfirmationDialog.DialogType;
import mekanism.common.MekanismLang;
import mekanism.common.Mekanism;
import mekanism.common.QIOGuiConstants;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.ContainerQIOFrequencySelect;
import mekanism.common.network.PacketSetTileFrequency.SetTileFrequencyMessage;
import mekanism.common.network.PacketSetFrequencyColor.SetFrequencyColorMessage;
import mekanism.common.network.PacketSimpleGui.SimpleGuiMessage;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public class GuiQIOFrequencySelect extends GuiTeleporterBase<ContainerQIOFrequencySelect> {

    private final TileEntityQIOComponent tile;
    private final int backGuiId;

    public GuiQIOFrequencySelect(InventoryPlayer inventory, TileEntityQIOComponent tile) {
        this(inventory, tile, tile instanceof mekanism.common.tile.qio.TileEntityQIODriveArray ? QIOGuiConstants.DRIVE_ARRAY : QIOGuiConstants.DASHBOARD);
    }

    public GuiQIOFrequencySelect(InventoryPlayer inventory, TileEntityQIOComponent tile, int backGuiId) {
        super(new ContainerQIOFrequencySelect(inventory, tile));
        this.tile = tile;
        this.backGuiId = backGuiId;
        ySize = 155;
        titleLabelY = 5;
        QIOFrequency frequency = tile.getQIOFrequency();
        if (frequency != null) {
            selectedMode = frequency.getSecurity();
        }
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new MekanismImageButton(this, 6, 6, 14, getButtonLocation("back"), this::goBack)
              .setTooltip(MekanismLang.BACK.translate()));
        addButton(new GuiSlot(SlotType.NORMAL, this, 131, 130));
        addButton(new ColorButton(this, 132, 131, 16, 16,
              () -> getFrequency() == null ? null : getFrequency().getColor(),
              () -> sendColorUpdate(true), () -> sendColorUpdate(false)));
    }

    @Override
    protected int getSelectorYOffset() {
        return 3;
    }

    @Override
    protected boolean useIconSecurityButtons() {
        return true;
    }

    @Override
    protected boolean showStatusIndicator() {
        return false;
    }

    @Override
    protected int getTitleOffset() {
        return 17;
    }

    @Override
    protected int getActionButtonWidth() {
        return 50;
    }

    private void sendColorUpdate(boolean next) {
        QIOFrequency frequency = getFrequency();
        if (frequency != null) {
            Mekanism.packetHandler.sendToServer(new SetFrequencyColorMessage(frequency, next));
        }
    }

    private void goBack() {
        Mekanism.packetHandler.sendToServer(new SimpleGuiMessage(Coord4D.get(tile), 0, backGuiId));
    }

    @Override
    protected String getGuiTitle() {
        return tile.getName();
    }

    @Override
    protected boolean isPortable() {
        return false;
    }

    @Override
    protected int getStatus() {
        return getFrequency() == null ? 0 : 1;
    }

    @Override
    protected QIOFrequency getFrequency() {
        return tile.getQIOFrequency();
    }

    @Override
    protected List<QIOFrequency> getPublicCache() {
        return tile.getPublicCache(FrequencyType.QIO);
    }

    @Override
    protected List<QIOFrequency> getPrivateCache() {
        return tile.getPrivateCache(FrequencyType.QIO);
    }

    @Override
    protected List<QIOFrequency> getTrustedCache() {
        return tile.getTrustedCache(FrequencyType.QIO);
    }

    @Override
    protected UUID getOwnerUUID() {
        return tile.getSecurity().getOwnerUUID();
    }

    @Override
    protected String getSelfOwnerName() {
        String owner = tile.getSecurity().getClientOwner();
        return owner == null ? "" : owner;
    }

    @Override
    protected void setFrequency(FrequencyIdentity identity) {
        Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(true, FrequencyType.QIO, identity, tile));
    }

    @Override
    protected void deleteSelectedFrequency() {
        mekanism.common.frequency.Frequency selected = getSelectedFrequency();
        if (selected != null) {
            GuiConfirmationDialog.show(this, MekanismLang.FREQUENCY_DELETE_CONFIRM.translate(), () -> {
                Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(false, FrequencyType.QIO, selected.getIdentity(), tile));
                scrollList.clearSelection();
                updateFrequencyButtons();
            }, DialogType.DANGER);
        }
    }
}
