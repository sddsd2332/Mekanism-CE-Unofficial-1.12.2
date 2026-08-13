package mekanism.qioprocessing.common.content.maintenance;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persistent rule catalog with configuration and runtime revisions kept separate. */
public final class QIOMaintenanceRuleCatalog {

    private static final int MAX_PERSISTED_RULES = 1_000_000;
    private static final int MAX_TARGETED_EVALUATIONS = 4_096;

    private final Map<UUID, QIOMaintenanceRule> rules = new LinkedHashMap<>();
    private final Map<UUID, QIOMaintenanceEvaluation> pendingEvaluations = new LinkedHashMap<>();
    private final Map<PortableResourceDescriptor, UUID> pendingByResource = new HashMap<>();
    private final Map<UUID, Set<UUID>> outstandingRulesByJob = new HashMap<>();
    private final Set<PortableResourceDescriptor> targetedEvaluations = new LinkedHashSet<>();
    private long groupedRulesRevision = -1;
    private Map<PortableResourceDescriptor, List<QIOMaintenanceRule>> groupedRulesCache =
          Collections.emptyMap();
    private long orderedRulesRevision = -1;
    private List<QIOMaintenanceRule> orderedRulesCache = Collections.emptyList();
    private long rulesRevision;
    private long runtimeRevision;
    private long evaluationGeneration;
    private int evaluationCursor;
    private long nextEvaluationTick;
    private boolean repeatEvaluationPass;
    @Nullable
    private UUID lastCompletedEvaluationId;

    public long getRulesRevision() {
        return rulesRevision;
    }

    public long getRuntimeRevision() {
        return runtimeRevision;
    }

    public long getEvaluationGeneration() {
        return evaluationGeneration;
    }

    public int getEvaluationCursor() {
        return evaluationCursor;
    }

    public long getNextEvaluationTick() {
        return nextEvaluationTick;
    }

    public boolean hasRules() {
        return !rules.isEmpty();
    }

    public boolean hasPendingEvaluations() {
        return !pendingEvaluations.isEmpty();
    }

    public boolean hasOutstandingRules() {
        return !outstandingRulesByJob.isEmpty();
    }

    public boolean hasRuleForResource(@Nullable PortableResourceDescriptor resource) {
        return resource != null && groupedRules().containsKey(resource);
    }

    public boolean hasTargetedEvaluations() {
        return !targetedEvaluations.isEmpty();
    }

    @Nullable
    public PortableResourceDescriptor pollTargetedEvaluation() {
        Iterator<PortableResourceDescriptor> iterator = targetedEvaluations.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        PortableResourceDescriptor resource = iterator.next();
        iterator.remove();
        return resource;
    }

    /** Queues one resource without disturbing an in-progress full evaluation pass. */
    public boolean requestTargetedEvaluation(@Nullable PortableResourceDescriptor resource) {
        if (resource == null || !hasRuleForResource(resource)) {
            return false;
        }
        if (targetedEvaluations.size() >= MAX_TARGETED_EVALUATIONS &&
              !targetedEvaluations.contains(resource)) {
            targetedEvaluations.clear();
            requestEvaluationPass();
            return true;
        }
        return targetedEvaluations.add(resource);
    }

    @Nullable
    public UUID getLastCompletedEvaluationId() {
        return lastCompletedEvaluationId;
    }

    @Nullable
    public QIOMaintenanceRule get(UUID ruleId) {
        return ruleId == null ? null : rules.get(ruleId);
    }

    @Nonnull
    public List<QIOMaintenanceRule> getRules() {
        if (orderedRulesRevision == rulesRevision) {
            return orderedRulesCache;
        }
        List<QIOMaintenanceRule> result = new ArrayList<>(rules.values());
        result.sort(Comparator.comparing(rule -> rule.getRuleId().toString()));
        orderedRulesCache = Collections.unmodifiableList(result);
        orderedRulesRevision = rulesRevision;
        return orderedRulesCache;
    }

