package mekanism.qioprocessing.common.content.transfer;

import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.math.BigInteger;

/** Crash-recoverable ownership handoff between QIO, a job buffer, and a machine. */
public final class QIODurableTransferRecord {

    public static final int SCHEMA_VERSION = 2;

    public enum Type {
        QIO_TO_JOB,
        JOB_TO_MACHINE,
        MACHINE_TO_JOB,
        JOB_TO_QIO
    }

    public enum Phase {
        PREPARED,
        SOURCE_DEBITED,
        DESTINATION_CREDITED,
        COMMITTED,
        ROLLBACK_REQUIRED
    }

    public enum Resolution {
        NONE,
        FORWARD_COMMITTED,
        COMPENSATED
    }

    private static final int MAX_RESOURCE_ENTRIES = 65_536;
    private static final int MAX_MACHINE_BASELINES = 256;
    private static final int MAX_ENDPOINT_LENGTH = 512;

    private final UUID transferId;
    private final UUID requestId;
    private final Type type;
    @Nullable
    private final UUID ownerJobId;
    @Nullable
    private final UUID ownerOperationId;
    private final int planRevision;
    private final String nodeId;
    @Nullable
    private final UUID leaseId;
    private final String source;
    private final String destination;
    private final Map<PortableResourceDescriptor, Long> resources;
    private final Map<PortableResourceDescriptor, UUID> qioResourceUUIDs;
    private final Map<PortableResourceDescriptor, BigInteger> qioBaselines;
    private final List<MachinePortBaseline> machineBaselines;
    private final long expectedContentsRevision;
    private final long expectedClaimRevision;
    private final String requestDigest;
    private Phase phase;
    private Resolution resolution;
    @Nullable
    private String sourceReceipt;
    @Nullable
    private String destinationReceipt;
    private long sourceStateRevision;
    @Nullable
    private UUID compensationTransferId;

    public QIODurableTransferRecord(@Nonnull UUID transferId, @Nonnull UUID requestId,
          @Nonnull Type type, @Nullable UUID ownerJobId, @Nullable UUID ownerOperationId,
          int planRevision, @Nonnull String nodeId, @Nullable UUID leaseId,
          @Nonnull String source, @Nonnull String destination,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        this(transferId, requestId, type, ownerJobId, ownerOperationId, planRevision, nodeId,
              leaseId, source, destination, resources, Collections.emptyMap(), Collections.emptyList(), -1, -1,
              Collections.emptyMap(),
              Phase.PREPARED, Resolution.NONE, null, null, -1, null, null);
    }

    public QIODurableTransferRecord(@Nonnull UUID transferId, @Nonnull UUID requestId,
          @Nonnull Type type, @Nullable UUID ownerJobId, @Nullable UUID ownerOperationId,
          int planRevision, @Nonnull String nodeId, @Nullable UUID leaseId,
          @Nonnull String source, @Nonnull String destination,
          @Nonnull Map<PortableResourceDescriptor, Long> resources,
          @Nonnull Map<PortableResourceDescriptor, BigInteger> qioBaselines) {
        this(transferId, requestId, type, ownerJobId, ownerOperationId, planRevision, nodeId,
              leaseId, source, destination, resources, Collections.emptyMap(), Collections.emptyList(),
              -1, -1, qioBaselines, Phase.PREPARED, Resolution.NONE, null, null, -1, null, null);
    }

    public QIODurableTransferRecord(@Nonnull UUID transferId, @Nonnull UUID requestId,
          @Nonnull Type type, @Nullable UUID ownerJobId, @Nullable UUID ownerOperationId,
          int planRevision, @Nonnull String nodeId, @Nullable UUID leaseId,
          @Nonnull String source, @Nonnull String destination,
          @Nonnull Map<PortableResourceDescriptor, Long> resources,
          @Nonnull Collection<MachinePortBaseline> machineBaselines) {
        this(transferId, requestId, type, ownerJobId, ownerOperationId, planRevision, nodeId,
              leaseId, source, destination, resources, Collections.emptyMap(), machineBaselines,
              -1, -1, Collections.emptyMap(), Phase.PREPARED, Resolution.NONE, null, null, -1, null, null);
    }

