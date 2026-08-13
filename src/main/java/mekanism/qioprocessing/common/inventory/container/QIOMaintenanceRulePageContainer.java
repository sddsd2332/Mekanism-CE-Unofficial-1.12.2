package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

public interface QIOMaintenanceRulePageContainer
      extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOMaintenanceRuleClientCache getMaintenanceRuleClientCache();

    boolean tryRequestMaintenanceRulePage(long currentTick);
}
