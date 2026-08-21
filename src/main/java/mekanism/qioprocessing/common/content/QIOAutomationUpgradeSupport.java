package mekanism.qioprocessing.common.content;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationForcedRecoveryService;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

/**
 * 三种 QIO 自动化升级共用的服务端校验和模式回调。
 *
 * <p>该类以物理升级库存为最终依据，并在 capability 状态与升级库存不一致时执行
 * 协调；Provider 动态重建期间的异常只会让本次检查失败，后续 tick 继续尝试。</p>
 */
public final class QIOAutomationUpgradeSupport {

    private static boolean registered;

    private QIOAutomationUpgradeSupport() {
    }

    /** 注册三种自动化升级的兼容性谓词；重复调用不会重复注册。 */
    public static synchronized void registerUpgradeSupport() {
        if (registered) {
            return;
        }
        registerModeSupport("scheduled", QIOAutomationMode.SCHEDULED, QIOProcessingUpgrades.QIO_AUTO_CRAFTING);
        registerModeSupport("passive", QIOAutomationMode.PASSIVE, QIOProcessingUpgrades.QIO_AUTO_PROCESSING);
        registerModeSupport("output", QIOAutomationMode.OUTPUT_ONLY, QIOProcessingUpgrades.QIO_AUTO_OUTPUT);
        registered = true;
    }

    /** 注册单个模式的升级支持回调。 */
    private static void registerModeSupport(String id, QIOAutomationMode mode, Upgrade upgrade) {
        ExternalUpgradeSupportRegistry.register(new ResourceLocation(MekanismQIOProcessing.MODID,
                    "machine_" + id + "_upgrade_support"), tile -> {
            try {
                MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
                return provider != null && provider.getQIOConformance().supports(mode);
            } catch (RuntimeException ignored) {
                // Addon-owned dynamic provider state is not allowed to escape the upgrade
                // compatibility predicate during tile load.
                return false;
            }
        }, upgrade);
    }

