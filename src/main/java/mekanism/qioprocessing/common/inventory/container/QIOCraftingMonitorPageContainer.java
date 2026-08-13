package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

public interface QIOCraftingMonitorPageContainer
      extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOCraftingMonitorClientCache getCraftingMonitorClientCache();

    boolean tryRequestCraftingMonitorPage(long currentTick);

    boolean tryRequestCraftingMonitorDetail(long currentTick);
}
