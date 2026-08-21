package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeNode;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService.CancelStatus;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * QIO 处理模块中的 QIOCraftingMonitorClientCache 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingMonitorClientCache {

    private final List<QIOCraftingMonitorEntry> entries = new ArrayList<>();
    @Nullable private UUID sessionNonce;
    private long sourceRevision = -1;
    private int totalSize;
    @Nullable private QIOPageCursor nextCursor;
    private long pageGeneration;
    private long cancelGeneration;
    @Nullable private UUID lastCancelRequestId;
    @Nullable private CancelStatus lastCancelStatus;
    @Nullable private UUID lastCancelJobId;
    @Nullable private UUID detailJobId;
    private int detailPlanRevision = -1;
    private String detailStructuralSignature = "";
    private final List<QIOCraftingMonitorPlanEntry> planEntries = new ArrayList<>();
    private long planSourceRevision = -1;
    private int planTotalSize;
    @Nullable private QIOPageCursor nextPlanCursor;
    private long planGeneration;
    private final Map<Long, QIOCraftingMonitorRuntimeNode> runtimeNodes =
          new LinkedHashMap<>();
    private long detailRuntimeRevision = -1;
    private int runtimeNodeOffset;
    private boolean runtimeSweepPending;
    private long runtimeGeneration;
    private boolean runtimeBaselineRequired = true;
    @Nullable private QIOCraftingMonitorRuntimeSnapshot runtimeHeader;
    private long mutationGeneration;
    @Nullable private UUID lastMutationRequestId;
    @Nullable private UUID lastMutationJobId;
    @Nullable private String lastMutationStatus;

    public boolean applyPage(@Nonnull UUID nonce, long revision, int offset,
          int total, @Nonnull List<QIOCraftingMonitorEntry> page,
          @Nullable QIOPageCursor cursor) {
        if (revision < 0 || offset < 0 || total < 0 || offset > total ||
            page.size() > total - offset || !validCursor(nonce, revision,
                  offset + page.size(), total, cursor)) return false;
        if (offset == 0) {
            if (nonce.equals(sessionNonce) && sourceRevision >= 0 &&
                revision < sourceRevision) {
                return false;
            }
            entries.clear();
            sessionNonce = nonce;
            sourceRevision = revision;
            totalSize = total;
        } else if (!nonce.equals(sessionNonce) || sourceRevision != revision ||
            totalSize != total || offset != entries.size()) return false;
        entries.addAll(page);
        nextCursor = cursor;
        pageGeneration = next(pageGeneration);
        return true;
    }

    public boolean applyCancel(@Nonnull UUID nonce, @Nonnull UUID requestId,
          @Nonnull UUID jobId, @Nonnull CancelStatus status) {
        if (sessionNonce != null && !sessionNonce.equals(nonce)) return false;
        sessionNonce = nonce;
        entries.clear();
        sourceRevision = -1;
        totalSize = 0;
        nextCursor = null;
        lastCancelRequestId = requestId;
        lastCancelJobId = jobId;
        lastCancelStatus = status;
        cancelGeneration = next(cancelGeneration);
        return true;
    }

    public void clear() {
        clearEntries(); sessionNonce = null;
        lastCancelRequestId = null; lastCancelJobId = null;
        lastCancelStatus = null;
        lastMutationRequestId = null; lastMutationJobId = null;
        lastMutationStatus = null;
        clearDetail();
    }

    /** Invalidates the directory without discarding the selected job's loaded plan. */
    public void clearEntries() {
        entries.clear(); sourceRevision = -1; totalSize = 0; nextCursor = null;
    }

    public void selectDetail(@Nonnull UUID jobId, int planRevision) {
        if (planRevision <= 0) throw new IllegalArgumentException("planRevision must be positive");
        if (!jobId.equals(detailJobId) || planRevision != detailPlanRevision) {
            clearDetail();
            detailJobId = jobId;
            detailPlanRevision = planRevision;
        }
    }

    public boolean applyPlanPage(@Nonnull UUID nonce, @Nonnull UUID jobId,
          int planRevision, @Nonnull String structuralSignature, long sourceRevision,
          int offset, int total, @Nonnull List<QIOCraftingMonitorPlanEntry> page,
          @Nullable QIOPageCursor cursor) {
        if (!nonce.equals(sessionNonce) || !jobId.equals(detailJobId) ||
            planRevision != detailPlanRevision || sourceRevision < 0 || offset < 0 ||
            total < 0 || offset > total || page.size() > total - offset ||
            !validCursor(nonce, sourceRevision, offset + page.size(), total, cursor)) {
            return false;
        }
        if (offset == 0) {
            planEntries.clear();
            planSourceRevision = sourceRevision;
            planTotalSize = total;
            detailStructuralSignature = structuralSignature;
        } else if (sourceRevision != planSourceRevision || total != planTotalSize ||
            offset != planEntries.size() ||
            !detailStructuralSignature.equals(structuralSignature)) {
            return false;
        }
        planEntries.addAll(page);
        nextPlanCursor = cursor;
        runtimeNodeOffset = 0;
        runtimeSweepPending = false;
        runtimeBaselineRequired = true;
        planGeneration = next(planGeneration);
        return true;
    }

    public boolean applyRuntime(@Nonnull UUID nonce,
          @Nonnull QIOCraftingMonitorRuntimeSnapshot snapshot) {
        if (!nonce.equals(sessionNonce) || !snapshot.getJobId().equals(detailJobId) ||
            snapshot.getPlanRevision() != detailPlanRevision ||
            snapshot.getNodeOffset() != runtimeNodeOffset ||
            snapshot.getRequestedNodeCount() > planEntries.size() - runtimeNodeOffset ||
            !detailStructuralSignature.isEmpty() && !detailStructuralSignature.equals(
                  snapshot.getStructuralSignature())) {
            return false;
        }
        if (snapshot.isBaseline()) {
            if (runtimeNodeOffset == 0) {
                runtimeNodes.clear();
            } else if (!runtimeBaselineRequired) {
                return false;
            }
        } else if (detailRuntimeRevision < 0 ||
            snapshot.getBaseRuntimeRevision() != detailRuntimeRevision) {
            runtimeBaselineRequired = true;
            return false;
        }
        for (QIOCraftingMonitorRuntimeNode node : snapshot.getNodes()) {
            runtimeNodes.put(node.getNodeId(), node);
        }
        detailRuntimeRevision = snapshot.getRuntimeRevision();
        runtimeHeader = snapshot;
        runtimeNodeOffset += snapshot.getRequestedNodeCount();
        runtimeSweepPending = runtimeNodeOffset < planEntries.size();
        if (!runtimeSweepPending) {
            runtimeNodeOffset = 0;
            runtimeBaselineRequired = false;
        }
        runtimeGeneration = next(runtimeGeneration);
        return true;
    }

    public void requireRuntimeBaseline() {
        runtimeBaselineRequired = true;
        runtimeNodeOffset = 0;
        runtimeSweepPending = false;
    }

    public void clearDetail() {
        detailJobId = null;
        detailPlanRevision = -1;
        detailStructuralSignature = "";
        planEntries.clear();
        planSourceRevision = -1;
        planTotalSize = 0;
        nextPlanCursor = null;
        runtimeNodes.clear();
        detailRuntimeRevision = -1;
        runtimeNodeOffset = 0;
        runtimeSweepPending = false;
        runtimeBaselineRequired = true;
        runtimeHeader = null;
    }

    public boolean applyMutation(@Nonnull UUID nonce, @Nonnull UUID requestId,
          @Nonnull UUID jobId, @Nonnull String status) {
        if (sessionNonce != null && !sessionNonce.equals(nonce)) return false;
        sessionNonce = nonce;
        lastMutationRequestId = requestId;
        lastMutationJobId = jobId;
        lastMutationStatus = status;
        runtimeBaselineRequired = true;
        mutationGeneration = next(mutationGeneration);
        return true;
    }

    @Nonnull public List<QIOCraftingMonitorEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
    @Nullable public QIOCraftingMonitorEntry getEntry(@Nonnull UUID entryId) {
        for (QIOCraftingMonitorEntry entry : entries) {
            if (entryId.equals(entry.getEntryId())) return entry;
        }
        return null;
    }
    public long getSourceRevision() { return sourceRevision; }
    public int getTotalSize() { return totalSize; }
    @Nullable public QIOPageCursor getNextCursor() { return nextCursor; }
    public boolean isPageComplete() {
        return sourceRevision >= 0 && nextCursor == null && entries.size() == totalSize;
    }
    public long getPageGeneration() { return pageGeneration; }
    public long getCancelGeneration() { return cancelGeneration; }
    @Nullable public UUID getLastCancelRequestId() { return lastCancelRequestId; }
    @Nullable public UUID getLastCancelJobId() { return lastCancelJobId; }
    @Nullable public CancelStatus getLastCancelStatus() { return lastCancelStatus; }
    @Nullable public UUID getDetailJobId() { return detailJobId; }
    public int getDetailPlanRevision() { return detailPlanRevision; }
    @Nonnull public String getDetailStructuralSignature() { return detailStructuralSignature; }
    @Nonnull public List<QIOCraftingMonitorPlanEntry> getPlanEntries() {
        return Collections.unmodifiableList(planEntries);
    }
    public long getPlanSourceRevision() { return planSourceRevision; }
    public int getPlanTotalSize() { return planTotalSize; }
    @Nullable public QIOPageCursor getNextPlanCursor() { return nextPlanCursor; }
    public boolean isPlanComplete() {
        return planSourceRevision >= 0 && nextPlanCursor == null &&
              planEntries.size() == planTotalSize;
    }
    public long getPlanGeneration() { return planGeneration; }
    @Nonnull public Map<Long, QIOCraftingMonitorRuntimeNode> getRuntimeNodes() {
        return Collections.unmodifiableMap(runtimeNodes);
    }
    public long getDetailRuntimeRevision() { return detailRuntimeRevision; }
    public long getRuntimeGeneration() { return runtimeGeneration; }
    public boolean isRuntimeBaselineRequired() { return runtimeBaselineRequired; }
    public int getRuntimeNodeOffset() { return runtimeNodeOffset; }
    public boolean isRuntimeSweepPending() { return runtimeSweepPending; }
    @Nullable public QIOCraftingMonitorRuntimeSnapshot getRuntimeHeader() {
        return runtimeHeader;
    }
    public long getMutationGeneration() { return mutationGeneration; }
    @Nullable public UUID getLastMutationRequestId() { return lastMutationRequestId; }
    @Nullable public UUID getLastMutationJobId() { return lastMutationJobId; }
    @Nullable public String getLastMutationStatus() { return lastMutationStatus; }

    @Nonnull
    public Map<Long, Long> getKnownNodeRevisions() {
        Map<Long, Long> revisions = new LinkedHashMap<>();
        int end = Math.min(planEntries.size(), runtimeNodeOffset + 1_024);
        for (int index = runtimeNodeOffset; index < end; index++) {
            QIOCraftingMonitorPlanEntry entry = planEntries.get(index);
            QIOCraftingMonitorRuntimeNode runtime = runtimeNodes.get(entry.getNodeId());
            revisions.put(entry.getNodeId(), runtime == null ? -1 :
                  runtime.getNodeRevision());
        }
        return revisions;
    }

    private static boolean validCursor(UUID nonce, long revision, int offset,
          int total, @Nullable QIOPageCursor cursor) {
        return cursor == null ? offset == total : offset < total &&
              nonce.equals(cursor.getSessionNonce()) &&
              revision == cursor.getSourceRevision() && offset == cursor.getOffset();
    }

    private static long next(long value) { return value == Long.MAX_VALUE ? 0 : value + 1; }
}
