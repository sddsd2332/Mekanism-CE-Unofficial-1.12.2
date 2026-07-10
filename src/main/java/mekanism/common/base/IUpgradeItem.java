package mekanism.common.base;

import mekanism.common.Upgrade;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;

public interface IUpgradeItem {

    Upgrade getUpgradeType(ItemStack stack);

    default boolean canInstallUpgrade(ItemStack stack, IUpgradeTile tile) {
        return true;
    }

    @Nullable
    default ITextComponent getInstallFailureMessage(ItemStack stack, IUpgradeTile tile) {
        return null;
    }

    default void onInstalled(ItemStack stack, IUpgradeTile tile, int amount) {
    }

    default ItemStack getUninstalledStack(IUpgradeTile tile, Upgrade upgrade, int amount, ItemStack defaultStack) {
        return defaultStack;
    }
}
