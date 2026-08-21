package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Exact UUID-aware QIO frequency resolution used by terminal block and item sessions. */
/**
 * QIO 处理模块中的 QIOProcessingFrequencyAccess 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingFrequencyAccess {

    private QIOProcessingFrequencyAccess() {
    }

    @Nullable
    public static QIOFrequency resolve(@Nullable QIOFrequencyReference reference) {
        if (reference == null) {
            return null;
        }
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(
              reference.getOwnerUUID(), reference.getSecurityMode());
        if (manager == null) {
            return null;
        }
        QIOFrequency frequency = manager.getFrequency(reference.getFrequencyName());
        return matches(reference, frequency) ? frequency : null;
    }

    @Nullable
    public static QIOFrequency resolveAccessible(@Nullable QIOFrequencyReference reference,
          @Nullable UUID requester) {
        QIOFrequency frequency = resolve(reference);
        return frequency != null && QIOFrequencyStorageAccess.INSTANCE.canAccess(
              reference, requester) ? frequency : null;
    }

    public static long getAccessibleRevision(@Nullable QIOFrequencyReference reference,
          @Nullable UUID requester) {
        QIOFrequency frequency = resolveAccessible(reference, requester);
        return frequency == null ? -1 : frequency.getAccessRevision();
    }

    public static boolean matches(@Nonnull QIOFrequencyReference reference,
          @Nullable QIOFrequency frequency) {
        Objects.requireNonNull(reference, "reference");
        return frequency != null && frequency.isValid() && !frequency.isRemoved() &&
              reference.getFrequencyUUID().equals(frequency.getFrequencyUUID()) &&
              reference.getFrequencyName().equals(frequency.getName()) &&
              Objects.equals(reference.getOwnerUUID(), frequency.getOwner()) &&
              reference.getSecurityMode() == frequency.getSecurity();
    }
}
