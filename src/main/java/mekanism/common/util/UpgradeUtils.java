package mekanism.common.util;

import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.base.IUpgradeableTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.tier.BaseTier;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.upgrade.TileUpgradeRegistry;
import mekanism.common.tile.base.TileEntityRestrictedTick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;

public class UpgradeUtils {

    private UpgradeUtils() {
    }

    public static ItemStack getStack(Upgrade upgrade) {
        return getStack(upgrade, 1);
    }

    public static ItemStack getStack(Upgrade upgrade, int count) {
        return upgrade == null ? ItemStack.EMPTY : upgrade.getStack(count);
    }

    public static List<String> getInfo(TileEntity tile, Upgrade upgrade) {
        return upgrade == null ? Collections.emptyList() : upgrade.getInfo(tile);
    }

    public static List<String> getMultScaledInfo(IUpgradeTile tile, Upgrade upgrade) {
        return upgrade == null ? Collections.emptyList() : upgrade.getMultScaledInfo(tile);
    }

    public static List<String> getExpScaledInfo(IUpgradeTile tile, Upgrade upgrade) {
        return upgrade == null ? Collections.emptyList() : upgrade.getExpScaledInfo(tile);
    }

    public static boolean isUpgradeable(TileEntity tile) {
        return tile instanceof IUpgradeableTile || TileUpgradeRegistry.find(tile) != null;
    }

    public static boolean canInstallUpgrade(TileEntity tile, BaseTier upgradeTier) {
        if (tile == null || upgradeTier == null) {
            return false;
        }
        TileUpgradeRegistry.BoundAdapter adapter = TileUpgradeRegistry.find(tile);
        return adapter != null && adapter.canInstallUpgrade(upgradeTier) ||
              tile instanceof IUpgradeableTile upgradeable && upgradeable.canInstallUpgrade(upgradeTier);
    }

    public static IBlockState getUpgradeResult(TileEntity tile, BaseTier upgradeTier) {
        TileUpgradeRegistry.BoundAdapter adapter = TileUpgradeRegistry.find(tile);
        if (adapter != null && adapter.canInstallUpgrade(upgradeTier)) {
            IBlockState result = adapter.getUpgradeResult(upgradeTier);
            if (result != null) {
                return result;
            }
        }
        return tile instanceof IUpgradeableTile upgradeable ? upgradeable.getUpgradeResult(upgradeTier) : null;
    }

    public static IUpgradeData getUpgradeData(TileEntity tile, BaseTier upgradeTier) {
        TileUpgradeRegistry.BoundAdapter adapter = TileUpgradeRegistry.find(tile);
        if (adapter != null && adapter.canInstallUpgrade(upgradeTier)) {
            IUpgradeData data = adapter.getUpgradeData(upgradeTier);
            if (data != null) {
                return data;
            }
        }
        return tile instanceof IUpgradeableTile upgradeable ? upgradeable.getUpgradeData(upgradeTier) : null;
    }

    public static void prepareForUpgrade(TileEntity tile) {
        TileUpgradeRegistry.BoundAdapter adapter = TileUpgradeRegistry.find(tile);
        if (adapter != null) {
            adapter.prepareForUpgrade();
        }
        if (tile instanceof IUpgradeableTile upgradeable) {
            upgradeable.prepareForUpgrade();
        }
        if (tile instanceof TileEntityRestrictedTick restrictedTick) {
            restrictedTick.suppressRadiationForUpgrade();
        }
    }

    public static boolean parseUpgradeData(TileEntity tile, IUpgradeData upgradeData) {
        if (tile == null || upgradeData == null) {
            return false;
        }
        TileUpgradeRegistry.BoundAdapter adapter = TileUpgradeRegistry.find(tile);
        if (adapter != null && adapter.parseUpgradeData(upgradeData)) {
            return true;
        }
        return tile instanceof IUpgradeableTile upgradeable && upgradeable.parseUpgradeData(upgradeData);
    }

    public static boolean replaceTileForUpgrade(TileEntity sourceTile, IBlockState targetState, IUpgradeData upgradeData) {
        World world = sourceTile.getWorld();
        BlockPos pos = sourceTile.getPos();
        if (world == null || pos == null) {
            return false;
        }
        prepareForUpgrade(sourceTile);
        world.setBlockToAir(pos);
        if (!world.setBlockState(pos, targetState, 3)) {
            return false;
        }
        TileEntity upgradedTile = world.getTileEntity(pos);
        if (!isUpgradeable(upgradedTile)) {
            MachineType targetType = MachineType.get(targetState);
            if (targetType != null) {
                TileEntity created = targetType.create();
                if (isUpgradeable(created)) {
                    world.setTileEntity(pos, created);
                    upgradedTile = world.getTileEntity(pos);
                }
            }
        }
        return parseUpgradeData(upgradedTile, upgradeData);
    }
}
