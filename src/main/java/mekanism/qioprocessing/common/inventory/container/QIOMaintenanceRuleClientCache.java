package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Client-only revision-consistent maintenance rule page cache. */
/**
 * QIO 处理模块中的 QIOMaintenanceRuleClientCache 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOMaintenanceRuleClientCache {

    private final List<QIOMaintenanceRule> rules = new ArrayList<>();
    private final List<QIOMaintenanceRule> readOnlyRules = Collections.unmodifiableList(rules);
    @Nullable
    private UUID sessionNonce;
    private long sourceRevision = -1;
    private int totalSize;
    @Nullable
    private QIOPageCursor nextCursor;
    private long pageGeneration;
    private long mutationGeneration;
    @Nullable
    private UUID lastMutationRequestId;
    @Nullable
    private MutationStatus lastMutationStatus;
    @Nullable
    private QIOMaintenanceRule lastAuthoritativeRule;

    public boolean applyPage(@Nonnull UUID responseSessionNonce,
          long responseRevision, int offset, int responseTotalSize,
          @Nonnull List<QIOMaintenanceRule> page,
          @Nullable QIOPageCursor responseNextCursor) {
        if (responseRevision < 0 || offset < 0 || responseTotalSize < 0 ||
            offset > responseTotalSize || page.size() > responseTotalSize - offset ||
            !validNextCursor(responseSessionNonce, responseRevision,
                  offset + page.size(), responseTotalSize, responseNextCursor)) {
            return false;
        }
        if (offset == 0) {
            if (responseSessionNonce.equals(sessionNonce) && sourceRevision >= 0 &&
                responseRevision < sourceRevision) {
                return false;
            }
            rules.clear();
            sessionNonce = responseSessionNonce;
            sourceRevision = responseRevision;
            totalSize = responseTotalSize;
        } else if (!responseSessionNonce.equals(sessionNonce) ||
            responseRevision != sourceRevision || responseTotalSize != totalSize ||
            offset != rules.size()) {
            return false;
        }
        rules.addAll(page);
        nextCursor = responseNextCursor;
        pageGeneration = nextGeneration(pageGeneration);
        return true;
    }

    public boolean applyMutation(@Nonnull UUID responseSessionNonce,
          @Nonnull UUID requestId, @Nonnull MutationStatus status,
          long responseRevision, @Nullable QIOMaintenanceRule authoritativeRule) {
        if (responseRevision < 0 ||
            sessionNonce != null && !responseSessionNonce.equals(sessionNonce)) {
            return false;
        }
        sessionNonce = responseSessionNonce;
        rules.clear();
        sourceRevision = -1;
        totalSize = 0;
        nextCursor = null;
        lastMutationRequestId = requestId;
        lastMutationStatus = status;
        lastAuthoritativeRule = authoritativeRule;
        mutationGeneration = nextGeneration(mutationGeneration);
        return true;
    }

    public void clear() {
        rules.clear();
        sessionNonce = null;
        sourceRevision = -1;
        totalSize = 0;
        nextCursor = null;
        lastMutationRequestId = null;
        lastMutationStatus = null;
        lastAuthoritativeRule = null;
    }

    @Nonnull
    public List<QIOMaintenanceRule> getRules() {
        return readOnlyRules;
    }

    public long getSourceRevision() {
        return sourceRevision;
    }

    public int getTotalSize() {
        return totalSize;
    }

    @Nullable
    public QIOPageCursor getNextCursor() {
        return nextCursor;
    }

    public long getPageGeneration() {
        return pageGeneration;
    }

    public long getMutationGeneration() {
        return mutationGeneration;
    }

    @Nullable
    public UUID getLastMutationRequestId() {
        return lastMutationRequestId;
    }

    @Nullable
    public MutationStatus getLastMutationStatus() {
        return lastMutationStatus;
    }

    @Nullable
    public QIOMaintenanceRule getLastAuthoritativeRule() {
        return lastAuthoritativeRule;
    }

    private static boolean validNextCursor(UUID nonce, long revision,
          int nextOffset, int totalSize, @Nullable QIOPageCursor cursor) {
        if (cursor == null) {
            return nextOffset == totalSize;
        }
        return nextOffset < totalSize && nonce.equals(cursor.getSessionNonce()) &&
              revision == cursor.getSourceRevision() && nextOffset == cursor.getOffset();
    }

    private static long nextGeneration(long generation) {
        return generation == Long.MAX_VALUE ? 0 : generation + 1;
    }
}
