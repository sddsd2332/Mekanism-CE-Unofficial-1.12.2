package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.PageMode;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Client-only ordered policy cache tied to one session and one policy revision. */
public final class QIOManagementPolicyClientCache {

    private final List<QIOPolicyEntrySnapshot> entries = new ArrayList<>();
    @Nullable
    private UUID sessionNonce;
    private long sourceRevision = -1;
    private int totalSize;
    @Nullable
    private QIOPageCursor nextCursor;
    @Nullable
    private UUID expectedPageRequestId;
    private PageMode pageMode = PageMode.CONFIGURED;
    private long recipeCatalogRevision = -1;
    @Nullable
    private MutationStatus lastMutationStatus;
    @Nullable
    private QIOPolicyEntrySnapshot lastAuthoritativeEntry;
    @Nullable
    private UUID lastMutationRequestId;
    @Nullable
    private UUID lastLookupRequestId;
    @Nullable
    private QIOPolicyEntrySnapshot lastLookupEntry;
    private long pageGeneration;
    private long mutationGeneration;
    private long lookupGeneration;

    public void expectPage(@Nonnull UUID requestId) {
        expectedPageRequestId = requestId;
    }

    public boolean applyPage(@Nonnull UUID responseSessionNonce,
          @Nonnull UUID responseRequestId, @Nonnull PageMode responsePageMode,
          long responseRecipeCatalogRevision, long responseRevision,
          int offset, int responseTotalSize,
          @Nonnull List<QIOPolicyEntrySnapshot> page,
          @Nullable QIOPageCursor responseNextCursor) {
        if (!responseRequestId.equals(expectedPageRequestId) ||
            responseRecipeCatalogRevision < -1 ||
            (responsePageMode == PageMode.CONFIGURED ?
                  responseRecipeCatalogRevision != -1 :
                  responseRecipeCatalogRevision < 0)) {
            return false;
        }
        if (offset > 0 && (responsePageMode != pageMode ||
            responseRecipeCatalogRevision != recipeCatalogRevision)) {
            return false;
        }
        if (!applyPageInternal(responseSessionNonce, responseRevision, offset,
              responseTotalSize, page, responseNextCursor)) {
            return false;
        }
        pageMode = responsePageMode;
        recipeCatalogRevision = responseRecipeCatalogRevision;
        expectedPageRequestId = null;
        return true;
    }

    public boolean applyPage(@Nonnull UUID responseSessionNonce, long responseRevision,
          int offset, int responseTotalSize,
          @Nonnull List<QIOPolicyEntrySnapshot> page,
          @Nullable QIOPageCursor responseNextCursor) {
        return applyPageInternal(responseSessionNonce, responseRevision, offset,
              responseTotalSize, page, responseNextCursor);
    }

    private boolean applyPageInternal(@Nonnull UUID responseSessionNonce,
          long responseRevision, int offset, int responseTotalSize,
          @Nonnull List<QIOPolicyEntrySnapshot> page,
          @Nullable QIOPageCursor responseNextCursor) {
        if (responseRevision < 0 || offset < 0 || responseTotalSize < 0 ||
            offset > responseTotalSize || page.size() > responseTotalSize - offset ||
            page.stream().anyMatch(entry ->
                  entry.getPolicyRevision() != responseRevision) ||
            !validNextCursor(responseSessionNonce, responseRevision,
                  offset + page.size(), responseTotalSize, responseNextCursor)) {
            return false;
        }
        if (offset == 0) {
            entries.clear();
            sessionNonce = responseSessionNonce;
            sourceRevision = responseRevision;
            totalSize = responseTotalSize;
        } else if (!responseSessionNonce.equals(sessionNonce) ||
            responseRevision != sourceRevision || responseTotalSize != totalSize ||
            offset != entries.size()) {
            return false;
        }
        entries.addAll(page);
        nextCursor = responseNextCursor;
        pageGeneration = nextGeneration(pageGeneration);
        return true;
    }

