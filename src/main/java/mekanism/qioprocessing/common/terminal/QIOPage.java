package mekanism.qioprocessing.common.terminal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable bounded page returned by a terminal directory service. */
/**
 * QIO 处理模块中的 QIOPage 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPage<T> {

    private final long sourceRevision;
    private final int offset;
    private final int totalSize;
    private final List<T> entries;
    @Nullable
    private final QIOPageCursor nextCursor;

    QIOPage(long sourceRevision, int offset, int totalSize, List<T> entries,
          @Nullable QIOPageCursor nextCursor) {
        this.sourceRevision = sourceRevision;
        this.offset = offset;
        this.totalSize = totalSize;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.nextCursor = nextCursor;
    }

    public long getSourceRevision() {
        return sourceRevision;
    }

    public int getOffset() {
        return offset;
    }

    public int getTotalSize() {
        return totalSize;
    }

    @Nonnull
    public List<T> getEntries() {
        return entries;
    }

    @Nullable
    public QIOPageCursor getNextCursor() {
        return nextCursor;
    }
}
