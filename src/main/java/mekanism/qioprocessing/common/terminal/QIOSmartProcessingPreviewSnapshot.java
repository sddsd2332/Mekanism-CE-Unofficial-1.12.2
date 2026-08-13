package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.order.QIOOrderPreview;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;
import mekanism.qioprocessing.common.planning.QIOPlanningTrace;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Bounded, display-oriented projection of one asynchronous order preview. */
public final class QIOSmartProcessingPreviewSnapshot {

    public static final int MAX_EXTERNAL_REQUIREMENTS = 512;
    public static final int MAX_PLAN_ENTRIES = 512;
    public static final int MAX_ERROR_MARKERS = 512;

    private final UUID previewId;
    private final QIOOrderPreview.State state;
    @Nullable
    private final QIOPlanningResult.Status planningStatus;
    private final String diagnostic;
    @Nullable
    private final PortableResourceDescriptor target;
    private final long amount;
    private final long priority;
    private final long expiresAtTick;
    private final long planningNanos;
    private final long mainThreadPreparationNanos;
    private final long routePreparationNanos;
    private final long schedulingNanos;
    private final long totalNanos;
    private final int planningAttempt;
    private final long plannedOperations;
    private final int planRevision;
    private final Map<PortableResourceDescriptor, Long> externalRequirements;
    private final List<QIOCraftingMonitorPlanEntry> planEntries;
    private final Set<Long> errorNodeIds;
    private final Set<PortableResourceDescriptor> errorResources;
    private final boolean rootError;
    private final boolean mergeOrder;
    private final boolean externalRequirementsTruncated;
    private final boolean planEntriesTruncated;
    @Nullable
    private final UUID jobId;

    public QIOSmartProcessingPreviewSnapshot(@Nonnull UUID previewId,
          @Nonnull QIOOrderPreview.State state, @Nullable QIOPlanningResult.Status planningStatus,
          @Nullable String diagnostic, @Nullable PortableResourceDescriptor target, long amount,
          long priority, long expiresAtTick, long planningNanos, long plannedOperations,
          int planRevision, @Nonnull Map<PortableResourceDescriptor, Long> externalRequirements,
          @Nonnull List<QIOCraftingMonitorPlanEntry> planEntries, @Nullable UUID jobId) {
        this(previewId, state, planningStatus, diagnostic, target, amount, priority,
              expiresAtTick, planningNanos, plannedOperations, planRevision,
              externalRequirements, planEntries, false, false, Collections.emptySet(),
              Collections.emptySet(), false, false, 0, 0, 0, 0, 1, jobId);
    }

    public QIOSmartProcessingPreviewSnapshot(@Nonnull UUID previewId,
          @Nonnull QIOOrderPreview.State state, @Nullable QIOPlanningResult.Status planningStatus,
          @Nullable String diagnostic, @Nullable PortableResourceDescriptor target, long amount,
          long priority, long expiresAtTick, long planningNanos, long plannedOperations,
          int planRevision, @Nonnull Map<PortableResourceDescriptor, Long> externalRequirements,
          @Nonnull List<QIOCraftingMonitorPlanEntry> planEntries,
          boolean externalRequirementsTruncated, boolean planEntriesTruncated,
          @Nonnull Set<Long> errorNodeIds,
          @Nonnull Set<PortableResourceDescriptor> errorResources, boolean rootError,
          boolean mergeOrder, @Nullable UUID jobId) {
        this(previewId, state, planningStatus, diagnostic, target, amount, priority,
              expiresAtTick, planningNanos, plannedOperations, planRevision,
              externalRequirements, planEntries, externalRequirementsTruncated,
              planEntriesTruncated, errorNodeIds, errorResources, rootError, mergeOrder,
              0, 0, 0, 0, 1, jobId);
    }

