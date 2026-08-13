package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

/** Client cache and throttling contract for the smart-processing order window. */
public interface QIOSmartProcessingPageContainer extends QIOProcessingTerminalFrequencyContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOSmartProcessingClientCache getSmartProcessingClientCache();

    @Nonnull
    QIOSmartProcessingRequestLedger getSmartProcessingRequestLedger();

    boolean tryRequestSmartProcessing(long currentTick);

    /** Read-only preview polls use a separate bucket so catalog loads cannot starve them. */
    boolean tryRequestSmartProcessingPoll(long currentTick);
}
