package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Client-facing frequency state shared by block and portable processing terminals. */
/**
 * QIO 处理模块中的 QIOProcessingTerminalFrequencyContainer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public interface QIOProcessingTerminalFrequencyContainer extends
      QIOProcessingTerminalSessionContainer {

    int getTerminalWindowId();

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nullable
    QIOFrequency getTerminalFrequency();

    @Nonnull
    List<QIOFrequency> getPublicTerminalFrequencies();

    @Nonnull
    List<QIOFrequency> getPrivateTerminalFrequencies();

    @Nonnull
    List<QIOFrequency> getTrustedTerminalFrequencies();

    @Nullable
    UUID getTerminalOwnerUUID();

    @Nonnull
    String getTerminalOwnerName();

    boolean isPortableTerminal();
}
