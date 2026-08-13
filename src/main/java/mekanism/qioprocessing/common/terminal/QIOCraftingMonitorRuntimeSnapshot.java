package mekanism.qioprocessing.common.terminal;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Header plus a bounded baseline/delta for the visible nodes of one exact plan revision. */
public final class QIOCraftingMonitorRuntimeSnapshot {

    private static final int MAX_NODES = 1_024;
    public static final int MAX_MISSING_RESOURCES = 4_096;

    public enum SlotState {
        UNREQUESTED,
        WAITING,
        HELD,
        RELEASING
    }

    private final UUID jobId;
    private final int planRevision;
    private final String structuralSignature;
    private final long baseRuntimeRevision;
    private final long runtimeRevision;
    private final boolean baseline;
    private final int nodeOffset;
    private final int requestedNodeCount;
    private final String state;
    private final long basePriority;
    private final long requestedRootAmount;
    private final long deliveredRootAmount;
    private final long elapsedTicks;
    private final long totalOperations;
    private final long completedOperations;
    private final int activeOperations;
    private final int missingResourceTypes;
    private final Map<PortableResourceDescriptor, Long> missingResources;
    private final boolean missingResourcesTruncated;
    private final int blockedSteps;
    private final int activeExecutionSlots;
    private final int configuredExecutionSlots;
    private final SlotState slotState;
    @Nullable
    private final UUID slotToken;
    private final long slotAcquiredAtSchedulerClock;
    private final List<QIOCraftingMonitorRuntimeNode> nodes;

