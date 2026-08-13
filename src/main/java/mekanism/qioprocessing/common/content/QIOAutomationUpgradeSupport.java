package mekanism.qioprocessing.common.content;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

/** Shared server-side checks and mode callbacks for the three QIO automation upgrades. */
public final class QIOAutomationUpgradeSupport {

    private static boolean registered;

    private QIOAutomationUpgradeSupport() {
    }

    public static synchronized void registerUpgradeSupport() {
        if (registered) {
            return;
        }
        registerModeSupport("scheduled", QIOAutomationMode.SCHEDULED, QIOProcessingUpgrades.QIO_AUTO_CRAFTING);
        registerModeSupport("passive", QIOAutomationMode.PASSIVE, QIOProcessingUpgrades.QIO_AUTO_PROCESSING);
        registerModeSupport("output", QIOAutomationMode.OUTPUT_ONLY, QIOProcessingUpgrades.QIO_AUTO_OUTPUT);
        registered = true;
    }

    private static void registerModeSupport(String id, QIOAutomationMode mode, Upgrade upgrade) {
        ExternalUpgradeSupportRegistry.register(new ResourceLocation(MekanismQIOProcessing.MODID,
                    "machine_" + id + "_upgrade_support"), tile -> {
            MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
            return provider != null && provider.getQIOConformance().supports(mode);
        }, upgrade);
    }

    public static boolean canInstall(TileEntityContainerBlock tile, IUpgradeTile upgradeTile,
          QIOAutomationMode mode) {
        if (tile == null || upgradeTile == null || !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return false;
        }
        QIOAutomationHost host = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        if (host == null || host.getState() == QIOAutomationHost.State.IDENTITY_CONFLICT ||
            host.getState() == QIOAutomationHost.State.DATA_ERROR ||
            host.getState() == QIOAutomationHost.State.DRAINING_CHANGE ||
            host.getEnabledMode() != null && host.getEnabledMode() != mode) {
            return false;
        }
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
        if (provider == null) {
            return false;
        }
        try {
            return provider.validateQIOEndpointConformance(mode).isConformant();
        } catch (RuntimeException ignored) {
            // A provider may not have finished constructing its dynamic port view yet. The
            // next slot check/binding attempt will retry against the authoritative state.
            return false;
        }
    }

    /**
     * Checks the physical upgrade inventory rather than trusting the capability mode. This is
     * used at every server entry point so stale or copied host NBT cannot grant access.
     */
    public static boolean isModeInstalled(@Nullable TileEntity tile, @Nullable QIOAutomationMode mode) {
        if (!(tile instanceof TileEntityContainerBlock) || !(tile instanceof IUpgradeTile upgradeTile) || mode == null) {
            return false;
        }
        return upgradeTile.isUpgradeInstalled(upgradeFor(mode));
    }

    /**
     * Repairs the mismatch that can occur because TileComponentUpgrade reads its NBT without
     * invoking Upgrade.onChanged. It is called after both capability and upgrade state exist.
     */
    public static void reconcile(@Nonnull TileEntity tile,
          @Nonnull mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost host) {
        if (!(tile instanceof TileEntityContainerBlock) || !(tile instanceof IUpgradeTile) ||
            host.getState() == QIOAutomationHost.State.IDENTITY_CONFLICT ||
            host.getState() == QIOAutomationHost.State.DATA_ERROR) {
            return;
        }
        QIOAutomationMode installed = null;
        for (QIOAutomationMode candidate : QIOAutomationMode.values()) {
            if (isModeInstalled(tile, candidate)) {
                installed = candidate;
                break;
            }
        }
        QIOAutomationMode configured = host.getEnabledMode();
        if (configured == installed || host.getState() == QIOAutomationHost.State.DRAINING_CHANGE) {
            return;
        }
        if (configured != null) {
            QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
            host.clearMode(configured != QIOAutomationMode.OUTPUT_ONLY);
        }
        if (installed != null && host.getEnabledMode() == null &&
            host.getState() != QIOAutomationHost.State.DRAINING_CHANGE) {
            host.selectMode(installed);
        }
    }

    public static void onUpgradeChanged(Upgrade upgrade, TileEntityContainerBlock tile, int previousAmount, int amount,
          QIOAutomationMode mode, Upgrade... allQIOUpgrades) {
        if (!(tile instanceof IUpgradeTile upgradeTile) || !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return;
        }
        QIOAutomationHost host = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        if (host == null) {
            return;
        }
        if (amount > 0) {
            host.selectMode(mode);
        } else {
            boolean anotherInstalled = false;
            for (Upgrade other : allQIOUpgrades) {
                if (other != upgrade && upgradeTile.isUpgradeInstalled(other)) {
                    anotherInstalled = true;
                    break;
                }
            }
            if (!anotherInstalled && host.getEnabledMode() == mode) {
                QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
                host.clearMode(mode != QIOAutomationMode.OUTPUT_ONLY);
            }
        }
    }

    private static Upgrade upgradeFor(QIOAutomationMode mode) {
        return switch (mode) {
            case SCHEDULED -> QIOProcessingUpgrades.QIO_AUTO_CRAFTING;
            case PASSIVE -> QIOProcessingUpgrades.QIO_AUTO_PROCESSING;
            case OUTPUT_ONLY -> QIOProcessingUpgrades.QIO_AUTO_OUTPUT;
        };
    }

    @Nullable
    public static QIOAutomationHost getHost(TileEntity tile) {
        return tile == null || QIOAutomationCapabilities.AUTOMATION_HOST == null ? null :
              tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
    }
}
