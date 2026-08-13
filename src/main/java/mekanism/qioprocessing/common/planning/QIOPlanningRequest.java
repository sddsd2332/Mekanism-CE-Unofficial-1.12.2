package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Immutable request passed to a QIO planning worker. */
public final class QIOPlanningRequest {

    private final UUID planId;
    private final int planRevision;
    private final QIOPlanningSnapshot snapshot;
    private final PortableResourceDescriptor rootResource;
    private final long rootAmount;

    public QIOPlanningRequest(@Nonnull UUID planId, int planRevision,
          @Nonnull QIOPlanningSnapshot snapshot,
          @Nonnull PortableResourceDescriptor rootResource, long rootAmount) {
        this.planId = Objects.requireNonNull(planId, "planId");
        this.planRevision = QIOProcessingNbt.requirePositive(planRevision, "planRevision");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.rootResource = Objects.requireNonNull(rootResource, "rootResource");
        if (rootAmount <= 0) {
            throw new IllegalArgumentException("rootAmount must be positive");
        }
        this.rootAmount = rootAmount;
    }

    @Nonnull
    public UUID getPlanId() {
        return planId;
    }

    public int getPlanRevision() {
        return planRevision;
    }

    @Nonnull
    public QIOPlanningSnapshot getSnapshot() {
        return snapshot;
    }

    @Nonnull
    public PortableResourceDescriptor getRootResource() {
        return rootResource;
    }

    public long getRootAmount() {
        return rootAmount;
    }
}