    @Nonnull
    public static QIODurableTransferRecord qioToJob(@Nonnull UUID transferId,
          @Nonnull UUID requestId, @Nonnull UUID frequencyUUID, @Nonnull UUID ownerJobId,
          int planRevision, @Nonnull Map<PortableResourceDescriptor, Long> resources,
          @Nonnull Map<PortableResourceDescriptor, UUID> qioResourceUUIDs,
          @Nonnull Map<PortableResourceDescriptor, BigInteger> qioBaselines,
          long expectedContentsRevision, long expectedClaimRevision) {
        return new QIODurableTransferRecord(transferId, requestId, Type.QIO_TO_JOB, ownerJobId,
              null, planRevision, "reservation", null, "qio/" + frequencyUUID,
              "job/" + ownerJobId + "/reserved", resources, qioResourceUUIDs,
              Collections.emptyList(), expectedContentsRevision, expectedClaimRevision, qioBaselines,
              Phase.PREPARED, Resolution.NONE,
              null, null, -1, null, null);
    }

    private QIODurableTransferRecord(UUID transferId, UUID requestId, Type type,
          @Nullable UUID ownerJobId, @Nullable UUID ownerOperationId, int planRevision,
          String nodeId, @Nullable UUID leaseId, String source, String destination,
          Map<PortableResourceDescriptor, Long> resources,
          Map<PortableResourceDescriptor, UUID> qioResourceUUIDs,
          Collection<MachinePortBaseline> machineBaselines,
          long expectedContentsRevision, long expectedClaimRevision,
          Map<PortableResourceDescriptor, BigInteger> qioBaselines,
          Phase phase, Resolution resolution,
          @Nullable String sourceReceipt, @Nullable String destinationReceipt,
          long sourceStateRevision, @Nullable UUID compensationTransferId,
          @Nullable String storedDigest) {
        this.transferId = Objects.requireNonNull(transferId, "transferId");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.type = Objects.requireNonNull(type, "type");
        if ((ownerJobId == null) == (ownerOperationId == null)) {
            throw new IllegalArgumentException("A transfer must have exactly one job or standalone operation owner");
        }
        this.ownerJobId = ownerJobId;
        this.ownerOperationId = ownerOperationId;
        if (ownerJobId != null && planRevision <= 0 || ownerJobId == null && planRevision != 0) {
            throw new IllegalArgumentException("Transfer plan revision does not match its owner kind");
        }
        this.planRevision = planRevision;
        this.nodeId = requireEndpoint(nodeId, "nodeId", true);
        this.leaseId = leaseId;
        this.source = requireEndpoint(source, "source", false);
        this.destination = requireEndpoint(destination, "destination", false);
        this.resources = QIOProcessingNbt.copyAmounts(resources, false, "transferResources");
        this.qioResourceUUIDs = copyBindings(qioResourceUUIDs);
        this.qioBaselines = QIOProcessingNbt.copyExactAmounts(qioBaselines, true,
              "qioBaselines");
        this.machineBaselines = copyBaselines(machineBaselines);
        this.expectedContentsRevision = QIOProcessingNbt.requireRevision(expectedContentsRevision,
              "expectedContentsRevision");
        this.expectedClaimRevision = QIOProcessingNbt.requireRevision(expectedClaimRevision,
              "expectedClaimRevision");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.sourceReceipt = sourceReceipt;
        this.destinationReceipt = destinationReceipt;
        this.sourceStateRevision = QIOProcessingNbt.requireRevision(sourceStateRevision,
              "sourceStateRevision");
        this.compensationTransferId = compensationTransferId;
        requestDigest = calculateDigest();
        if (storedDigest != null && !requestDigest.equals(storedDigest)) {
            throw new IllegalArgumentException("Transfer request digest does not match its contents");
        }
        validatePhase();
    }

    @Nonnull
    public UUID getTransferId() {
        return transferId;
    }

    @Nonnull
    public UUID getRequestId() {
        return requestId;
    }

