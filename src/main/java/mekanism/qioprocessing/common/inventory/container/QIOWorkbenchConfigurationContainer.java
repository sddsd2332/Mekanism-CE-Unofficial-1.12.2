package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.inventory.container.slot.IVirtualSlot;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * QIO 处理模块中的 QIOWorkbenchConfigurationContainer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public interface QIOWorkbenchConfigurationContainer
      extends QIOProcessingTerminalSessionContainer {

    enum RequestStream {
        PRODUCTS,
        RECIPES,
        CANDIDATES,
        COMMANDS
    }

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOWorkbenchConfigurationClientCache getWorkbenchConfigurationClientCache();

    @Nonnull
    List<? extends IVirtualSlot> getWorkbenchEditorInventorySlots();

    boolean tryRequestWorkbenchConfiguration(long currentTick,
          @Nonnull RequestStream requestStream);
}