    public QIOCraftingMonitorRuntimeSnapshot(@Nonnull UUID jobId, int planRevision,
          @Nonnull String structuralSignature, long baseRuntimeRevision,
          long runtimeRevision, boolean baseline, int nodeOffset, int requestedNodeCount,
          @Nonnull String state, long basePriority,
          long requestedRootAmount, long deliveredRootAmount, long elapsedTicks,
          long totalOperations, long completedOperations, int activeOperations,
          int missingResourceTypes,
          @Nonnull Map<PortableResourceDescriptor, Long> missingResources,
          boolean missingResourcesTruncated, int blockedSteps, int activeExecutionSlots,
          int configuredExecutionSlots, @Nonnull SlotState slotState,
          @Nullable UUID slotToken, long slotAcquiredAtSchedulerClock,
          @Nonnull List<QIOCraftingMonitorRuntimeNode> nodes) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.planRevision = QIOProcessingNbt.requirePositive(planRevision, "planRevision");
        this.structuralSignature = bounded(structuralSignature, 128);
        this.baseRuntimeRevision = baseline ? -1 :
              QIOProcessingNbt.requireNonNegative(baseRuntimeRevision, "baseRuntimeRevision");
        this.runtimeRevision = QIOProcessingNbt.requireNonNegative(runtimeRevision,
              "runtimeRevision");
        this.baseline = baseline;
        if (nodeOffset < 0 || requestedNodeCount < 0 || requestedNodeCount > MAX_NODES ||
              nodes.size() > requestedNodeCount) {
            throw new IllegalArgumentException("Invalid QIO monitor runtime node window");
        }
        this.nodeOffset = nodeOffset;
        this.requestedNodeCount = requestedNodeCount;
        this.state = bounded(state, 64);
        this.basePriority = basePriority;
        this.requestedRootAmount = positive(requestedRootAmount, "requestedRootAmount");
        this.deliveredRootAmount = QIOProcessingNbt.requireNonNegative(deliveredRootAmount,
              "deliveredRootAmount");
        this.elapsedTicks = QIOProcessingNbt.requireNonNegative(elapsedTicks, "elapsedTicks");
        this.totalOperations = QIOProcessingNbt.requireNonNegative(totalOperations,
              "totalOperations");
        this.completedOperations = QIOProcessingNbt.requireNonNegative(completedOperations,
              "completedOperations");
        if (deliveredRootAmount > requestedRootAmount || completedOperations > totalOperations ||
            activeOperations < 0 || missingResourceTypes < 0 || blockedSteps < 0 ||
            activeExecutionSlots < 0 || configuredExecutionSlots <= 0 ||
            activeExecutionSlots > configuredExecutionSlots || nodes.size() > MAX_NODES) {
            throw new IllegalArgumentException("Invalid QIO monitor runtime snapshot");
        }
        this.activeOperations = activeOperations;
        this.missingResourceTypes = missingResourceTypes;
        this.missingResources = QIOProcessingNbt.copyAmounts(missingResources, true,
              "missingResources");
        this.missingResourcesTruncated = missingResourcesTruncated;
        if (this.missingResources.size() > MAX_MISSING_RESOURCES ||
            missingResourceTypes < this.missingResources.size() ||
            missingResourcesTruncated ==
                  (missingResourceTypes == this.missingResources.size())) {
            throw new IllegalArgumentException("Invalid QIO monitor missing resources");
        }
        this.blockedSteps = blockedSteps;
        this.activeExecutionSlots = activeExecutionSlots;
        this.configuredExecutionSlots = configuredExecutionSlots;
        this.slotState = Objects.requireNonNull(slotState, "slotState");
        this.slotToken = slotToken;
        this.slotAcquiredAtSchedulerClock = slotToken == null ? -1 :
              QIOProcessingNbt.requireNonNegative(slotAcquiredAtSchedulerClock,
                    "slotAcquiredAtSchedulerClock");
        if ((slotState == SlotState.HELD || slotState == SlotState.RELEASING) !=
            (slotToken != null)) {
            throw new IllegalArgumentException("QIO monitor slot state has an invalid token");
        }
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    @Nonnull public UUID getJobId() { return jobId; }
    public int getPlanRevision() { return planRevision; }
    @Nonnull public String getStructuralSignature() { return structuralSignature; }
    public long getBaseRuntimeRevision() { return baseRuntimeRevision; }
    public long getRuntimeRevision() { return runtimeRevision; }
    public boolean isBaseline() { return baseline; }
    public int getNodeOffset() { return nodeOffset; }
    public int getRequestedNodeCount() { return requestedNodeCount; }
    @Nonnull public String getState() { return state; }
    public long getBasePriority() { return basePriority; }
    public long getRequestedRootAmount() { return requestedRootAmount; }
    public long getDeliveredRootAmount() { return deliveredRootAmount; }
    public long getElapsedTicks() { return elapsedTicks; }
    public long getTotalOperations() { return totalOperations; }
    public long getCompletedOperations() { return completedOperations; }
    public int getActiveOperations() { return activeOperations; }
    public int getMissingResourceTypes() { return missingResourceTypes; }
    @Nonnull public Map<PortableResourceDescriptor, Long> getMissingResources() {
        return missingResources;
    }
    public boolean isMissingResourcesTruncated() { return missingResourcesTruncated; }
    public int getBlockedSteps() { return blockedSteps; }
    public int getActiveExecutionSlots() { return activeExecutionSlots; }
    public int getConfiguredExecutionSlots() { return configuredExecutionSlots; }
    @Nonnull public SlotState getSlotState() { return slotState; }
    @Nullable public UUID getSlotToken() { return slotToken; }
    public long getSlotAcquiredAtSchedulerClock() { return slotAcquiredAtSchedulerClock; }
    @Nonnull public List<QIOCraftingMonitorRuntimeNode> getNodes() { return nodes; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "jobId", jobId);
        data.setInteger("planRevision", planRevision);
        data.setString("structuralSignature", structuralSignature);
        data.setLong("baseRuntimeRevision", baseRuntimeRevision);
        data.setLong("runtimeRevision", runtimeRevision);
        data.setBoolean("baseline", baseline);
        data.setInteger("nodeOffset", nodeOffset);
        data.setInteger("requestedNodeCount", requestedNodeCount);
        data.setString("state", state);
        data.setLong("basePriority", basePriority);
        data.setLong("requestedRootAmount", requestedRootAmount);
        data.setLong("deliveredRootAmount", deliveredRootAmount);
        data.setLong("elapsedTicks", elapsedTicks);
        data.setLong("totalOperations", totalOperations);
        data.setLong("completedOperations", completedOperations);
        data.setInteger("activeOperations", activeOperations);
        data.setInteger("missingResourceTypes", missingResourceTypes);
        data.setTag("missingResources", QIOProcessingNbt.writeAmounts(missingResources));
        data.setBoolean("missingResourcesTruncated", missingResourcesTruncated);
        data.setInteger("blockedSteps", blockedSteps);
        data.setInteger("activeExecutionSlots", activeExecutionSlots);
        data.setInteger("configuredExecutionSlots", configuredExecutionSlots);
        data.setString("slotState", slotState.name());
        if (slotToken != null) {
            QIOProcessingNbt.writeUUID(data, "slotToken", slotToken);
            data.setLong("slotAcquiredAtSchedulerClock", slotAcquiredAtSchedulerClock);
        }
        NBTTagList nodeList = new NBTTagList();
        for (QIOCraftingMonitorRuntimeNode node : nodes) nodeList.appendTag(node.write());
        data.setTag("nodes", nodeList);
        return data;
    }

    @Nonnull
    public static QIOCraftingMonitorRuntimeSnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            NBTTagList nodeList = data.getTagList("nodes", NBT.TAG_COMPOUND);
            if (nodeList.tagCount() > MAX_NODES) {
                throw new QIOProcessingDataException("QIO monitor runtime snapshot is too large");
            }
            List<QIOCraftingMonitorRuntimeNode> nodes = new ArrayList<>(nodeList.tagCount());
            for (int i = 0; i < nodeList.tagCount(); i++) {
                nodes.add(QIOCraftingMonitorRuntimeNode.read(nodeList.getCompoundTagAt(i)));
            }
            boolean baseline = data.getBoolean("baseline");
            return new QIOCraftingMonitorRuntimeSnapshot(
                  QIOProcessingNbt.readUUID(data, "jobId"),
                  data.getInteger("planRevision"), data.getString("structuralSignature"),
                  baseline ? -1 : data.getLong("baseRuntimeRevision"),
                  data.getLong("runtimeRevision"), baseline, data.getInteger("nodeOffset"),
                  data.getInteger("requestedNodeCount"), data.getString("state"),
                  data.getLong("basePriority"), data.getLong("requestedRootAmount"),
                  data.getLong("deliveredRootAmount"), data.getLong("elapsedTicks"),
                  data.getLong("totalOperations"), data.getLong("completedOperations"),
                  data.getInteger("activeOperations"), data.getInteger("missingResourceTypes"),
                  QIOProcessingNbt.readAmounts(data, "missingResources",
                        MAX_MISSING_RESOURCES),
                  data.getBoolean("missingResourcesTruncated"),
                  data.getInteger("blockedSteps"), data.getInteger("activeExecutionSlots"),
                  data.getInteger("configuredExecutionSlots"),
                  QIOProcessingNbt.readEnum(data, "slotState", SlotState.class),
                  data.hasKey("slotToken", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readUUID(data, "slotToken") : null,
                  data.hasKey("slotToken", NBT.TAG_STRING) ?
                        data.getLong("slotAcquiredAtSchedulerClock") : -1,
                  nodes);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO monitor runtime snapshot", e);
        }
    }

    private static long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static String bounded(String value, int maximum) {
        String checked = Objects.requireNonNull(value, "value").trim();
        if (checked.isEmpty()) throw new IllegalArgumentException("QIO monitor string is empty");
        return checked.substring(0, Math.min(maximum, checked.length()));
    }
}
