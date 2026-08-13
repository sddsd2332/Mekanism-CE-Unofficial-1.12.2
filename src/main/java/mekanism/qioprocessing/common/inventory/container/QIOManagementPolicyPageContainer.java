package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

public interface QIOManagementPolicyPageContainer
      extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOManagementPolicyClientCache getPolicyClientCache();

    boolean tryRequestPolicyPage(long currentTick);

    boolean tryRequestPolicyLookup(long currentTick);
}