    @Nonnull
    public Type getType() {
        return type;
    }

    @Nullable
    public UUID getOwnerJobId() {
        return ownerJobId;
    }

    @Nullable
    public UUID getOwnerOperationId() {
        return ownerOperationId;
    }

    public int getPlanRevision() {
        return planRevision;
    }

    @Nonnull
    public String getNodeId() {
        return nodeId;
    }

    @Nullable
    public UUID getLeaseId() {
        return leaseId;
    }

    @Nonnull
    public String getSource() {
        return source;
    }

    @Nonnull
    public String getDestination() {
        return destination;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getResources() {
        return resources;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, UUID> getQIOResourceUUIDs() {
        return qioResourceUUIDs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, BigInteger> getQIOBaselines() {
        return qioBaselines;
    }

    @Nonnull
    public List<MachinePortBaseline> getMachineBaselines() {
        return machineBaselines;
    }

    public long getExpectedContentsRevision() {
        return expectedContentsRevision;
    }

    public long getExpectedClaimRevision() {
        return expectedClaimRevision;
    }

    @Nonnull
    public String getRequestDigest() {
        return requestDigest;
    }

    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    @Nonnull
    public Resolution getResolution() {
        return resolution;
    }

    @Nullable
    public String getSourceReceipt() {
        return sourceReceipt;
    }

    @Nullable
    public String getDestinationReceipt() {
        return destinationReceipt;
    }

    public long getSourceStateRevision() {
        return sourceStateRevision;
    }

    @Nullable
    public UUID getCompensationTransferId() {
        return compensationTransferId;
    }

    public void markSourceDebited(@Nonnull String receipt) {
        markSourceDebited(receipt, -1);
    }

    public void markSourceDebited(@Nonnull String receipt, long sourceStateRevision) {
        requirePhase(Phase.PREPARED);
        sourceReceipt = requireEndpoint(receipt, "sourceReceipt", false);
        this.sourceStateRevision = QIOProcessingNbt.requireRevision(sourceStateRevision,
              "sourceStateRevision");
        phase = Phase.SOURCE_DEBITED;
    }

    public void markDestinationCredited(@Nonnull String receipt) {
        requirePhase(Phase.SOURCE_DEBITED);
        destinationReceipt = requireEndpoint(receipt, "destinationReceipt", false);
        phase = Phase.DESTINATION_CREDITED;
    }

    public void commitForward() {
        requirePhase(Phase.DESTINATION_CREDITED);
        phase = Phase.COMMITTED;
        resolution = Resolution.FORWARD_COMMITTED;
    }

    public void requireRollback(@Nonnull UUID compensationTransferId) {
        if (phase != Phase.SOURCE_DEBITED && phase != Phase.DESTINATION_CREDITED) {
            throw new IllegalStateException("Only an in-flight debited transfer can require rollback");
        }
        this.compensationTransferId = Objects.requireNonNull(compensationTransferId,
              "compensationTransferId");
        phase = Phase.ROLLBACK_REQUIRED;
    }

    public void commitCompensation() {
        requirePhase(Phase.ROLLBACK_REQUIRED);
        if (compensationTransferId == null) {
            throw new IllegalStateException("Compensation transfer ID is missing");
        }
        phase = Phase.COMMITTED;
        resolution = Resolution.COMPENSATED;
    }

    /**
     * Reopens an endpoint-local transfer after the persistent endpoint proves that both its
     * source and destination rolled back to the prepared baseline. Cross-storage callers must
     * never use this as a substitute for a compensating transfer.
     */
    public void restartAfterEndpointRollback() {
        if (type != Type.MACHINE_TO_JOB || ownerJobId != null || machineBaselines.isEmpty() ||
              phase == Phase.ROLLBACK_REQUIRED || resolution == Resolution.COMPENSATED) {
            throw new IllegalStateException("Only an automatic output transfer with a persistent baseline can be restarted");
        }
        phase = Phase.PREPARED;
        resolution = Resolution.NONE;
        sourceReceipt = null;
        destinationReceipt = null;
        sourceStateRevision = -1;
        compensationTransferId = null;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("durableTransferSchemaVersion", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "transferId", transferId);
        QIOProcessingNbt.writeUUID(data, "requestId", requestId);
        data.setString("type", type.name());
        if (ownerJobId != null) {
            QIOProcessingNbt.writeUUID(data, "ownerJobId", ownerJobId);
        } else {
            QIOProcessingNbt.writeUUID(data, "ownerOperationId", ownerOperationId);
        }
        data.setInteger("planRevision", planRevision);
        data.setString("nodeId", nodeId);
        if (leaseId != null) {
            QIOProcessingNbt.writeUUID(data, "leaseId", leaseId);
        }
        data.setString("source", source);
        data.setString("destination", destination);
        data.setTag("resources", QIOProcessingNbt.writeAmounts(resources));
        NBTTagList bindings = new NBTTagList();
        qioResourceUUIDs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound binding = new NBTTagCompound();
            binding.setTag("resource", entry.getKey().write());
            QIOProcessingNbt.writeUUID(binding, "qioResourceUUID", entry.getValue());
            bindings.appendTag(binding);
        });
        data.setTag("qioResourceUUIDs", bindings);
        data.setTag("qioBaselines", QIOProcessingNbt.writeExactAmounts(qioBaselines));
        NBTTagList baselines = new NBTTagList();
        machineBaselines.forEach(baseline -> baselines.appendTag(baseline.write()));
        data.setTag("machineBaselines", baselines);
        data.setLong("expectedContentsRevision", expectedContentsRevision);
        data.setLong("expectedClaimRevision", expectedClaimRevision);
        data.setString("requestDigest", requestDigest);
        data.setString("phase", phase.name());
        data.setString("resolution", resolution.name());
        if (sourceReceipt != null) {
            data.setString("sourceReceipt", sourceReceipt);
        }
        if (destinationReceipt != null) {
            data.setString("destinationReceipt", destinationReceipt);
        }
        if (sourceStateRevision >= 0) {
            data.setLong("sourceStateRevision", sourceStateRevision);
        }
        if (compensationTransferId != null) {
            QIOProcessingNbt.writeUUID(data, "compensationTransferId", compensationTransferId);
        }
        return data;
    }

    @Nonnull
    public static QIODurableTransferRecord read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        int schema = data.getInteger("durableTransferSchemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported durable QIO transfer schema " +
                  schema + ", expected " + SCHEMA_VERSION);
        }
        try {
            return new QIODurableTransferRecord(QIOProcessingNbt.readUUID(data, "transferId"),
                  QIOProcessingNbt.readUUID(data, "requestId"),
                  QIOProcessingNbt.readEnum(data, "type", Type.class),
                  QIOProcessingNbt.readOptionalUUID(data, "ownerJobId"),
                  QIOProcessingNbt.readOptionalUUID(data, "ownerOperationId"),
                  data.getInteger("planRevision"), data.getString("nodeId"),
                  QIOProcessingNbt.readOptionalUUID(data, "leaseId"), data.getString("source"),
                  data.getString("destination"),
                  QIOProcessingNbt.readAmounts(data, "resources", MAX_RESOURCE_ENTRIES),
                  readBindings(data), readBaselines(data), data.getLong("expectedContentsRevision"),
                  data.getLong("expectedClaimRevision"),
                  QIOProcessingNbt.readExactAmounts(data, "qioBaselines", MAX_RESOURCE_ENTRIES),
                  QIOProcessingNbt.readEnum(data, "phase", Phase.class),
                  QIOProcessingNbt.readEnum(data, "resolution", Resolution.class),
                  data.hasKey("sourceReceipt", NBT.TAG_STRING) ? data.getString("sourceReceipt") : null,
                  data.hasKey("destinationReceipt", NBT.TAG_STRING) ? data.getString("destinationReceipt") : null,
                  data.hasKey("sourceStateRevision", NBT.TAG_LONG) ?
                        data.getLong("sourceStateRevision") : -1,
                  QIOProcessingNbt.readOptionalUUID(data, "compensationTransferId"),
                  data.getString("requestDigest"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid durable QIO transfer record", e);
        }
    }

    private void validatePhase() {
        if (type == Type.QIO_TO_JOB && (!qioResourceUUIDs.keySet().equals(resources.keySet()) ||
              expectedContentsRevision < 0 || expectedClaimRevision < 0)) {
            throw new IllegalArgumentException("QIO_TO_JOB transfer is missing its core resource bindings");
        }
        if (type != Type.QIO_TO_JOB && !qioResourceUUIDs.isEmpty()) {
            throw new IllegalArgumentException("Only QIO_TO_JOB may persist claim resource bindings");
        }
        boolean hasQIOEndpoint = type == Type.QIO_TO_JOB || type == Type.JOB_TO_QIO;
        if (hasQIOEndpoint != qioBaselines.keySet().equals(resources.keySet())) {
            throw new IllegalArgumentException(
                  "QIO physical baselines must match a QIO endpoint transfer");
        }
        if (type == Type.QIO_TO_JOB) {
            for (Map.Entry<PortableResourceDescriptor, Long> entry : resources.entrySet()) {
                BigInteger baseline = qioBaselines.get(entry.getKey());
                if (baseline != null && baseline.compareTo(BigInteger.valueOf(entry.getValue())) < 0) {
                    throw new IllegalArgumentException(
                          "A QIO extraction baseline cannot be smaller than its transfer amount");
                }
            }
        }
        if (!machineBaselines.isEmpty() &&
              (type != Type.JOB_TO_MACHINE && type != Type.MACHINE_TO_JOB || leaseId == null)) {
            throw new IllegalArgumentException(
                  "Only leased machine transfers may persist machine baselines");
        }
        if ((phase == Phase.SOURCE_DEBITED || phase == Phase.DESTINATION_CREDITED ||
              phase == Phase.ROLLBACK_REQUIRED || phase == Phase.COMMITTED) && sourceReceipt == null) {
            throw new IllegalArgumentException("Debited transfer is missing its source receipt");
        }
        if ((phase == Phase.DESTINATION_CREDITED ||
              phase == Phase.COMMITTED && resolution == Resolution.FORWARD_COMMITTED) &&
              destinationReceipt == null) {
            throw new IllegalArgumentException("Credited transfer is missing its destination receipt");
        }
        if (phase == Phase.COMMITTED && resolution == Resolution.NONE ||
              phase != Phase.COMMITTED && resolution != Resolution.NONE) {
            throw new IllegalArgumentException("Transfer phase and resolution disagree");
        }
        if ((phase == Phase.ROLLBACK_REQUIRED || resolution == Resolution.COMPENSATED) &&
              compensationTransferId == null) {
            throw new IllegalArgumentException("Compensating transfer reference is missing");
        }
        if (type == Type.QIO_TO_JOB && phase != Phase.PREPARED && sourceStateRevision < 0) {
            throw new IllegalArgumentException("Debited QIO claim transfer is missing its claim revision");
        }
    }

    private void requirePhase(Phase required) {
        if (phase != required) {
            throw new IllegalStateException("Expected transfer phase " + required + ", found " + phase);
        }
    }

    private String calculateDigest() {
        StringBuilder canonical = new StringBuilder(type.name())
              .append('|').append(ownerJobId == null ? ownerOperationId : ownerJobId)
              .append('|').append(planRevision).append('|').append(nodeId)
              .append('|').append(leaseId).append('|').append(source).append('|').append(destination)
              .append('|').append(expectedContentsRevision).append('|').append(expectedClaimRevision);
        for (Map.Entry<PortableResourceDescriptor, Long> entry : resources.entrySet()) {
            canonical.append('|').append(entry.getKey()).append('@').append(entry.getValue())
                  .append('#').append(qioResourceUUIDs.get(entry.getKey()));
            if (!qioBaselines.isEmpty()) {
                canonical.append("^baseline=").append(qioBaselines.get(entry.getKey()));
            }
        }
        for (MachinePortBaseline baseline : machineBaselines) {
            canonical.append("|baseline=").append(baseline.write());
        }
        return QIOHashing.sha256(canonical);
    }

    private static String requireEndpoint(String value, String name, boolean allowEmpty) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (!allowEmpty && trimmed.isEmpty() || trimmed.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        return trimmed;
    }

    @Nonnull
    private static Map<PortableResourceDescriptor, UUID> copyBindings(
          Map<PortableResourceDescriptor, UUID> bindings) {
        Objects.requireNonNull(bindings, "qioResourceUUIDs");
        Map<PortableResourceDescriptor, UUID> copy = new LinkedHashMap<>();
        Set<UUID> seenUUIDs = new HashSet<>();
        bindings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            PortableResourceDescriptor resource = Objects.requireNonNull(entry.getKey(),
                  "qioResourceUUID resource");
            UUID uuid = Objects.requireNonNull(entry.getValue(), "qioResourceUUID");
            if (!seenUUIDs.add(uuid)) {
                throw new IllegalArgumentException("One QIO resource UUID is bound to multiple descriptors");
            }
            copy.put(resource, uuid);
        });
        return Collections.unmodifiableMap(copy);
    }

    @Nonnull
    private static Map<PortableResourceDescriptor, UUID> readBindings(NBTTagCompound data)
          throws QIOProcessingDataException {
        if (!data.hasKey("qioResourceUUIDs", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(
                  "QIO transfer resource bindings are missing from current-schema data");
        }
        NBTTagList list = data.getTagList("qioResourceUUIDs", NBT.TAG_COMPOUND);
        if (list.tagCount() > MAX_RESOURCE_ENTRIES) {
            throw new QIOProcessingDataException("QIO transfer resource binding list exceeds its limit");
        }
        Map<PortableResourceDescriptor, UUID> bindings = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound binding = list.getCompoundTagAt(i);
            PortableResourceDescriptor resource;
            try {
                resource = PortableResourceDescriptor.read(binding.getCompoundTag("resource"));
            } catch (RuntimeException e) {
                throw new QIOProcessingDataException("Invalid QIO transfer resource binding", e);
            }
            UUID uuid = QIOProcessingNbt.readUUID(binding, "qioResourceUUID");
            if (bindings.put(resource, uuid) != null) {
                throw new QIOProcessingDataException("Duplicate QIO transfer resource binding");
            }
        }
        return bindings;
    }

    @Nonnull
    private static List<MachinePortBaseline> copyBaselines(
          Collection<MachinePortBaseline> baselines) {
        Objects.requireNonNull(baselines, "machineBaselines");
        if (baselines.size() > MAX_MACHINE_BASELINES) {
            throw new IllegalArgumentException("QIO transfer has too many machine baselines");
        }
        List<MachinePortBaseline> copy = new ArrayList<>(baselines.size());
        Set<String> portIds = new HashSet<>();
        for (MachinePortBaseline baseline : baselines) {
            MachinePortBaseline checked = Objects.requireNonNull(baseline, "machineBaseline");
            if (!portIds.add(checked.portId())) {
                throw new IllegalArgumentException("Duplicate QIO transfer machine baseline port");
            }
            copy.add(checked);
        }
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    @Nonnull
    private static List<MachinePortBaseline> readBaselines(NBTTagCompound data)
          throws QIOProcessingDataException {
        if (!data.hasKey("machineBaselines", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(
                  "QIO transfer machine baselines are missing from current-schema data");
        }
        NBTTagList stored = data.getTagList("machineBaselines", NBT.TAG_COMPOUND);
        if (stored.tagCount() > MAX_MACHINE_BASELINES) {
            throw new QIOProcessingDataException("QIO transfer machine baseline list exceeds its limit");
        }
        List<MachinePortBaseline> baselines = new ArrayList<>(stored.tagCount());
        try {
            for (int index = 0; index < stored.tagCount(); index++) {
                baselines.add(MachinePortBaseline.read(stored.getCompoundTagAt(index)));
            }
            return baselines;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO transfer machine baseline", e);
        }
    }
}
