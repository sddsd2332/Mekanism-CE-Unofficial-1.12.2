package mekanism.qioprocessing.common.content.maintenance;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable claim for one resource-group evaluation while its plan is built asynchronously. */
public final class QIOMaintenanceEvaluation {

    private static final int MAX_RULES = 1_000_000;

    private final UUID evaluationId;
    private final UUID jobId;
    private final long generation;
    private final PortableResourceDescriptor resource;
    private final Map<UUID, Long> groupRuleRevisions;
    private final List<UUID> triggeredRuleIds;
    private final long rulesRevision;
    private final QIOPlanSourceRevisions sourceRevisions;
    private final long requestAmount;
    private final long jobPriority;
    private final long createdAtTick;

    private QIOMaintenanceEvaluation(@Nonnull UUID evaluationId, @Nonnull UUID jobId,
          long generation, @Nonnull PortableResourceDescriptor resource,
          @Nonnull Map<UUID, Long> groupRuleRevisions, @Nonnull List<UUID> triggeredRuleIds,
          long rulesRevision, @Nonnull QIOPlanSourceRevisions sourceRevisions,
          long requestAmount, long jobPriority, long createdAtTick) {
        this.evaluationId = Objects.requireNonNull(evaluationId, "evaluationId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.generation = QIOProcessingNbt.requireNonNegative(generation,
              "maintenanceEvaluationGeneration");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.groupRuleRevisions = checkedRuleRevisions(groupRuleRevisions);
        this.triggeredRuleIds = checkedTriggeredRules(triggeredRuleIds,
              this.groupRuleRevisions.keySet());
        this.rulesRevision = QIOProcessingNbt.requireNonNegative(rulesRevision,
              "maintenanceRulesRevision");
        this.sourceRevisions = Objects.requireNonNull(sourceRevisions, "sourceRevisions");
        if (requestAmount <= 0) {
            throw new IllegalArgumentException("Maintenance request amount must be positive");
        }
        this.requestAmount = requestAmount;
        this.jobPriority = jobPriority;
        this.createdAtTick = QIOProcessingNbt.requireNonNegative(createdAtTick, "createdAtTick");
    }

    @Nonnull
    public static QIOMaintenanceEvaluation create(@Nonnull UUID frequencyUUID, long generation,
          @Nonnull PortableResourceDescriptor resource, @Nonnull List<QIOMaintenanceRule> group,
          @Nonnull List<QIOMaintenanceRule> triggeredRules, long rulesRevision,
          @Nonnull QIOPlanSourceRevisions sourceRevisions, long requestAmount,
          long jobPriority, long createdAtTick) {
        Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        Objects.requireNonNull(resource, "resource");
        Map<UUID, Long> revisions = new LinkedHashMap<>();
        for (QIOMaintenanceRule rule : sortedRules(group)) {
            if (!resource.equals(rule.getResource()) ||
                  revisions.put(rule.getRuleId(), rule.getRuleRevision()) != null) {
                throw new IllegalArgumentException("Invalid maintenance resource rule group");
            }
        }
        List<UUID> triggered = new ArrayList<>();
        for (QIOMaintenanceRule rule : sortedRules(triggeredRules)) {
            if (!resource.equals(rule.getResource()) || !revisions.containsKey(rule.getRuleId())) {
                throw new IllegalArgumentException("Triggered maintenance rule is outside its group");
            }
            triggered.add(rule.getRuleId());
        }
        UUID evaluationId = stableEvaluationId(frequencyUUID, generation, resource, revisions,
              triggered);
        UUID jobId = stableJobId(evaluationId);
        return new QIOMaintenanceEvaluation(evaluationId, jobId, generation, resource, revisions,
              triggered, rulesRevision, sourceRevisions, requestAmount, jobPriority, createdAtTick);
    }

    @Nonnull
    public UUID getEvaluationId() {
        return evaluationId;
    }

    @Nonnull
    public UUID getJobId() {
        return jobId;
    }

    public long getGeneration() {
        return generation;
    }

