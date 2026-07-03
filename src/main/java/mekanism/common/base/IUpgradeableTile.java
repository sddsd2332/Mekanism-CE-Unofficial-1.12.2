package mekanism.common.base;

import mekanism.common.tier.BaseTier;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.block.state.IBlockState;

import javax.annotation.Nullable;

/**
 * High-version style bridge for blocks upgraded by tier installers.
 */
public interface IUpgradeableTile {

    default boolean canInstallUpgrade(BaseTier upgradeTier) {
        return false;
    }

    @Nullable
    default IBlockState getUpgradeResult(BaseTier upgradeTier) {
        return null;
    }

    default void prepareForUpgrade() {
    }

    @Nullable
    default IUpgradeData getUpgradeData(BaseTier upgradeTier) {
        return null;
    }

    default boolean parseUpgradeData(IUpgradeData upgradeData) {
        return false;
    }
}
