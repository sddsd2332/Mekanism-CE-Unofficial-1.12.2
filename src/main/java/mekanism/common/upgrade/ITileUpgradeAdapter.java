package mekanism.common.upgrade;

import mekanism.common.tier.BaseTier;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;

/**
 * External adapter for tier-installer behavior on a tile class that cannot directly implement Mekanism's upgrade API.
 */
public interface ITileUpgradeAdapter<TILE extends TileEntity> {

    default boolean canInstallUpgrade(TILE tile, BaseTier upgradeTier) {
        return false;
    }

    @Nullable
    default IBlockState getUpgradeResult(TILE tile, BaseTier upgradeTier) {
        return null;
    }

    default void prepareForUpgrade(TILE tile) {
    }

    @Nullable
    default IUpgradeData getUpgradeData(TILE tile, BaseTier upgradeTier) {
        return null;
    }

    default boolean parseUpgradeData(TILE tile, IUpgradeData upgradeData) {
        return false;
    }
}
