package mekanism.common.util;

import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.base.IUpgradeableTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.upgrade.IUpgradeData;
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

    public static boolean replaceTileForUpgrade(TileEntity sourceTile, IBlockState targetState, IUpgradeData upgradeData) {
        World world = sourceTile.getWorld();
        BlockPos pos = sourceTile.getPos();
        if (world == null || pos == null) {
            return false;
        }
        if (sourceTile instanceof IUpgradeableTile upgradeable) {
            upgradeable.prepareForUpgrade();
        }
        world.setBlockToAir(pos);
        if (!world.setBlockState(pos, targetState, 3)) {
            return false;
        }
        TileEntity upgradedTile = world.getTileEntity(pos);
        if (!(upgradedTile instanceof IUpgradeableTile)) {
            MachineType targetType = MachineType.get(targetState);
            if (targetType != null) {
                TileEntity created = targetType.create();
                if (created instanceof IUpgradeableTile) {
                    world.setTileEntity(pos, created);
                    upgradedTile = world.getTileEntity(pos);
                }
            }
        }
        return upgradedTile instanceof IUpgradeableTile upgradeable && upgradeable.parseUpgradeData(upgradeData);
    }
}
