package mekanism.qioprocessing.common.terminal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable bounded page returned by a terminal directory service. */
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
