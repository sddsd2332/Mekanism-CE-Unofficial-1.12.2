package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.terminal.QIOManagementDeviceGroupSnapshot;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Revision-consistent client cache for management machine-type pages. */
/**
 * QIO 处理模块中的 QIOManagementDeviceGroupClientCache 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOManagementDeviceGroupClientCache {

    private final List<QIOManagementDeviceGroupSnapshot> groups = new ArrayList<>();
    @Nullable private UUID sessionNonce;
    private long sourceRevision = -1;
    private int totalSize;
    @Nullable private QIOPageCursor nextCursor;
    private long generation;

    public boolean apply(@Nonnull UUID responseSessionNonce, long responseRevision,
          int offset, int responseTotalSize,
          @Nonnull List<QIOManagementDeviceGroupSnapshot> page,
          @Nullable QIOPageCursor responseNextCursor) {
        if (responseRevision < 0 || offset < 0 || responseTotalSize < 0 ||
            offset > responseTotalSize || page.size() > responseTotalSize - offset) return false;
        if (offset == 0) {
            if (responseSessionNonce.equals(sessionNonce) && sourceRevision >= 0 &&
                responseRevision < sourceRevision) {
                return false;
            }
            groups.clear();
            sessionNonce = responseSessionNonce;
            sourceRevision = responseRevision;
            totalSize = responseTotalSize;
        } else if (!responseSessionNonce.equals(sessionNonce) ||
            responseRevision != sourceRevision || responseTotalSize != totalSize ||
            offset != groups.size()) return false;
        groups.addAll(page);
        nextCursor = responseNextCursor;
        generation = next(generation);
        return true;
    }

    public void clear() {
        groups.clear();
        sessionNonce = null;
        sourceRevision = -1;
        totalSize = 0;
        nextCursor = null;
    }

    @Nonnull
    public List<QIOManagementDeviceGroupSnapshot> getGroups() {
        return Collections.unmodifiableList(new ArrayList<>(groups));
    }

    public long getSourceRevision() { return sourceRevision; }
    public int getTotalSize() { return totalSize; }
    @Nullable public QIOPageCursor getNextCursor() { return nextCursor; }
    public long getGeneration() { return generation; }

    public boolean hasPageFor(@Nonnull UUID expectedSessionNonce) {
        return sourceRevision >= 0 && expectedSessionNonce.equals(sessionNonce);
    }

    private static long next(long value) {
        return value == Long.MAX_VALUE ? 0 : value + 1;
    }
}
