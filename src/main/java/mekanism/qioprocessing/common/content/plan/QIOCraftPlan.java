package mekanism.qioprocessing.common.content.plan;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable first-version plan envelope. DAG nodes are added by the route planner without changing
 * the material and identity contract established here.
 */
public final class QIOCraftPlan {

    public static final int SCHEMA_VERSION = 3;
    private static final int MAX_RESOURCE_ENTRIES = 65_536;
    private static final int MAX_PLAN_STEPS = 65_536;

    private final UUID planId;
    private final int revision;
    private final QIOPlanSourceRevisions sourceRevisions;
    private final PortableResourceDescriptor rootResource;
    private final long rootAmount;
    private final Map<PortableResourceDescriptor, Long> externalRequirements;
    private final List<QIOPlanStep> steps;
    private final List<QIOCyclePlanNode> cycleNodes;
    private final QIOPlanMaterialRequirements materialRequirements;
    private final String structuralSignature;

    public QIOCraftPlan(@Nonnull UUID planId, int revision,
          @Nonnull QIOPlanSourceRevisions sourceRevisions,
          @Nonnull PortableResourceDescriptor rootResource, long rootAmount,
          @Nonnull Map<PortableResourceDescriptor, Long> externalRequirements) {
        this(planId, revision, sourceRevisions, rootResource, rootAmount, externalRequirements,
              Collections.emptyList(), Collections.emptyList());
    }

    public QIOCraftPlan(@Nonnull UUID planId, int revision,
          @Nonnull QIOPlanSourceRevisions sourceRevisions,
          @Nonnull PortableResourceDescriptor rootResource, long rootAmount,
          @Nonnull Map<PortableResourceDescriptor, Long> externalRequirements,
          @Nonnull List<QIOPlanStep> steps) {
        this(planId, revision, sourceRevisions, rootResource, rootAmount,
              externalRequirements, steps, Collections.emptyList());
    }

    public QIOCraftPlan(@Nonnull UUID planId, int revision,
          @Nonnull QIOPlanSourceRevisions sourceRevisions,
          @Nonnull PortableResourceDescriptor rootResource, long rootAmount,
          @Nonnull Map<PortableResourceDescriptor, Long> externalRequirements,
          @Nonnull List<QIOPlanStep> steps,
          @Nonnull List<QIOCyclePlanNode> cycleNodes) {
        this.planId = Objects.requireNonNull(planId, "planId");
        this.revision = QIOProcessingNbt.requirePositive(revision, "planRevision");
        this.sourceRevisions = Objects.requireNonNull(sourceRevisions, "sourceRevisions");
        this.rootResource = Objects.requireNonNull(rootResource, "rootResource");
        if (rootAmount <= 0) {
            throw new IllegalArgumentException("rootAmount must be positive");
        }
        this.rootAmount = rootAmount;
        this.externalRequirements = QIOProcessingNbt.copyAmounts(externalRequirements, true,
              "externalRequirements");
        this.steps = checkedSteps(steps);
        this.cycleNodes = checkedCycles(cycleNodes, this.steps);
        materialRequirements = QIOPlanMaterialRequirements.derive(this.externalRequirements,
              this.steps);
        structuralSignature = calculateSignature();
    }

    @Nonnull
    public UUID getPlanId() {
        return planId;
    }

    public int getRevision() {
        return revision;
    }

    @Nonnull
    public QIOPlanSourceRevisions getSourceRevisions() {
        return sourceRevisions;
    }

    @Nonnull
    public PortableResourceDescriptor getRootResource() {
        return rootResource;
    }

