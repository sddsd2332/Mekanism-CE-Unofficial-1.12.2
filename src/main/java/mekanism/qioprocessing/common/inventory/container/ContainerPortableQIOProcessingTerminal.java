package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.item.ItemStackSlotAccess;
import mekanism.common.inventory.container.item.MekanismItemContainer;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.slot.IVirtualSlot;
import mekanism.common.inventory.container.slot.MainInventorySlot;
import mekanism.common.inventory.container.sync.FrequencyContainerSync;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.QIOProcessingFrequencyAccess;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exact-stack portable terminal container with generation and duplicate identity checks. */
public class ContainerPortableQIOProcessingTerminal extends MekanismItemContainer
      implements QIOPortableTerminalContainer, QIOManagementDevicePageContainer,
      QIOManagementPolicyPageContainer, QIOManagementRecipeContainer,
      QIOWorkbenchConfigurationContainer,
      QIOMaintenanceRulePageContainer,
      QIOMaintenanceResourcePageContainer,
      QIOCraftingMonitorPageContainer, QIOProcessingTerminalFrequencyContainer {

    private final QIOPortableTerminalSessionTarget sessionTarget;
    private final QIOProcessingTerminalContainerState terminalState;
    private final QIOManagementDeviceClientCache deviceClientCache =
          new QIOManagementDeviceClientCache();
    private final QIOManagementDeviceGroupClientCache deviceGroupClientCache =
          new QIOManagementDeviceGroupClientCache();
    private final QIOManagementPolicyClientCache policyClientCache =
          new QIOManagementPolicyClientCache();
    private final QIOManagementRecipeClientCache managementRecipeClientCache =
          new QIOManagementRecipeClientCache();
    private final QIOWorkbenchConfigurationClientCache workbenchConfigurationClientCache =
          new QIOWorkbenchConfigurationClientCache();
    private final QIOMaintenanceRuleClientCache maintenanceRuleClientCache =
          new QIOMaintenanceRuleClientCache();
    private final QIOSmartProcessingClientCache maintenanceResourceClientCache =
          new QIOSmartProcessingClientCache();
    private final QIOCraftingMonitorClientCache craftingMonitorClientCache =
          new QIOCraftingMonitorClientCache();
    private final FrequencyContainerSync<QIOFrequency> frequencySync =
          new FrequencyContainerSync<>();
    private final QIOManagementPageRequestThrottle managementPageThrottle =
          new QIOManagementPageRequestThrottle();
    private long lastMaintenanceRuleRequestTick = Long.MIN_VALUE;
    private long lastMaintenanceResourceRequestTick = Long.MIN_VALUE;
    private long lastCraftingMonitorRequestTick = Long.MIN_VALUE;
    private long lastCraftingMonitorDetailRequestTick = Long.MIN_VALUE;

    public ContainerPortableQIOProcessingTerminal(InventoryPlayer inventory,
          EnumHand hand, int itemSlot, ItemStack openingStack) {
        super(inventory, hand, itemSlot, openingStack);
        sessionTarget = new QIOPortableTerminalSessionTarget(inventory, itemAccess,
              isRemote());
        terminalState = new QIOProcessingTerminalContainerState(this,
              this::getTerminalSession);
        frequencySync.addTrackers(this, FrequencyType.QIO,
              this::resolveCurrentFrequency);
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
    public int getTerminalWindowId() {
        return windowId;
    }

    @Nullable
    @Override
    public QIOFrequency getTerminalFrequency() {
        return isRemote() ? frequencySync.getFrequency() : resolveCurrentFrequency();
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

    @Override
    public QIOManagementDeviceClientCache getDeviceClientCache() {
        return deviceClientCache;
    }

    @Override
    public QIOManagementDeviceGroupClientCache getDeviceGroupClientCache() {
        return deviceGroupClientCache;
    }

    @Override
    public QIOManagementPolicyClientCache getPolicyClientCache() {
        return policyClientCache;
    }

    @Override
    public boolean tryRequestDevicePage(long currentTick) {
        return managementPageThrottle.tryDevice(currentTick);
    }

    @Override
    public boolean tryRequestDeviceGroupPage(long currentTick) {
        return managementPageThrottle.tryDeviceGroup(currentTick);
    }

    @Override
    public boolean tryRequestPolicyPage(long currentTick) {
        return managementPageThrottle.tryPolicy(currentTick);
    }

    @Override
    public boolean tryRequestPolicyLookup(long currentTick) {
        return managementPageThrottle.tryPolicyLookup(currentTick);
    }

    @Override
    public boolean tryRequestDeviceCommand(long currentTick) {
        return managementPageThrottle.tryDeviceCommand(currentTick);
    }

    @Override
    public QIOManagementRecipeClientCache getManagementRecipeClientCache() {
        return managementRecipeClientCache;
    }

    @Override
    public boolean tryRequestManagementRecipe(long currentTick) {
        return managementPageThrottle.tryRecipe(currentTick);
    }

    @Override
    public QIOWorkbenchConfigurationClientCache getWorkbenchConfigurationClientCache() {
        return workbenchConfigurationClientCache;
    }

    @Nonnull
    @Override
    public List<? extends IVirtualSlot> getWorkbenchEditorInventorySlots() {
        List<IVirtualSlot> slots = new ArrayList<>(36);
        for (MainInventorySlot slot : mainInventorySlots) {
            if (slot instanceof IVirtualSlot virtualSlot) {
                slots.add(virtualSlot);
            }
        }
        for (HotBarSlot slot : hotBarSlots) {
            if (slot instanceof IVirtualSlot virtualSlot) {
                slots.add(virtualSlot);
            }
        }
        return slots;
    }

    @Override
    public boolean tryRequestWorkbenchConfiguration(long currentTick,
          @Nonnull RequestStream requestStream) {
        return managementPageThrottle.tryWorkbenchConfiguration(currentTick, requestStream);
    }

    @Override
    public QIOMaintenanceRuleClientCache getMaintenanceRuleClientCache() {
        return maintenanceRuleClientCache;
    }

    @Override
    public boolean tryRequestMaintenanceRulePage(long currentTick) {
        if (currentTick <= lastMaintenanceRuleRequestTick) {
            return false;
        }
        lastMaintenanceRuleRequestTick = currentTick;
        return true;
    }

    @Override
    public QIOSmartProcessingClientCache getMaintenanceResourceClientCache() {
        return maintenanceResourceClientCache;
    }

    @Override
    public boolean tryRequestMaintenanceResourcePage(long currentTick) {
        if (currentTick <= lastMaintenanceResourceRequestTick) {
            return false;
        }
        lastMaintenanceResourceRequestTick = currentTick;
        return true;
    }

    @Override
    public QIOCraftingMonitorClientCache getCraftingMonitorClientCache() {
        return craftingMonitorClientCache;
    }

    @Override
    public boolean tryRequestCraftingMonitorPage(long currentTick) {
        if (currentTick <= lastCraftingMonitorRequestTick) return false;
        lastCraftingMonitorRequestTick = currentTick;
        return true;
    }

    @Override
    public boolean tryRequestCraftingMonitorDetail(long currentTick) {
        if (currentTick <= lastCraftingMonitorDetailRequestTick) return false;
        lastCraftingMonitorDetailRequestTick = currentTick;
        return true;
    }

    @Nonnull
    @Override
    public ItemStack getPortableStack() {
        return getStack();
    }

    @Override
    public boolean isPortableTargetUsable(@Nonnull EntityPlayer player) {
        return canInteractWith(player);
    }

    @Override
    protected boolean isValidStack(@Nonnull ItemStack current) {
        return super.isValidStack(current) &&
              current.getItem() instanceof ItemPortableQIOProcessingTerminal;
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        if (!super.canInteractWith(player) || player.world.isRemote) {
            return player.world.isRemote && super.canInteractWith(player);
        }
        return sessionTarget.validate(player);
    }

    public boolean acceptAuthorizedMutation(long expectedGeneration,
          long updatedGeneration) {
        return sessionTarget.acceptMutation(expectedGeneration, updatedGeneration);
    }

    @Override
    protected HotBarSlot createHotBarSlot(@Nonnull InventoryPlayer inventory, int index,
          int x, int y) {
        if (hand == EnumHand.MAIN_HAND && index == getItemSlot()) {
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
            if (hand == EnumHand.OFF_HAND && dragType == ItemStackSlotAccess.OFFHAND_SLOT) {
                return ItemStack.EMPTY;
            }
            if (hand == EnumHand.MAIN_HAND && dragType >= 0 &&
                dragType < hotBarSlots.size() &&
                !hotBarSlots.get(dragType).canTakeStack(player)) {
                return ItemStack.EMPTY;
            }
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        deviceClientCache.clear();
        deviceGroupClientCache.clear();
        policyClientCache.clear();
        managementRecipeClientCache.clear();
        workbenchConfigurationClientCache.clear();
        maintenanceRuleClientCache.clear();
        maintenanceResourceClientCache.clear();
        craftingMonitorClientCache.clear();
        sessionTarget.close();
        super.closeInventory(player);
    }

    @Override
    protected void addInventorySlots(@Nonnull InventoryPlayer inventory) {
        ItemStack stack = getStack();
        if (!(stack.getItem() instanceof ItemPortableQIOProcessingTerminal item) ||
            item.getTerminalType() !=
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT &&
            item.getTerminalType() !=
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            super.addInventorySlots(inventory);
            return;
        }

        if (item.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            addWorkbenchPatternInventorySlots(inventory);
            track(SyncableItemStack.create(() -> inventory.player.getHeldItemOffhand(),
                  updated -> inventory.offHandInventory.set(0, updated)));
            return;
        }

        // Empty portable screens still need live hand/hotbar tracking for exact-stack validation.
        track(SyncableItemStack.create(() -> inventory.player.getHeldItemOffhand(),
              updated -> inventory.offHandInventory.set(0, updated)));
        if (hand == EnumHand.MAIN_HAND) {
            track(SyncableItemStack.create(itemAccess::getStack, itemAccess::setStack));
        }
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); slot++) {
            if (hand == EnumHand.MAIN_HAND && slot == itemAccess.getSlot()) continue;
            int trackedSlot = slot;
            track(SyncableItemStack.create(() -> inventory.mainInventory.get(trackedSlot),
                  updated -> inventory.mainInventory.set(trackedSlot, updated)));
        }
    }

    private void addWorkbenchPatternInventorySlots(InventoryPlayer inventory) {
        int x = QIOWorkbenchEditorSlots.INVENTORY_X;
        int y = QIOWorkbenchEditorSlots.INVENTORY_Y;
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                MainInventorySlot slot = QIOWorkbenchEditorSlots.main(this, inventory,
                      InventoryPlayer.getHotbarSize() + slotX + slotY * 9,
                      x + slotX * 18, y + slotY * 18);
                addSlot(slot);
            }
        }
        for (int slotX = 0; slotX < InventoryPlayer.getHotbarSize(); slotX++) {
            boolean locked = hand == EnumHand.MAIN_HAND && slotX == itemAccess.getSlot();
            HotBarSlot slot = QIOWorkbenchEditorSlots.hotbar(this, inventory, slotX,
                  x + slotX * 18, y + 58, locked);
            addSlot(slot);
        }
    }

    @Override
    protected int getInventoryYOffset() {
        ItemStack stack = getStack();
        if (stack.getItem() instanceof ItemPortableQIOProcessingTerminal item) {
            if (item.getTerminalType() ==
                mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
                return 280;
            }
            if (item.getTerminalType() ==
                mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
                return 258;
            }
            if (item.getTerminalType() ==
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
                return 134;
            }
        }
        return super.getInventoryYOffset();
    }

    @Override
    protected int getInventoryXOffset() {
        ItemStack stack = getStack();
        if (stack.getItem() instanceof ItemPortableQIOProcessingTerminal item &&
            item.getTerminalType() ==
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            return 227;
        }
        if (stack.getItem() instanceof ItemPortableQIOProcessingTerminal item &&
            item.getTerminalType() ==
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            return 38;
        }
        return super.getInventoryXOffset();
    }

    @Nullable
    private QIOFrequency resolveCurrentFrequency() {
        ItemStack stack = getPortableStack();
        if (stack.isEmpty() ||
            !(stack.getItem() instanceof ItemPortableQIOProcessingTerminal item)) {
            return null;
        }
        return QIOProcessingFrequencyAccess.resolveAccessible(
              item.getFrequencyReference(stack), inv.player.getUniqueID());
    }
}
