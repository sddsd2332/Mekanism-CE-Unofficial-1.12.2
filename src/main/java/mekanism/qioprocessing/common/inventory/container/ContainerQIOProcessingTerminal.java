package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.slot.IVirtualSlot;
import mekanism.common.inventory.container.slot.MainInventorySlot;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shared secured block container for management, request, maintenance and monitor terminals. */
public class ContainerQIOProcessingTerminal extends
      MekanismTileContainer<QIOProcessingTerminal>
      implements QIOBlockTerminalContainer, QIOManagementDevicePageContainer,
      QIOManagementPolicyPageContainer, QIOManagementRecipeContainer,
      QIOWorkbenchConfigurationContainer,
      QIOMaintenanceRulePageContainer,
      QIOMaintenanceResourcePageContainer,
      QIOCraftingMonitorPageContainer, QIOProcessingTerminalFrequencyContainer {

    private final QIOBlockTerminalSessionTarget sessionTarget;
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
    private final QIOManagementPageRequestThrottle managementPageThrottle =
          new QIOManagementPageRequestThrottle();
    private long lastMaintenanceRuleRequestTick = Long.MIN_VALUE;
    private long lastMaintenanceResourceRequestTick = Long.MIN_VALUE;
    private long lastCraftingMonitorRequestTick = Long.MIN_VALUE;
    private long lastCraftingMonitorDetailRequestTick = Long.MIN_VALUE;

    public ContainerQIOProcessingTerminal(InventoryPlayer inventory,
          QIOProcessingTerminal terminal) {
        super(terminal, inventory);
        sessionTarget = new QIOBlockTerminalSessionTarget(inventory, terminal, isRemote());
        terminalState = new QIOProcessingTerminalContainerState(this,
              this::getTerminalSession);
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
        return tile.getQIOFrequency() == null && isRemote() &&
              tile.getFrequencyReference() != null ? clientFrequency(
              tile.getFrequencyReference().getFrequencyUUID()) : tile.getQIOFrequency();
    }

    @Override
    public List<QIOFrequency> getPublicTerminalFrequencies() {
        return tile.getPublicCache(FrequencyType.QIO);
    }

    @Override
    public List<QIOFrequency> getPrivateTerminalFrequencies() {
        return tile.getPrivateCache(FrequencyType.QIO);
    }

    @Override
    public List<QIOFrequency> getTrustedTerminalFrequencies() {
        return tile.getTrustedCache(FrequencyType.QIO);
    }

    @Nullable
    @Override
    public UUID getTerminalOwnerUUID() {
        return tile.getSecurity().getOwnerUUID();
    }

    @Override
    public String getTerminalOwnerName() {
        String owner = tile.getSecurity().getClientOwner();
        return owner == null ? "" : owner;
    }

    @Override
    public boolean isPortableTerminal() {
        return false;
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

    @Override
    public QIOProcessingTerminal getTerminalTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (!super.canInteractWith(player) || player.world.isRemote) {
            return player.world.isRemote && super.canInteractWith(player);
        }
        return sessionTarget.validate(player);
    }

    public boolean acceptAuthorizedBindingChange(long expectedRevision,
          long updatedRevision) {
        return sessionTarget.acceptMutation(expectedRevision, updatedRevision);
    }

    @Override
    protected void closeInventory(EntityPlayer player) {
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
        if (tile != null && tile.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            addWorkbenchPatternInventorySlots(inventory);
            return;
        }
        if (tile != null && tile.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            return;
        }
        super.addInventorySlots(inventory);
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
            HotBarSlot slot = QIOWorkbenchEditorSlots.hotbar(this, inventory, slotX,
                  x + slotX * 18, y + 58, false);
            addSlot(slot);
        }
    }

    @Override
    protected int getInventoryYOffset() {
        if (tile != null && tile.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            return 280;
        }
        if (tile != null && tile.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            return 258;
        }
        return tile != null && tile.getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE ?
              134 : super.getInventoryYOffset();
    }

    @Override
    protected int getInventoryXOffset() {
        if (tile != null && tile.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            return 227;
        }
        if (tile != null && tile.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            return 38;
        }
        return super.getInventoryXOffset();
    }

    @Nullable
    private QIOFrequency clientFrequency(UUID frequencyUUID) {
        for (QIOFrequency frequency : getPublicTerminalFrequencies()) {
            if (frequencyUUID.equals(frequency.getFrequencyUUID())) {
                return frequency;
            }
        }
        for (QIOFrequency frequency : getPrivateTerminalFrequencies()) {
            if (frequencyUUID.equals(frequency.getFrequencyUUID())) {
                return frequency;
            }
        }
        for (QIOFrequency frequency : getTrustedTerminalFrequencies()) {
            if (frequencyUUID.equals(frequency.getFrequencyUUID())) {
                return frequency;
            }
        }
        return null;
    }
}
