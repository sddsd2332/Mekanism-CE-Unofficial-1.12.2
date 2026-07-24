package mekanism.client.gui.item;

import mekanism.client.gui.GuiTeleporterBase;
import mekanism.client.ClientTickHandler;
import mekanism.client.MekanismClient;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.Mekanism;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.item.PortableTeleporterContainer;
import mekanism.common.item.ItemPortableTeleporter;
import mekanism.common.network.PacketSetItemFrequency.SetItemFrequencyMessage;
import mekanism.common.security.IOwnerItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.LangUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;

import java.util.List;
import java.util.UUID;

public class GuiPortableTeleporter extends GuiTeleporterBase<PortableTeleporterContainer> {

    private final EntityPlayer player;
    private final EnumHand currentHand;
    private final PortableTeleporterContainer container;
    private Frequency clientFreq;
    private MekanismButton teleportButton;

    public GuiPortableTeleporter(EntityPlayer player, EnumHand hand, ItemStack stack) {
        this(new PortableTeleporterContainer(player.inventory, hand, stack), player, hand, stack);
    }

    public GuiPortableTeleporter(EntityPlayer player, EnumHand hand, int itemSlot, ItemStack stack) {
        this(new PortableTeleporterContainer(player.inventory, hand, itemSlot, stack), player, hand, stack);
    }

    private GuiPortableTeleporter(PortableTeleporterContainer container, EntityPlayer player, EnumHand hand, ItemStack stack) {
        super(container);
        this.container = container;
        this.player = player;
        currentHand = hand;
        ySize = 172;
        FrequencyIdentity identity = ((ItemPortableTeleporter) stack.getItem()).getFrequency(stack);
        if (identity != null) {
            selectedMode = identity.securityMode();
            clientFreq = new TeleporterFrequency(String.valueOf(identity.key()), identity.ownerUUID(), identity.securityMode());
        }
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVerticalPowerBar(this, container.getEnergyStorage(), 158, 26)
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> getStatus() == 4));
        teleportButton = addButton(new MekanismButton(this, 42, 147, 92, 20, new TextComponentString(LangUtils.localize("gui.teleport")),
              this::teleport, null));
        updateButtons();
    }

    @Override
    protected int getActionButtonWidth() {
        return 60;
    }

    private void teleport() {
        if (getFrequency() != null && getStatus() == 1) {
            mc.setIngameFocus();
            ClientTickHandler.portableTeleport(player, currentHand, container.getItemSlot(), getFrequency().getIdentity());
        }
    }

    public void updateButtons() {
        updateFrequencyButtons();
        if (teleportButton != null) {
            teleportButton.active = getFrequency() != null && getStatus() == 1;
        }
    }

    @Override
    protected void updateFrequencyButtons() {
        super.updateFrequencyButtons();
        if (teleportButton != null) {
            teleportButton.active = getFrequency() != null && getStatus() == 1;
        }
    }

    public boolean isStackEmpty() {
        return container.getStack().isEmpty();
    }

    @Override
    protected String getGuiTitle() {
        return container.getStack().getDisplayName();
    }

    @Override
    protected int getTitleOffset() {
        return status == null ? 0 : status.getRelativeRight();
    }

    @Override
    protected boolean isPortable() {
        return true;
    }

    @Override
    protected int getStatus() {
        return container.getStatus();
    }

    @Override
    protected Frequency getFrequency() {
        Frequency syncedFrequency = container.getClientFrequency();
        return syncedFrequency == null ? clientFreq : syncedFrequency;
    }

    @Override
    protected List<? extends Frequency> getPublicCache() {
        return container.getPublicCache();
    }

    @Override
    protected List<? extends Frequency> getPrivateCache() {
        return container.getPrivateCache();
    }

    @Override
    protected List<? extends Frequency> getTrustedCache() {
        return container.getTrustedCache();
    }

    @Override
    protected UUID getOwnerUUID() {
        ItemStack stack = container.getStack();
        return stack.isEmpty() || !(stack.getItem() instanceof IOwnerItem) ? null : ((IOwnerItem) stack.getItem()).getOwnerUUID(stack);
    }

    @Override
    protected String getSelfOwnerName() {
        return MekanismClient.clientUUIDMap.get(getOwnerUUID());
    }

    @Override
    protected void setFrequency(FrequencyIdentity identity) {
        if (identity != null) {
            clientFreq = new TeleporterFrequency(String.valueOf(identity.key()), identity.ownerUUID(), identity.securityMode());
            clientFreq.clientOwner = getSelfOwnerName();
            Mekanism.packetHandler.sendToServer(new SetItemFrequencyMessage(container.windowId, true, FrequencyType.TELEPORTER, identity, currentHand));
        }
    }

    @Override
    protected void deleteSelectedFrequency() {
        Frequency selected = getSelectedFrequency();
        if (selected != null) {
            Mekanism.packetHandler.sendToServer(new SetItemFrequencyMessage(container.windowId, false, FrequencyType.TELEPORTER, selected.getIdentity(), currentHand));
            scrollList.clearSelection();
        }
        updateButtons();
    }

}
