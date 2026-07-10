package mekanism.common.base;

import mekanism.common.Upgrade;
import mekanism.common.inventory.slot.UpgradeInventorySlot;
import mekanism.common.tile.component.TileComponentUpgrade;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import mekanism.api.Action;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface IUpgradeTile extends IGetBackMachine {

    default boolean supportsUpgrades() {
        return true;
    }

    default boolean supportsUpgrade(Upgrade upgradeType) {
        return supportsUpgrades() && getComponent().supports(upgradeType);
    }

    default Set<Upgrade> getSupportedUpgradeTypes() {
        return getComponent().getSupportedTypes();
    }

    default void setSupportedUpgrade(Upgrade upgradeType) {
        getComponent().setSupported(upgradeType);
    }

    default void setSupportedUpgrade(Upgrade upgradeType, boolean supported) {
        getComponent().setSupported(upgradeType, supported);
    }

    default void setSupportedUpgrades(Upgrade... upgradeTypes) {
        getComponent().setSupported(upgradeTypes);
    }

    default void setSupportedUpgrades(Collection<Upgrade> upgradeTypes) {
        getComponent().setSupported(upgradeTypes);
    }

    default void removeSupportedUpgrade(Upgrade upgradeType) {
        getComponent().removeSupported(upgradeType);
    }

    default void removeSupportedUpgrades(Upgrade... upgradeTypes) {
        getComponent().removeSupported(upgradeTypes);
    }

    default void removeSupportedUpgrades(Collection<Upgrade> upgradeTypes) {
        getComponent().removeSupported(upgradeTypes);
    }

    default void clearSupportedUpgrades() {
        getComponent().clearSupportedTypes();
    }

    default double getScaledUpgradeProgress() {
        return getComponent().getScaledUpgradeProgress();
    }

    default UpgradeInventorySlot getUpgradeSlot() {
        return getComponent().getUpgradeSlot();
    }

    default UpgradeInventorySlot getUpgradeOutputSlot() {
        return getComponent().getUpgradeOutputSlot();
    }

    default void readUpgrades(NBTTagCompound nbtTags) {
        getComponent().read(nbtTags);
    }

    default void writeUpgrades(NBTTagCompound nbtTags) {
        getComponent().write(nbtTags);
    }

    default int getInstalledUpgrades(Upgrade upgradeType) {
        return getComponent().getUpgrades(upgradeType);
    }

    default boolean isUpgradeInstalled(Upgrade upgradeType) {
        return getInstalledUpgrades(upgradeType) > 0;
    }

    default Map<Upgrade, Integer> getInstalledUpgrades() {
        return getComponent().getInstalledUpgrades();
    }

    default Set<Upgrade> getInstalledUpgradeTypes() {
        return getComponent().getInstalledTypes();
    }

    default int setInstalledUpgrades(Upgrade upgradeType, int amount) {
        return getComponent().setUpgrades(upgradeType, amount);
    }

    default int addInstalledUpgrades(Upgrade upgradeType, int amount) {
        return getComponent().addUpgrades(upgradeType, amount);
    }

    default int getUpgradeInstallRoom(Upgrade upgradeType) {
        return getComponent().getInstallRoom(upgradeType);
    }

    default boolean canInstallUpgrade(Upgrade upgradeType) {
        return getComponent().canInstall(upgradeType);
    }

    default boolean canInstallUpgrade(ItemStack stack) {
        return getComponent().canInstall(stack);
    }

    default int installUpgrade(ItemStack stack, Action action) {
        return getComponent().installUpgrade(stack, action);
    }

    default void clearInstalledUpgrades() {
        getComponent().clearUpgrades();
    }

    default void removeInstalledUpgrade(Upgrade upgradeType, boolean removeAll) {
        getComponent().removeUpgrade(upgradeType, removeAll);
    }

    TileComponentUpgrade getComponent();
}
