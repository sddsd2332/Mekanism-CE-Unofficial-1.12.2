package mekanism.common.inventory.container;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.common.HashList;
import mekanism.common.Mekanism;
import mekanism.common.content.filter.IFilter;
import mekanism.common.frequency.Frequency;
import mekanism.common.inventory.container.slot.*;
import mekanism.common.inventory.container.sync.*;
import mekanism.common.inventory.container.sync.ISyncableData.DirtyType;
import mekanism.common.network.PacketWindowSelect.WindowSelectMessage;
import mekanism.common.network.to_client.container.PacketUpdateContainer.UpdateContainerMessage;
import mekanism.common.network.to_client.container.property.PropertyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.IntUnaryOperator;

public abstract class MekanismContainer extends Container {

    public static final int BASE_Y_OFFSET = 84;
    public static final int TRANSPORTER_CONFIG_WINDOW = 0;
    public static final int SIDE_CONFIG_WINDOW = 1;
    public static final int UPGRADE_WINDOW = 2;
    public static final int SKIN_SELECT_WINDOW = 3;

    protected final InventoryPlayer inv;
    protected final List<InventoryContainerSlot> inventoryContainerSlots = new ArrayList<>();
    protected final List<ArmorSlot> armorSlots = new ArrayList<>();
    protected final List<MainInventorySlot> mainInventorySlots = new ArrayList<>();
    protected final List<HotBarSlot> hotBarSlots = new ArrayList<>();
    protected final List<OffhandSlot> offhandSlots = new ArrayList<>();
    private final List<ISyncableData> trackedData = new ArrayList<>();
    private final Map<Object, List<ISyncableData>> specificTrackedData = new HashMap<>();
    @Nullable
    protected SelectedWindowData selectedWindow;
    @Nullable
    private Map<UUID, SelectedWindowData> selectedWindows;

    protected MekanismContainer(@Nullable InventoryPlayer inv) {
        this.inv = inv;
        if (inv != null && !isRemote()) {
            selectedWindows = new HashMap<>(1);
        }
    }

    public boolean isRemote() {
        return inv != null && inv.player.world.isRemote;
    }

    @Nullable
    public UUID getPlayerUUID() {
        return inv == null ? null : inv.player.getUniqueID();
    }