    @Nonnull
    public List<QIOMaintenanceEvaluation> getPendingEvaluations() {
        List<QIOMaintenanceEvaluation> result = new ArrayList<>(pendingEvaluations.values());
        result.sort(Comparator.comparing(evaluation -> evaluation.getEvaluationId().toString()));
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public QIOMaintenanceEvaluation getPending(@Nullable UUID evaluationId) {
        return evaluationId == null ? null : pendingEvaluations.get(evaluationId);
    }

    @Nullable
    public QIOMaintenanceEvaluation getPendingForResource(
          @Nullable PortableResourceDescriptor resource) {
        UUID evaluationId = resource == null ? null : pendingByResource.get(resource);
        return evaluationId == null ? null : pendingEvaluations.get(evaluationId);
    }

    @Nonnull
    public Map<PortableResourceDescriptor, List<QIOMaintenanceRule>> groupedRules() {
        if (groupedRulesRevision == rulesRevision) {
            return groupedRulesCache;
        }
        Map<PortableResourceDescriptor, List<QIOMaintenanceRule>> grouped = new java.util.TreeMap<>();
        for (QIOMaintenanceRule rule : rules.values()) {
            grouped.computeIfAbsent(rule.getResource(), ignored -> new ArrayList<>()).add(rule);
        }
        grouped.values().forEach(list -> list.sort(Comparator.comparing(rule ->
              rule.getRuleId().toString())));
        Map<PortableResourceDescriptor, List<QIOMaintenanceRule>> immutable = new LinkedHashMap<>();
        grouped.forEach((resource, list) -> immutable.put(resource,
              Collections.unmodifiableList(list)));
        groupedRulesCache = Collections.unmodifiableMap(immutable);
        groupedRulesRevision = rulesRevision;
        return groupedRulesCache;
    }

    @Nonnull
    public QIOMaintenanceRule create(@Nonnull PortableResourceDescriptor resource,
          @Nonnull UUID creator, boolean enabled, long triggerAmount, long targetAmount,
          long maximumSingleRequest, long jobPriority, int retryIntervalTicks, long currentTick,
          int maximumRules) {
        if (rules.size() >= maximumRules) {
            throw new IllegalStateException("QIO maintenance rule limit reached");
        }
        QIOMaintenanceRule rule = new QIOMaintenanceRule(UUID.randomUUID(), resource, creator,
              enabled, triggerAmount, targetAmount, maximumSingleRequest, jobPriority,
              retryIntervalTicks, currentTick);
        rules.put(rule.getRuleId(), rule);
        incrementRulesRevision();
        return rule;
    }

    public void update(@Nonnull UUID ruleId, long expectedRevision, @Nonnull UUID editor,
          boolean enabled, long triggerAmount, long targetAmount, long maximumSingleRequest,
          long jobPriority, int retryIntervalTicks, long currentTick) {
        QIOMaintenanceRule rule = require(ruleId);
        rule.update(editor, expectedRevision, enabled, triggerAmount, targetAmount,
              maximumSingleRequest, jobPriority, retryIntervalTicks, currentTick);
        incrementRulesRevision();
    }

    public boolean remove(@Nonnull UUID ruleId, long expectedRevision) {
        QIOMaintenanceRule rule = require(ruleId);
        if (rule.getRuleRevision() != expectedRevision) {
            throw new IllegalStateException("QIO maintenance rule revision changed");
        }
        removeOutstandingIndex(rule, rule.getOutstandingJobId());
        rules.remove(ruleId);
        evaluationCursor = 0;
        incrementRulesRevision();
        return true;
    }

    public void recordGroupEvaluation(@Nonnull List<QIOMaintenanceRule> group,
          @Nonnull UUID evaluationId, @Nonnull QIOMaintenanceRule.EvaluationStatus status,
          long currentTick, @Nullable String diagnostic) {
        for (QIOMaintenanceRule rule : group) {
            rule.recordEvaluation(evaluationId, status, currentTick, diagnostic);
        }
        incrementRuntimeRevision();
    }

    /** Persist a resource-group claim before submitting its asynchronous planning task. */
    public void beginEvaluation(@Nonnull QIOMaintenanceEvaluation evaluation,
          @Nonnull List<QIOMaintenanceRule> group, long currentTick) {
        Objects.requireNonNull(evaluation, "evaluation");
        Objects.requireNonNull(group, "group");
        QIOMaintenanceEvaluation existing = pendingEvaluations.get(evaluation.getEvaluationId());
        if (existing != null && !existing.getResource().equals(evaluation.getResource())) {
            throw new IllegalStateException("Maintenance evaluation identity changed");
        }
        QIOMaintenanceEvaluation resourceExisting = getPendingForResource(evaluation.getResource());
        if (resourceExisting != null && !resourceExisting.getEvaluationId().equals(
              evaluation.getEvaluationId())) {
            throw new IllegalStateException("A maintenance evaluation already owns this resource");
        }
        validateRuleSnapshot(evaluation, group);
        pendingEvaluations.put(evaluation.getEvaluationId(), evaluation);
        pendingByResource.put(evaluation.getResource(), evaluation.getEvaluationId());
        for (UUID ruleId : evaluation.getTriggeredRuleIds()) {
            require(ruleId).recordEvaluation(evaluation.getEvaluationId(),
                  QIOMaintenanceRule.EvaluationStatus.PLANNING, currentTick, null);
        }
        incrementRuntimeRevision();
    }

    public void replacePendingEvaluation(@Nonnull QIOMaintenanceEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation");
        QIOMaintenanceEvaluation existing = pendingEvaluations.get(evaluation.getEvaluationId());
        if (existing == null || !existing.getResource().equals(evaluation.getResource())) {
            throw new IllegalArgumentException("Unknown maintenance evaluation");
        }
        pendingEvaluations.put(evaluation.getEvaluationId(), evaluation);
        incrementRuntimeRevision();
    }

    public void bindOutstanding(@Nonnull List<QIOMaintenanceRule> group,
          @Nonnull UUID evaluationId, @Nonnull UUID jobId) {
        for (QIOMaintenanceRule rule : group) {
            UUID outstanding = rule.getOutstandingJobId();
            if (outstanding != null && !outstanding.equals(jobId)) {
                throw new IllegalStateException("Maintenance rule already owns another order");
            }
        }
        for (QIOMaintenanceRule rule : group) {
            rule.bindOutstanding(evaluationId, jobId);
            indexOutstanding(rule, jobId);
        }
        incrementRuntimeRevision();
    }

    /** Completes a pending claim and binds only the rules that actually triggered it. */
    public boolean completeEvaluation(@Nonnull UUID evaluationId, @Nonnull UUID jobId) {
        QIOMaintenanceEvaluation evaluation = pendingEvaluations.get(
              Objects.requireNonNull(evaluationId, "evaluationId"));
        if (evaluation == null) {
            return false;
        }
        for (UUID ruleId : evaluation.getTriggeredRuleIds()) {
            QIOMaintenanceRule rule = rules.get(ruleId);
            if (rule != null && evaluation.getResource().equals(rule.getResource()) &&
                  Objects.equals(rule.getLastEvaluationId(), evaluationId)) {
                UUID outstanding = rule.getOutstandingJobId();
                if (outstanding != null && !outstanding.equals(jobId)) {
                    throw new IllegalStateException("Maintenance rule already owns another order");
                }
            }
        }
        pendingEvaluations.remove(evaluationId);
        pendingByResource.remove(evaluation.getResource(), evaluationId);
        for (UUID ruleId : evaluation.getTriggeredRuleIds()) {
            QIOMaintenanceRule rule = rules.get(ruleId);
            if (rule != null && evaluation.getResource().equals(rule.getResource()) &&
                  Objects.equals(rule.getLastEvaluationId(), evaluationId)) {
                rule.bindOutstanding(evaluationId, jobId);
                indexOutstanding(rule, jobId);
            }
        }
        lastCompletedEvaluationId = evaluationId;
        incrementRuntimeRevision();
        return true;
    }

    /** Invalidates a stale planning claim without applying the normal failure backoff. */
    public boolean invalidateEvaluation(@Nonnull UUID evaluationId) {
        QIOMaintenanceEvaluation evaluation = pendingEvaluations.remove(
              Objects.requireNonNull(evaluationId, "evaluationId"));
        if (evaluation == null) {
            return false;
        }
        pendingByResource.remove(evaluation.getResource(), evaluationId);
        for (UUID ruleId : evaluation.getTriggeredRuleIds()) {
            QIOMaintenanceRule rule = rules.get(ruleId);
            if (rule != null && evaluation.getResource().equals(rule.getResource()) &&
                  Objects.equals(rule.getLastEvaluationId(), evaluationId)) {
                UUID outstanding = rule.getOutstandingJobId();
                rule.clearOutstanding();
                removeOutstandingIndex(rule, outstanding);
            }
        }
        lastCompletedEvaluationId = evaluationId;
        requestTargetedEvaluation(evaluation.getResource());
        incrementRuntimeRevision();
        return true;
    }

    /** Finishes a pending claim without creating an order and records a bounded diagnostic. */
    public boolean failEvaluation(@Nonnull UUID evaluationId,
          @Nonnull QIOMaintenanceRule.EvaluationStatus status, long currentTick,
          @Nullable String diagnostic) {
        QIOMaintenanceEvaluation evaluation = pendingEvaluations.remove(
              Objects.requireNonNull(evaluationId, "evaluationId"));
        if (evaluation == null) {
            return false;
        }
        pendingByResource.remove(evaluation.getResource(), evaluationId);
        for (UUID ruleId : evaluation.getTriggeredRuleIds()) {
            QIOMaintenanceRule rule = rules.get(ruleId);
            if (rule != null && evaluation.getResource().equals(rule.getResource()) &&
                  Objects.equals(rule.getLastEvaluationId(), evaluationId)) {
                rule.recordEvaluation(evaluationId, status, currentTick, diagnostic);
            }
        }
        lastCompletedEvaluationId = evaluationId;
        incrementRuntimeRevision();
        return true;
    }

    public boolean clearOutstandingForJob(@Nonnull UUID jobId) {
        Set<UUID> indexedRules = outstandingRulesByJob.remove(
              Objects.requireNonNull(jobId, "jobId"));
        if (indexedRules == null || indexedRules.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (UUID ruleId : indexedRules) {
            QIOMaintenanceRule rule = rules.get(ruleId);
            if (rule != null && jobId.equals(rule.getOutstandingJobId())) {
                rule.clearOutstanding();
                changed = true;
            }
        }
        if (changed) {
            for (UUID ruleId : indexedRules) {
                QIOMaintenanceRule rule = rules.get(ruleId);
                if (rule != null) {
                    requestTargetedEvaluation(rule.getResource());
                }
            }
            incrementRuntimeRevision();
        }
        return changed;
    }

    public boolean clearTerminalOutstanding(Map<UUID, Boolean> terminalJobs) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Set<UUID>>> iterator =
              outstandingRulesByJob.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<UUID>> entry = iterator.next();
            UUID jobId = entry.getKey();
            if (!terminalJobs.getOrDefault(jobId, true)) {
                continue;
            }
            for (UUID ruleId : entry.getValue()) {
                QIOMaintenanceRule rule = rules.get(ruleId);
                if (rule != null && jobId.equals(rule.getOutstandingJobId())) {
                    rule.clearOutstanding();
                    requestTargetedEvaluation(rule.getResource());
                    changed = true;
                }
            }
            iterator.remove();
        }
        if (changed) {
            incrementRuntimeRevision();
        }
        return changed;
    }