    public QIOSmartProcessingPreviewSnapshot(@Nonnull UUID previewId,
          @Nonnull QIOOrderPreview.State state, @Nullable QIOPlanningResult.Status planningStatus,
          @Nullable String diagnostic, @Nullable PortableResourceDescriptor target, long amount,
          long priority, long expiresAtTick, long planningNanos, long plannedOperations,
          int planRevision, @Nonnull Map<PortableResourceDescriptor, Long> externalRequirements,
          @Nonnull List<QIOCraftingMonitorPlanEntry> planEntries,
          boolean externalRequirementsTruncated, boolean planEntriesTruncated,
          @Nonnull Set<Long> errorNodeIds,
          @Nonnull Set<PortableResourceDescriptor> errorResources, boolean rootError,
          boolean mergeOrder, long mainThreadPreparationNanos, long routePreparationNanos,
          long schedulingNanos, long totalNanos, int planningAttempt, @Nullable UUID jobId) {
        this.previewId = Objects.requireNonNull(previewId, "previewId");
        this.state = Objects.requireNonNull(state, "state");
        this.planningStatus = planningStatus;
        this.diagnostic = bounded(diagnostic, 512);
        this.target = target;
        if (amount < 0 || priority < Long.MIN_VALUE || expiresAtTick < 0 || planningNanos < 0 ||
            mainThreadPreparationNanos < 0 || routePreparationNanos < 0 ||
            schedulingNanos < 0 || totalNanos < 0 || planningAttempt <= 0 ||
            plannedOperations < 0 || planRevision < 0) {
            throw new IllegalArgumentException("Invalid preview snapshot bounds");
        }
        this.amount = amount;
        this.priority = priority;
        this.expiresAtTick = expiresAtTick;
        this.planningNanos = planningNanos;
        this.mainThreadPreparationNanos = mainThreadPreparationNanos;
        this.routePreparationNanos = routePreparationNanos;
        this.schedulingNanos = schedulingNanos;
        this.totalNanos = totalNanos;
        this.planningAttempt = planningAttempt;
        this.plannedOperations = plannedOperations;
        this.planRevision = planRevision;
        if (externalRequirements.size() > MAX_EXTERNAL_REQUIREMENTS ||
            planEntries.size() > MAX_PLAN_ENTRIES ||
            errorNodeIds.size() > MAX_ERROR_MARKERS ||
            errorResources.size() > MAX_ERROR_MARKERS) {
            throw new IllegalArgumentException("Preview snapshot is too large");
        }
        this.externalRequirements = Collections.unmodifiableMap(new LinkedHashMap<>(externalRequirements));
        this.planEntries = Collections.unmodifiableList(new ArrayList<>(planEntries));
        LinkedHashSet<Long> checkedErrorNodes = new LinkedHashSet<>();
        for (Long nodeId : errorNodeIds) {
            if (Objects.requireNonNull(nodeId, "errorNodeId") < 0) {
                throw new IllegalArgumentException("Negative preview error node identity");
            }
            checkedErrorNodes.add(nodeId);
        }
        this.errorNodeIds = Collections.unmodifiableSet(checkedErrorNodes);
        this.errorResources = Collections.unmodifiableSet(new LinkedHashSet<>(errorResources));
        this.rootError = rootError;
        this.mergeOrder = mergeOrder;
        this.externalRequirementsTruncated = externalRequirementsTruncated;
        this.planEntriesTruncated = planEntriesTruncated;
        this.jobId = jobId;
    }

