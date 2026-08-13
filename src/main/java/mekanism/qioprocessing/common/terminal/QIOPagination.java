package mekanism.qioprocessing.common.terminal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Stateless revision-aware pagination shared by all terminal directories. */
public final class QIOPagination {

    private QIOPagination() {
    }

    @Nonnull
    public static <T> QIOPage<T> page(@Nonnull List<T> orderedSource,
          long sourceRevision, @Nonnull UUID sessionNonce,
          @Nullable QIOPageCursor cursor, int requestedPageSize, int maximumPageSize) {
        Objects.requireNonNull(orderedSource, "orderedSource");
        Objects.requireNonNull(sessionNonce, "sessionNonce");
        if (sourceRevision < 0) {
            throw new IllegalArgumentException("sourceRevision cannot be negative");
        }
        if (maximumPageSize <= 0 || requestedPageSize <= 0 ||
            requestedPageSize > maximumPageSize) {
            throw new IllegalArgumentException("Invalid QIO terminal page size");
        }
        int offset = 0;
        if (cursor != null) {
            if (!sessionNonce.equals(cursor.getSessionNonce())) {
                throw new SecurityException("QIO page cursor belongs to another terminal session");
            }
            if (sourceRevision != cursor.getSourceRevision()) {
                throw new IllegalStateException("QIO terminal directory changed between pages");
            }
            offset = cursor.getOffset();
        }
        if (offset > orderedSource.size()) {
            throw new IllegalArgumentException("QIO page cursor is past the directory end");
        }
        int end = Math.min(orderedSource.size(), offset + requestedPageSize);
        QIOPageCursor next = end < orderedSource.size() ?
              new QIOPageCursor(sessionNonce, sourceRevision, end) : null;
        return new QIOPage<>(sourceRevision, offset, orderedSource.size(),
              orderedSource.subList(offset, end), next);
    }
}