    public void advanceCursor(int groupCount, int processed, long currentTick,
          int evaluationInterval) {
        if (groupCount <= 0 || evaluationCursor + processed >= groupCount) {
            evaluationCursor = 0;
            evaluationGeneration = evaluationGeneration == Long.MAX_VALUE ? 0 :
                  evaluationGeneration + 1;
            nextEvaluationTick = repeatEvaluationPass ? 0 :
                  saturatedAdd(currentTick, evaluationInterval);
            repeatEvaluationPass = false;
        } else {
            evaluationCursor += processed;
        }
        incrementRuntimeRevision();
    }

    public void requestImmediateEvaluation() {
        evaluationCursor = 0;
        nextEvaluationTick = 0;
        repeatEvaluationPass = false;
        targetedEvaluations.clear();
        incrementRuntimeRevision();
    }

    /** Requests a full pass while preserving progress if one is already running. */
    public boolean requestEvaluationPass() {
        if (nextEvaluationTick == 0) {
            if (evaluationCursor == 0 || repeatEvaluationPass) {
                return false;
            }
            repeatEvaluationPass = true;
            incrementRuntimeRevision();
            return true;
        }
        nextEvaluationTick = 0;
        repeatEvaluationPass = false;
        incrementRuntimeRevision();
        return true;
    }