    public boolean applyMutation(@Nonnull UUID responseSessionNonce,
          @Nonnull UUID responseRequestId,
          @Nonnull MutationStatus status, long responseRevision,
          @Nonnull QIOPolicyEntrySnapshot authoritativeEntry) {
        if (responseRevision < 0 ||
            authoritativeEntry.getPolicyRevision() != responseRevision ||
            sessionNonce != null && !responseSessionNonce.equals(sessionNonce)) {
            return false;
        }
        if (sourceRevision != responseRevision) {
            entries.clear();
            sourceRevision = -1;
            totalSize = 0;
            nextCursor = null;
            expectedPageRequestId = null;
            pageMode = PageMode.CONFIGURED;
            recipeCatalogRevision = -1;
        }
        sessionNonce = responseSessionNonce;
        lastMutationRequestId = responseRequestId;
        lastMutationStatus = status;
        lastAuthoritativeEntry = authoritativeEntry;
        mutationGeneration = nextGeneration(mutationGeneration);
        return true;
    }

    public boolean applyLookup(@Nonnull UUID responseSessionNonce,
          @Nonnull UUID responseRequestId,
          @Nonnull QIOPolicyEntrySnapshot authoritativeEntry) {
        if (authoritativeEntry.getKind() !=
            QIOPolicyEntrySnapshot.Kind.DEVICE_DEFAULT ||
            authoritativeEntry.getDeviceUUID() == null ||
            sessionNonce != null && !responseSessionNonce.equals(sessionNonce)) {
            return false;
        }
        sessionNonce = responseSessionNonce;
        lastLookupRequestId = responseRequestId;
        lastLookupEntry = authoritativeEntry;
        lookupGeneration = nextGeneration(lookupGeneration);
        return true;
    }

    public void clear() {
        entries.clear();
        sessionNonce = null;
        sourceRevision = -1;
        totalSize = 0;
        nextCursor = null;
        expectedPageRequestId = null;
        pageMode = PageMode.CONFIGURED;
        recipeCatalogRevision = -1;
        lastMutationRequestId = null;
        lastLookupRequestId = null;
        lastMutationStatus = null;
        lastAuthoritativeEntry = null;
        lastLookupEntry = null;
    }

    @Nonnull
    public List<QIOPolicyEntrySnapshot> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
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

    @Nonnull
    public PageMode getPageMode() {
        return pageMode;
    }

    public long getRecipeCatalogRevision() {
        return recipeCatalogRevision;
    }

    @Nullable
    public MutationStatus getLastMutationStatus() {
        return lastMutationStatus;
    }

    @Nullable
    public UUID getLastMutationRequestId() {
        return lastMutationRequestId;
    }

    @Nullable
    public QIOPolicyEntrySnapshot getLastAuthoritativeEntry() {
        return lastAuthoritativeEntry;
    }

    public long getPageGeneration() {
        return pageGeneration;
    }

    public long getMutationGeneration() {
        return mutationGeneration;
    }

    public long getLookupGeneration() {
        return lookupGeneration;
    }

    @Nullable
    public UUID getLastLookupRequestId() {
        return lastLookupRequestId;
    }

    @Nullable
    public QIOPolicyEntrySnapshot getLastLookupEntry() {
        return lastLookupEntry;
    }

    private static boolean validNextCursor(UUID nonce, long revision, int nextOffset,
          int totalSize, @Nullable QIOPageCursor cursor) {
        if (cursor == null) {
            return nextOffset == totalSize;
        }
        return nextOffset < totalSize && nonce.equals(cursor.getSessionNonce()) &&
              revision == cursor.getSourceRevision() &&
              nextOffset == cursor.getOffset();
    }

    private static long nextGeneration(long generation) {
        return generation == Long.MAX_VALUE ? 0 : generation + 1;
    }
}