    @Nonnull public UUID getPreviewId() { return previewId; }
    @Nonnull public QIOOrderPreview.State getState() { return state; }
    @Nullable public QIOPlanningResult.Status getPlanningStatus() { return planningStatus; }
    @Nonnull public String getDiagnostic() { return diagnostic; }
    @Nullable public PortableResourceDescriptor getTarget() { return target; }
    public long getAmount() { return amount; }
    public long getPriority() { return priority; }
    public long getExpiresAtTick() { return expiresAtTick; }
    public long getPlanningNanos() { return planningNanos; }
    public long getMainThreadPreparationNanos() { return mainThreadPreparationNanos; }
    public long getRoutePreparationNanos() { return routePreparationNanos; }
    public long getSchedulingNanos() { return schedulingNanos; }
    public long getTotalNanos() { return totalNanos; }
    public int getPlanningAttempt() { return planningAttempt; }
    public long getPlannedOperations() { return plannedOperations; }
    public int getPlanRevision() { return planRevision; }
    @Nonnull public Map<PortableResourceDescriptor, Long> getExternalRequirements() { return externalRequirements; }
    @Nonnull public List<QIOCraftingMonitorPlanEntry> getPlanEntries() { return planEntries; }
    @Nonnull public Set<Long> getErrorNodeIds() { return errorNodeIds; }
    @Nonnull public Set<PortableResourceDescriptor> getErrorResources() { return errorResources; }
    public boolean isRootError() { return rootError; }
    public boolean isMergeOrder() { return mergeOrder; }
    public boolean isExternalRequirementsTruncated() { return externalRequirementsTruncated; }
    public boolean isStepsTruncated() { return planEntriesTruncated; }
    @Nullable public UUID getJobId() { return jobId; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "previewId", previewId);
        data.setString("state", state.name());
        if (planningStatus != null) data.setString("planningStatus", planningStatus.name());
        if (!diagnostic.isEmpty()) data.setString("diagnostic", diagnostic);
        if (target != null) data.setTag("target", target.write());
        data.setLong("amount", amount);
        data.setLong("priority", priority);
        data.setLong("expiresAtTick", expiresAtTick);
        data.setLong("planningNanos", planningNanos);
        data.setLong("mainThreadPreparationNanos", mainThreadPreparationNanos);
        data.setLong("routePreparationNanos", routePreparationNanos);
        data.setLong("schedulingNanos", schedulingNanos);
        data.setLong("totalNanos", totalNanos);
        data.setInteger("planningAttempt", planningAttempt);
        data.setLong("plannedOperations", plannedOperations);
        data.setInteger("planRevision", planRevision);
        data.setTag("externalRequirements", QIOProcessingNbt.writeAmounts(externalRequirements));
        NBTTagList entries = new NBTTagList();
        for (QIOCraftingMonitorPlanEntry entry : planEntries) {
            entries.appendTag(entry.write());
        }
        data.setTag("planEntries", entries);
        NBTTagList errorNodes = new NBTTagList();
        for (long nodeId : errorNodeIds) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("nodeId", nodeId);
            errorNodes.appendTag(entry);
        }
        data.setTag("errorNodeIds", errorNodes);
        NBTTagList errorResourceList = new NBTTagList();
        for (PortableResourceDescriptor resource : errorResources) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("resource", resource.write());
            errorResourceList.appendTag(entry);
        }
        data.setTag("errorResources", errorResourceList);
        data.setBoolean("rootError", rootError);
        data.setBoolean("mergeOrder", mergeOrder);
        data.setBoolean("externalRequirementsTruncated", externalRequirementsTruncated);
        data.setBoolean("planEntriesTruncated", planEntriesTruncated);
        if (jobId != null) QIOProcessingNbt.writeUUID(data, "jobId", jobId);
        return data;
    }

    @Nonnull
    public static QIOSmartProcessingPreviewSnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            NBTTagList list = data.getTagList("planEntries", NBT.TAG_COMPOUND);
            if (list.tagCount() > MAX_PLAN_ENTRIES) {
                throw new QIOProcessingDataException("Preview has too many plan entries");
            }
            List<QIOCraftingMonitorPlanEntry> planEntries = new ArrayList<>(list.tagCount());
            for (int i = 0; i < list.tagCount(); i++) {
                planEntries.add(QIOCraftingMonitorPlanEntry.read(list.getCompoundTagAt(i)));
            }
            NBTTagList storedErrorNodes = data.getTagList("errorNodeIds", NBT.TAG_COMPOUND);
            NBTTagList storedErrorResources = data.getTagList("errorResources", NBT.TAG_COMPOUND);
            if (storedErrorNodes.tagCount() > MAX_ERROR_MARKERS ||
                storedErrorResources.tagCount() > MAX_ERROR_MARKERS) {
                throw new QIOProcessingDataException("Preview has too many error markers");
            }
            Set<Long> errorNodeIds = new LinkedHashSet<>();
            for (int i = 0; i < storedErrorNodes.tagCount(); i++) {
                errorNodeIds.add(storedErrorNodes.getCompoundTagAt(i).getLong("nodeId"));
            }
            Set<PortableResourceDescriptor> errorResources = new LinkedHashSet<>();
            for (int i = 0; i < storedErrorResources.tagCount(); i++) {
                errorResources.add(PortableResourceDescriptor.read(
                      storedErrorResources.getCompoundTagAt(i).getCompoundTag("resource")));
            }
            return new QIOSmartProcessingPreviewSnapshot(
                  QIOProcessingNbt.readUUID(data, "previewId"),
                  QIOProcessingNbt.readEnum(data, "state", QIOOrderPreview.State.class),
                  data.hasKey("planningStatus", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readEnum(data, "planningStatus", QIOPlanningResult.Status.class) : null,
                  data.getString("diagnostic"),
                  data.hasKey("target", NBT.TAG_COMPOUND) ?
                        PortableResourceDescriptor.read(data.getCompoundTag("target")) : null,
                  data.getLong("amount"), data.getLong("priority"), data.getLong("expiresAtTick"),
                  data.getLong("planningNanos"), data.getLong("plannedOperations"),
                  data.getInteger("planRevision"),
                  QIOProcessingNbt.readAmounts(data, "externalRequirements", MAX_EXTERNAL_REQUIREMENTS),
                  planEntries, data.getBoolean("externalRequirementsTruncated"),
                  data.getBoolean("planEntriesTruncated"), errorNodeIds, errorResources,
                  data.getBoolean("rootError"), data.getBoolean("mergeOrder"),
                  data.getLong("mainThreadPreparationNanos"),
                  data.getLong("routePreparationNanos"), data.getLong("schedulingNanos"),
                  data.getLong("totalNanos"), data.hasKey("planningAttempt", NBT.TAG_INT) ?
                        data.getInteger("planningAttempt") : 1,
                  data.hasKey("jobId") ? QIOProcessingNbt.readUUID(data, "jobId") : null);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid preview snapshot", e);
        }
    }

    @Nonnull
    public static QIOSmartProcessingPreviewSnapshot fromPreview(@Nonnull QIOOrderPreview preview,
          @Nullable UUID jobId) {
        QIOPlanningResult result = preview.getResult();
        Map<PortableResourceDescriptor, Long> requirements = new LinkedHashMap<>();
        List<QIOCraftingMonitorPlanEntry> planEntries = new ArrayList<>();
        Set<Long> errorNodeIds = new LinkedHashSet<>();
        Set<PortableResourceDescriptor> errorResources = new LinkedHashSet<>();
        boolean rootError = false;
        int revision = 0;
        long operations = 0;
        QIOPlanningResult.Status status = null;
        String diagnostic = "";
        boolean requirementsTruncated = false;
        boolean planEntriesTruncated = false;
        if (result != null) {
            status = result.getStatus();
            diagnostic = result.getDiagnostic();
            operations = result.getPlannedOperations();
            if (result.getPlan() != null) {
                revision = result.getPlan().getRevision();
                for (Map.Entry<PortableResourceDescriptor, Long> requirement :
                      result.getPlan().getExternalRequirements().entrySet()) {
                    if (requirements.size() >= MAX_EXTERNAL_REQUIREMENTS) {
                        requirementsTruncated = true;
                        break;
                    }
                    requirements.put(requirement.getKey(), requirement.getValue());
                }
                for (mekanism.qioprocessing.common.content.plan.QIOPlanStep step :
                      result.getPlan().getSteps()) {
                    if (!addPlanEntry(planEntries,
                          QIOCraftingMonitorPlanEntry.step(step))) {
                        planEntriesTruncated = true;
                        break;
                    }
                }
                if (!planEntriesTruncated) {
                    for (mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode cycle :
                          result.getPlan().getCycleNodes()) {
                        if (!addPlanEntry(planEntries,
                              QIOCraftingMonitorPlanEntry.cycle(cycle))) {
                            planEntriesTruncated = true;
                            break;
                        }
                    }
                }
            } else {
                QIOPlanningTrace trace = result.getTrace();
                for (mekanism.qioprocessing.common.content.plan.QIOPlanStep step :
                      trace.getSteps()) {
                    if (!addPlanEntry(planEntries,
                          QIOCraftingMonitorPlanEntry.step(step))) {
                        planEntriesTruncated = true;
                        break;
                    }
                }
                if (!planEntriesTruncated) {
                    for (mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode cycle :
                          trace.getCycles()) {
                        if (!addPlanEntry(planEntries,
                              QIOCraftingMonitorPlanEntry.cycle(cycle))) {
                            planEntriesTruncated = true;
                            break;
                        }
                    }
                }
                trace.getErrorNodeIds().stream().limit(MAX_ERROR_MARKERS)
                      .forEach(errorNodeIds::add);
                trace.getErrorResources().stream().limit(MAX_ERROR_MARKERS)
                      .forEach(errorResources::add);
                rootError = trace.isRootError();
            }
        }
        return new QIOSmartProcessingPreviewSnapshot(preview.getPreviewId(), preview.getState(),
              status, diagnostic, preview.getTarget(), preview.getAmount(), preview.getPriority(),
              preview.getExpiresAtTick(), preview.getPlanningNanos(), operations, revision,
              requirements, planEntries, requirementsTruncated, planEntriesTruncated,
              errorNodeIds, errorResources, rootError, preview.isMergeOrder(),
              preview.getMainThreadPreparationNanos(), preview.getRoutePreparationNanos(),
              preview.getSchedulingNanos(), preview.getTotalNanos(),
              preview.getPlanningAttempt(), jobId);
    }

    private static boolean addPlanEntry(List<QIOCraftingMonitorPlanEntry> entries,
          QIOCraftingMonitorPlanEntry entry) {
        if (entries.size() >= MAX_PLAN_ENTRIES) return false;
        entries.add(entry);
        return true;
    }

    private static String bounded(@Nullable String value, int max) {
        String checked = value == null ? "" : value.trim();
        return checked.length() <= max ? checked : checked.substring(0, max);
    }

}