    @Nonnull
    public PortableResourceDescriptor getResource() {
        return resource;
    }

    @Nonnull
    public Map<UUID, Long> getGroupRuleRevisions() {
        return groupRuleRevisions;
    }

    @Nonnull
    public List<UUID> getTriggeredRuleIds() {
        return triggeredRuleIds;
    }

    public long getRulesRevision() {
        return rulesRevision;
    }

    @Nonnull
    public QIOPlanSourceRevisions getSourceRevisions() {
        return sourceRevisions;
    }

    public long getRequestAmount() {
        return requestAmount;
    }

    public long getJobPriority() {
        return jobPriority;
    }

    public long getCreatedAtTick() {
        return createdAtTick;
    }

    @Nonnull
    public QIOMaintenanceEvaluation withSourceRevisions(
          @Nonnull QIOPlanSourceRevisions revisions) {
        return new QIOMaintenanceEvaluation(evaluationId, jobId, generation, resource,
              groupRuleRevisions, triggeredRuleIds, rulesRevision, revisions, requestAmount,
              jobPriority, createdAtTick);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "evaluationId", evaluationId);
        QIOProcessingNbt.writeUUID(data, "jobId", jobId);
        data.setLong("generation", generation);
        data.setTag("resource", resource.write());
        data.setLong("rulesRevision", rulesRevision);
        data.setTag("sourceRevisions", sourceRevisions.write());
        data.setLong("requestAmount", requestAmount);
        data.setLong("jobPriority", jobPriority);
        data.setLong("createdAtTick", createdAtTick);
        NBTTagList storedRules = new NBTTagList();
        for (Map.Entry<UUID, Long> entry : groupRuleRevisions.entrySet()) {
            NBTTagCompound stored = new NBTTagCompound();
            QIOProcessingNbt.writeUUID(stored, "ruleId", entry.getKey());
            stored.setLong("ruleRevision", entry.getValue());
            storedRules.appendTag(stored);
        }
        data.setTag("groupRuleRevisions", storedRules);
        NBTTagList storedTriggered = new NBTTagList();
        for (UUID ruleId : triggeredRuleIds) {
            NBTTagCompound stored = new NBTTagCompound();
            QIOProcessingNbt.writeUUID(stored, "ruleId", ruleId);
            storedTriggered.appendTag(stored);
        }
        data.setTag("triggeredRuleIds", storedTriggered);
        return data;
    }

    @Nonnull
    public static QIOMaintenanceEvaluation read(@Nonnull UUID frequencyUUID,
          @Nonnull NBTTagCompound data) throws QIOProcessingDataException {
        try {
            NBTTagList storedRules = data.getTagList("groupRuleRevisions", NBT.TAG_COMPOUND);
            if (storedRules.tagCount() <= 0 || storedRules.tagCount() > MAX_RULES) {
                throw new QIOProcessingDataException("Invalid maintenance evaluation rule count");
            }
            Map<UUID, Long> revisions = new LinkedHashMap<>();
            for (int index = 0; index < storedRules.tagCount(); index++) {
                NBTTagCompound stored = storedRules.getCompoundTagAt(index);
                UUID ruleId = QIOProcessingNbt.readUUID(stored, "ruleId");
                long revision = QIOProcessingNbt.requireNonNegative(
                      stored.getLong("ruleRevision"), "ruleRevision");
                if (revisions.put(ruleId, revision) != null) {
                    throw new QIOProcessingDataException("Duplicate maintenance evaluation rule");
                }
            }
            NBTTagList storedTriggered = data.getTagList("triggeredRuleIds", NBT.TAG_COMPOUND);
            if (storedTriggered.tagCount() <= 0 || storedTriggered.tagCount() > revisions.size()) {
                throw new QIOProcessingDataException("Invalid triggered maintenance rule count");
            }
            List<UUID> triggered = new ArrayList<>();
            for (int index = 0; index < storedTriggered.tagCount(); index++) {
                triggered.add(QIOProcessingNbt.readUUID(
                      storedTriggered.getCompoundTagAt(index), "ruleId"));
            }
            QIOMaintenanceEvaluation evaluation = new QIOMaintenanceEvaluation(
                  QIOProcessingNbt.readUUID(data, "evaluationId"),
                  QIOProcessingNbt.readUUID(data, "jobId"), data.getLong("generation"),
                  PortableResourceDescriptor.read(data.getCompoundTag("resource")), revisions,
                  triggered, data.getLong("rulesRevision"),
                  QIOPlanSourceRevisions.read(data.getCompoundTag("sourceRevisions")),
                  data.getLong("requestAmount"), data.getLong("jobPriority"),
                  data.getLong("createdAtTick"));
            UUID expectedEvaluationId = stableEvaluationId(frequencyUUID,
                  evaluation.generation, evaluation.resource, evaluation.groupRuleRevisions,
                  evaluation.triggeredRuleIds);
            if (!expectedEvaluationId.equals(evaluation.evaluationId) ||
                  !stableJobId(evaluation.evaluationId).equals(evaluation.jobId)) {
                throw new QIOProcessingDataException(
                      "Maintenance evaluation stable identity does not match its contents");
            }
            return evaluation;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO maintenance evaluation", e);
        }
    }

    @Nonnull
    private static Map<UUID, Long> checkedRuleRevisions(Map<UUID, Long> source) {
        Objects.requireNonNull(source, "groupRuleRevisions");
        if (source.isEmpty() || source.size() > MAX_RULES) {
            throw new IllegalArgumentException("Invalid maintenance evaluation rule count");
        }
        List<Map.Entry<UUID, Long>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        Map<UUID, Long> checked = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> entry : entries) {
            UUID ruleId = Objects.requireNonNull(entry.getKey(), "ruleId");
            long revision = QIOProcessingNbt.requireNonNegative(
                  Objects.requireNonNull(entry.getValue(), "ruleRevision"), "ruleRevision");
            checked.put(ruleId, revision);
        }
        return Collections.unmodifiableMap(checked);
    }

    @Nonnull
    private static List<UUID> checkedTriggeredRules(List<UUID> source, Set<UUID> groupRules) {
        Objects.requireNonNull(source, "triggeredRuleIds");
        Set<UUID> checked = new LinkedHashSet<>();
        for (UUID ruleId : source) {
            UUID id = Objects.requireNonNull(ruleId, "triggeredRuleId");
            if (!groupRules.contains(id) || !checked.add(id)) {
                throw new IllegalArgumentException("Invalid triggered maintenance rule");
            }
        }
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("Maintenance evaluation has no triggered rules");
        }
        List<UUID> sorted = new ArrayList<>(checked);
        sorted.sort(Comparator.comparing(UUID::toString));
        return Collections.unmodifiableList(sorted);
    }

    @Nonnull
    private static List<QIOMaintenanceRule> sortedRules(List<QIOMaintenanceRule> source) {
        Objects.requireNonNull(source, "rules");
        List<QIOMaintenanceRule> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparing(rule -> rule.getRuleId().toString()));
        return sorted;
    }

    private static UUID stableEvaluationId(UUID frequencyUUID, long generation,
          PortableResourceDescriptor resource, Map<UUID, Long> groupRuleRevisions,
          List<UUID> triggeredRuleIds) {
        StringBuilder key = new StringBuilder("qio-maintenance-evaluation-v1|")
              .append(frequencyUUID).append('|').append(generation).append('|').append(resource);
        groupRuleRevisions.forEach((ruleId, revision) -> key.append("|rule=")
              .append(ruleId).append('@').append(revision));
        triggeredRuleIds.stream().sorted(Comparator.comparing(UUID::toString)).forEach(ruleId ->
              key.append("|triggered=").append(ruleId));
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static UUID stableJobId(UUID evaluationId) {
        return UUID.nameUUIDFromBytes(("qio-maintenance-job-v1|" + evaluationId)
              .getBytes(StandardCharsets.UTF_8));
    }
}