    /** Allocates a durable identity generation immediately before a new planning claim. */
    public long reserveEvaluationGeneration() {
        evaluationGeneration = evaluationGeneration == Long.MAX_VALUE ? 0 :
              evaluationGeneration + 1;
        incrementRuntimeRevision();
        return evaluationGeneration;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("rulesRevision", rulesRevision);
        data.setLong("runtimeRevision", runtimeRevision);
        data.setLong("evaluationGeneration", evaluationGeneration);
        data.setInteger("evaluationCursor", evaluationCursor);
        data.setLong("nextEvaluationTick", nextEvaluationTick);
        data.setBoolean("repeatEvaluationPass", repeatEvaluationPass);
        if (lastCompletedEvaluationId != null) {
            QIOProcessingNbt.writeUUID(data, "lastCompletedEvaluationId", lastCompletedEvaluationId);
        }
        NBTTagList storedRules = new NBTTagList();
        getRules().forEach(rule -> storedRules.appendTag(rule.write()));
        data.setTag("rules", storedRules);
        NBTTagList storedEvaluations = new NBTTagList();
        getPendingEvaluations().forEach(evaluation -> storedEvaluations.appendTag(evaluation.write()));
        data.setTag("pendingEvaluations", storedEvaluations);
        return data;
    }

    @Nonnull
    public static QIOMaintenanceRuleCatalog read(@Nonnull UUID frequencyUUID,
          @Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        QIOMaintenanceRuleCatalog catalog = new QIOMaintenanceRuleCatalog();
        catalog.rulesRevision = QIOProcessingNbt.requireNonNegative(
              data.getLong("rulesRevision"), "maintenanceRulesRevision");
        catalog.runtimeRevision = QIOProcessingNbt.requireNonNegative(
              data.getLong("runtimeRevision"), "maintenanceRuntimeRevision");
        catalog.evaluationGeneration = QIOProcessingNbt.requireNonNegative(
              data.getLong("evaluationGeneration"), "maintenanceEvaluationGeneration");
        catalog.evaluationCursor = data.getInteger("evaluationCursor");
        catalog.nextEvaluationTick = QIOProcessingNbt.requireNonNegative(
              data.getLong("nextEvaluationTick"), "maintenanceNextEvaluationTick");
        catalog.repeatEvaluationPass = data.getBoolean("repeatEvaluationPass");
        catalog.lastCompletedEvaluationId = data.hasKey("lastCompletedEvaluationId", NBT.TAG_STRING) ?
              QIOProcessingNbt.readUUID(data, "lastCompletedEvaluationId") : null;
        NBTTagList rules = data.getTagList("rules", NBT.TAG_COMPOUND);
        if (rules.tagCount() > MAX_PERSISTED_RULES) {
            throw new QIOProcessingDataException("QIO maintenance rule catalog is too large");
        }
        for (int index = 0; index < rules.tagCount(); index++) {
            QIOMaintenanceRule rule = QIOMaintenanceRule.read(rules.getCompoundTagAt(index));
            if (catalog.rules.put(rule.getRuleId(), rule) != null) {
                throw new QIOProcessingDataException("Duplicate QIO maintenance rule " +
                      rule.getRuleId());
            }
            if (rule.getOutstandingJobId() != null) {
                catalog.indexOutstanding(rule, rule.getOutstandingJobId());
            }
        }
        NBTTagList evaluations = data.getTagList("pendingEvaluations", NBT.TAG_COMPOUND);
        if (evaluations.tagCount() > MAX_PERSISTED_RULES) {
            throw new QIOProcessingDataException("QIO maintenance evaluation catalog is too large");
        }
        for (int index = 0; index < evaluations.tagCount(); index++) {
            QIOMaintenanceEvaluation evaluation = QIOMaintenanceEvaluation.read(frequencyUUID,
                  evaluations.getCompoundTagAt(index));
            if (catalog.pendingEvaluations.containsKey(evaluation.getEvaluationId()) ||
                  catalog.pendingByResource.containsKey(evaluation.getResource())) {
                throw new QIOProcessingDataException("Duplicate pending maintenance evaluation");
            }
            catalog.pendingEvaluations.put(evaluation.getEvaluationId(), evaluation);
            catalog.pendingByResource.put(evaluation.getResource(), evaluation.getEvaluationId());
        }
        if (catalog.evaluationCursor < 0 || catalog.evaluationCursor >
              catalog.groupedRules().size()) {
            catalog.evaluationCursor = 0;
        }
        return catalog;
    }

