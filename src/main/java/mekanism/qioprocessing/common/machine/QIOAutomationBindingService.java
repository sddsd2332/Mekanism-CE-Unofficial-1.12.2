package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * 由服务端权威执行的 QIO 频率绑定入口，供机器 GUI 和管理命令共用。
 *
 * <p>绑定前同时检查物理升级、Provider 端点能力、机器权限和频率权限；动态 Provider
 * 暂时不可用只返回失败，下一次请求可以重试。</p>
 */
public final class QIOAutomationBindingService {

    private QIOAutomationBindingService() {
    }

    /**
     * 把机器绑定到指定 QIO 频率。
     *
     * @param tile 目标机器方块
     * @param frequency 要绑定的 QIO 频率
     * @param requester 发起请求的玩家 UUID
     * @return 绑定成功时返回 true
     */
    public static boolean bind(@Nonnull TileEntity tile, @Nonnull QIOFrequency frequency,
          @Nonnull UUID requester) {
        Objects.requireNonNull(tile, "Machine tile cannot be null");
        Objects.requireNonNull(frequency, "QIO frequency cannot be null");
        Objects.requireNonNull(requester, "Binding requester cannot be null");
        DefaultQIOAutomationHost host = getMutableHost(tile);
        if (host == null || host.getEnabledMode() == null ||
            host.getState() == QIOAutomationHost.State.IDENTITY_CONFLICT ||
            !QIOAutomationUpgradeSupport.isModeInstalled(tile, host.getEnabledMode()) ||
            !SecurityUtils.canAccess(requester, tile) ||
            !SecurityUtils.canAccess(frequency.getSecurity(), requester, frequency.getOwner())) {
            return false;
        }
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
        if (!acceptsEndpoint(provider, host.getEnabledMode())) {
            return false;
        }
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(frequency, requester);
        if (!QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, requester)) {
            return false;
        }
        if (!clearRecoveryForExplicitBindingChange(host)) {
            return false;
        }
        return host.configureBinding(reference, host.getEnabledMode(), true);
    }

    /** 判断 Provider 是否声明支持给定自动化模式的端点契约。 */
    static boolean acceptsEndpoint(@Nullable MachineRecipeProviderRegistry.BoundProvider provider,
          @Nullable QIOAutomationMode mode) {
        if (provider == null || mode == null) {
            return false;
        }
        try {
            return provider.validateQIOEndpointConformance(mode).isConformant();
        } catch (RuntimeException ignored) {
            // Provider state can be transient while a tile restores its template/configuration.
            // Treat that observation as unavailable and let the next binding attempt retry.
            return false;
        }
    }

    /**
     * 解除机器的频率绑定；已有所有权会先走安全恢复/排空路径。
     *
     * @param tile 目标机器方块
     * @param requester 发起请求的玩家 UUID
     * @return 解除成功或请求被安全接受时返回 true
     */
    public static boolean unbind(@Nonnull TileEntity tile, @Nonnull UUID requester) {
        Objects.requireNonNull(tile, "Machine tile cannot be null");
        Objects.requireNonNull(requester, "Binding requester cannot be null");
        DefaultQIOAutomationHost host = getMutableHost(tile);
        if (host == null || !SecurityUtils.canAccess(requester, tile) ||
              !clearRecoveryForExplicitBindingChange(host)) {
            return false;
        }
        return host.configureBinding(null, host.getEnabledMode(), false);
    }

    /**
     * 根据频率身份和期望 UUID 解析频率后执行绑定。
     *
     * @param tile 目标机器方块
     * @param identity 频率所有者/安全模式/键
     * @param expectedFrequencyUUID 调用方观察到的频率 UUID
     * @param requester 发起请求的玩家 UUID
     * @return 频率仍匹配且绑定成功时返回 true
     */
    public static boolean bind(@Nonnull TileEntity tile, @Nonnull FrequencyIdentity identity,
          @Nonnull UUID expectedFrequencyUUID, @Nonnull UUID requester) {
        Objects.requireNonNull(identity, "QIO frequency identity cannot be null");
        Objects.requireNonNull(expectedFrequencyUUID, "Expected QIO frequency UUID cannot be null");
        Objects.requireNonNull(requester, "Binding requester cannot be null");
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(
              identity.ownerUUID(), identity.securityMode());
        QIOFrequency frequency = manager == null ? null : manager.getFrequency(identity.key());
        return frequency != null && expectedFrequencyUUID.equals(frequency.getFrequencyUUID()) &&
              bind(tile, frequency, requester);
    }

    /** 重新验证已保存频率引用的访问权限并更新主机生命周期。 */
    static boolean refreshAccess(@Nonnull DefaultQIOAutomationHost host) {
        QIOFrequencyReference reference = host.getFrequencyReference();
        if (reference == null) {
            return host.getState() == QIOAutomationHost.State.UNBOUND;
        }
        boolean accessible = QIOFrequencyStorageAccess.INSTANCE.canAccess(reference,
              reference.getBindingPlayerUUID());
        host.setAccessValidated(accessible);
        return accessible;
    }

    @Nullable
    private static DefaultQIOAutomationHost getMutableHost(TileEntity tile) {
        if (QIOAutomationCapabilities.AUTOMATION_HOST == null ||
            !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return null;
        }
        QIOAutomationHost host = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        return host instanceof DefaultQIOAutomationHost mutable ? mutable : null;
    }

    private static boolean clearRecoveryForExplicitBindingChange(DefaultQIOAutomationHost host) {
        // DATA_ERROR is a legacy lifecycle value. Current hosts stay online and expose
        // diagnostics through RecoveryState. An idle diagnostic has no external owner and can
        // be cleared locally, even while the frequency network is unloaded. Only a record that
        // actually contains leases, buffers, deferred recovery, or raw quarantined NBT needs the
        // audited network-side force-clear protocol.
        if (host.getRecoveryState() != QIOAutomationHost.RecoveryState.QUARANTINED) {
            return true;
        }
        if (!host.hasPotentialExternalOwnership()) {
            host.clearRecoveryPending();
            return true;
        }
        return QIOAutomationForcedRecoveryService.forceClear(host);
    }
}
