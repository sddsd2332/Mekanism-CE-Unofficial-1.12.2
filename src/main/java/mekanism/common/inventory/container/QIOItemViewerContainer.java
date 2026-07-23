package mekanism.common.inventory.container;

import mekanism.api.Action;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIOCapacitySummary;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOStorageUnits;
import mekanism.common.config.ClientConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.VirtualCraftingOutputSlot;
import mekanism.common.inventory.container.slot.VirtualCraftingSlot;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import mekanism.common.network.qio.PacketQIOViewerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared server/client container for Dashboard and Portable Dashboard viewers.
 * The server keeps the frequency reference; the client only owns a rendered
 * snapshot supplied by PacketQIOViewerData.
 */
public class QIOItemViewerContainer extends MekanismContainer implements IQIOItemViewerContainer {

    public static final int SLOTS_X_MIN = ClientConfig.QIO_VIEWER_SLOTS_X_MIN;
    public static final int SLOTS_X_MAX = ClientConfig.QIO_VIEWER_SLOTS_X_MAX;
    public static final int SLOTS_Y_MIN = ClientConfig.QIO_VIEWER_SLOTS_Y_MIN;
    public static final int SLOTS_Y_MAX = ClientConfig.QIO_VIEWER_SLOTS_Y_MAX;
    public static final int SLOTS_START_Y = 43;

    @Nullable
    protected QIOFrequency frequency;
    private final Map<UUID, QIOResourceEntry> entries = new LinkedHashMap<>();
    private long totalCount;
    private QIOAmount exactTotalCount = QIOAmount.ZERO;
    private QIOCapacitySummary capacitySummary = QIOCapacitySummary.EMPTY;
    private long resourceRevision;
    private boolean killed;
    private boolean shiftClickIntoFrequency = true;
    @Nullable
    protected IQIOCraftingWindowHolder craftingHolder;
    private VirtualCraftingSlot[][] craftingSlots;
    private int slotElementStartIndex;

    public QIOItemViewerContainer(InventoryPlayer inventory, @Nullable QIOFrequency frequency) {
        this(inventory, frequency, true);
    }

    /**
     * Constructor used by tile/item-specific viewers that need to register
     * their own trackers before opening the inventory.
     */
    protected QIOItemViewerContainer(InventoryPlayer inventory, @Nullable QIOFrequency frequency, boolean open) {
        super(inventory);
        this.frequency = frequency;
        if (open) {
            addSlotsAndOpen();
            openViewer();
        }
    }

