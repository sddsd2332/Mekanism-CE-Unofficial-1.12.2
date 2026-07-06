package mekanism.common.inventory.slot;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.slot.VirtualInventoryContainerSlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

public class UpgradeInventorySlot extends BasicInventorySlot {

    public static UpgradeInventorySlot input(IUpgradeTile tile, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(tile, "Upgrade tile cannot be null");
        return input(tile.getSupportedUpgradeTypes(), listener);
    }

    public static UpgradeInventorySlot input(@Nullable IContentsListener listener, Set<Upgrade> supportedTypes) {
        return input(supportedTypes, listener);
    }

    public static UpgradeInventorySlot input(Set<Upgrade> supportedTypes, @Nullable IContentsListener listener, int x, int y) {
        return input(supportedTypes, listener);
    }

    public static UpgradeInventorySlot input(Set<Upgrade> supportedTypes, @Nullable IContentsListener listener) {
        Objects.requireNonNull(supportedTypes, "Supported upgrade types cannot be null");
        return new UpgradeInventorySlot(listener, (stack, automationType) -> isSupportedUpgrade(stack, supportedTypes));
    }

    public static UpgradeInventorySlot output(@Nullable IContentsListener listener, int x, int y) {
        return output(listener);
    }

    public static UpgradeInventorySlot output(@Nullable IContentsListener listener) {
        return new UpgradeInventorySlot(listener, internalOnly);
    }

    public static UpgradeInventorySlot any(@Nullable IContentsListener listener, int x, int y) {
        return any(listener);
    }

    public static UpgradeInventorySlot any(@Nullable IContentsListener listener) {
        return new UpgradeInventorySlot(listener, (stack, automationType) -> Upgrade.isUpgrade(stack));
    }

    private static boolean isSupportedUpgrade(ItemStack stack, Set<Upgrade> supportedTypes) {
        Upgrade upgradeType = Upgrade.byStack(stack);
        return upgradeType != null && supportedTypes.contains(upgradeType);
    }

    private UpgradeInventorySlot(@Nullable IContentsListener listener, BiPredicate<ItemStack, AutomationType> canInsert) {
        super(manualOnly, canInsert, Upgrade::isUpgrade, listener, 0, 0);
        setSlotOverlay(SlotOverlay.UPGRADE);
    }

    @Nonnull
    @Override
    public VirtualInventoryContainerSlot createContainerSlot() {
        return new VirtualInventoryContainerSlot(this, new SelectedWindowData(WindowType.UPGRADE), getSlotOverlay(), this::setStackUnchecked);
    }
}
