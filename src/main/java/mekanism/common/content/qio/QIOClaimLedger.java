package mekanism.common.content.qio;

import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOResourceClaim;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Frequency-owned, persistent logical claim state. All methods are called under the QIO frequency lock. */
final class QIOClaimLedger {

    private static final String CLAIM_REVISION = "qioClaimRevision";
    private static final String CLAIMS = "qioClaims";
    private static final String RECEIPTS = "qioClaimReceipts";
    private static final int MAX_RECEIPTS = 131_072;
    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    });

    interface Storage {

        boolean isClaimable(UUID resource);

        @Nonnull
        QIOAmount getStored(UUID resource);

        boolean extractClaimed(Map<UUID, Long> resources);

        boolean persistPhysical();
    }

    static final class Submission {

        private final QIOClaimResult result;
        private final Set<UUID> changedResources;

        private Submission(QIOClaimResult result, Set<UUID> changedResources) {
            this.result = result;
            this.changedResources = changedResources;
        }

        QIOClaimResult getResult() {
            return result;
        }

        Set<UUID> getChangedResources() {
            return changedResources;
        }
    }

    private final Map<UUID, QIOResourceClaim> claims = new LinkedHashMap<>();
    private final Map<UUID, QIOAmount> committedByResource = new LinkedHashMap<>();
    private final Map<UUID, RequestReceipt> receipts = new LinkedHashMap<>();
    private long revision;

    long getRevision() {
        return revision;
    }

    void setClientRevision(long revision) {
        if (!claims.isEmpty() || !receipts.isEmpty()) {
            throw new IllegalStateException("Cannot replace a populated QIO claim ledger revision");
        }
        this.revision = Math.max(0, revision);
    }

    @Nonnull
    QIOAmount getCommitted(UUID resource) {
        return resource == null ? QIOAmount.ZERO : committedByResource.getOrDefault(resource, QIOAmount.ZERO);
    }

    @Nonnull
    QIOAmount getAvailable(UUID resource, QIOAmount stored) {
        QIOAmount committed = getCommitted(resource);
        return stored.compareTo(committed) <= 0 ? QIOAmount.ZERO : stored.subtract(committed);
    }

    @Nullable
    QIOResourceClaim getClaim(UUID claimId) {
        return claimId == null ? null : claims.get(claimId);
    }

    @Nonnull
    List<QIOResourceClaim> getClaims() {
        return Collections.unmodifiableList(new ArrayList<>(claims.values()));
    }

    /**
     * Assigns current physical stock to claims without mutating logical ownership. This keeps
     * claims durable while drive holders are temporarily absent and gives consumers a stable
     * view of which orders may safely proceed.
     */
    @Nonnull
    Map<UUID, Map<UUID, Long>> calculateBackedAmounts(
          @Nonnull Function<UUID, QIOAmount> storedAmount) {
        Objects.requireNonNull(storedAmount, "storedAmount");
        List<QIOResourceClaim> ordered = new ArrayList<>(claims.values());
        ordered.sort(Comparator.comparingLong(QIOResourceClaim::getPriority).reversed()
              .thenComparingLong(QIOResourceClaim::getEnqueueSequence)
              .thenComparing(QIOResourceClaim::getOwnerNamespace)
              .thenComparing(QIOResourceClaim::getOwnerId)
              .thenComparing(claim -> claim.getClaimId().toString()));
        Map<UUID, QIOAmount> remainingByResource = new LinkedHashMap<>();
        Map<UUID, Map<UUID, Long>> backingByClaim = new LinkedHashMap<>();
        for (QIOResourceClaim claim : ordered) {
            Map<UUID, Long> backed = new LinkedHashMap<>();
            for (Map.Entry<UUID, Long> entry : claim.getResourceAmounts().entrySet()) {
                QIOAmount remaining = remainingByResource.computeIfAbsent(entry.getKey(),
                      storedAmount);
                long granted = Math.min(entry.getValue(), remaining.longValueClamped());
                if (granted > 0) {
                    backed.put(entry.getKey(), granted);
                    remainingByResource.put(entry.getKey(), remaining.subtract(granted));
                }
            }
            backingByClaim.put(claim.getClaimId(), Collections.unmodifiableMap(backed));
        }
        return Collections.unmodifiableMap(backingByClaim);
    }

    @Nonnull
    Submission submit(@Nonnull QIOClaimRequest request, long contentsRevision, @Nonnull Storage storage) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(storage, "storage");
        String digest = digest(request);
        RequestReceipt existingReceipt = receipts.get(request.getRequestId());
        if (existingReceipt != null) {
            if (existingReceipt.digest.equals(digest)) {
                if (request.getMode() == QIOClaimRequest.Mode.CONSUME &&
                      !existingReceipt.result.getConsumedAmounts().isEmpty()) {
                    QIOClaimResult reconciliation = reconcilePhysicalConsume(request, storage,
                          existingReceipt.result.getConsumedAmounts());
                    if (reconciliation != null) {
                        return unchanged(reconciliation);
                    }
                }
                if (request.getMode() == QIOClaimRequest.Mode.CONSUME &&
                      existingReceipt.result.getStatus() == QIOClaimResult.Status.FAILED) {
                    Submission retry = consume(request, storage);
                    putReceipt(request.getRequestId(), new RequestReceipt(digest, retry.result));
                    return retry;
                }
                return unchanged(existingReceipt.result);
            }
            return unchanged(result(request, QIOClaimResult.Status.REQUEST_ID_CONFLICT, null,
                  Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
        }
        boolean reconciledConsume = request.getMode() == QIOClaimRequest.Mode.CONSUME;
        if (!reconciledConsume && request.getExpectedContentsRevision() != QIOClaimRequest.ANY_REVISION &&
              request.getExpectedContentsRevision() != contentsRevision) {
            return record(request, digest, result(request, QIOClaimResult.Status.STALE_CONTENTS, null,
                  Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()), Collections.emptySet());
        }
        if (!reconciledConsume && request.getExpectedClaimRevision() != QIOClaimRequest.ANY_REVISION &&
              request.getExpectedClaimRevision() != revision) {
            return record(request, digest, result(request, QIOClaimResult.Status.STALE_CLAIMS, null,
                  Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()), Collections.emptySet());
        }
        Submission submission;
        switch (request.getMode()) {
            case CREATE_OR_ADJUST:
                submission = createOrAdjust(request, storage);
                break;
            case REASSIGN:
                submission = reassign(request, storage);
                break;
            case RELEASE:
                submission = release(request);
                break;
            case CONSUME:
                submission = consume(request, storage);
                break;
            default:
                submission = unchanged(result(request, QIOClaimResult.Status.INVALID_REQUEST, null,
                      Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
                break;
        }
        putReceipt(request.getRequestId(), new RequestReceipt(digest, submission.result));
        return submission;
    }

    private Submission createOrAdjust(QIOClaimRequest request, Storage storage) {
        if (!allClaimable(request.getResourceAmounts(), storage)) {
            return unchanged(result(request, QIOClaimResult.Status.INVALID_REQUEST, null,
                  Collections.emptyMap(), request.getResourceAmounts(), Collections.emptyMap()));
        }
        QIOResourceClaim previous = claims.get(request.getClaimId());
        if (previous != null && !ownerMatches(previous, request)) {
            return unchanged(result(request, QIOClaimResult.Status.OWNER_MISMATCH, previous,
                  previous.getResourceAmounts(), Collections.emptyMap(), Collections.emptyMap()));
        }
        Map<UUID, Long> previousAmounts = previous == null ? Collections.emptyMap() : previous.getResourceAmounts();
        Map<UUID, Long> committed = new LinkedHashMap<>();
        Map<UUID, Long> unavailable = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> target : request.getResourceAmounts().entrySet()) {
            long owned = previousAmounts.getOrDefault(target.getKey(), 0L);
            QIOAmount free = getAvailable(target.getKey(), storage.getStored(target.getKey())).add(owned);
            long granted = Math.min(target.getValue(), free.longValueClamped());
            if (granted > 0) {
                committed.put(target.getKey(), granted);
            }
            if (granted < target.getValue()) {
                unavailable.put(target.getKey(), target.getValue() - granted);
            }
        }
        boolean metadataChanged = previous == null || previous.getPriority() != request.getPriority() ||
              previous.getEnqueueSequence() != request.getEnqueueSequence();
        boolean amountsChanged = !previousAmounts.equals(committed);
        if (!metadataChanged && !amountsChanged) {
            QIOClaimResult.Status status = unavailable.isEmpty() ? QIOClaimResult.Status.NO_CHANGE : QIOClaimResult.Status.PARTIAL;
            return unchanged(result(request, status, previous, committed, unavailable, Collections.emptyMap()));
        }
        Set<UUID> changed = replaceClaim(previous, request.getClaimId(), request.getOwnerNamespace(),
              request.getOwnerId(), request.getPriority(), request.getEnqueueSequence(), committed);
        QIOResourceClaim current = claims.get(request.getClaimId());
        QIOClaimResult.Status status = unavailable.isEmpty() ? QIOClaimResult.Status.APPLIED : QIOClaimResult.Status.PARTIAL;
        return changed(result(request, status, current, committed, unavailable, Collections.emptyMap()), changed);
    }

    private Submission reassign(QIOClaimRequest request, Storage storage) {
        if (!allClaimable(request.getResourceAmounts(), storage)) {
            return unchanged(result(request, QIOClaimResult.Status.INVALID_REQUEST, null,
                  Collections.emptyMap(), request.getResourceAmounts(), Collections.emptyMap()));
        }
        UUID sourceId = request.getSourceClaimId();
        if (sourceId == null || sourceId.equals(request.getClaimId())) {
            return unchanged(result(request, QIOClaimResult.Status.INVALID_REQUEST, null,
                  Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
        }
        QIOResourceClaim source = claims.get(sourceId);
        QIOResourceClaim targetPrevious = claims.get(request.getClaimId());
        if (source == null) {
            return unchanged(result(request, QIOClaimResult.Status.NOT_FOUND, null,
                  Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
        }
        if (!source.getOwnerNamespace().equals(request.getOwnerNamespace()) ||
              targetPrevious != null && !ownerMatches(targetPrevious, request)) {
            return unchanged(result(request, QIOClaimResult.Status.OWNER_MISMATCH, targetPrevious,
                  targetPrevious == null ? Collections.emptyMap() : targetPrevious.getResourceAmounts(),
                  Collections.emptyMap(), Collections.emptyMap()));
        }
        Map<UUID, Long> targetOld = targetPrevious == null ? Collections.emptyMap() : targetPrevious.getResourceAmounts();
        Map<UUID, Long> targetNew = new LinkedHashMap<>();
        Map<UUID, Long> unavailable = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> desired : request.getResourceAmounts().entrySet()) {
            long sourceOwned = source.getResourceAmounts().getOrDefault(desired.getKey(), 0L);
            long targetOwned = targetOld.getOrDefault(desired.getKey(), 0L);
            QIOAmount usable = getAvailable(desired.getKey(), storage.getStored(desired.getKey()))
                  .add(sourceOwned).add(targetOwned);
            long granted = Math.min(desired.getValue(), usable.longValueClamped());
            if (granted > 0) {
                targetNew.put(desired.getKey(), granted);
            }
            if (granted < desired.getValue()) {
                unavailable.put(desired.getKey(), desired.getValue() - granted);
            }
        }
        Set<UUID> changedResources = new HashSet<>();
        changedResources.addAll(removeClaim(source));
        if (targetPrevious != null) {
            changedResources.addAll(removeClaim(targetPrevious));
        }
        QIOResourceClaim target = createClaim(request.getClaimId(), request.getOwnerNamespace(), request.getOwnerId(),
              request.getPriority(), request.getEnqueueSequence(), targetPrevious, targetNew);
        if (target != null) {
            claims.put(target.getClaimId(), target);
            addTotals(target.getResourceAmounts());
            changedResources.addAll(target.getResourceAmounts().keySet());
        }
        incrementRevision();
        QIOClaimResult.Status status = unavailable.isEmpty() ? QIOClaimResult.Status.APPLIED : QIOClaimResult.Status.PARTIAL;
        return new Submission(result(request, status, target, targetNew, unavailable, Collections.emptyMap()),
              immutableSet(changedResources));
    }

    private Submission release(QIOClaimRequest request) {
        QIOResourceClaim previous = claims.get(request.getClaimId());
        if (previous == null) {
            return unchanged(result(request, QIOClaimResult.Status.NOT_FOUND, null,
                  Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
        }
        if (!ownerMatches(previous, request)) {
            return unchanged(result(request, QIOClaimResult.Status.OWNER_MISMATCH, previous,
                  previous.getResourceAmounts(), Collections.emptyMap(), Collections.emptyMap()));
        }
        Map<UUID, Long> remaining = new LinkedHashMap<>(previous.getResourceAmounts());
        if (request.getResourceAmounts().isEmpty()) {
            remaining.clear();
        } else {
            for (Map.Entry<UUID, Long> release : request.getResourceAmounts().entrySet()) {
                long current = remaining.getOrDefault(release.getKey(), 0L);
                long next = Math.max(0, current - release.getValue());
                if (next == 0) {
                    remaining.remove(release.getKey());
                } else {
                    remaining.put(release.getKey(), next);
                }
            }
        }
        if (remaining.equals(previous.getResourceAmounts())) {
            return unchanged(result(request, QIOClaimResult.Status.NO_CHANGE, previous, remaining,
                  Collections.emptyMap(), Collections.emptyMap()));
        }
        Set<UUID> changed = replaceClaim(previous, request.getClaimId(), previous.getOwnerNamespace(),
              previous.getOwnerId(), previous.getPriority(), previous.getEnqueueSequence(), remaining);
        return changed(result(request, QIOClaimResult.Status.APPLIED, claims.get(request.getClaimId()),
              remaining, Collections.emptyMap(), Collections.emptyMap()), changed);
    }

    private Submission consume(QIOClaimRequest request, Storage storage) {
        QIOResourceClaim previous = claims.get(request.getClaimId());
        if (previous == null) {
            return unchanged(result(request, QIOClaimResult.Status.NOT_FOUND, null,
                  Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
        }
        if (!ownerMatches(previous, request)) {
            return unchanged(result(request, QIOClaimResult.Status.OWNER_MISMATCH, previous,
                  previous.getResourceAmounts(), Collections.emptyMap(), Collections.emptyMap()));
        }
        Map<UUID, Long> consume = request.getResourceAmounts().isEmpty() ?
              previous.getResourceAmounts() : request.getResourceAmounts();
        if (!allClaimable(consume, storage)) {
            return unchanged(result(request, QIOClaimResult.Status.INVALID_REQUEST, previous,
                  previous.getResourceAmounts(), consume, Collections.emptyMap()));
        }
        for (Map.Entry<UUID, Long> entry : consume.entrySet()) {
            if (entry.getValue() > previous.getResourceAmounts().getOrDefault(entry.getKey(), 0L)) {
                return unchanged(result(request, QIOClaimResult.Status.INSUFFICIENT_RESOURCES, previous,
                      previous.getResourceAmounts(), consume, Collections.emptyMap()));
            }
        }
        Map<UUID, Long> physicalExtraction = remainingPhysicalExtraction(request, storage, consume);
        if (physicalExtraction == null) {
            return unchanged(result(request, QIOClaimResult.Status.RECONCILIATION_CONFLICT,
                  previous, previous.getResourceAmounts(), consume, Collections.emptyMap()));
        }
        if (!physicalExtraction.isEmpty() && !storage.extractClaimed(physicalExtraction) ||
              !storage.persistPhysical()) {
            return unchanged(result(request, QIOClaimResult.Status.FAILED, previous,
                  previous.getResourceAmounts(), Collections.emptyMap(), Collections.emptyMap()));
        }
        Map<UUID, Long> remaining = new LinkedHashMap<>(previous.getResourceAmounts());
        for (Map.Entry<UUID, Long> consumed : consume.entrySet()) {
            long next = remaining.get(consumed.getKey()) - consumed.getValue();
            if (next == 0) {
                remaining.remove(consumed.getKey());
            } else {
                remaining.put(consumed.getKey(), next);
            }
        }
        Set<UUID> changed = replaceClaim(previous, request.getClaimId(), previous.getOwnerNamespace(),
              previous.getOwnerId(), previous.getPriority(), previous.getEnqueueSequence(), remaining);
        return changed(result(request, QIOClaimResult.Status.APPLIED, claims.get(request.getClaimId()),
              remaining, Collections.emptyMap(), consume), changed);
    }

    /**
     * Reaches the exact post-consume physical target before replaying a saved logical receipt.
     * Each resource must still be at its exact baseline or at its exact post-consume target.
     * Intermediate values are ambiguous with unrelated storage traffic and therefore fail closed.
     */
    @Nullable
    private QIOClaimResult reconcilePhysicalConsume(QIOClaimRequest request, Storage storage,
          Map<UUID, Long> consumed) {
        Map<UUID, Long> remaining = remainingPhysicalExtraction(request, storage, consumed);
        QIOResourceClaim current = claims.get(request.getClaimId());
        if (remaining == null) {
            return result(request, QIOClaimResult.Status.RECONCILIATION_CONFLICT, current,
                  current == null ? Collections.emptyMap() : current.getResourceAmounts(),
                  consumed, Collections.emptyMap());
        }
        if (!remaining.isEmpty() && !storage.extractClaimed(remaining) || !storage.persistPhysical()) {
            return result(request, QIOClaimResult.Status.FAILED, current,
                  current == null ? Collections.emptyMap() : current.getResourceAmounts(),
                  Collections.emptyMap(), Collections.emptyMap());
        }
        return null;
    }

    @Nullable
    private static Map<UUID, Long> remainingPhysicalExtraction(QIOClaimRequest request,
          Storage storage, Map<UUID, Long> consumed) {
        Map<UUID, Long> remaining = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> entry : consumed.entrySet()) {
            java.math.BigInteger baseline = request.getExpectedStoredAmounts().get(entry.getKey());
            if (baseline == null) {
                return null;
            }
            java.math.BigInteger target = baseline.subtract(
                  java.math.BigInteger.valueOf(entry.getValue()));
            java.math.BigInteger current = storage.getStored(entry.getKey()).toBigInteger();
            if (!current.equals(target) && !current.equals(baseline)) {
                return null;
            }
            long missing = current.equals(baseline) ? entry.getValue() : 0;
            if (missing > 0) {
                remaining.put(entry.getKey(), missing);
            }
        }
        return remaining;
    }

    private Set<UUID> replaceClaim(@Nullable QIOResourceClaim previous, UUID claimId,
          String ownerNamespace, String ownerId, long priority, long enqueueSequence,
          Map<UUID, Long> amounts) {
        Set<UUID> changedResources = new HashSet<>();
        if (previous != null) {
            changedResources.addAll(removeClaim(previous));
        }
        QIOResourceClaim next = createClaim(claimId, ownerNamespace, ownerId, priority,
              enqueueSequence, previous, amounts);
        if (next != null) {
            claims.put(claimId, next);
            addTotals(amounts);
            changedResources.addAll(amounts.keySet());
        }
        incrementRevision();
        return immutableSet(changedResources);
    }

    @Nullable
    private QIOResourceClaim createClaim(UUID claimId, String ownerNamespace, String ownerId,
          long priority, long enqueueSequence, @Nullable QIOResourceClaim previous,
          Map<UUID, Long> amounts) {
        if (amounts.isEmpty()) {
            return null;
        }
        long claimRevision = previous == null ? 1 : nextRevision(previous.getRevision());
        return new QIOResourceClaim(claimId, ownerNamespace, ownerId, priority, enqueueSequence,
              claimRevision, QIOResourceClaim.State.ACTIVE, amounts);
    }

    private Set<UUID> removeClaim(QIOResourceClaim claim) {
        claims.remove(claim.getClaimId());
        subtractTotals(claim.getResourceAmounts());
        return new HashSet<>(claim.getResourceAmounts().keySet());
    }

    private void addTotals(Map<UUID, Long> amounts) {
        for (Map.Entry<UUID, Long> entry : amounts.entrySet()) {
            committedByResource.put(entry.getKey(),
                  committedByResource.getOrDefault(entry.getKey(), QIOAmount.ZERO).add(entry.getValue()));
        }
    }

    private void subtractTotals(Map<UUID, Long> amounts) {
        for (Map.Entry<UUID, Long> entry : amounts.entrySet()) {
            QIOAmount current = committedByResource.getOrDefault(entry.getKey(), QIOAmount.ZERO);
            QIOAmount amount = QIOAmount.of(entry.getValue());
            if (current.compareTo(amount) <= 0) {
                committedByResource.remove(entry.getKey());
            } else {
                committedByResource.put(entry.getKey(), current.subtract(amount));
            }
        }
    }

    private void incrementRevision() {
        revision = nextRevision(revision);
    }

    private static boolean ownerMatches(QIOResourceClaim claim, QIOClaimRequest request) {
        return claim.getOwnerNamespace().equals(request.getOwnerNamespace()) &&
              claim.getOwnerId().equals(request.getOwnerId());
    }

    private static boolean allClaimable(Map<UUID, Long> amounts, Storage storage) {
        for (UUID resource : amounts.keySet()) {
            if (!storage.isClaimable(resource)) {
                return false;
            }
        }
        return true;
    }

    private Submission record(QIOClaimRequest request, String digest, QIOClaimResult result,
          Set<UUID> changedResources) {
        putReceipt(request.getRequestId(), new RequestReceipt(digest, result));
        return new Submission(result, immutableSet(changedResources));
    }

    private static Submission changed(QIOClaimResult result, Set<UUID> changedResources) {
        return new Submission(result, immutableSet(changedResources));
    }

    private static Submission unchanged(QIOClaimResult result) {
        return new Submission(result, Collections.emptySet());
    }

    private QIOClaimResult result(QIOClaimRequest request, QIOClaimResult.Status status,
          @Nullable QIOResourceClaim claim, Map<UUID, Long> committed, Map<UUID, Long> unavailable,
          Map<UUID, Long> consumed) {
        return new QIOClaimResult(request.getRequestId(), status, revision, claim, committed,
              unavailable, consumed);
    }

    void write(NBTTagCompound data) {
        data.setLong(CLAIM_REVISION, revision);
        NBTTagList claimList = new NBTTagList();
        for (QIOResourceClaim claim : claims.values()) {
            claimList.appendTag(writeClaim(claim));
        }
        data.setTag(CLAIMS, claimList);
        NBTTagList receiptList = new NBTTagList();
        for (Map.Entry<UUID, RequestReceipt> entry : receipts.entrySet()) {
            NBTTagCompound receipt = new NBTTagCompound();
            receipt.setString("requestId", entry.getKey().toString());
            receipt.setString("digest", entry.getValue().digest);
            receipt.setTag("result", writeResult(entry.getValue().result));
            receiptList.appendTag(receipt);
        }
        data.setTag(RECEIPTS, receiptList);
    }

    void read(NBTTagCompound data) {
        claims.clear();
        committedByResource.clear();
        receipts.clear();
        revision = Math.max(0, data.getLong(CLAIM_REVISION));
        NBTTagList claimList = data.getTagList(CLAIMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < claimList.tagCount(); i++) {
            try {
                QIOResourceClaim claim = readClaim(claimList.getCompoundTagAt(i));
                if (claims.putIfAbsent(claim.getClaimId(), claim) == null) {
                    addTotals(claim.getResourceAmounts());
                }
            } catch (RuntimeException e) {
                QIOLog.LOGGER.error("Unable to restore a QIO resource claim", e);
            }
        }
        NBTTagList receiptList = data.getTagList(RECEIPTS, Constants.NBT.TAG_COMPOUND);
        int firstReceipt = Math.max(0, receiptList.tagCount() - MAX_RECEIPTS);
        for (int i = firstReceipt; i < receiptList.tagCount(); i++) {
            NBTTagCompound receipt = receiptList.getCompoundTagAt(i);
            try {
                UUID requestId = UUID.fromString(receipt.getString("requestId"));
                String storedDigest = receipt.getString("digest");
                QIOClaimResult storedResult = readResult(receipt.getCompoundTag("result"));
                if (!storedDigest.isEmpty() && requestId.equals(storedResult.getRequestId())) {
                    receipts.putIfAbsent(requestId, new RequestReceipt(storedDigest, storedResult));
                }
            } catch (RuntimeException e) {
                QIOLog.LOGGER.error("Unable to restore a QIO claim request receipt", e);
            }
        }
        if (firstReceipt > 0) {
            QIOLog.LOGGER.warn("Discarded {} oldest QIO claim receipts above the supported limit {}",
                  firstReceipt, MAX_RECEIPTS);
        }
    }

    private static NBTTagCompound writeClaim(QIOResourceClaim claim) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("claimId", claim.getClaimId().toString());
        data.setString("ownerNamespace", claim.getOwnerNamespace());
        data.setString("ownerId", claim.getOwnerId());
        data.setLong("priority", claim.getPriority());
        data.setLong("enqueueSequence", claim.getEnqueueSequence());
        data.setLong("revision", claim.getRevision());
        data.setString("state", claim.getState().name());
        data.setTag("resources", writeAmounts(claim.getResourceAmounts()));
        return data;
    }

    private static QIOResourceClaim readClaim(NBTTagCompound data) {
        return new QIOResourceClaim(UUID.fromString(data.getString("claimId")),
              data.getString("ownerNamespace"), data.getString("ownerId"), data.getLong("priority"),
              data.getLong("enqueueSequence"), data.getLong("revision"),
              QIOResourceClaim.State.valueOf(data.getString("state")), readAmounts(data.getTagList("resources",
              Constants.NBT.TAG_COMPOUND)));
    }

    private static NBTTagCompound writeResult(QIOClaimResult result) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("requestId", result.getRequestId().toString());
        data.setString("status", result.getStatus().name());
        data.setLong("claimRevision", result.getClaimRevision());
        if (result.getClaim() != null) {
            data.setTag("claim", writeClaim(result.getClaim()));
        }
        data.setTag("committed", writeAmounts(result.getCommittedAmounts()));
        data.setTag("unavailable", writeAmounts(result.getUnavailableAmounts()));
        data.setTag("consumed", writeAmounts(result.getConsumedAmounts()));
        return data;
    }

    private static QIOClaimResult readResult(NBTTagCompound data) {
        QIOResourceClaim claim = data.hasKey("claim", Constants.NBT.TAG_COMPOUND) ?
              readClaim(data.getCompoundTag("claim")) : null;
        return new QIOClaimResult(UUID.fromString(data.getString("requestId")),
              QIOClaimResult.Status.valueOf(data.getString("status")), data.getLong("claimRevision"), claim,
              readAmounts(data.getTagList("committed", Constants.NBT.TAG_COMPOUND)),
              readAmounts(data.getTagList("unavailable", Constants.NBT.TAG_COMPOUND)),
              readAmounts(data.getTagList("consumed", Constants.NBT.TAG_COMPOUND)));
    }

    private static NBTTagList writeAmounts(Map<UUID, Long> amounts) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Long> entry : amounts.entrySet()) {
            NBTTagCompound amount = new NBTTagCompound();
            amount.setString("resource", entry.getKey().toString());
            amount.setLong("amount", entry.getValue());
            list.appendTag(amount);
        }
        return list;
    }

    private static Map<UUID, Long> readAmounts(NBTTagList list) {
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound amount = list.getCompoundTagAt(i);
            UUID resource = UUID.fromString(amount.getString("resource"));
            long value = amount.getLong("amount");
            if (value <= 0 || amounts.putIfAbsent(resource, value) != null) {
                throw new IllegalArgumentException("Invalid QIO claim resource amount");
            }
        }
        return amounts;
    }

    private static String digest(QIOClaimRequest request) {
        StringBuilder value = new StringBuilder();
        value.append(request.getMode()).append('|').append(request.getClaimId()).append('|')
              .append(request.getSourceClaimId()).append('|').append(request.getTransferId()).append('|')
              .append(request.getOwnerNamespace()).append('|').append(request.getOwnerId()).append('|')
              .append(request.getPriority()).append('|').append(request.getEnqueueSequence()).append('|')
              .append(request.getExpectedContentsRevision()).append('|').append(request.getExpectedClaimRevision());
        for (Map.Entry<UUID, Long> entry : request.getResourceAmounts().entrySet()) {
            value.append('|').append(entry.getKey()).append('=').append(entry.getValue());
            if (!request.getExpectedStoredAmounts().isEmpty()) {
                value.append('@').append(request.getExpectedStoredAmounts().get(entry.getKey()));
            }
        }
        MessageDigest digest = SHA_256.get();
        digest.reset();
        byte[] bytes = digest.digest(value.toString().getBytes(StandardCharsets.UTF_8));
        char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int current = bytes[index] & 0xFF;
            encoded[index * 2] = LOWER_HEX[current >>> 4];
            encoded[index * 2 + 1] = LOWER_HEX[current & 0x0F];
        }
        return new String(encoded);
    }

    private static Set<UUID> immutableSet(Set<UUID> values) {
        return values.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(values));
    }

    private void putReceipt(UUID requestId, RequestReceipt receipt) {
        if (!receipts.containsKey(requestId) && receipts.size() >= MAX_RECEIPTS) {
            java.util.Iterator<UUID> iterator = receipts.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        receipts.put(requestId, receipt);
    }

    private static long nextRevision(long current) {
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1;
    }

    private static final class RequestReceipt {

        private final String digest;
        private final QIOClaimResult result;

        private RequestReceipt(String digest, QIOClaimResult result) {
            this.digest = digest;
            this.result = result;
        }
    }
}
