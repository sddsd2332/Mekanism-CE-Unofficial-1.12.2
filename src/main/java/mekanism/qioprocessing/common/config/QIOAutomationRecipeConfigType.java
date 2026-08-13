package mekanism.qioprocessing.common.config;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The two route-policy editors exposed by QIO machine automation upgrades. */
public enum QIOAutomationRecipeConfigType {
    SCHEDULED(QIOAutomationMode.SCHEDULED, "qio_auto_crafting_upgrade"),
    PASSIVE(QIOAutomationMode.PASSIVE, "qio_auto_processing_upgrade");

    private final QIOAutomationMode mode;
    private final String itemTextureName;

    QIOAutomationRecipeConfigType(QIOAutomationMode mode, String itemTextureName) {
        this.mode = mode;
        this.itemTextureName = itemTextureName;
    }

    @Nonnull
    public QIOAutomationMode getMode() {
        return mode;
    }

    @Nonnull
    public String getItemTextureName() {
        return itemTextureName;
    }

    @Nonnull
    public Upgrade getUpgrade() {
        return this == SCHEDULED ? QIOProcessingUpgrades.QIO_AUTO_CRAFTING :
              QIOProcessingUpgrades.QIO_AUTO_PROCESSING;
    }

    public boolean isInstalledIn(@Nullable TileEntityContainerBlock tile) {
        return tile instanceof IUpgradeTile upgradeTile &&
              upgradeTile.isUpgradeInstalled(getUpgrade());
    }

    @Nonnull
    public String getTitleKey() {
        return this == SCHEDULED ?
              "gui.mekanismqioprocessing.auto_crafting_config" :
              "gui.mekanismqioprocessing.auto_processing_config";
    }

    @Nonnull
    public WindowType getWindowType() {
        return this == SCHEDULED ? QIOProcessingWindowTypes.AUTOMATION_CRAFTING_CONFIG :
              QIOProcessingWindowTypes.AUTOMATION_PROCESSING_CONFIG;
    }

    @Nullable
    public static QIOAutomationRecipeConfigType byIndex(int index) {
        QIOAutomationRecipeConfigType[] values = values();
        return index >= 0 && index < values.length ? values[index] : null;
    }
}
