package mekanism.qioprocessing.common.item;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.base.IUpgradeItem;
import mekanism.common.item.ItemUpgrade;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import net.minecraft.item.ItemStack;

import java.util.Objects;

/** Upgrade item whose installation is admitted by the QIO provider contract, without a machine mixin. */
public final class ItemQIOAutomationUpgrade extends ItemUpgrade {

    private final QIOAutomationMode mode;

    public ItemQIOAutomationUpgrade(mekanism.common.Upgrade upgrade, QIOAutomationMode mode) {
        super(upgrade);
        this.mode = Objects.requireNonNull(mode, "Automation mode cannot be null");
    }

    public QIOAutomationMode getAutomationMode() {
        return mode;
    }

    @Override
    public boolean canInstallUpgrade(ItemStack stack, IUpgradeTile tile) {
        return tile instanceof TileEntityContainerBlock container &&
              QIOAutomationUpgradeSupport.canInstall(container, tile, mode);
    }
}