    @Nonnull
    private QIOMaintenanceRule require(UUID ruleId) {
        QIOMaintenanceRule rule = rules.get(Objects.requireNonNull(ruleId, "ruleId"));
        if (rule == null) {
            throw new IllegalArgumentException("Unknown QIO maintenance rule " + ruleId);
        }
        return rule;
    }

    private void incrementRulesRevision() {
        if (rulesRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO maintenance rules revision exhausted");
        }
        rulesRevision++;
        groupedRulesRevision = -1;
        groupedRulesCache = Collections.emptyMap();
        orderedRulesRevision = -1;
        orderedRulesCache = Collections.emptyList();
        requestImmediateEvaluation();
    }

    private void indexOutstanding(QIOMaintenanceRule rule, UUID jobId) {
        outstandingRulesByJob.computeIfAbsent(jobId, ignored -> new HashSet<>())
              .add(rule.getRuleId());
    }

    private void removeOutstandingIndex(QIOMaintenanceRule rule, @Nullable UUID jobId) {
        if (jobId == null) {
            return;
        }
        Set<UUID> indexed = outstandingRulesByJob.get(jobId);
        if (indexed != null) {
            indexed.remove(rule.getRuleId());
            if (indexed.isEmpty()) {
                outstandingRulesByJob.remove(jobId);
            }
        }
    }

    private void validateRuleSnapshot(QIOMaintenanceEvaluation evaluation,
          List<QIOMaintenanceRule> group) {
        Map<UUID, Long> current = new LinkedHashMap<>();
        for (QIOMaintenanceRule rule : group) {
            if (!evaluation.getResource().equals(rule.getResource())) {
                throw new IllegalArgumentException("Maintenance evaluation resource changed");
            }
            current.put(rule.getRuleId(), rule.getRuleRevision());
        }
        if (!current.equals(evaluation.getGroupRuleRevisions())) {
            throw new IllegalStateException("Maintenance rule group changed during evaluation");
        }
    }

    private void incrementRuntimeRevision() {
        if (runtimeRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO maintenance runtime revision exhausted");
        }
        runtimeRevision++;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
