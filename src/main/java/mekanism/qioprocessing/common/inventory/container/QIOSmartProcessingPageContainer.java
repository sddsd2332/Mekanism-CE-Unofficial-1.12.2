package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

/** Client cache and throttling contract for the smart-processing order window. */
/**
 * QIO 处理模块中的 QIOSmartProcessingPageContainer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
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
