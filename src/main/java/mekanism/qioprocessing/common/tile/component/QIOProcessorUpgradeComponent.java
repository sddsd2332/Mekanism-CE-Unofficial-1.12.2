package mekanism.qioprocessing.common.tile.component;

import mekanism.common.Upgrade;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;

import javax.annotation.Nonnull;

/** Applies definition-specific upgrade limits on top of Mekanism's shared upgrade component. */
/**
 * QIO 处理模块中的 QIOProcessorUpgradeComponent 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessorUpgradeComponent extends TileComponentUpgrade {

    private final QIOCraftingProcessor processor;

    public QIOProcessorUpgradeComponent(@Nonnull QIOCraftingProcessor processor) {
        super(processor);
        this.processor = processor;
        setSupported(QIOProcessingUpgrades.QIO_STACKING);
    }

    @Override
    public int getInstallRoom(Upgrade upgrade) {
        return Math.min(super.getInstallRoom(upgrade),
              Math.max(0, limit(upgrade) - getUpgrades(upgrade)));
    }

    @Override
    public int setUpgrades(Upgrade upgrade, int amount) {
        return super.setUpgrades(upgrade, Math.min(Math.max(0, amount), limit(upgrade)));
    }

    private int limit(Upgrade upgrade) {
        QIOCraftingProcessorDefinition definition = processor.getProcessorState()
              .getResolvedDefinition();
        if (definition == null) {
            return 0;
        }
        if (upgrade == Upgrade.SPEED) {
            return definition.getSpeedUpgradeLimit();
        }
        if (upgrade == Upgrade.ENERGY) {
            return definition.getEnergyUpgradeLimit();
        }
        if (upgrade == QIOProcessingUpgrades.QIO_STACKING) {
            return definition.getStackingUpgradeLimit();
        }
        return 0;
    }
}
