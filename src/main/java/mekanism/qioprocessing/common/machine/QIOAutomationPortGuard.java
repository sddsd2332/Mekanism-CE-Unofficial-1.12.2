package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Narrows ordinary extraction protection to the physical containers owned by a QIO lease.
 *
 * <p>The common Mekanism module invokes this through a callback so it does not need a compile
 * time dependency on QIO.  A provider rebuild or an unavailable host is treated as a retryable
 * QIO condition and leaves ordinary ejection available, as required by the recovery protocol.</p>
 */
public final class QIOAutomationPortGuard {

    private QIOAutomationPortGuard() {
    }

    /** Returns true when an external/manual extraction must be denied for this container. */
    public static boolean isExtractionBlocked(TileEntityContainerBlock tile, Object container) {
        if (tile == null || container == null || tile.getWorld() == null ||
              tile.getWorld().isRemote || QIOAutomationCapabilities.AUTOMATION_HOST == null ||
              !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return false;
        }
        QIOAutomationHost exposed = tile.getCapability(
              QIOAutomationCapabilities.AUTOMATION_HOST, null);
        if (!(exposed instanceof DefaultQIOAutomationHost host) ||
              host.isProcessingOutputCollectionPaused() ||
              host.isAutomaticOutputCollectionPaused() || host.getLeases().isEmpty()) {
            return false;
        }

        final List<MachinePort> ports;
        try {
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find((TileEntity) tile);
            if (provider == null) {
                return false;
            }
            ports = provider.getPorts();
        } catch (RuntimeException ignored) {
            // Do not make a dynamic addon inventory unusable while QIO is retrying it.
            return false;
        }
        if (ports == null || ports.isEmpty()) {
            return false;
        }

        for (MachineOperationLease lease : host.getLeases().values()) {
            if (!protectable(lease)) {
                continue;
            }
            for (MachinePortBaseline baseline : lease.baselines()) {
                MachinePort port = findPort(ports, baseline);
                if (port != null && port.role().allowsOutput() && !port.isConfiguration() &&
                      samePhysicalContainer(port.container(), container)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean protectable(@Nullable MachineOperationLease lease) {
        if (lease == null || lease.state() == MachineOperationLease.State.RELEASED ||
              lease.state() == MachineOperationLease.State.CONTAMINATED) {
            return false;
        }
        return lease.mode() == MachineOperationLease.Mode.PROCESSING_EXCLUSIVE ||
              lease.mode() == MachineOperationLease.Mode.OUTPUT_DRAIN;
    }

    @Nullable
    private static MachinePort findPort(Collection<MachinePort> ports,
          MachinePortBaseline baseline) {
        for (MachinePort port : ports) {
            if (port != null && baseline.portId().equals(port.portId()) &&
                  baseline.portGroupId().equals(port.portGroupId()) &&
                  baseline.kind() == port.kind()) {
                return port;
            }
        }
        return null;
    }

    /** Handles grouped ports whose provider exposes a fresh list wrapper each tick. */
    private static boolean samePhysicalContainer(@Nullable Object portContainer,
          Object requestedContainer) {
        if (portContainer == requestedContainer) {
            return true;
        }
        if (portContainer instanceof List<?> list) {
            for (Object member : list) {
                if (member == requestedContainer || samePhysicalContainer(member, requestedContainer)) {
                    return true;
                }
            }
        }
        if (requestedContainer instanceof List<?> list) {
            for (Object member : list) {
                if (member == portContainer || samePhysicalContainer(portContainer, member)) {
                    return true;
                }
            }
        }
        return false;
    }
}
