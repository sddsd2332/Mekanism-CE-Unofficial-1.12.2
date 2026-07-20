package mekanism.client.gui.qio;

import mekanism.api.EnumColor;
import mekanism.client.MekanismClient;
import mekanism.client.gui.GuiTeleporterBase;
import mekanism.client.gui.element.button.ColorButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiConfirmationDialog;
import mekanism.client.gui.element.window.GuiConfirmationDialog.DialogType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.QIOGuiConstants;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.QIOItemFrequencySelectContainer;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.network.PacketSetItemFrequency.SetItemFrequencyMessage;
import mekanism.common.network.PacketSetFrequencyColor.SetFrequencyColorMessage;
import mekanism.common.network.qio.PacketQIOPortableGui;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public class GuiQIOItemFrequencySelect extends GuiTeleporterBase<QIOItemFrequencySelectContainer> {

    private final EnumHand hand;

    public GuiQIOItemFrequencySelect(InventoryPlayer inventory, EnumHand hand, ItemStack stack) {
        super(new QIOItemFrequencySelectContainer(inventory, hand, stack));
        this.hand = hand;
        ySize = 155;
        titleLabelY = 5;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new MekanismImageButton(this, 6, 6, 14, getButtonLocation("back"),
              () -> Mekanism.packetHandler.sendToServer(new PacketQIOPortableGui.Message(inventorySlots.windowId, hand,
                    QIOGuiConstants.PORTABLE_DASHBOARD))).setTooltip(MekanismLang.BACK.translate()));
        addButton(new GuiSlot(SlotType.NORMAL, this, 131, 130));
        addButton(new ColorButton(this, 132, 131, 16, 16,
              () -> getFrequency() == null ? null : getFrequency().getColor(),
              () -> sendColorUpdate(true), () -> sendColorUpdate(false)));
    }

    @Override protected int getSelectorYOffset() { return 3; }
    @Override protected boolean useIconSecurityButtons() { return true; }
    @Override protected boolean showStatusIndicator() { return false; }
    @Override protected int getTitleOffset() { return 17; }

    @Override protected int getActionButtonWidth() { return 50; }
    private void sendColorUpdate(boolean next) {
        QIOFrequency frequency = getFrequency();
        if (frequency != null) {
            Mekanism.packetHandler.sendToServer(new SetFrequencyColorMessage(frequency, next));
        }
    }

    private QIOItemFrequencySelectContainer getContainer() { return (QIOItemFrequencySelectContainer) inventorySlots; }
    @Override protected String getGuiTitle() { return getContainer().getStack().getDisplayName(); }
    @Override protected boolean isPortable() { return true; }
    @Override protected int getStatus() { return getFrequency() == null ? 0 : 1; }
    @Override protected QIOFrequency getFrequency() { return getContainer().getClientFrequency(); }
    @Override protected List<QIOFrequency> getPublicCache() { return getContainer().getPublicCache(); }
    @Override protected List<QIOFrequency> getPrivateCache() { return getContainer().getPrivateCache(); }
    @Override protected List<QIOFrequency> getTrustedCache() { return getContainer().getTrustedCache(); }
    @Override protected UUID getOwnerUUID() {
        ItemStack stack = getContainer().getStack();
        return stack.isEmpty() || !(stack.getItem() instanceof ItemPortableQIODashboard) ? null :
              ((ItemPortableQIODashboard) stack.getItem()).getOwnerUUID(stack);
    }
    @Override protected String getSelfOwnerName() {
        UUID owner = getOwnerUUID();
        if (owner == null) {
            return "";
        }
        String name = MekanismClient.clientUUIDMap.get(owner);
        if (name == null && mc.player != null && owner.equals(mc.player.getUniqueID())) {
            return mc.player.getName();
        }
        return name == null ? "" : name;
    }
    @Override protected void setFrequency(FrequencyIdentity identity) {
        Mekanism.packetHandler.sendToServer(new SetItemFrequencyMessage(true, FrequencyType.QIO, identity, hand));
    }
    @Override protected void deleteSelectedFrequency() {
        Frequency selected = getSelectedFrequency();
        if (selected != null) {
            GuiConfirmationDialog.show(this, MekanismLang.FREQUENCY_DELETE_CONFIRM.translate(), () -> {
                Mekanism.packetHandler.sendToServer(new SetItemFrequencyMessage(false, FrequencyType.QIO, selected.getIdentity(), hand));
                scrollList.clearSelection();
                updateFrequencyButtons();
            }, DialogType.DANGER);
        }
    }
}
