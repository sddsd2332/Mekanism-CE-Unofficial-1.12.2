package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.item.ItemStack;

import java.math.BigInteger;
import java.util.*;

/**
 * Converts proven initial stock demand into a claim request without mutating QIO.
 */
public final class QIOPlanClaimIntent {
    public static final String OWNER_NAMESPACE = "mekanismqioprocessing";
    private static final BigInteger MAX_PLANNABLE_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);

    private QIOPlanClaimIntent() {
    }

    /**
     * Capture only item availability on the owning server thread before background planning.
     */
    public static Map<RecipeItemKey, Long> inventory(QIOStorageSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<RecipeItemKey, Long> available = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            ItemStack stack = entry.getItem();
            if (stack.isEmpty()) continue;
            BigInteger exact = entry.getExactAvailableAmount();
            if (exact.signum() == 0) continue;
            if (exact.compareTo(MAX_PLANNABLE_AMOUNT) > 0) {
                throw new IllegalArgumentException("QIO item availability exceeds planner long range");
            }
            RecipeItemKey key = RecipeItemKey.capture(stack);
            if (available.putIfAbsent(key, exact.longValueExact()) != null) {
                throw new IllegalArgumentException("Duplicate QIO item identity in snapshot");
            }
        }
        return Collections.unmodifiableMap(available);
    }

    /**
     * Resolve exact item identities and claim revisions from one authorized server-side QIO snapshot.
     */
    public static Optional<QIOClaimRequest> prepare(QIOPatternPlanner.Result plan,
                                                    QIOStorageSnapshot snapshot, UUID orderId, UUID requestId, UUID claimId) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(claimId, "claimId");
        if (!plan.successful() || snapshot.getContentsRevision() < 0 || snapshot.getClaimRevision() < 0) {
            throw new IllegalArgumentException("Claim requires a successful plan and exact QIO revisions");
        }
        Map<RecipeItemKey, UUID> resourceIds = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            ItemStack stack = entry.getItem();
            if (stack.isEmpty()) continue;
            RecipeItemKey key = RecipeItemKey.capture(stack);
            Long needed = plan.initialWithdrawals().get(key);
            if (needed == null) continue;
            if (entry.getExactAvailableAmount().compareTo(BigInteger.valueOf(needed)) < 0
                    || resourceIds.putIfAbsent(key, entry.getResourceUUID()) != null) {
                throw new IllegalArgumentException("Unavailable or ambiguous QIO item in snapshot");
            }
        }
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        for (Map.Entry<RecipeItemKey, Long> entry : plan.initialWithdrawals().entrySet()) {
            UUID resourceId = resourceIds.get(entry.getKey());
            if (resourceId == null || amounts.putIfAbsent(resourceId, entry.getValue()) != null) {
                throw new IllegalArgumentException("Missing or ambiguous QIO resource identity");
            }
        }
        if (amounts.isEmpty()) return Optional.empty();
        return Optional.of(QIOClaimRequest.createOrAdjust(requestId, claimId, OWNER_NAMESPACE,
                orderId.toString(), 0, 0, snapshot.getContentsRevision(), snapshot.getClaimRevision(), amounts));
    }
}

