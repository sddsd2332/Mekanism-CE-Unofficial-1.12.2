package mekanism.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.sync.FrequencyContainerSync;
import mekanism.common.inventory.PortableQIODashboardInventory;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.Mekanism;
import mekanism.common.network.qio.PacketQIOPortableGui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/** Item-backed viewer container for the portable Dashboard. */
public class PortableQIODashboardContainer extends QIOItemViewerContainer {

    private final EnumHand hand;
    private ItemStack stack;
    private final PortableQIODashboardInventory craftingInventory;
    private final FrequencyContainerSync<QIOFrequency> frequencySync = new FrequencyContainerSync<>();

    public PortableQIODashboardContainer(InventoryPlayer inventory, EnumHand hand, ItemStack stack) {
        super(inventory, resolveFrequency(inventory, stack), false);
        this.hand = hand;
        this.stack = stack;
        // The stack object in a player inventory can be replaced by vanilla
        // slot synchronization while this container is open. Keep the
        // crafting window attached to the current hand rather than to the
        // constructor snapshot.
        Supplier<ItemStack> currentStack = () -> getCurrentStack();
        this.craftingInventory = new PortableQIODashboardInventory(currentStack, inventory.player.world);
        setCraftingWindowHolder(craftingInventory);
        frequencySync.addTrackers(this, FrequencyType.QIO, this::getFrequency);
        addSlotsAndOpen();
        openViewer();
    }

    private static QIOFrequency resolveFrequency(InventoryPlayer inventory, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IFrequencyItem)) {
            return null;
        }
        FrequencyIdentity identity = ((IFrequencyItem) stack.getItem()).getFrequency(stack);
        return identity == null ? null : FrequencyType.QIO.getFrequency(identity, inventory.player.getUniqueID());
    }

    public EnumHand getHand() {
        return hand;
    }

    public ItemStack getStack() {
        return getCurrentStack();
    }

    /**
     * Returns the stack currently occupying the hand used to open this
     * container. The fallback is only needed while the client is constructing
     * its mirror container, before its inventory has been populated.
     */
    private ItemStack getCurrentStack() {
        if (inv != null && inv.player != null) {
            return inv.player.getHeldItem(hand);
        }
        return stack;
    }

    public PortableQIODashboardInventory getCraftingInventory() {
        return craftingInventory;
    }

    @Override
    public boolean shiftClickIntoFrequency() {
        ItemStack current = getCurrentStack();
        return !ItemDataUtils.hasData(current, "qioInsertIntoFrequency") || ItemDataUtils.getBoolean(current, "qioInsertIntoFrequency");
    }

    @Override
    public void toggleTargetDirection() {
        boolean current = shiftClickIntoFrequency();
        ItemDataUtils.setBoolean(getCurrentStack(), "qioInsertIntoFrequency", !current);
        Mekanism.packetHandler.sendToServer(new PacketQIOPortableGui.Message(windowId, hand, PacketQIOPortableGui.ACTION_TOGGLE_TARGET));
    }

    @Override
    public QIOFrequency getFrequency() {
        if (isRemote()) {
            return frequencySync.getFrequency();
        } else {
            ItemStack current = getCurrentStack();
            QIOFrequency resolved = resolveFrequency(inv, current);
            if (resolved != null || current.getItem() instanceof ItemPortableQIODashboard) {
                if (resolved != frequency && inv != null && inv.player instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) inv.player;
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
            }
        }
        return frequency;
    }

    @Override
    public void detectAndSendChanges() {
        if (!isRemote()) {
            // Frequency selection is an in-place window now, so refresh the
            // viewer mount before the regular tracker packet is assembled.
            getFrequency();
        }
        super.detectAndSendChanges();
    }

    public java.util.List<QIOFrequency> getPublicFrequencyCache() {
        return frequencySync.getPublicCache();
    }

    public java.util.List<QIOFrequency> getPrivateFrequencyCache() {
        return frequencySync.getPrivateCache();
    }

    public java.util.List<QIOFrequency> getTrustedFrequencyCache() {
        return frequencySync.getTrustedCache();
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        // The client can construct this mirror container before the hand
        // inventory synchronization packet arrives.  The server remains the
        // authority for both the held item and security access; enforcing the
        // hand stack here on the client closes the GUI during its first tick.
        if (player.world.isRemote) {
            return !isKilled();
        }
        ItemStack held = player.getHeldItem(hand);
        return !held.isEmpty() && held.getItem() instanceof ItemPortableQIODashboard &&
              (player.world.isRemote || SecurityUtils.canAccess(player, held)) && super.canInteractWith(player);
    }

    @Override
    protected HotBarSlot createHotBarSlot(@Nonnull InventoryPlayer inventory, int index, int x, int y) {
        if (hand == EnumHand.MAIN_HAND && index == inventory.currentItem) {
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
    public ItemStack slotClick(int slotId, int dragType, net.minecraft.inventory.ClickType clickType, EntityPlayer player) {
        if (clickType == net.minecraft.inventory.ClickType.SWAP) {
            if (hand == EnumHand.OFF_HAND && dragType == 40) {
                // Prevent pressing F from swapping the dashboard out of the
                // hand that backs this container.
                return ItemStack.EMPTY;
            }
            if (hand == EnumHand.MAIN_HAND && dragType == player.inventory.currentItem) {
                // The selected hotbar slot is likewise the backing item.
                return ItemStack.EMPTY;
            }
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        if (!player.world.isRemote) {
            craftingInventory.flush();
            player.inventory.markDirty();
        }
        super.closeInventory(player);
    }
}