    public long getRootAmount() {
        return rootAmount;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getExternalRequirements() {
        return externalRequirements;
    }

    @Nonnull
    public List<QIOPlanStep> getSteps() {
        return steps;
    }

    @Nonnull
    public List<QIOCyclePlanNode> getCycleNodes() {
        return cycleNodes;
    }

    @Nonnull
    public QIOPlanMaterialRequirements getMaterialRequirements() {
        return materialRequirements;
    }

    @Nullable
    public QIOCyclePlanNode getCycleForMember(long memberNodeId) {
        for (QIOCyclePlanNode cycle : cycleNodes) {
            if (cycle.containsMember(memberNodeId)) return cycle;
        }
        return null;
    }

    @Nonnull
    public String getStructuralSignature() {
        return structuralSignature;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("planSchemaVersion", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "planId", planId);
        data.setInteger("revision", revision);
        data.setTag("sourceRevisions", sourceRevisions.write());
        data.setTag("rootResource", rootResource.write());
        data.setLong("rootAmount", rootAmount);
        data.setTag("externalRequirements", QIOProcessingNbt.writeAmounts(externalRequirements));
        data.setTag("materialRequirements", materialRequirements.write());
        NBTTagList stepList = new NBTTagList();
        for (QIOPlanStep step : steps) {
            stepList.appendTag(step.write());
        }
        data.setTag("steps", stepList);
        NBTTagList cycles = new NBTTagList();
        for (QIOCyclePlanNode cycle : cycleNodes) cycles.appendTag(cycle.write());
        data.setTag("cycleNodes", cycles);
        data.setString("structuralSignature", structuralSignature);
        return data;
    }

    @Nonnull
    public static QIOCraftPlan read(@Nonnull NBTTagCompound data) throws QIOProcessingDataException {
        int schema = data.getInteger("planSchemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported QIO plan schema " + schema +
                  ", expected " + SCHEMA_VERSION);
        }
        try {
            if (!data.hasKey("planId", NBT.TAG_STRING) ||
                  !data.hasKey("revision", NBT.TAG_INT) ||
                  !data.hasKey("sourceRevisions", NBT.TAG_COMPOUND) ||
                  !data.hasKey("rootResource", NBT.TAG_COMPOUND) ||
                  !data.hasKey("rootAmount", NBT.TAG_LONG) ||
                  !data.hasKey("externalRequirements", NBT.TAG_LIST) ||
                  !data.hasKey("materialRequirements", NBT.TAG_COMPOUND) ||
                  !data.hasKey("steps", NBT.TAG_LIST) ||
                  !data.hasKey("cycleNodes", NBT.TAG_LIST) ||
                  !data.hasKey("structuralSignature", NBT.TAG_STRING)) {
                throw new QIOProcessingDataException(
                      "QIO plan is missing required current-schema state");
            }
            NBTTagList stepList = data.getTagList("steps", NBT.TAG_COMPOUND);
            if (stepList.tagCount() > MAX_PLAN_STEPS) {
                throw new QIOProcessingDataException("QIO craft plan contains too many steps");
            }
            List<QIOPlanStep> steps = new ArrayList<>(stepList.tagCount());
            for (int i = 0; i < stepList.tagCount(); i++) {
                steps.add(QIOPlanStep.read(stepList.getCompoundTagAt(i)));
            }
            NBTTagList cycleList = data.getTagList("cycleNodes", NBT.TAG_COMPOUND);
            if (cycleList.tagCount() > MAX_PLAN_STEPS) {
                throw new QIOProcessingDataException("QIO craft plan contains too many cycle nodes");
            }
            List<QIOCyclePlanNode> cycles = new ArrayList<>(cycleList.tagCount());
            for (int i = 0; i < cycleList.tagCount(); i++) cycles.add(QIOCyclePlanNode.read(cycleList.getCompoundTagAt(i)));
            QIOCraftPlan plan = new QIOCraftPlan(QIOProcessingNbt.readUUID(data, "planId"),
                  data.getInteger("revision"),
                  QIOPlanSourceRevisions.read(data.getCompoundTag("sourceRevisions")),
                  PortableResourceDescriptor.read(data.getCompoundTag("rootResource")),
                  data.getLong("rootAmount"),
                  QIOProcessingNbt.readAmounts(data, "externalRequirements", MAX_RESOURCE_ENTRIES),
                  steps, cycles);
            QIOPlanMaterialRequirements storedRequirements =
                  QIOPlanMaterialRequirements.read(data.getCompoundTag("materialRequirements"));
            if (!storedRequirements.write().equals(plan.materialRequirements.write())) {
                throw new QIOProcessingDataException(
                      "QIO plan material requirements disagree with its steps");
            }
            String storedSignature = data.getString("structuralSignature");
            if (!plan.structuralSignature.equals(storedSignature)) {
                throw new QIOProcessingDataException("QIO plan structural signature does not match its contents");
            }
            return plan;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO craft plan", e);
        }
    }

    private String calculateSignature() {
        StringBuilder canonical = new StringBuilder();
        canonical.append("v=").append(SCHEMA_VERSION)
              .append("|root=").append(rootResource)
              .append("|amount=").append(rootAmount);
        for (Map.Entry<PortableResourceDescriptor, Long> entry : externalRequirements.entrySet()) {
            canonical.append("|in=").append(entry.getKey()).append('@').append(entry.getValue());
        }
        for (Map.Entry<PortableResourceDescriptor, Long> entry :
              materialRequirements.getExactAmounts().entrySet()) {
            canonical.append("|exactClaim=").append(entry.getKey()).append('@')
                  .append(entry.getValue());
        }
        for (QIOCandidateRequirement requirement :
              materialRequirements.getCandidateRequirements()) {
            canonical.append("|candidateClaim=").append(requirement.getRequirementId())
                  .append('@').append(requirement.getRequiredUnits());
            for (QIOCandidateOption option : requirement.getOptions()) {
                canonical.append(':').append(option.getCandidateId()).append('@')
                      .append(option.getResource()).append('@')
                      .append(option.getAmountPerUnit());
            }
        }
        for (QIOPlanStep step : steps) {
            step.appendStructuralSignature(canonical);
        }
        for (QIOCyclePlanNode cycle : cycleNodes) cycle.appendStructuralSignature(canonical);
        return QIOHashing.sha256(canonical);
    }

