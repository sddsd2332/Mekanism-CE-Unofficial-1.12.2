package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable partial planning graph retained for failed order analysis. */
public final class QIOPlanningTrace {

    private static final QIOPlanningTrace EMPTY = new QIOPlanningTrace(
          Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
          Collections.emptySet(), true);

    private final List<QIOPlanStep> steps;
    private final List<QIOCyclePlanNode> cycles;
    private final Set<Long> errorNodeIds;
    private final Set<PortableResourceDescriptor> errorResources;
    private final boolean rootError;

    public QIOPlanningTrace(@Nonnull List<QIOPlanStep> steps,
          @Nonnull List<QIOCyclePlanNode> cycles, @Nonnull Set<Long> errorNodeIds,
          @Nonnull Set<PortableResourceDescriptor> errorResources, boolean rootError) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(
              Objects.requireNonNull(steps, "steps")));
        this.cycles = Collections.unmodifiableList(new ArrayList<>(
              Objects.requireNonNull(cycles, "cycles")));
        this.errorNodeIds = Collections.unmodifiableSet(new LinkedHashSet<>(
              Objects.requireNonNull(errorNodeIds, "errorNodeIds")));
        this.errorResources = Collections.unmodifiableSet(new LinkedHashSet<>(
              Objects.requireNonNull(errorResources, "errorResources")));
        this.rootError = rootError;
    }

    @Nonnull
    public static QIOPlanningTrace rootFailure(
          @Nonnull PortableResourceDescriptor resource) {
        return new QIOPlanningTrace(Collections.emptyList(), Collections.emptyList(),
              Collections.emptySet(), Collections.singleton(
                    Objects.requireNonNull(resource, "resource")), true);
    }

    @Nonnull
    public static QIOPlanningTrace empty() {
        return EMPTY;
    }

    @Nonnull public List<QIOPlanStep> getSteps() { return steps; }
    @Nonnull public List<QIOCyclePlanNode> getCycles() { return cycles; }
    @Nonnull public Set<Long> getErrorNodeIds() { return errorNodeIds; }
    @Nonnull public Set<PortableResourceDescriptor> getErrorResources() {
        return errorResources;
    }
    public boolean isRootError() { return rootError; }
}