    protected final void openViewer() {
        if (frequency != null && !isRemote() && inv != null && inv.player instanceof EntityPlayerMP) {
            frequency.openViewer((EntityPlayerMP) inv.player);
        }
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) listener;
            QIOFrequency current = getFrequency();
            if (current != null) {
                current.openViewer(player);
            }
            sendViewerSync(player);
        }
    }

    /** Sets the persistent crafting owner before {@link #addSlotsAndOpen()}. */
    protected final void setCraftingWindowHolder(@Nullable IQIOCraftingWindowHolder holder) {
        craftingHolder = holder;
    }

    @Override
    protected void addSlots() {
        slotElementStartIndex = 0;
        if (craftingHolder != null && craftingHolder.getCraftingWindows() != null) {
            QIOCraftingWindow[] windows = craftingHolder.getCraftingWindows();
            craftingSlots = new VirtualCraftingSlot[windows.length][10];
            for (int window = 0; window < windows.length; window++) {
                QIOCraftingWindow craftingWindow = windows[window];
                if (craftingWindow == null) {
                    continue;
                }
                for (int slot = 0; slot < 9; slot++) {
                    VirtualCraftingSlot virtual = new VirtualCraftingSlot(craftingWindow.getCraftingInventory(), slot, 0, 0);
                    craftingSlots[window][slot] = virtual;
                    addSlot(virtual);
                }
                VirtualCraftingOutputSlot output = new VirtualCraftingOutputSlot(craftingWindow, craftingWindow.getResultInventory(), 0, 0, 0);
                craftingSlots[window][9] = output;
                addSlot(output);
            }
            slotElementStartIndex = inventorySlots.size();
        } else {
            craftingSlots = new VirtualCraftingSlot[0][0];
        }
    }

    @Nullable
    public QIOFrequency getFrequency() {
        return frequency;
    }

    @Override
    @Nullable
    public QIOFrequency getServerFrequency() {
        return isRemote() ? null : getFrequency();
    }

    @Override
    @Nullable
    public QIOCraftingWindow getCraftingWindow(byte window) {
        return craftingHolder == null ? null : craftingHolder.getCraftingWindow(window);
    }

    @Override
    public VirtualCraftingSlot getCraftingWindowSlot(byte window, int slot) {
        if (craftingSlots == null || window < 0 || window >= craftingSlots.length || slot < 0 || slot >= 10) {
            return null;
        }
        return craftingSlots[window][slot];
    }

    @Override
    public int getSlotElementStartIndex() {
        return slotElementStartIndex;
    }

    /** Returns the crafting window currently focused by the client GUI. */
    public byte getSelectedCraftingGrid() {
        SelectedWindowData selected = getSelectedWindow();
        return selected != null && selected.type == SelectedWindowData.WindowType.CRAFTING ? selected.extraData : -1;
    }

    @Nonnull
    @Override
    public ItemStack slotClick(int slotID, int dragType, @Nonnull ClickType clickType, @Nonnull EntityPlayer player) {
        boolean serverCraftingOutput = !isRemote() && clickType != ClickType.QUICK_MOVE && slotID >= 0 &&
              slotID < inventorySlots.size() && inventorySlots.get(slotID) instanceof VirtualCraftingOutputSlot;
        ItemStack result = super.slotClick(slotID, dragType, clickType, player);
        if (serverCraftingOutput) {
            // Normal output clicks consume the inputs only on the server, just
            // like QIO's custom shift-click handling does.
            syncServerOnlyChanges(player, true);
        }
        return result;
    }

    @Override
    public ItemStack quickMoveStack(@Nonnull EntityPlayer player, int slotID) {
        if (slotID < 0 || slotID >= inventorySlots.size()) {
            return ItemStack.EMPTY;
        }
        Slot currentSlot = inventorySlots.get(slotID);
        if (currentSlot == null || !currentSlot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        if (currentSlot instanceof VirtualCraftingOutputSlot) {
            if (!isRemote()) {
                ItemStack crafted = ((VirtualCraftingOutputSlot) currentSlot).performShiftCraft(player, this);
                if (!crafted.isEmpty()) {
                    syncServerOnlyChanges(player, true);
                }
            }
            // QIO decides the entire batch in one server-side operation. Both
            // sides must return EMPTY so vanilla neither repeats the transfer
            // nor rejects the click transaction due to different results.
            return ItemStack.EMPTY;
        }
        ItemStack original = currentSlot.getStack().copy();
        ItemStack slotStack = currentSlot.getStack();
        if (currentSlot instanceof VirtualCraftingSlot) {
            if (!mergeItemStack(slotStack, slotElementStartIndex, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
            return finishCraftingTransfer(player, currentSlot, slotStack, original);
        }
        if (slotID >= slotElementStartIndex) {
            // QIO's custom inventory transfer is server-authoritative. Running
            // mergeItemStack on both sides lets 1.12 disagree about the
            // QUICK_MOVE result and revert the client slots as ghosts.
            if (isRemote()) {
                return ItemStack.EMPTY;
            }
            byte selected = getSelectedCraftingGrid(player.getUniqueID());
            if (!shiftClickIntoFrequency() && tryTransferToCraftingWindow(player, currentSlot, slotStack, original, selected)) {
                syncServerOnlyChanges(player, false);
                // Match the client-side result so the click transaction is not
                // rejected after the authoritative slot packet is sent.
                return ItemStack.EMPTY;
            }
            QIOFrequency serverFrequency = getFrequency();
            if (serverFrequency != null && serverFrequency.isValid() && !serverFrequency.isRemoved()) {
                // Give QIO a copy so it cannot mutate the player's source
                // stack. Then remove exactly the amount it accepted through
                // the standard container transfer path. This preserves any
                // remainder when storage is only partially available.
                ItemStack toInsert = slotStack.copy();
                long inserted = serverFrequency.massInsert(toInsert, toInsert.getCount(), Action.EXECUTE);
                if (inserted > 0) {
                    int transferAmount = (int) Math.min((long) slotStack.getCount(), inserted);
                    ItemStack remaining = slotStack.copy();
                    remaining.shrink(transferAmount);
                    ItemStack moved = transferSuccess(currentSlot, player, slotStack, remaining);
                    if (!moved.isEmpty()) {
                        syncServerOnlyChanges(player, true);
                        // QUICK_MOVE must have the same return value on both
                        // sides of the click transaction. The client cannot
                        // mutate the server frequency, so it always returns
                        // EMPTY for this special transfer.
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (shiftClickIntoFrequency() && tryTransferToCraftingWindow(player, currentSlot, currentSlot.getStack(), original, selected)) {
                syncServerOnlyChanges(player, false);
                // The client returned EMPTY before this server-only fallback;
                // keep the QUICK_MOVE confirmation identical on both sides.
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, slotID);
    }

    /**
     * QIO transfers performed only on the server have no matching client-side
     * inventory mutation. Send the authoritative slots before 1.12 suppresses
     * its post-click slot updates.
     */
    protected void syncServerOnlyChanges(@Nonnull EntityPlayer player, boolean syncViewer) {
        player.inventory.markDirty();
        detectAndSendChanges();
        if (syncViewer) {
            sendViewerSync(player);
        }
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).sendContainerToPlayer(this);
        }
    }

    private boolean tryTransferToCraftingWindow(@Nonnull EntityPlayer player, @Nonnull Slot currentSlot,
          @Nonnull ItemStack slotStack, @Nonnull ItemStack original, byte selected) {
        if (selected < 0) {
            return false;
        }
        QIOCraftingWindow craftingWindow = getCraftingWindow(selected);
        if (craftingWindow == null || craftingWindow.isOutput(slotStack)) {
            return false;
        }
        int start = selected * 10;
        if (!mergeItemStack(slotStack, start, start + 9, false)) {
            return false;
        }
        finishCraftingTransfer(player, currentSlot, slotStack, original);
        return true;
    }

    @Nonnull
    private ItemStack finishCraftingTransfer(@Nonnull EntityPlayer player, @Nonnull Slot slot, @Nonnull ItemStack current,
          @Nonnull ItemStack original) {
        if (current.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        if (current.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, current);
        return original;
    }

    @Override
    protected int getInventoryYOffset() {
        return SLOTS_START_Y + getConfiguredRows() * 18 + 15;
    }

    @Override
    protected int getInventoryXOffset() {
        return 8 + (getConfiguredColumns() - SLOTS_X_MIN) * 9;
    }

    public static int getConfiguredColumns() {
        return Math.max(SLOTS_X_MIN, Math.min(SLOTS_X_MAX, MekanismConfig.current().client.qioItemViewerSlotsX.val()));
    }

    public static int getConfiguredRows() {
        return Math.max(SLOTS_Y_MIN, Math.min(SLOTS_Y_MAX, MekanismConfig.current().client.qioItemViewerSlotsY.val()));
    }

    /** Repositions the existing client container when the viewer is resized without reopening the server window. */
    public void repositionPlayerInventory() {
        int xOffset = getInventoryXOffset();
        int yOffset = getInventoryYOffset();
        for (int i = 0; i < mainInventorySlots.size(); i++) {
            Slot slot = mainInventorySlots.get(i);
            slot.xPos = xOffset + i % 9 * 18;
            slot.yPos = yOffset + i / 9 * 18;
        }
        for (int i = 0; i < hotBarSlots.size(); i++) {
            Slot slot = hotBarSlots.get(i);
            slot.xPos = xOffset + i * 18;
            slot.yPos = yOffset + 58;
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        return !killed;
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        if (frequency != null && !player.world.isRemote && player instanceof EntityPlayerMP) {
            frequency.closeViewer((EntityPlayerMP) player);
        }
        super.closeInventory(player);
    }

    /** Applies a complete authoritative snapshot on the client. */
    public void applyBatch(Collection<QIOResourceEntry> snapshot, long countCapacity, int typeCapacity) {
        applyBatchChunk(snapshot, new QIOCapacitySummary(QIOAmount.of(countCapacity), QIOAmount.of(typeCapacity), 0, 0), true);
    }

    /** Applies one packet of a complete snapshot; only the first packet clears stale entries. */
    public void applyBatchChunk(Collection<QIOResourceEntry> snapshot, long countCapacity, int typeCapacity, boolean first) {
        applyBatchChunk(snapshot, new QIOCapacitySummary(QIOAmount.of(countCapacity), QIOAmount.of(typeCapacity), 0, 0), first);
    }

    public void applyBatchChunk(Collection<QIOResourceEntry> snapshot, QIOCapacitySummary capacitySummary, boolean first) {
        if (!isRemote()) {
            return;
        }
        Map<UUID, QIOResourceEntry> oldEntries = new LinkedHashMap<>(entries);
        QIOCapacitySummary oldCapacity = this.capacitySummary;
        if (first) {
            entries.clear();
        }
        applyEntries(snapshot);
        recalculateTotals();
        this.capacitySummary = capacitySummary == null ? QIOCapacitySummary.EMPTY : capacitySummary;
        killed = false;
        if (!oldEntries.equals(entries) || !oldCapacity.equals(this.capacitySummary)) {
            markResourcesChanged();
        }
    }

    /** Applies changed entries; zero amounts remove a resource. */
    public void applyUpdate(Collection<QIOResourceEntry> updates, long countCapacity, int typeCapacity) {
        applyUpdate(updates, new QIOCapacitySummary(QIOAmount.of(countCapacity), QIOAmount.of(typeCapacity), 0, 0));
    }

    public void applyUpdate(Collection<QIOResourceEntry> updates, QIOCapacitySummary capacitySummary) {
        if (!isRemote()) {
            return;
        }
        Map<UUID, QIOResourceEntry> oldEntries = new LinkedHashMap<>(entries);
        QIOCapacitySummary oldCapacity = this.capacitySummary;
        applyEntries(updates);
        recalculateTotals();
        this.capacitySummary = capacitySummary == null ? QIOCapacitySummary.EMPTY : capacitySummary;
        killed = false;
        if (!oldEntries.equals(entries) || !oldCapacity.equals(this.capacitySummary)) {
            markResourcesChanged();
        }
    }

    public void applyKill() {
        if (isRemote()) {
            boolean changed = !killed || !entries.isEmpty() || !capacitySummary.equals(QIOCapacitySummary.EMPTY);
            killed = true;
            entries.clear();
            totalCount = 0;
            exactTotalCount = QIOAmount.ZERO;
            capacitySummary = QIOCapacitySummary.EMPTY;
            if (changed) {
                markResourcesChanged();
            }
        }
    }

    private void markResourcesChanged() {
        resourceRevision++;
        if (Mekanism.proxy != null) {
            Mekanism.proxy.onQIOViewerResourcesChanged();
        }
    }

    private void recalculateTotals() {
        QIOAmount storageUnits = QIOAmount.ZERO;
        for (QIOResourceEntry entry : entries.values()) {
            storageUnits = storageUnits.add(entry.getExactAmount().multiply(QIOStorageUnits.getUnitsPerResource(entry.getKind())));
        }
        exactTotalCount = storageUnits.divideRoundUp(QIOStorageUnits.UNITS_PER_ITEM);
        totalCount = exactTotalCount.longValueClamped();
    }

    private void applyEntries(Collection<QIOResourceEntry> updates) {
        if (updates == null) {
            return;
        }
        for (QIOResourceEntry entry : updates) {
            if (entry == null) {
                continue;
            }
            if (entry.getExactAmount().isZero()) {
                entries.remove(entry.getUUID());
            } else {
                entries.put(entry.getUUID(), entry);
            }
        }
    }

    @Nonnull
    public List<QIOResourceEntry> getResourceEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    @Nullable
    public QIOResourceEntry getResourceEntry(@Nullable UUID resource) {
        return resource == null ? null : entries.get(resource);
    }

    public long getTotalCount() {
        return totalCount;
    }

    @Nonnull
    public QIOAmount getExactTotalCount() {
        return exactTotalCount;
    }

    public int getTotalTypes() {
        return entries.size();
    }

    public long getTotalCountCapacity() {
        return capacitySummary.getCountCapacityClamped();
    }

    public int getTotalTypeCapacity() {
        return capacitySummary.getTypeCapacityClamped();
    }

    @Nonnull
    public QIOCapacitySummary getCapacitySummary() {
        return capacitySummary;
    }

    public long getResourceRevision() {
        return resourceRevision;
    }

    public boolean isKilled() {
        return killed;
    }

    public boolean shiftClickIntoFrequency() {
        return shiftClickIntoFrequency;
    }

    public void toggleTargetDirection() {
        shiftClickIntoFrequency = !shiftClickIntoFrequency;
    }
}
