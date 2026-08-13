package mekanism.qioprocessing.common.content;

import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.content.material.QIOClaimWakeService;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import mekanism.qioprocessing.common.order.QIOOrderService;
import mekanism.qioprocessing.common.execution.QIOEndpointPersistenceService;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.planning.QIOPlanningService;
import mekanism.qioprocessing.common.planning.QIOReplanningService;
import mekanism.qioprocessing.common.maintenance.QIOMaintenanceService;

/** Connects the independent QIO Processing store to the overworld/server lifecycle. */
public final class QIOProcessingNetworkService {

    public static final QIOProcessingNetworkService INSTANCE = new QIOProcessingNetworkService();

    private QIOProcessingNetworkService() {
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        QIOProcessingNetworkManager.INSTANCE.createOrLoad(event.getWorld());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.world.isRemote &&
              event.world.provider.getDimension() == 0) {
            QIOProcessingNetworkManager.INSTANCE.tick(event.world);
            QIOPlanningService.INSTANCE.drainCompletedForCurrentTick();
            QIOOrderService.INSTANCE.tick(event.world.getTotalWorldTime());
            QIOReplanningService.INSTANCE.tick(event.world);
            QIOMaintenanceService.INSTANCE.tick(event.world);
            QIOClaimWakeService.INSTANCE.tick(
                  QIOProcessingNetworkManager.INSTANCE.getNetworks(),
                  MekanismConfig.current().qioProcessing.claimRefreshesPerTick.val(),
                  QIOFrequencyStorageAccess.INSTANCE,
                  QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
            QIOProcessingExecutionService.INSTANCE.tick(event.world);
            QIOEndpointPersistenceService.INSTANCE.flushPending();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            QIOEndpointPersistenceService.INSTANCE.discardWorld(event.getWorld());
        }
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            QIOMaintenanceService.INSTANCE.shutdown();
            QIOReplanningService.INSTANCE.shutdown();
            QIOClaimWakeService.INSTANCE.shutdown();
            QIOOrderService.INSTANCE.clear();
            QIOEndpointPersistenceService.INSTANCE.clear();
            QIOProcessingNetworkManager.INSTANCE.shutdown();
        }
    }
}
