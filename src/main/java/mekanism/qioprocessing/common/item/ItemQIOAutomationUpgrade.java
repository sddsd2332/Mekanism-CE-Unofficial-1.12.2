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
/**
 * QIO 处理模块中的 ItemQIOAutomationUpgrade 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
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