    @Override
    protected Slot addSlotToContainer(Slot slot) {
        super.addSlotToContainer(slot);
        if (slot instanceof IHasExtraData hasExtraData && inv != null) {
            hasExtraData.addTrackers(inv.player, this::track);
        }
        if (slot instanceof InventoryContainerSlot) {
            inventoryContainerSlots.add((InventoryContainerSlot) slot);
        } else if (slot instanceof ArmorSlot) {
            armorSlots.add((ArmorSlot) slot);
        } else if (slot instanceof MainInventorySlot) {
            mainInventorySlots.add((MainInventorySlot) slot);
        } else if (slot instanceof HotBarSlot) {
            hotBarSlots.add((HotBarSlot) slot);
        } else if (slot instanceof OffhandSlot) {
            offhandSlots.add((OffhandSlot) slot);
        }
        return slot;
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP player) {
            sendInitialDataToRemote(player, trackedData, IntUnaryOperator.identity());
        }
    }

    protected Slot addSlot(Slot slot) {
        return addSlotToContainer(slot);
    }

    /**
     * Adds slots and opens, must be called at end of extending classes constructors.
     */
    protected void addSlotsAndOpen() {
        addSlots();
        if (inv != null) {
            addInventorySlots(inv);
            openInventory(inv);
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }

    @Override
    public boolean canMergeSlot(ItemStack stack, Slot slot) {
        if (slot instanceof IInsertableSlot insertableSlot) {
            if (!insertableSlot.canMergeWith(stack)) {
                return false;
            }
            UUID player = getPlayerUUID();
            SelectedWindowData selectedWindow = isRemote() ? getSelectedWindow() : player == null ? null : getSelectedWindow(player);
            if (!insertableSlot.exists(selectedWindow)) {
                return false;
            }
        }
        return super.canMergeSlot(stack, slot);
    }

    @Override
    public void onContainerClosed(@Nonnull EntityPlayer player) {
        super.onContainerClosed(player);
        closeInventory(player);
    }

    protected void closeInventory(@Nonnull EntityPlayer player) {
        if (!player.world.isRemote) {
            clearSelectedWindow(player.getUniqueID());
        }
    }

    protected void openInventory(@Nonnull InventoryPlayer inv) {
    }

    protected int getInventoryYOffset() {
        return BASE_Y_OFFSET;
    }

    protected int getInventoryXOffset() {
        return 8;
    }

    protected void addInventorySlots(@Nonnull InventoryPlayer inv) {
        if (this instanceof IEmptyContainer) {
            return;
        }
        int yOffset = getInventoryYOffset();
        int xOffset = getInventoryXOffset();
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                addSlot(new MainInventorySlot(inv, InventoryPlayer.getHotbarSize() + slotX + slotY * 9, xOffset + slotX * 18, yOffset + slotY * 18));
            }
        }
        yOffset += 58;
        for (int slotX = 0; slotX < InventoryPlayer.getHotbarSize(); slotX++) {
            addSlot(createHotBarSlot(inv, slotX, xOffset + slotX * 18, yOffset));
        }
    }

    public static final EntityEquipmentSlot[] EQUIPMENT_SLOT_TYPES = EntityEquipmentSlot.values();

    protected void addArmorSlots(@Nonnull InventoryPlayer inv, int x, int y, int offhandOffset) {
        for (int index = 0; index < inv.armorInventory.size(); index++) {
            final EntityEquipmentSlot slotType = EQUIPMENT_SLOT_TYPES[2 + inv.armorInventory.size() - index - 1];
            addSlot(new ArmorSlot(inv, 36 + inv.armorInventory.size() - index - 1, x, y, slotType));
            y += 18;
        }
        if (offhandOffset != -1) {
            addSlot(new OffhandSlot(inv, 40, x, y + offhandOffset));
        }
    }

    protected HotBarSlot createHotBarSlot(@Nonnull InventoryPlayer inv, int index, int x, int y) {
        return new HotBarSlot(inv, index, x, y);
    }

    protected void addSlots() {
    }

    public List<InventoryContainerSlot> getInventoryContainerSlots() {
        return Collections.unmodifiableList(inventoryContainerSlots);
    }

    public List<MainInventorySlot> getMainInventorySlots() {
        return Collections.unmodifiableList(mainInventorySlots);
    }

    public List<HotBarSlot> getHotBarSlots() {
        return Collections.unmodifiableList(hotBarSlots);
    }

    @Nonnull
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (clickTypeIn == ClickType.PICKUP && (dragType == 0 || dragType == 1)) {
            ItemStack result = trySafeInsertPickupClick(slotId, dragType, player);
            if (result != null) {
                return result;
            }
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Nullable
    private ItemStack trySafeInsertPickupClick(int slotId, int dragType, EntityPlayer player) {
        if (slotId < 0 || slotId >= inventorySlots.size()) {
            return null;
        }
        ItemStack heldStack = player.inventory.getItemStack();
        if (heldStack.isEmpty()) {
            return null;
        }
        Slot slot = inventorySlots.get(slotId);
        if (!(slot instanceof IInsertableSlot insertableSlot)) {
            return null;
        }
        SelectedWindowData selectedWindow = player.world.isRemote ? getSelectedWindow() : getSelectedWindow(player.getUniqueID());
        if (!insertableSlot.exists(selectedWindow)) {
            return ItemStack.EMPTY;
        }
        if (!slot.isItemValid(heldStack)) {
            return null;
        }
        ItemStack slotStack = slot.getStack();
        ItemStack originalSlotStack = slotStack.isEmpty() ? ItemStack.EMPTY : slotStack.copy();
        ItemStack toInsert = heldStack.copy();
        toInsert.setCount(dragType == 0 ? heldStack.getCount() : 1);
        ItemStack remainder = insertableSlot.insertItem(toInsert, Action.EXECUTE);
        int inserted = toInsert.getCount() - remainder.getCount();
        if (inserted <= 0) {
            return null;
        }
        heldStack.shrink(inserted);
        if (heldStack.isEmpty()) {
            player.inventory.setItemStack(ItemStack.EMPTY);
        }
        return originalSlotStack;
    }

    /**
     * {@inheritDoc}
     *
     * @return The contents in this slot after transferring items away.
     */
    @Nonnull
    @Override
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer player, int slotID) {
        return quickMoveStack(player, slotID);
    }

    @Nonnull
    public ItemStack quickMoveStack(@Nonnull EntityPlayer player, int slotID) {
        Slot currentSlot = inventorySlots.get(slotID);
        if (currentSlot == null || !currentSlot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        SelectedWindowData selectedWindow = player.world.isRemote ? getSelectedWindow() : getSelectedWindow(player.getUniqueID());
        if (currentSlot instanceof IInsertableSlot insertableSlot && !insertableSlot.exists(selectedWindow)) {
            return ItemStack.EMPTY;
        }
        ItemStack slotStack = currentSlot.getStack();
        ItemStack stackToInsert = slotStack;
        if (currentSlot instanceof InventoryContainerSlot) {
            if (slotStack.getCount() > slotStack.getMaxStackSize()) {
                stackToInsert = slotStack = slotStack.copy();
                stackToInsert.setCount(slotStack.getMaxStackSize());
            }
            stackToInsert = insertItem(armorSlots, stackToInsert, true, selectedWindow);
            stackToInsert = insertItem(hotBarSlots, stackToInsert, true, selectedWindow);
            stackToInsert = insertItem(mainInventorySlots, stackToInsert, true, selectedWindow);
            stackToInsert = insertItem(armorSlots, stackToInsert, false, selectedWindow);
            stackToInsert = insertItem(hotBarSlots, stackToInsert, false, selectedWindow);
            stackToInsert = insertItem(mainInventorySlots, stackToInsert, false, selectedWindow);
        } else {
            stackToInsert = insertItem(inventoryContainerSlots, stackToInsert, true, selectedWindow);
            if (slotStack.getCount() == stackToInsert.getCount()) {
                stackToInsert = insertItem(inventoryContainerSlots, stackToInsert, false, selectedWindow);
                if (slotStack.getCount() == stackToInsert.getCount()) {
                    if (currentSlot instanceof ArmorSlot || currentSlot instanceof OffhandSlot) {
                        stackToInsert = insertItem(hotBarSlots, stackToInsert, true, selectedWindow);
                        stackToInsert = insertItem(mainInventorySlots, stackToInsert, true, selectedWindow);
                        stackToInsert = insertItem(hotBarSlots, stackToInsert, false, selectedWindow);
                        stackToInsert = insertItem(mainInventorySlots, stackToInsert, false, selectedWindow);
                    } else if (currentSlot instanceof MainInventorySlot) {
                        stackToInsert = insertItem(armorSlots, stackToInsert, false, selectedWindow);
                        stackToInsert = insertItem(hotBarSlots, stackToInsert, selectedWindow);
                    } else if (currentSlot instanceof HotBarSlot) {
                        stackToInsert = insertItem(armorSlots, stackToInsert, false, selectedWindow);
                        stackToInsert = insertItem(mainInventorySlots, stackToInsert, selectedWindow);
                    }
                }
            }
        }
        if (stackToInsert.getCount() == slotStack.getCount()) {
            return ItemStack.EMPTY;
        }
        return transferSuccess(currentSlot, player, slotStack, stackToInsert);
    }

    public static <SLOT extends Slot & IInsertableSlot> ItemStack insertItem(List<SLOT> slots, @Nonnull ItemStack stack, @Nullable SelectedWindowData selectedWindow) {
        stack = insertItem(slots, stack, true, selectedWindow);
        return insertItem(slots, stack, false, selectedWindow);
    }

    public static <SLOT extends Slot & IInsertableSlot> ItemStack insertItem(List<SLOT> slots, @Nonnull ItemStack stack, boolean ignoreEmpty,
          @Nullable SelectedWindowData selectedWindow) {
        return insertItem(slots, stack, ignoreEmpty, selectedWindow, Action.EXECUTE);
    }

    @Nonnull
    public static <SLOT extends Slot & IInsertableSlot> ItemStack insertItem(List<SLOT> slots, @Nonnull ItemStack stack, boolean ignoreEmpty,
          @Nullable SelectedWindowData selectedWindow, @Nonnull Action action) {
        return insertItem(slots, stack, ignoreEmpty, false, selectedWindow, action);
    }

    @Nonnull
    public static <SLOT extends Slot & IInsertableSlot> ItemStack insertItemCheckAll(List<SLOT> slots, @Nonnull ItemStack stack,
          @Nullable SelectedWindowData selectedWindow, @Nonnull Action action) {
        return insertItem(slots, stack, false, true, selectedWindow, action);
    }

    @Nonnull
    public static <SLOT extends Slot & IInsertableSlot> ItemStack insertItem(List<SLOT> slots, @Nonnull ItemStack stack, boolean ignoreEmpty, boolean checkAll,
          @Nullable SelectedWindowData selectedWindow, @Nonnull Action action) {
        if (stack.isEmpty()) {
            return stack;
        }
        for (SLOT slot : slots) {
            if (!checkAll && ignoreEmpty != slot.getHasStack()) {
                continue;
            } else if (!slot.exists(selectedWindow)) {
                continue;
            }
            stack = slot.insertItem(stack, action);
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack;
    }

    @Nonnull
    protected ItemStack transferSuccess(@Nonnull Slot currentSlot, @Nonnull EntityPlayer player, @Nonnull ItemStack slotStack, @Nonnull ItemStack stackToInsert) {
        int difference = slotStack.getCount() - stackToInsert.getCount();
        ItemStack newStack = currentSlot.decrStackSize(difference);
        currentSlot.onTake(player, newStack);
        return newStack;
    }

    /**
     * @apiNote Only call on client.
     */
    @Nullable
    public SelectedWindowData getSelectedWindow() {
        return selectedWindow;
    }

    /**
     * @apiNote Only call on server.
     */
    @Nullable
    public SelectedWindowData getSelectedWindow(UUID player) {
        return selectedWindows == null ? null : selectedWindows.get(player);
    }

    /**
     * @apiNote Only call on client.
     */
    public void setSelectedWindow(@Nullable SelectedWindowData selectedWindow) {
        if (!Objects.equals(this.selectedWindow, selectedWindow)) {
            this.selectedWindow = selectedWindow;
            if (isRemote()) {
                Mekanism.packetHandler.sendToServer(new WindowSelectMessage(selectedWindow));
            }
        }
    }

    /**
     * @apiNote Only call on server.
     */
    public void setSelectedWindow(UUID player, @Nullable SelectedWindowData selectedWindow) {
        if (selectedWindow == null) {
            clearSelectedWindow(player);
        } else if (selectedWindows != null) {
            selectedWindows.put(player, selectedWindow);
        }
    }

    /**
     * @apiNote Only call on server.
     */
    protected void clearSelectedWindow(UUID player) {
        if (selectedWindows != null) {
            selectedWindows.remove(player);
        }
    }

    public void startTrackingServer(Object key, ISpecificContainerTracker tracker) {
        int currentSize = trackedData.size();
        List<ISyncableData> list = startTracking(key, tracker);
        if (inv != null && inv.player instanceof EntityPlayerMP player) {
            sendInitialDataToRemote(player, list, index -> (short) (index + currentSize));
        }
    }

    public List<ISyncableData> startTracking(Object key, ISpecificContainerTracker tracker) {
        List<ISyncableData> list = tracker.getSpecificSyncableData();
        for (ISyncableData data : list) {
            track(data);
        }
        specificTrackedData.put(key, list);
        return list;
    }

    public void stopTracking(Object key) {
        List<ISyncableData> list = specificTrackedData.remove(key);
        if (list != null) {
            trackedData.removeAll(list);
        }
    }

    public void track(ISyncableData data) {
        trackedData.add(data);
    }

    public void trackArray(boolean[] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            track(SyncableBoolean.create(arrayIn, i));
        }
    }

    public void trackArray(byte[] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            track(SyncableByte.create(arrayIn, i));
        }
    }

    public void trackArray(double[] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            track(SyncableDouble.create(arrayIn, i));
        }
    }

    public void trackArray(float[] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            track(SyncableFloat.create(arrayIn, i));
        }
    }

    public void trackArray(int[] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            track(SyncableInt.create(arrayIn, i));
        }
    }

    public void trackArray(long[] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            track(SyncableLong.create(arrayIn, i));
        }
    }

    public void trackArray(short[] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            track(SyncableShort.create(arrayIn, i));
        }
    }

    public void trackArray(boolean[][] arrayIn) {
        for (int i = 0; i < arrayIn.length; i++) {
            for (int j = 0; j < arrayIn[i].length; j++) {
                track(SyncableBoolean.create(arrayIn, i, j));
            }
        }
    }

    @Nullable
    private ISyncableData getTrackedData(short property) {
        if (property >= 0 && property < trackedData.size()) {
            return trackedData.get(property);
        }
        Mekanism.logger.warn("Received out of bounds window property {} for container {}. There are currently {} tracked properties.",
              property, getClass().getSimpleName(), trackedData.size());
        return null;
    }

    public void handleWindowProperty(short property, boolean value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableBoolean syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, byte value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableByte syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, short value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableShort syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, int value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableInt syncable) {
            syncable.set(value);
        } else if (data instanceof SyncableEnum<?> syncable) {
            syncable.set(value);
        } else if (data instanceof SyncableItemStack syncable) {
            syncable.set(value);
        } else if (data instanceof SyncableFluidStack syncable) {
            syncable.set(value);
        } else if (data instanceof SyncableGasStack syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, long value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableLong syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, float value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableFloat syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, double value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableDouble syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, @Nonnull ItemStack value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableItemStack syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, @Nullable FluidStack value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableFluidStack syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, @Nullable GasStack value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableGasStack syncable) {
            syncable.set(value);
        }
    }

    public void handleWindowProperty(short property, @Nullable Frequency value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableFrequency<?> syncable) {
            syncable.set(value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void handleWindowProperty(short property, @Nonnull HashList<? extends IFilter> value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableFilterList syncable) {
            syncable.set(value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void handleWindowProperty(short property, @Nonnull List<? extends Frequency> value) {
        ISyncableData data = getTrackedData(property);
        if (data instanceof SyncableFrequencyList syncable) {
            syncable.set(value);
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (trackedData.isEmpty()) {
            return;
        }
        List<PropertyData> dirtyData = new ArrayList<>();
        for (short i = 0; i < trackedData.size(); i++) {
            ISyncableData data = trackedData.get(i);
            DirtyType dirtyType = data.isDirty();
            if (dirtyType != DirtyType.CLEAN) {
                dirtyData.add(data.getPropertyData(i, dirtyType));
            }
        }
        if (!dirtyData.isEmpty()) {
            for (IContainerListener listener : listeners) {
                if (listener instanceof EntityPlayerMP player) {
                    Mekanism.packetHandler.sendTo(new UpdateContainerMessage((short) windowId, dirtyData), player);
                }
            }
        }
    }

    private void sendInitialDataToRemote(EntityPlayerMP player, List<ISyncableData> syncableData, IntUnaryOperator propertyIndex) {
        if (syncableData.isEmpty()) {
            return;
        }
        List<PropertyData> dirtyData = new ArrayList<>();
        for (short i = 0; i < syncableData.size(); i++) {
            ISyncableData data = syncableData.get(i);
            data.isDirty();
            dirtyData.add(data.getPropertyData((short) propertyIndex.applyAsInt(i), DirtyType.DIRTY));
        }
        if (!dirtyData.isEmpty()) {
            Mekanism.packetHandler.sendTo(new UpdateContainerMessage((short) windowId, dirtyData), player);
        }
    }

    public interface ISpecificContainerTracker {

        List<ISyncableData> getSpecificSyncableData();
    }
}
