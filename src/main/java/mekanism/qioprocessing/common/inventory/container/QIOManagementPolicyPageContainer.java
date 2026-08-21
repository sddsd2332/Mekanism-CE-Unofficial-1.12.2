package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

/**
 * QIO 处理模块中的 QIOManagementPolicyPageContainer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public interface QIOManagementPolicyPageContainer
      extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOManagementPolicyClientCache getPolicyClientCache();

    boolean tryRequestPolicyPage(long currentTick);

    boolean tryRequestPolicyLookup(long currentTick);
}