    private static List<QIOPlanStep> checkedSteps(List<QIOPlanStep> steps) {
        Objects.requireNonNull(steps, "steps");
        if (steps.size() > MAX_PLAN_STEPS) {
            throw new IllegalArgumentException("QIO craft plan contains too many steps");
        }
        List<QIOPlanStep> copy = new ArrayList<>(steps.size());
        Map<Long, QIOPlanStep> byId = new HashMap<>();
        for (QIOPlanStep step : steps) {
            QIOPlanStep checked = Objects.requireNonNull(step, "plan step");
            if (byId.put(checked.getNodeId(), checked) != null) {
                throw new IllegalArgumentException("Duplicate QIO plan node " + checked.getNodeId());
            }
            copy.add(checked);
        }
        for (QIOPlanStep step : copy) {
            for (long dependency : step.getDependencies()) {
                if (!byId.containsKey(dependency)) {
                    throw new IllegalArgumentException("QIO plan node " + step.getNodeId() +
                          " references missing dependency " + dependency);
                }
            }
        }
        validateAcyclic(byId);
        return Collections.unmodifiableList(copy);
    }

    private static List<QIOCyclePlanNode> checkedCycles(List<QIOCyclePlanNode> cycles,
          List<QIOPlanStep> steps) {
        Objects.requireNonNull(cycles, "cycleNodes");
        if (cycles.size() > MAX_PLAN_STEPS) throw new IllegalArgumentException("Too many cycle nodes");
        Set<Long> stepIds = new HashSet<>();
        Map<Long, QIOPlanStep> stepsById = new LinkedHashMap<>();
        for (QIOPlanStep step : steps) {
            stepIds.add(step.getNodeId());
            stepsById.put(step.getNodeId(), step);
        }
        Set<Long> cycleIds = new HashSet<>();
        Set<Long> members = new HashSet<>();
        List<QIOCyclePlanNode> copy = new ArrayList<>();
        for (QIOCyclePlanNode cycle : cycles) {
            QIOCyclePlanNode checked = Objects.requireNonNull(cycle, "cycleNode");
            if (!cycleIds.add(checked.getNodeId()) || stepIds.contains(checked.getNodeId()))
                throw new IllegalArgumentException("Duplicate QIO cycle node ID " + checked.getNodeId());
            for (long member : checked.getMemberQuotas().keySet()) {
                if (!stepIds.contains(member) || !members.add(member))
                    throw new IllegalArgumentException("Invalid or duplicate QIO cycle member " + member);
                long expectedOperations = Math.multiplyExact(checked.getTotalRounds(),
                      checked.getMemberQuotas().get(member));
                if (stepsById.get(member).getOperations() != expectedOperations) {
                    throw new IllegalArgumentException(
                          "QIO cycle member operation count disagrees with its quota");
                }
            }
            copy.add(checked);
        }
        copy.sort((left,right)->Long.compare(left.getNodeId(),right.getNodeId()));
        return Collections.unmodifiableList(copy);
    }

    private static void validateAcyclic(Map<Long, QIOPlanStep> byId) {
        Map<Long, Integer> remainingDependencies = new HashMap<>();
        Map<Long, List<Long>> dependents = new HashMap<>();
        ArrayDeque<Long> ready = new ArrayDeque<>();
        for (QIOPlanStep step : byId.values()) {
            int dependencyCount = step.getDependencies().size();
            remainingDependencies.put(step.getNodeId(), dependencyCount);
            if (dependencyCount == 0) ready.addLast(step.getNodeId());
            for (long dependency : step.getDependencies()) {
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>())
                      .add(step.getNodeId());
            }
        }
        int resolved = 0;
        while (!ready.isEmpty()) {
            long nodeId = ready.removeFirst();
            resolved++;
            for (long dependent : dependents.getOrDefault(nodeId,
                  Collections.emptyList())) {
                int remaining = remainingDependencies.get(dependent) - 1;
                remainingDependencies.put(dependent, remaining);
                if (remaining == 0) ready.addLast(dependent);
            }
        }
        if (resolved != byId.size()) {
            throw new IllegalArgumentException("QIO plan contains a dependency cycle");
        }
    }
}
