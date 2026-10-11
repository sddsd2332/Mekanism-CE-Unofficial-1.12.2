package mekanism.qioprocessing.common.content;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    /**
     * 注册三种自动化升级的兼容性谓词；重复调用不会重复注册。
     */
    public static synchronized void registerUpgradeSupport() {
        if (registered) {
            return;
        }
        registerModeSupport("scheduled", QIOAutomationMode.SCHEDULED, QIOProcessingUpgrades.QIO_AUTO_CRAFTING);
        registerModeSupport("passive", QIOAutomationMode.PASSIVE, QIOProcessingUpgrades.QIO_AUTO_PROCESSING);
        registerModeSupport("output", QIOAutomationMode.OUTPUT_ONLY, QIOProcessingUpgrades.QIO_AUTO_OUTPUT);
        registered = true;
    }

    /**
     * 注册单个模式的升级支持回调。
     */
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
     * @param tile        目标机器方块
     * @param upgradeTile 提供升级库存的机器接口
     * @param mode        要安装的自动化模式
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

    /**
     * 检查物理升级库存，不信任可能过期或复制的 capability 模式。
     */
    public static boolean isModeInstalled(@Nullable TileEntity tile, @Nullable QIOAutomationMode mode) {
        if (!(tile instanceof TileEntityContainerBlock) || !(tile instanceof IUpgradeTile upgradeTile) || mode == null) {
            return false;
        }
        return upgradeTile.isUpgradeInstalled(upgradeFor(mode));
    }

    /**
     * 修复升级组件读取 NBT 未触发 Upgrade.onChanged 导致的 capability/库存状态不一致。
     */
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
                if (host.clearMode() && host.getState() != QIOAutomationHost.State.DRAINING_CHANGE) {
                    QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
                }
            }
            return;
        }
        if (configured != null) {
            // Component disable stops new work, but must preserve the destination and
            // existing leases/buffers until recovery has settled their ownership.
            if (!host.clearMode() || host.getState() == QIOAutomationHost.State.DRAINING_CHANGE) {
                return;
            }
            QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
        }
        if (installed != null && host.getEnabledMode() == null &&
                host.getState() != QIOAutomationHost.State.DRAINING_CHANGE) {
            // A damaged capability can forget which mode owned its binding while the physical
            // inventory still contains a replacement upgrade. Treat that as an explicit mode
            // replacement so stale frequency/recovery state cannot follow the new mode.
            if (host.getFrequencyReference() != null || host.hasRecoveryPending() ||
                    host.getState() != QIOAutomationHost.State.UNBOUND) {
                if (!host.clearMode() || host.getState() == QIOAutomationHost.State.DRAINING_CHANGE) {
                    return;
                }
                QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(tile);
            }
            host.selectMode(installed);
        }
    }

    /**
     * 返回自动化模式对应的物品升级类型。
     */
    private static Upgrade upgradeFor(QIOAutomationMode mode) {
        return switch (mode) {
            case SCHEDULED -> QIOProcessingUpgrades.QIO_AUTO_CRAFTING;
            case PASSIVE -> QIOProcessingUpgrades.QIO_AUTO_PROCESSING;
            case OUTPUT_ONLY -> QIOProcessingUpgrades.QIO_AUTO_OUTPUT;
        };
    }

    /**
     * 从方块 capability 取得自动化主机；缺少 capability 时返回 null。
     */
    @Nullable
    public static QIOAutomationHost getHost(TileEntity tile) {
        return tile == null || QIOAutomationCapabilities.AUTOMATION_HOST == null ? null :
                tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
    }
}
