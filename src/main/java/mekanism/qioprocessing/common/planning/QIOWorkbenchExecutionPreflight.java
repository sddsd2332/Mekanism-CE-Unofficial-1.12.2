package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.*;
import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternIndex;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * Read-only admission check, not an execution or delivery authorization.
 */
public final class QIOWorkbenchExecutionPreflight {
    public enum Status {READY_FOR_DESIGN, CANCELLED, LEGACY_ORDER, STALE_CONFIGURATION, AWAITING_AUTHORIZATION, CLAIM_UNVERIFIED, RESOURCE_MISMATCH}

    private final Thread owner = Thread.currentThread();
    private final QIOClaimOrderStore store;

    public QIOWorkbenchExecutionPreflight(QIOClaimOrderStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public Status inspect(UUID orderId, WorkbenchSnapshot book, RecipeCatalog catalog,
                          IQIOFrequencyStorageAccess access) throws IOException {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Execution preflight requires its owner thread");
        Objects.requireNonNull(access);
        QIOClaimOrderIntent intent = store.load(orderId);
        if (store.cancellationRequested(orderId)) return Status.CANCELLED;
        QIOWorkbenchExecutionManifest manifest = intent.manifest();
        if (manifest == null) return Status.LEGACY_ORDER;
        if (book == null || catalog == null || !intent.frequencyId().equals(book.id())
                || intent.bookRevision() != book.revision() || !intent.generation().equals(catalog.generation())) {
            return Status.STALE_CONFIGURATION;
        }
        WorkbenchPatternIndex index = WorkbenchPatternIndex.build(book, catalog);
        for (QIOWorkbenchExecutionManifest.Step step : manifest.steps()) {
            List<VerifiedWorkbenchPattern> candidates = index.patterns(step.pattern().recipeId());
            int matches = 0;
            for (VerifiedWorkbenchPattern candidate : candidates) {
                if (candidate.id().equals(step.pattern().id()) && candidate.equals(step.pattern())) matches++;
            }
            if (matches != 1) return Status.STALE_CONFIGURATION;
        }
        QIOFrequencyReference reference = intent.frequencyReference();
        if (reference == null || reference.getBindingPlayerUUID() == null) return Status.AWAITING_AUTHORIZATION;
        IQIOStorageView view = access.open(reference, reference.getBindingPlayerUUID());
        if (view == null) return Status.AWAITING_AUTHORIZATION;
        try {
            if (!view.isValid() || !intent.frequencyId().equals(view.getFrequencyUUID()))
                return Status.AWAITING_AUTHORIZATION;
            QIOResourceClaim claim = view.getClaim(intent.claimId());
            QIOClaimBacking backing = view.getClaimBacking(intent.claimId());
            if (!matchesClaim(intent, claim) || backing == null || !matchesClaim(intent, backing.getClaim())
                    || backing.getContentsRevision() != intent.contentsRevision()
                    || backing.getClaimRevision() != intent.claimRevision()
                    || !backing.isFullyBacked() || !intent.amounts().equals(backing.getBackedAmounts())) {
                return Status.CLAIM_UNVERIFIED;
            }
            QIOStorageSnapshot snapshot = view.getSnapshot();
            if (snapshot == null || !intent.frequencyId().equals(snapshot.getFrequencyUUID())
                    || snapshot.getContentsRevision() != intent.contentsRevision()
                    || snapshot.getClaimRevision() != intent.claimRevision()) {
                return Status.RESOURCE_MISMATCH;
            }
            Map<UUID, QIOStorageEntry> resources = new HashMap<>();
            for (QIOStorageEntry entry : snapshot.getEntries()) {
                if (manifest.withdrawals().containsKey(entry.getResourceUUID())
                        && resources.putIfAbsent(entry.getResourceUUID(), entry) != null)
                    return Status.RESOURCE_MISMATCH;
            }
            for (Map.Entry<UUID, QIOWorkbenchExecutionManifest.Withdrawal> withdrawal : manifest.withdrawals().entrySet()) {
                QIOStorageEntry entry = resources.get(withdrawal.getKey());
                if (entry == null || entry.getExactStoredAmount().compareTo(BigInteger.valueOf(withdrawal.getValue().amount())) < 0) {
                    return Status.RESOURCE_MISMATCH;
                }
                ItemStack stack = entry.getItem();
                if (stack.isEmpty() || !withdrawal.getValue().item().equals(RecipeItemKey.capture(stack))) {
                    return Status.RESOURCE_MISMATCH;
                }
            }
            return view.isValid() ? Status.READY_FOR_DESIGN : Status.AWAITING_AUTHORIZATION;
        } finally {
            view.close();
        }
    }

    private static boolean matchesClaim(QIOClaimOrderIntent intent, QIOResourceClaim claim) {
        return claim != null && intent.claimId().equals(claim.getClaimId())
                && QIOPlanClaimIntent.OWNER_NAMESPACE.equals(claim.getOwnerNamespace())
                && intent.orderId().toString().equals(claim.getOwnerId())
                && claim.getState() == QIOResourceClaim.State.ACTIVE
                && intent.amounts().equals(claim.getResourceAmounts());
    }
}

