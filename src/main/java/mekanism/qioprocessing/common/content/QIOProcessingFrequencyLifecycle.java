package mekanism.qioprocessing.common.content;

import mekanism.api.qio.external.IQIOFrequencyLifecycleListener;
import mekanism.api.qio.external.QIOFrequencyDeleteCheck;
import mekanism.api.qio.external.QIOFrequencyReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Prevents normal frequency deletion while the processing module owns persistent assets. */
/**
 * QIO 处理模块中的 QIOProcessingFrequencyLifecycle 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingFrequencyLifecycle implements IQIOFrequencyLifecycleListener {

    public static final QIOProcessingFrequencyLifecycle INSTANCE =
          new QIOProcessingFrequencyLifecycle();

    private QIOProcessingFrequencyLifecycle() {
    }

    @Nonnull
    @Override
    public QIOFrequencyDeleteCheck beforeDelete(@Nonnull QIOFrequencyReference reference,
          @Nullable UUID requester) {
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              reference.getFrequencyUUID());
        if (network == null) {
            return QIOFrequencyDeleteCheck.allowed();
        }
        List<String> blockers = network.getFrequencyDeletionBlockers();
        return blockers.isEmpty() ? QIOFrequencyDeleteCheck.allowed() :
              QIOFrequencyDeleteCheck.blocked(blockers);
    }

    @Override
    public void afterDelete(@Nonnull QIOFrequencyReference reference,
          @Nullable UUID requester) {
        QIOProcessingNetworkManager.INSTANCE.frequencyDeleted(reference.getFrequencyUUID());
    }
}
