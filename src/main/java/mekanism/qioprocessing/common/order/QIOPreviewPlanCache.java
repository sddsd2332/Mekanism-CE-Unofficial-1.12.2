package mekanism.qioprocessing.common.order;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.*;
import mekanism.qioprocessing.common.planning.QIOPlanningMetrics;
import mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Metric;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;
import mekanism.qioprocessing.common.planning.QIOWorkbenchMemory;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Bounded, process-local reuse of complete preview plans; never retains a catalog or live world.
 */
final class QIOPreviewPlanCache {
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, .75F, true);
    private final int maximumCount;
    private final long maximumBytes;
    private long bytes, epoch;

    QIOPreviewPlanCache() {
        this(16, 16 * QIOWorkbenchMemory.MIB);
    }

    QIOPreviewPlanCache(int maximumCount, long maximumBytes) {
        if (maximumCount <= 0 || maximumBytes <= 0) throw new IllegalArgumentException("Invalid preview cache budget");
        this.maximumCount = maximumCount;
        this.maximumBytes = maximumBytes;
    }

    synchronized long epoch() {
        return epoch;
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized long retainedBytes() {
        return bytes;
    }

    synchronized void clear() {
        epoch++;
        entries.clear();
        bytes = 0;
    }

    @Nullable
    synchronized QIOPlanningResult get(Key key, UUID planId, int revision) {
        Entry entry = entries.get(key);
        QIOPlanningMetrics.current().add(entry == null ? Metric.PREVIEW_PLAN_CACHE_MISSES : Metric.PREVIEW_PLAN_CACHE_HITS, 1);
        if (entry == null) return null;
        QIOCraftPlan rebound = entry.result.getPlan().withIdentity(planId, revision, key.sources);
        QIOPlanningMetrics metrics = QIOPlanningMetrics.current();
        metrics.maximum(Metric.PEAK_PLAN_STEPS, rebound.getSteps().size());
        metrics.maximum(Metric.PEAK_OPERATIONS, entry.result.getPlannedOperations());
        metrics.maximum(Metric.PEAK_CYCLE_NODES, rebound.getCycleNodes().size());
        // Search counters describe this invocation; a cache hit performs no exploration.
        return QIOPlanningResult.OPTIMIZATION_LIMIT_DIAGNOSTIC.equals(entry.result.getDiagnostic()) ?
                QIOPlanningResult.optimizedUntilBudget(rebound, 0, entry.result.getPlannedOperations()) :
                QIOPlanningResult.success(rebound, 0, entry.result.getPlannedOperations());
    }

    boolean put(Key key, QIOPlanningResult result, long expectedEpoch) {
        QIOCraftPlan plan = result.getPlan();
        if (result.getStatus() != QIOPlanningResult.Status.SUCCESS || plan == null ||
                !key.target.equals(plan.getRootResource()) || key.amount != plan.getRootAmount() ||
                !Arrays.equals(key.revisions, revisions(plan.getSourceRevisions()))) return false;
        synchronized (this) {
            if (epoch != expectedEpoch) return false;
        }
        // Weigh outside the cache lock. Large plans cannot stall another worker's cache hit.
        Weight weight = new Weight(maximumBytes);
        weight.add(512 + 8L * key.revisions.length);
        weight.text(key.generation);
        weight.resource(key.target);
        weight.resources(key.available);
        weight.plan(plan);
        synchronized (this) {
            if (epoch != expectedEpoch) return false;
            if (weight.exceeded()) {
                QIOPlanningMetrics.current().add(Metric.PREVIEW_PLAN_CACHE_REJECTIONS, 1);
                return false;
            }
            Entry previous = entries.remove(key);
            if (previous != null) bytes -= previous.bytes;
            entries.put(key, new Entry(result, weight.bytes));
            bytes += weight.bytes;
            Iterator<Entry> iterator = entries.values().iterator();
            while (entries.size() > maximumCount || bytes > maximumBytes) {
                bytes -= iterator.next().bytes;
                iterator.remove();
            }
            QIOPlanningMetrics.current().add(Metric.PREVIEW_PLAN_CACHE_STORES, 1);
            return true;
        }
    }

    static final class Key {
        private final UUID frequency, requester;
        private final int dimension;
        private final PortableResourceDescriptor target;
        private final long amount, validationRevision, directoryEpoch, serverEpoch, routeRevision;
        private final String generation;
        private final QIOPlanSourceRevisions sources;
        private final long[] revisions;
        private final Map<PortableResourceDescriptor, Long> available;
        private final int hash;

        Key(UUID frequency, UUID requester, int dimension, PortableResourceDescriptor target, long amount,
            QIOPlanSourceRevisions sources, Map<PortableResourceDescriptor, Long> available,
            String generation, long validationRevision, long directoryEpoch, long serverEpoch, long routeRevision) {
            this.frequency = Objects.requireNonNull(frequency);
            this.requester = Objects.requireNonNull(requester);
            this.dimension = dimension;
            this.target = Objects.requireNonNull(target);
            if (amount <= 0) throw new IllegalArgumentException("Invalid preview amount");
            this.amount = amount;
            this.sources = Objects.requireNonNull(sources);
            this.revisions = revisions(sources);
            this.available = Collections.unmodifiableMap(new LinkedHashMap<>(available));
            this.generation = Objects.requireNonNull(generation);
            this.validationRevision = validationRevision;
            this.directoryEpoch = directoryEpoch;
            this.serverEpoch = serverEpoch;
            this.routeRevision = routeRevision;
            hash = Objects.hash(frequency, requester, dimension, target, amount, Arrays.hashCode(revisions),
                    this.available, generation, validationRevision, directoryEpoch, serverEpoch, routeRevision);
        }

        /**
         * Validation during a cold solve advances knowledge without changing its captured inventory.
         */
        Key withValidationRevision(long revision) {
            return revision == validationRevision ? this : new Key(this, revision);
        }

        private Key(Key source, long revision) {
            frequency = source.frequency;
            requester = source.requester;
            dimension = source.dimension;
            target = source.target;
            amount = source.amount;
            sources = source.sources;
            revisions = source.revisions;
            available = source.available;
            generation = source.generation;
            validationRevision = revision;
            directoryEpoch = source.directoryEpoch;
            serverEpoch = source.serverEpoch;
            routeRevision = source.routeRevision;
            hash = Objects.hash(frequency, requester, dimension, target, amount, Arrays.hashCode(revisions),
                    available, generation, validationRevision, directoryEpoch, serverEpoch, routeRevision);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return hash == key.hash && dimension == key.dimension && amount == key.amount &&
                    validationRevision == key.validationRevision && directoryEpoch == key.directoryEpoch &&
                    serverEpoch == key.serverEpoch && routeRevision == key.routeRevision &&
                    frequency.equals(key.frequency) && requester.equals(key.requester) && target.equals(key.target) &&
                    generation.equals(key.generation) && Arrays.equals(revisions, key.revisions) && available.equals(key.available);
        }
    }

    private static long[] revisions(QIOPlanSourceRevisions value) {
        return new long[]{value.getContentRevision(), value.getCapacityRevision(), value.getClaimRevision(),
                value.getAccessRevision(), value.getRecipeCatalogRevision(), value.getProviderCatalogRevision(),
                value.getPolicyRevision(), value.getTaskCommitmentRevision()};
    }

    private static final class Entry {
        final QIOPlanningResult result;
        final long bytes;

        Entry(QIOPlanningResult result, long bytes) {
            this.result = result;
            this.bytes = bytes;
        }
    }

    /**
     * Conservative retained-data weights, using the same resource estimates as candidate admission.
     */
    private static final class Weight {
        final long limit;
        final Map<PortableResourceDescriptor, Boolean> resources = new IdentityHashMap<>();
        long bytes;

        Weight(long limit) {
            this.limit = limit;
        }

        boolean exceeded() {
            return bytes > limit;
        }

        void add(long amount) {
            if (!exceeded()) bytes = amount > limit - bytes ? Long.MAX_VALUE : bytes + amount;
        }

        void text(String value) {
            if (!exceeded()) add(QIOWorkbenchMemory.text(value));
        }

        void resource(PortableResourceDescriptor value) {
            if (!exceeded() && resources.put(value, Boolean.TRUE) == null) add(QIOWorkbenchMemory.resource(value));
        }

        void resources(Map<PortableResourceDescriptor, Long> values) {
            add(128L + 96L * values.size());
            for (PortableResourceDescriptor value : values.keySet()) {
                if (exceeded()) break;
                resource(value);
            }
        }

        void options(Iterable<QIOCandidateOption> values) {
            for (QIOCandidateOption value : values) {
                if (exceeded()) break;
                add(128);
                text(value.getCandidateId());
                resource(value.getResource());
            }
        }

        void plan(QIOCraftPlan plan) {
            add(512L + 16L * (plan.getSteps().size() + plan.getCycleNodes().size()));
            text(plan.getStructuralSignature());
            resource(plan.getRootResource());
            resources(plan.getExternalRequirements());
            resources(plan.getMaterialRequirements().getExactAmounts());
            for (QIOCandidateRequirement group : plan.getMaterialRequirements().getCandidateRequirements()) {
                if (exceeded()) return;
                add(128);
                text(group.getRequirementId());
                options(group.getOptions());
            }
            for (QIOPlanStep step : plan.getSteps()) {
                if (exceeded()) return;
                add(512L + 32L * step.getDependencies().size());
                text(step.getProviderId());
                text(step.getRouteId());
                text(step.getRecipeKey());
                text(step.getVariantId());
                text(step.getRouteSignature());
                resources(step.getExactInputs());
                resources(step.getFixedInputs());
                resources(step.getConfigurationInputs());
                resources(step.getGuaranteedOutputs());
                resources(step.getOptionalOutputs());
                for (QIOCandidateInputGroup group : step.getCandidateInputs()) {
                    if (exceeded()) return;
                    add(192L + 32L * group.getSlots().size());
                    text(group.getGroupId());
                    resources(group.getPlannedInputs());
                    options(group.getOptions());
                }
            }
            for (QIOCyclePlanNode cycle : plan.getCycleNodes()) {
                if (exceeded()) return;
                add(256L + 96L * cycle.getMemberQuotas().size() + 32L * cycle.getRoundSchedule().size());
                text(cycle.getMatrixSignature());
                resources(cycle.getSeedRequirements());
                resources(cycle.getNetPerRound());
            }
        }
    }
}
