package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Captures claim-aware storage amounts into the worker-safe planning representation. */
public final class QIOPlanningSnapshotFactory {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private QIOPlanningSnapshotFactory() {
    }

    @Nonnull
    public static QIOPlanningSnapshot create(@Nonnull QIOStorageSnapshot storage,
          long recipeCatalogRevision, long providerCatalogRevision, long policyRevision,
          long taskCommitmentRevision, @Nonnull List<QIOPlanningRoute> routes) {
        return create(storage, recipeCatalogRevision, providerCatalogRevision, policyRevision,
              taskCommitmentRevision, routes, Integer.MAX_VALUE, Integer.MAX_VALUE,
              Long.MAX_VALUE);
    }

    /** Builds a worker result from resources and revisions already captured on the server thread. */
    @Nonnull
    public static QIOPlanningSnapshot create(@Nonnull QIOPlanSourceRevisions revisions,
          @Nonnull Map<PortableResourceDescriptor, Long> available,
          @Nonnull List<QIOPlanningRoute> routes) {
        return new QIOPlanningSnapshot(Objects.requireNonNull(revisions, "revisions"),
              Objects.requireNonNull(available, "available"),
              Objects.requireNonNull(routes, "routes"), Integer.MAX_VALUE,
              Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    @Nonnull
    static QIOPlanningSnapshot create(@Nonnull QIOStorageSnapshot storage,
          long recipeCatalogRevision, long providerCatalogRevision, long policyRevision,
          long taskCommitmentRevision, @Nonnull List<QIOPlanningRoute> routes,
          int maximumDepth, int maximumNodes, long maximumOperations) {
        Objects.requireNonNull(storage, "storage");
        Map<PortableResourceDescriptor, Long> available = captureAvailableResources(storage);
        QIOPlanSourceRevisions revisions = new QIOPlanSourceRevisions(
              storage.getContentsRevision(), storage.getCapacityRevision(),
              storage.getClaimRevision(), storage.getAccessRevision(), recipeCatalogRevision,
              providerCatalogRevision, policyRevision, taskCommitmentRevision);
        return new QIOPlanningSnapshot(revisions, available,
              Objects.requireNonNull(routes, "routes"), maximumDepth, maximumNodes,
              maximumOperations);
    }

    @Nonnull
    public static Map<PortableResourceDescriptor, Long> captureAvailableResources(
          @Nonnull QIOStorageSnapshot storage) {
        Objects.requireNonNull(storage, "storage");
        Map<PortableResourceDescriptor, BigInteger> exactAvailable = new LinkedHashMap<>();
        for (QIOStorageEntry entry : storage.getEntries()) {
            if (entry == null || entry.getExactAvailableAmount().signum() <= 0) {
                continue;
            }
            PortableResourceDescriptor resource = PortableResourceDescriptor.fromStorageEntry(entry);
            exactAvailable.merge(resource, entry.getExactAvailableAmount(), BigInteger::add);
        }
        Map<PortableResourceDescriptor, Long> available = new LinkedHashMap<>();
        exactAvailable.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
              available.put(entry.getKey(), entry.getValue().min(LONG_MAX).longValue()));
        return available;
    }
}