    /**
     * 判断指定模式升级能否安装到机器。
     *
     * @param tile 目标机器方块
     * @param upgradeTile 提供升级库存的机器接口
     * @param mode 要安装的自动化模式
     * @return 当前状态和 Provider 契约都允许安装时返回 true
     */
    public static boolean canInstall(TileEntityContainerBlock tile, IUpgradeTile upgradeTile,
          QIOAutomationMode mode) {
        if (tile == null || upgradeTile == null || !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return false;
        }
        QIOAutomationHost host = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        if (host == null || host.getState() == QIOAutomationHost.State.IDENTITY_CONFLICT ||
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

    /** 检查物理升级库存，不信任可能过期或复制的 capability 模式。 */
    public static boolean isModeInstalled(@Nullable TileEntity tile, @Nullable QIOAutomationMode mode) {
        if (!(tile instanceof TileEntityContainerBlock) || !(tile instanceof IUpgradeTile upgradeTile) || mode == null) {
            return false;
        }
        return upgradeTile.isUpgradeInstalled(upgradeFor(mode));
    }

    /** 修复升级组件读取 NBT 未触发 Upgrade.onChanged 导致的 capability/库存状态不一致。 */
    public static void reconcile(@Nonnull TileEntity tile,
          @Nonnull mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost host) {
        if (!(tile instanceof TileEntityContainerBlock) || !(tile instanceof IUpgradeTile) ||
            host.getState() == QIOAutomationHost.State.IDENTITY_CONFLICT) {
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
        if (configured == installed) {
            // A damaged/legacy capability can lose enabledMode while retaining a quarantine.
            // When the physical inventory also contains no QIO automation upgrade, removal is
            // still an explicit request to discard that stale capability state.
            if (installed == null && configured == null &&
                  (host.hasRecoveryPending() || host.getFrequencyReference() != null ||
                        host.getState() != QIOAutomationHost.State.UNBOUND)) {
                QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
                QIOAutomationForcedRecoveryService.clearAfterUpgradeRemoval(host);
            }
            return;
        }
        if (configured != null) {
            QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
            // Physical removal (or replacement by another QIO mode) is an explicit
            // ownership-discard request. Do not leave the old mode in DRAINING_CHANGE;
            // that state is reserved for an intentional frequency/mode change while
            // the upgrade remains installed.
            if (!QIOAutomationForcedRecoveryService.clearAfterUpgradeRemoval(host)) {
                return;
            }
        }
        if (installed != null && host.getEnabledMode() == null &&
            host.getState() != QIOAutomationHost.State.DRAINING_CHANGE) {
            // A damaged capability can forget which mode owned its binding while the physical
            // inventory still contains a replacement upgrade. Treat that as an explicit mode
            // replacement so stale frequency/recovery state cannot follow the new mode.
            if (host.getFrequencyReference() != null || host.hasRecoveryPending() ||
                  host.getState() != QIOAutomationHost.State.UNBOUND) {
                QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
                if (!QIOAutomationForcedRecoveryService.clearAfterUpgradeRemoval(host)) {
                    return;
                }
            }
            host.selectMode(installed);
        }
    }

    /**
     * 处理自动化升级数量变化，并在移除/替换时触发安全清理或重新武装。
     *
     * @param upgrade 发生变化的升级类型
     * @param tile 受影响的机器方块
     * @param previousAmount 变化前数量
     * @param amount 变化后数量
     * @param mode 发生变化的自动化模式
     * @param allQIOUpgrades 所有 QIO 自动化升级类型（由调用方提供的兼容参数）
     */
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
            if (host.hasRecoveryPending() &&
                  host instanceof DefaultQIOAutomationHost mutable &&
                  mutable.rearmAfterUpgrade(mode)) {
                // Reinstalling the same upgrade is an explicit retry request. The host either
                // queues its known output quarantine or clears a diagnostic with no owned data.
            } else {
                host.selectMode(mode);
            }
        } else {
            QIOAutomationMode replacement = installedMode(upgradeTile);
            boolean staleAutomationState = host.getEnabledMode() != null ||
                  host.getFrequencyReference() != null || host.hasRecoveryPending() ||
                  host.getState() != QIOAutomationHost.State.UNBOUND;
            // The upgrade component removes the physical item before invoking this callback.
            // Clear stale capability data even when a damaged host no longer remembers which
            // mode owned it, but never clear a different still-installed mode.
            if (host.getEnabledMode() == mode || replacement == null && staleAutomationState) {
                if (host instanceof DefaultQIOAutomationHost mutable &&
                      !QIOAutomationForcedRecoveryService.clearAfterUpgradeRemoval(mutable)) {
                    return;
                }
                QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
            }
            if (replacement != null && host.getEnabledMode() == null &&
                  host.getState() != QIOAutomationHost.State.DRAINING_CHANGE) {
                if (staleAutomationState) {
                    QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
                    if (host instanceof DefaultQIOAutomationHost mutable &&
                          !QIOAutomationForcedRecoveryService.clearAfterUpgradeRemoval(mutable)) {
                        return;
                    }
                }
                host.selectMode(replacement);
            }
        }
    }

    /** 从物理升级库存解析当前安装的自动化模式。 */
    @Nullable
    private static QIOAutomationMode installedMode(IUpgradeTile tile) {
        if (tile == null) {
            return null;
        }
        for (QIOAutomationMode candidate : QIOAutomationMode.values()) {
            if (tile.isUpgradeInstalled(upgradeFor(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /** 返回自动化模式对应的物品升级类型。 */
    private static Upgrade upgradeFor(QIOAutomationMode mode) {
        return switch (mode) {
            case SCHEDULED -> QIOProcessingUpgrades.QIO_AUTO_CRAFTING;
            case PASSIVE -> QIOProcessingUpgrades.QIO_AUTO_PROCESSING;
            case OUTPUT_ONLY -> QIOProcessingUpgrades.QIO_AUTO_OUTPUT;
        };
    }

    /** 从方块 capability 取得自动化主机；缺少 capability 时返回 null。 */
    @Nullable
    public static QIOAutomationHost getHost(TileEntity tile) {
        return tile == null || QIOAutomationCapabilities.AUTOMATION_HOST == null ? null :
              tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
    }
}
