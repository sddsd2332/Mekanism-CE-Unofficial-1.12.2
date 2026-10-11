package mekanism.qioprocessing.common.processor;

import mekanism.common.Upgrade;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;

/**
 * Applies the immutable Processor definition limits to the normal Mekanism upgrade slots.
 */
public final class QIOProcessorUpgradeComponent extends TileComponentUpgrade {
    private final QIOCraftingProcessorTile processor;

    public QIOProcessorUpgradeComponent(QIOCraftingProcessorTile processor) {
        super(processor);
        this.processor = processor;
        setSupported(Upgrade.SPEED);
        setSupported(Upgrade.ENERGY);
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
        if (upgrade == Upgrade.SPEED) return processor.definition().getSpeedUpgradeLimit();
        if (upgrade == Upgrade.ENERGY) return processor.definition().getEnergyUpgradeLimit();
        if (upgrade == QIOProcessingUpgrades.QIO_STACKING) return processor.definition().getStackingUpgradeLimit();
        return 0;
    }
}

