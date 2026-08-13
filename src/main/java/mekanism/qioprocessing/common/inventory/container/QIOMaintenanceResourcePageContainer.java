package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

/** Client-facing paging contract for the maintenance resource selector. */
public interface QIOMaintenanceResourcePageContainer extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOSmartProcessingClientCache getMaintenanceResourceClientCache();

    boolean tryRequestMaintenanceResourcePage(long currentTick);
}
