package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Immutable partial planning graph retained for failed order analysis.
 */

/**
 * QIO 处理模块中的 QIOPlanningTrace 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanningTrace {

    private static final QIOPlanningTrace EMPTY = new QIOPlanningTrace(
            Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
            Collections.emptySet(), true);

    private final List<QIOPlanStep> steps;
    private final List<QIOCyclePlanNode> cycles;
    private final Set<Long> errorNodeIds;
    private final Set<PortableResourceDescriptor> errorResources;
    private final boolean rootError;
    private final Map<PortableResourceDescriptor, Long> requirements;

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
        this.requirements = QIOPlanningDiagnostics.requirements(this.steps);
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

    @Nonnull
    public List<QIOPlanStep> getSteps() {
        return steps;
    }

    @Nonnull
    public List<QIOCyclePlanNode> getCycles() {
        return cycles;
    }

    @Nonnull
    public Set<Long> getErrorNodeIds() {
        return errorNodeIds;
    }

    @Nonnull
    public Set<PortableResourceDescriptor> getErrorResources() {
        return errorResources;
    }

    public boolean isRootError() {
        return rootError;
    }

    /** Requirements of this partial graph, including inputs of unfinished steps. */
    @Nonnull
    public Map<PortableResourceDescriptor, Long> getRequirements() {
        return requirements;
    }
}
