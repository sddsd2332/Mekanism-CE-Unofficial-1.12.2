package mekanism.qioprocessing.common.inventory.container;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.inventory.container.item.ItemStackSlotAccess;
import mekanism.common.inventory.container.item.IItemStackBackedContainer;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.sync.FrequencyContainerSync;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.PortableQIOProcessingTerminalInventory;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData;
import mekanism.qioprocessing.common.terminal.QIOProcessingFrequencyAccess;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.network.PacketQIOProcessingTerminalPreference;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Portable smart-processing viewer with item-owned native QIO crafting windows. */
/**
 * QIO 处理模块中的 ContainerPortableQIOSmartProcessingTerminal 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class ContainerPortableQIOSmartProcessingTerminal extends QIOItemViewerContainer
      implements QIOPortableTerminalContainer, IItemStackBackedContainer,
      QIOProcessingTerminalFrequencyContainer, QIOSmartProcessingPageContainer {

    private final ItemStackSlotAccess itemAccess;
    private final QIOPortableTerminalSessionTarget sessionTarget;
    private final QIOProcessingTerminalContainerState terminalState;
    private final PortableQIOProcessingTerminalInventory craftingInventory;
    private final FrequencyContainerSync<QIOFrequency> frequencySync =
          new FrequencyContainerSync<>();
    private final QIOSmartProcessingClientCache smartProcessingClientCache =
          new QIOSmartProcessingClientCache();
    private final QIOSmartProcessingRequestLedger smartProcessingRequestLedger =
          new QIOSmartProcessingRequestLedger();
    private long lastSmartProcessingRequestTick = Long.MIN_VALUE;
    private long lastSmartProcessingPollTick = Long.MIN_VALUE;

    public ContainerPortableQIOSmartProcessingTerminal(InventoryPlayer inventory,
          EnumHand hand, int itemSlot, ItemStack openingStack) {
        super(inventory, resolveFrequency(inventory, openingStack), false);
        itemAccess = new ItemStackSlotAccess(inventory, hand, itemSlot, openingStack);
        sessionTarget = new QIOPortableTerminalSessionTarget(inventory, itemAccess,
              isRemote());
        terminalState = new QIOProcessingTerminalContainerState(this,
              this::getTerminalSession);
        craftingInventory = new PortableQIOProcessingTerminalInventory(
              () -> itemAccess.isOriginalStackPresent() ? itemAccess.getStack() :
                    ItemStack.EMPTY, inventory.player.world,
              sessionTarget::acceptMutation);
        frequencySync.addTrackers(this, FrequencyType.QIO,
              this::currentFrequency);
        setCraftingWindowHolder(craftingInventory);
        addSlotsAndOpen();
        openViewer();
    }

    @Nullable
    @Override
    public QIOProcessingTerminalSession getTerminalSession() {
        return sessionTarget.getSession();
    }

    public QIOProcessingTerminalContainerState getTerminalState() {
        return terminalState;
    }

    @Override
    public QIOSmartProcessingClientCache getSmartProcessingClientCache() {
        return smartProcessingClientCache;
    }

    @Override
    public QIOSmartProcessingRequestLedger getSmartProcessingRequestLedger() {
        return smartProcessingRequestLedger;
    }

    @Override
    public boolean tryRequestSmartProcessing(long currentTick) {
        if (currentTick <= lastSmartProcessingRequestTick) {
            return false;
        }
        lastSmartProcessingRequestTick = currentTick;
        return true;
    }

    @Override
    public boolean tryRequestSmartProcessingPoll(long currentTick) {
        if (currentTick <= lastSmartProcessingPollTick) {
            return false;
        }
        lastSmartProcessingPollTick = currentTick;
        return true;
    }

    @Override
    public int getTerminalWindowId() {
        return windowId;
    }

    @Nullable
    @Override
    public QIOFrequency getTerminalFrequency() {
        return getFrequency();
    }

    @Override
    public List<QIOFrequency> getPublicTerminalFrequencies() {
        return frequencySync.getPublicCache();
    }

    @Override
    public List<QIOFrequency> getPrivateTerminalFrequencies() {
        return frequencySync.getPrivateCache();
    }

    @Override
    public List<QIOFrequency> getTrustedTerminalFrequencies() {
        return frequencySync.getTrustedCache();
    }

    @Nullable
    @Override
    public UUID getTerminalOwnerUUID() {
        ItemStack stack = getPortableStack();
        return stack.getItem() instanceof ItemPortableQIOProcessingTerminal item ?
              item.getOwnerUUID(stack) : null;
    }

    @Override
    public String getTerminalOwnerName() {
        return "";
    }

    @Override
    public boolean isPortableTerminal() {
        return true;
    }

    @Nonnull
    @Override
    public ItemStack getPortableStack() {
        return itemAccess.getStack();
    }

    @Nonnull
    @Override
    public ItemStackSlotAccess getItemAccess() {
        return itemAccess;
    }

    public EnumHand getHand() {
        return itemAccess.getHand();
    }

    public int getItemSlot() {
        return itemAccess.getSlot();
    }

    @Override
    public boolean isPortableTargetUsable(@Nonnull EntityPlayer player) {
        return canInteractWith(player);
    }

    @Override
    public boolean acceptAuthorizedMutation(long expectedGeneration,
          long updatedGeneration) {
        return sessionTarget.acceptMutation(expectedGeneration, updatedGeneration);
    }

    @Override
    @Nullable
    public QIOFrequency getFrequency() {
        if (isRemote()) {
            frequency = frequencySync.getFrequency();
            return frequency;
        }
        QIOFrequency resolved = currentFrequency();
        if (!isRemote() && resolved != frequency &&
            inv.player instanceof EntityPlayerMP player) {
            if (frequency != null) {
                frequency.closeViewer(player);
            }
            frequency = resolved;
            if (frequency != null) {
                frequency.openViewer(player);
            }
            sendViewerSync(player);
        } else {
            frequency = resolved;
        }
        return frequency;
    }

    @Override
    public void detectAndSendChanges() {
        if (!isRemote()) {
            getFrequency();
        }
        super.detectAndSendChanges();
    }

    @Override
    public boolean shiftClickIntoFrequency() {
        try {
            PortableQIOProcessingTerminalData data =
                  PortableQIOProcessingTerminalData.read(getPortableStack());
            return data == null || data.shiftClickIntoFrequency();
        } catch (QIOProcessingDataException e) {
            return true;
        }
    }

    @Override
    public void toggleTargetDirection() {
        if (isRemote() && terminalState.isValid()) {
            QIOProcessingPacketHandler.INSTANCE.sendToServer(
                  PacketQIOProcessingTerminalPreference.Message.create(windowId,
                        terminalState));
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        if (player.world.isRemote) {
            return !isKilled() && itemAccess.isOriginalStackPresent();
        }
        ItemStack stack = getPortableStack();
        return !isKilled() && itemAccess.isOriginalStackPresent() && isSmartTerminal(stack) &&
              SecurityUtils.canAccess(player, stack) && sessionTarget.validate(player);
    }

    @Override
    protected HotBarSlot createHotBarSlot(@Nonnull InventoryPlayer inventory, int index,
          int x, int y) {
        if (itemAccess.getHand() == EnumHand.MAIN_HAND &&
            index == itemAccess.getSlot()) {
            return new HotBarSlot(inventory, index, x, y) {
                @Override
                public boolean canTakeStack(@Nonnull EntityPlayer player) {
                    return false;
                }
            };
        }
        return super.createHotBarSlot(inventory, index, x, y);
    }

    @Nonnull
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType,
          EntityPlayer player) {
        if (!player.world.isRemote && !canInteractWith(player)) {
            return ItemStack.EMPTY;
        }
        if (clickType == ClickType.SWAP) {
            if (itemAccess.getHand() == EnumHand.OFF_HAND &&
                dragType == ItemStackSlotAccess.OFFHAND_SLOT) {
                return ItemStack.EMPTY;
            }
            if (itemAccess.getHand() == EnumHand.MAIN_HAND && dragType >= 0 &&
                dragType < hotBarSlots.size() &&
                !hotBarSlots.get(dragType).canTakeStack(player)) {
                return ItemStack.EMPTY;
            }
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        craftingInventory.flush();
        smartProcessingClientCache.clear();
        smartProcessingRequestLedger.clear();
        sessionTarget.close();
        super.closeInventory(player);
    }

    @Nullable
    private QIOFrequency currentFrequency() {
        if (!itemAccess.isOriginalStackPresent() ||
            !isSmartTerminal(getPortableStack())) {
            return null;
        }
        ItemPortableQIOProcessingTerminal item =
              (ItemPortableQIOProcessingTerminal) getPortableStack().getItem();
        return QIOProcessingFrequencyAccess.resolveAccessible(
              item.getFrequencyReference(getPortableStack()), inv.player.getUniqueID());
    }

    @Nullable
    private static QIOFrequency resolveFrequency(InventoryPlayer inventory,
          ItemStack stack) {
        if (!isSmartTerminal(stack)) {
            return null;
        }
        ItemPortableQIOProcessingTerminal item =
              (ItemPortableQIOProcessingTerminal) stack.getItem();
        QIOFrequencyReference reference = item.getFrequencyReference(stack);
        return QIOProcessingFrequencyAccess.resolveAccessible(reference,
              inventory.player.getUniqueID());
    }

    private static boolean isSmartTerminal(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() &&
              stack.getItem() instanceof ItemPortableQIOProcessingTerminal item &&
              item.getTerminalType() == QIOProcessingTerminalType.SMART_PROCESSING;
    }
}
