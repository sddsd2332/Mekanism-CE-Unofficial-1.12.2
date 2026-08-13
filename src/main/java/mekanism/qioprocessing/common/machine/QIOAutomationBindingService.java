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

/** Server-authoritative frequency binding entry point shared by machine GUI and management commands. */
public final class QIOAutomationBindingService {

    private QIOAutomationBindingService() {
    }

    public static boolean bind(@Nonnull TileEntity tile, @Nonnull QIOFrequency frequency,
          @Nonnull UUID requester) {
        Objects.requireNonNull(tile, "Machine tile cannot be null");
        Objects.requireNonNull(frequency, "QIO frequency cannot be null");
        Objects.requireNonNull(requester, "Binding requester cannot be null");
        DefaultQIOAutomationHost host = getMutableHost(tile);
        if (host == null || host.getEnabledMode() == null || host.getState() == QIOAutomationHost.State.DATA_ERROR ||
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
        return host.configureBinding(reference, host.getEnabledMode(), true);
    }

    static boolean acceptsEndpoint(@Nullable MachineRecipeProviderRegistry.BoundProvider provider,
          @Nullable QIOAutomationMode mode) {
        return provider != null && mode != null &&
              provider.validateQIOEndpointConformance(mode).isConformant();
    }

    public static boolean unbind(@Nonnull TileEntity tile, @Nonnull UUID requester) {
        Objects.requireNonNull(tile, "Machine tile cannot be null");
        Objects.requireNonNull(requester, "Binding requester cannot be null");
        DefaultQIOAutomationHost host = getMutableHost(tile);
        return host != null && SecurityUtils.canAccess(requester, tile) &&
              host.configureBinding(null, host.getEnabledMode(), false);
    }

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
}
