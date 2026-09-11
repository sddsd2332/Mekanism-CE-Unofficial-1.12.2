package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import net.minecraft.tileentity.TileEntity;
import mekanism.common.Mekanism;
import mekanism.common.tile.prefab.TileEntityBasicBlock;

/** Routes QIO machine work through the machine's own stable, pre-component tick. */
public final class QIOAutomationTileTickService {

    public static final QIOAutomationTileTickService INSTANCE =
          new QIOAutomationTileTickService();

    private QIOAutomationTileTickService() {
    }

    public void tick(TileEntityBasicBlock tile) {
        if (tile == null || tile.isInvalid() || tile.getWorld() == null ||
            tile.getWorld().isRemote || QIOAutomationCapabilities.AUTOMATION_HOST == null ||
            !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return;
        }
        QIOAutomationHost exposed = tile.getCapability(
              QIOAutomationCapabilities.AUTOMATION_HOST, null);
        if (!(exposed instanceof DefaultQIOAutomationHost host)) {
            return;
        }
        long gameTick = Math.max(0, tile.getWorld().getTotalWorldTime());
        tile.runContainerTransaction(() -> tick(host, tile, gameTick));
    }

    void tick(DefaultQIOAutomationHost host, TileEntity tile, long gameTick) {
        if (host == null || tile == null || host.tile() != tile) {
            return;
        }
        QIOAutomationMode mode = host.getEnabledMode();
        if (mode == null) {
            return;
        }
        try {
            if (mode == QIOAutomationMode.OUTPUT_ONLY) {
                QIOAutomaticOutputService.INSTANCE.tickDevice(host, tile, gameTick);
            } else {
                // New order allocation remains frequency-wide. An operation which already owns
                // this machine must collect its output here, before TileComponentEjector runs.
                // The ordinary ejector is deliberately left active: the optional QIO guard
                // filters only the physical output containers covered by the active lease.
                QIOProcessingExecutionService.INSTANCE.onMachinePreComponentTick(host);
            }
        } catch (RuntimeException e) {
            if (mode == QIOAutomationMode.SCHEDULED || mode == QIOAutomationMode.PASSIVE) {
                String message = e.getMessage();
                host.pauseProcessingOutputCollection("pre-ejection hook failed: " +
                      (message == null || message.isEmpty() ? e.getClass().getSimpleName() :
                            message.substring(0, Math.min(384, message.length()))));
            }
            Mekanism.logger.error("QIO machine tick failed for device {}; processing collection " +
                  "was paused when applicable", host.getPersistentDeviceUUID(), e);
        }
    }
}
