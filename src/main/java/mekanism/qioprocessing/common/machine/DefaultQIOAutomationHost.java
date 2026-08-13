package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.api.machine.MachineActivitySnapshot;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.api.machine.QIOOutputBufferEntry;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.execution.QIOEndpointPersistenceService;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Default serializable implementation attached to admitted machine tiles. */
public final class DefaultQIOAutomationHost implements QIOAutomationHost {

    static final int SCHEMA_VERSION = 2;
    static final int MAX_ACTIVE_LEASES = 256;
    static final int MAX_OPERATION_TOKENS = 256;
    static final int MAX_OUTPUT_BUFFERS = 256;
    private static final String SCHEMA = "schema";
    private static final String DEVICE_UUID = "deviceUUID";
    private static final String FREQUENCY = "frequency";
    private static final String ENABLED_MODE = "enabledMode";
    private static final String STATE = "state";
    private static final String CONFIGURATION_REVISION = "configurationRevision";
    private static final String MANAGEMENT_PAUSED = "managementPaused";
    private static final String LEASES = "leases";
    private static final String TOKENS = "tokens";
    private static final String OUTPUT_BUFFERS = "outputBuffers";
    private static final String DATA_ERROR = "dataError";
    private static final String QUARANTINED_DATA = "quarantinedData";
    private static final String CLEAR_MODE_WHEN_DRAINED = "clearModeWhenDrained";

    @Nullable
    private final TileEntity tile;
    private UUID persistentDeviceUUID = UUID.randomUUID();
    @Nullable
    private QIOFrequencyReference frequencyReference;
    @Nullable
    private QIOAutomationMode enabledMode;
    private boolean clearModeWhenDrained;
    private State state = State.UNBOUND;
    private long configurationRevision;
    private boolean managementPaused;
    private Map<UUID, MachineOperationLease> leases = new LinkedHashMap<>();
    private Map<UUID, MachineOperationToken> operationTokens = new LinkedHashMap<>();
    private Map<UUID, QIOOutputBufferEntry> outputBuffers = new LinkedHashMap<>();
    private final Set<UUID> persistedCompletedOperations = new java.util.LinkedHashSet<>();
    private final Set<UUID> persistedTransferReceipts = new java.util.LinkedHashSet<>();
    private final Map<Long, MachineActivitySnapshot> activitySnapshots = new LinkedHashMap<>();
    @Nullable
    private String dataError;
    @Nullable
    private NBTTagCompound quarantinedData;

    /** Factory constructor used only by Forge's detached capability default. */
    public DefaultQIOAutomationHost() {
        tile = null;
    }

    public DefaultQIOAutomationHost(@Nonnull TileEntity tile) {
        this.tile = Objects.requireNonNull(tile, "Host tile cannot be null");
    }

    @Nullable
    TileEntity tile() {
        return tile;
    }

    @Nonnull
    @Override
    public UUID getPersistentDeviceUUID() {
        return persistentDeviceUUID;
    }

    @Nullable
    @Override
    public QIOFrequencyReference getFrequencyReference() {
        return frequencyReference;
    }

    @Nullable
    @Override
    public QIOAutomationMode getEnabledMode() {
        return enabledMode;
    }

    @Nonnull
    @Override
    public State getState() {
        return state;
    }

    @Nullable
    public String getDataError() {
        return dataError;
    }

    @Override
    public long getConfigurationRevision() {
        return configurationRevision;
    }

    @Override
    public boolean isManagementPaused() {
        return managementPaused;
    }

    @Override
    public boolean setManagementPaused(boolean paused) {
        if (state == State.IDENTITY_CONFLICT || state == State.DATA_ERROR) return false;
        if (managementPaused != paused) {
            managementPaused = paused;
            incrementConfigurationRevision();
        }
        return true;
    }

    @Override
    public boolean selectMode(@Nonnull QIOAutomationMode mode) {
        Objects.requireNonNull(mode, "Automation mode cannot be null");
        if (clearModeWhenDrained || hasUnsettledOperations() ||
            state == State.DRAINING_CHANGE || state == State.IDENTITY_CONFLICT ||
            state == State.DATA_ERROR) {
            return false;
        }
        enabledMode = mode;
        state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        return true;
    }

    @Override
    public boolean clearMode() {
        return clearMode(false);
    }

    @Override
    public boolean clearMode(boolean detachFrequencyImmediately) {
        if (state == State.IDENTITY_CONFLICT || state == State.DATA_ERROR) {
            return false;
        }
        if (hasUnsettledOperations()) {
            boolean changed = !clearModeWhenDrained || state != State.DRAINING_CHANGE;
            if (detachFrequencyImmediately && frequencyReference != null) {
                frequencyReference = null;
                changed = true;
            }
            if (changed) {
                clearModeWhenDrained = true;
                state = State.DRAINING_CHANGE;
                incrementConfigurationRevision();
            }
            return true;
        }
        clearModeWhenDrained = false;
        enabledMode = null;
        frequencyReference = null;
        state = State.UNBOUND;
        incrementConfigurationRevision();
        return true;
    }

    @Nonnull
    @Override
    public Map<UUID, MachineOperationLease> getLeases() {
        return Collections.unmodifiableMap(leases);
    }

    @Nonnull
    @Override
    public Map<UUID, MachineOperationToken> getOperationTokens() {
        return Collections.unmodifiableMap(operationTokens);
    }

    @Nonnull
    @Override
    public Map<Long, MachineActivitySnapshot> getActivitySnapshots() {
        return Collections.unmodifiableMap(activitySnapshots);
    }

    @Nonnull
    @Override
    public Map<UUID, QIOOutputBufferEntry> getOutputBufferEntries() {
        return Collections.unmodifiableMap(outputBuffers);
    }

    /** Applies an already authorized binding change. Mutation draining is added by the machine controller layer. */
    public boolean configureBinding(@Nullable QIOFrequencyReference frequency, @Nullable QIOAutomationMode mode,
          boolean accessValidated) {
        if (clearModeWhenDrained || hasUnsettledOperations() ||
            state == State.DRAINING_CHANGE || state == State.IDENTITY_CONFLICT ||
            state == State.DATA_ERROR) {
            return false;
        }
        if (frequency != null && mode == null) {
            throw new IllegalArgumentException("A frequency binding requires an automation mode");
        }
        frequencyReference = frequency;
        enabledMode = mode;
        state = frequency == null ? State.UNBOUND : accessValidated ? State.ACTIVE : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        return true;
    }

    public boolean setAccessValidated(boolean valid) {
        if (frequencyReference == null || enabledMode == null || state == State.IDENTITY_CONFLICT || state == State.DATA_ERROR) {
            return false;
        }
        if (clearModeWhenDrained || state == State.DRAINING_CHANGE) {
            return valid;
        }
        State next = valid ? State.ACTIVE : State.WAITING_ACCESS;
        if (state != next) {
            state = next;
            markDirty();
        }
        return true;
    }

    void quarantineIdentityConflict() {
        if (state != State.DATA_ERROR && state != State.IDENTITY_CONFLICT) {
            state = State.IDENTITY_CONFLICT;
            markDirty();
        }
    }

    /** Explicit recovery hook; callers must audit and re-register the endpoint around this operation. */
    boolean regenerateDeviceIdentity() {
        if (clearModeWhenDrained || hasUnsettledOperations()) {
            return false;
        }
        persistentDeviceUUID = UUID.randomUUID();
        state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        return true;
    }

    @Nullable
    @Override
    public MachineOperationLease tryAcquireLease(@Nonnull UUID leaseId, @Nonnull UUID ownerOperationId,
          @Nonnull MachineOperationLease.Mode mode, long laneId, long createdAt,
          @Nonnull Collection<MachinePortBaseline> baselines) {
        Objects.requireNonNull(leaseId, "Lease id cannot be null");
        Objects.requireNonNull(ownerOperationId, "Operation id cannot be null");
        Objects.requireNonNull(mode, "Lease mode cannot be null");
        Objects.requireNonNull(baselines, "Lease baselines cannot be null");
        if (managementPaused || !state.acceptsNewOperations() || !modeMatchesEnabledAutomation(mode) || leases.containsKey(leaseId) ||
            operationTokens.containsKey(ownerOperationId) || leases.size() >= MAX_ACTIVE_LEASES) {
            return null;
        }
        for (MachineOperationLease existing : leases.values()) {
            if (existing.state() == MachineOperationLease.State.RELEASED) {
                continue;
            }
            if (existing.ownerOperationId().equals(ownerOperationId)) {
                return null;
            }
            if (mode == MachineOperationLease.Mode.PROCESSING_EXCLUSIVE &&
                existing.mode() == MachineOperationLease.Mode.PROCESSING_EXCLUSIVE && existing.laneId() == laneId) {
                return null;
            }
            if (sharesPortGroup(existing.portGroupIds(), baselines)) {
                return null;
            }
        }
        MachineOperationLease lease = MachineOperationLease.acquire(leaseId, ownerOperationId, mode, laneId,
              createdAt, baselines);
        leases.put(leaseId, lease);
        markDirty();
        return lease;
    }

    @Override
    public boolean attachOperationToken(@Nonnull MachineOperationToken token) {
        Objects.requireNonNull(token, "Operation token cannot be null");
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || lease.state() != MachineOperationLease.State.ACQUIRED ||
            !lease.ownerOperationId().equals(token.operationId()) || lease.laneId() != token.laneId() ||
            operationTokens.containsKey(token.operationId()) || operationTokens.size() >= MAX_OPERATION_TOKENS ||
            !tokenMatchesLeaseMode(token, lease)) {
            return false;
        }
        operationTokens.put(token.operationId(), token);
        persistedCompletedOperations.remove(token.operationId());
        markDirty();
        return true;
    }

    @Override
    public boolean transitionOperation(@Nonnull UUID operationId, @Nonnull MachineOperationToken.State tokenState,
          @Nonnull MachineOperationLease.State leaseState) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        if (token == null) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || !lease.ownerOperationId().equals(operationId)) {
            enterDataError("Operation token references a missing or mismatched lease");
            return false;
        }
        try {
            MachineOperationToken nextToken = token.transition(tokenState);
            MachineOperationLease nextLease = lease.transition(leaseState);
            operationTokens.put(operationId, nextToken);
            leases.put(lease.leaseId(), nextLease);
            markDirty();
            // A completed token is not durable until its lease has also been released.  The
            // execution service performs those transitions separately; requesting a checkpoint
            // after the first transition could acknowledge a snapshot that still owns the lane.
            if (nextToken.state() == MachineOperationToken.State.COMPLETED &&
                  nextLease.state() == MachineOperationLease.State.RELEASED && tile != null) {
                QIOEndpointPersistenceService.INSTANCE.requestAutomationHost(tile, this,
                      operationId);
            }
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    @Override
    public boolean recordTransferReceipt(@Nonnull UUID operationId, @Nonnull UUID transferId) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        Objects.requireNonNull(transferId, "Transfer id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        if (token == null || token.state().isTerminal()) {
            return false;
        }
        MachineOperationToken updated = token.withTransferReceipt(transferId);
        if (updated != token) {
            operationTokens.put(operationId, updated);
            persistedTransferReceipts.remove(transferId);
            markDirty();
        }
        return true;
    }

    @Override
    public boolean contaminateLease(@Nonnull UUID leaseId, @Nonnull String reason) {
        Objects.requireNonNull(leaseId, "Lease id cannot be null");
        MachineOperationLease lease = leases.get(leaseId);
        if (lease == null || lease.state() == MachineOperationLease.State.RELEASED) {
            return false;
        }
        leases.put(leaseId, lease.contaminate(reason));
        MachineOperationToken token = operationTokens.get(lease.ownerOperationId());
        if (token != null && !token.state().isTerminal()) {
            operationTokens.put(token.operationId(), token.transition(MachineOperationToken.State.CONTAMINATED));
        }
        markDirty();
        return true;
    }

    @Override
    public boolean abandonJobOperation(@Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        if (token == null || token.kind() != MachineOperationToken.Kind.JOB ||
              token.state() == MachineOperationToken.State.COLLECTING ||
              token.state() == MachineOperationToken.State.COMPLETED) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || !lease.ownerOperationId().equals(operationId)) {
            enterDataError("Operation token references a missing or mismatched lease");
            return false;
        }
        try {
            MachineOperationToken settledToken = token.state().isTerminal() ? token :
                  token.transition(MachineOperationToken.State.CANCELLED);
            MachineOperationLease releasedLease = lease.state() ==
                  MachineOperationLease.State.RELEASED ? lease :
                  lease.transition(MachineOperationLease.State.RELEASED);
            operationTokens.put(operationId, settledToken);
            leases.put(releasedLease.leaseId(), releasedLease);
            activitySnapshots.remove(token.laneId());
            markDirty();
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    @Override
    public boolean releaseUnattachedLease(@Nonnull UUID leaseId) {
        MachineOperationLease lease = leases.get(Objects.requireNonNull(leaseId, "Lease id cannot be null"));
        if (lease == null || lease.state() != MachineOperationLease.State.ACQUIRED ||
              operationTokens.containsKey(lease.ownerOperationId())) {
            return false;
        }
        leases.remove(leaseId);
        markDirty();
        return true;
    }

    boolean prepareOutputBuffer(@Nonnull QIOOutputBufferEntry entry) {
        Objects.requireNonNull(entry, "Output buffer entry cannot be null");
        MachineOperationToken token = operationTokens.get(entry.operationId());
        MachineOperationLease lease = leases.get(entry.leaseId());
        if (entry.phase() != QIOOutputBufferEntry.Phase.PREPARED || outputBuffers.containsKey(entry.bufferId()) ||
            outputBuffers.size() >= MAX_OUTPUT_BUFFERS || token == null || lease == null ||
            token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN || token.state() != MachineOperationToken.State.COLLECTING ||
            !token.leaseId().equals(entry.leaseId()) || lease.state() != MachineOperationLease.State.COLLECTING ||
            !entry.baseline().portGroupId().equals(lease.baselines().get(0).portGroupId())) {
            return false;
        }
        outputBuffers.put(entry.bufferId(), entry);
        markDirty();
        return true;
    }

    boolean holdOutput(@Nonnull UUID bufferId, @Nonnull PortableResourceDescriptor resource, long amount) {
        QIOOutputBufferEntry entry = outputBuffers.get(Objects.requireNonNull(bufferId, "Output buffer id cannot be null"));
        if (entry == null || entry.phase() != QIOOutputBufferEntry.Phase.PREPARED) {
            return false;
        }
        outputBuffers.put(bufferId, entry.hold(resource, amount));
        markDirty();
        return true;
    }

    boolean beginOutputDelivery(@Nonnull UUID bufferId, @Nonnull UUID transferId, long requestedAmount) {
        QIOOutputBufferEntry entry = outputBuffers.get(Objects.requireNonNull(bufferId, "Output buffer id cannot be null"));
        if (entry == null || entry.phase() != QIOOutputBufferEntry.Phase.HELD) {
            return false;
        }
        outputBuffers.put(bufferId, entry.beginDelivery(transferId, requestedAmount));
        markDirty();
        return true;
    }

    boolean applyOutputDeliveryReceipt(@Nonnull UUID bufferId, @Nonnull UUID transferId, long transferredAmount) {
        QIOOutputBufferEntry entry = outputBuffers.get(Objects.requireNonNull(bufferId, "Output buffer id cannot be null"));
        if (entry == null || entry.phase() != QIOOutputBufferEntry.Phase.DELIVERING) {
            return false;
        }
        MachineOperationToken token = operationTokens.get(entry.operationId());
        MachineOperationLease lease = leases.get(entry.leaseId());
        if (token == null || lease == null || token.state() != MachineOperationToken.State.COLLECTING ||
            lease.state() != MachineOperationLease.State.COLLECTING) {
            enterDataError("Output buffer lost its collecting operation ownership");
            return false;
        }
        QIOOutputBufferEntry remaining = entry.applyDeliveryReceipt(transferId, transferredAmount);
        operationTokens.put(token.operationId(), token.withTransferReceipt(transferId));
        if (remaining == null) {
            outputBuffers.remove(bufferId);
            MachineOperationToken completedToken = operationTokens.get(token.operationId())
                  .transition(MachineOperationToken.State.COMPLETED);
            MachineOperationLease completedLease = lease.transition(MachineOperationLease.State.COMPLETED)
                  .transition(MachineOperationLease.State.RELEASED);
            operationTokens.put(completedToken.operationId(), completedToken);
            leases.put(completedLease.leaseId(), completedLease);
        } else {
            outputBuffers.put(bufferId, remaining);
        }
        markDirty();
        if (remaining == null && tile != null) {
            QIOEndpointPersistenceService.INSTANCE.requestAutomationHost(tile, this,
                  token.operationId());
        }
        return true;
    }

    @Override
    public boolean forgetSettledOperation(@Nonnull UUID operationId) {
        MachineOperationToken token = operationTokens.get(Objects.requireNonNull(operationId, "Operation id cannot be null"));
        if (token == null || !token.state().isTerminal()) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || lease.state() != MachineOperationLease.State.RELEASED ||
            outputBuffers.values().stream().anyMatch(entry -> entry.operationId().equals(operationId))) {
            return false;
        }
        operationTokens.remove(operationId);
        persistedCompletedOperations.remove(operationId);
        persistedTransferReceipts.removeAll(token.transferReceipts());
        leases.remove(lease.leaseId());
        activitySnapshots.remove(token.laneId());
        markDirty();
        if (frequencyReference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDevice(
                  frequencyReference.getFrequencyUUID(), persistentDeviceUUID);
        }
        return true;
    }

    public boolean isPersistedCompletedOperation(@Nonnull UUID operationId) {
        return persistedCompletedOperations.contains(Objects.requireNonNull(operationId,
              "Operation id cannot be null"));
    }

    @Override
    public boolean isTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
        MachineOperationToken token = operationTokens.get(Objects.requireNonNull(operationId,
              "operationId"));
        UUID checkedTransfer = Objects.requireNonNull(transferId, "transferId");
        return token != null && token.hasTransferReceipt(checkedTransfer) &&
              persistedTransferReceipts.contains(checkedTransfer);
    }

    /** Confirms one operation receipt was included in a completed endpoint snapshot. */
    @Override
    public void confirmTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
        MachineOperationToken token = operationTokens.get(Objects.requireNonNull(operationId,
              "operationId"));
        UUID checkedTransfer = Objects.requireNonNull(transferId, "transferId");
        if (token != null && token.hasTransferReceipt(checkedTransfer)) {
            persistedTransferReceipts.add(checkedTransfer);
            wakeExecution();
        }
    }

    /** Confirms one completed operation was included in an endpoint persistence snapshot. */
    @Override
    public void confirmCompletedOperationPersisted(@Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "operationId");
        MachineOperationToken token = operationTokens.get(checked);
        MachineOperationLease lease = token == null ? null : leases.get(token.leaseId());
        if (token != null && token.state() == MachineOperationToken.State.COMPLETED &&
              lease != null && lease.state() == MachineOperationLease.State.RELEASED &&
              lease.ownerOperationId().equals(checked)) {
            persistedCompletedOperations.add(checked);
            wakeExecution();
        }
    }

    public void confirmCompletedOperationsPersisted() {
        int before = persistedCompletedOperations.size();
        operationTokens.values().stream()
              .filter(token -> token.state() == MachineOperationToken.State.COMPLETED)
              .filter(token -> {
                  MachineOperationLease lease = leases.get(token.leaseId());
                  return lease != null && lease.state() == MachineOperationLease.State.RELEASED &&
                        lease.ownerOperationId().equals(token.operationId());
              })
              .map(MachineOperationToken::operationId)
              .forEach(persistedCompletedOperations::add);
        if (persistedCompletedOperations.size() != before) {
            wakeExecution();
        }
    }

    @Override
    public void updateActivitySnapshot(@Nonnull MachineActivitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Activity snapshot cannot be null");
        activitySnapshots.put(snapshot.laneId(), snapshot);
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(SCHEMA, SCHEMA_VERSION);
        data.setString(DEVICE_UUID, persistentDeviceUUID.toString());
        if (frequencyReference != null) {
            data.setTag(FREQUENCY, frequencyReference.write());
        }
        if (enabledMode != null) {
            data.setString(ENABLED_MODE, enabledMode.name());
        }
        data.setBoolean(CLEAR_MODE_WHEN_DRAINED, clearModeWhenDrained);
        data.setString(STATE, state.name());
        data.setLong(CONFIGURATION_REVISION, configurationRevision);
        data.setBoolean(MANAGEMENT_PAUSED, managementPaused);
        NBTTagList leaseList = new NBTTagList();
        sortedLeases().forEach(lease -> leaseList.appendTag(lease.write()));
        data.setTag(LEASES, leaseList);
        NBTTagList tokenList = new NBTTagList();
        sortedTokens().forEach(token -> tokenList.appendTag(token.write()));
        data.setTag(TOKENS, tokenList);
        NBTTagList outputBufferList = new NBTTagList();
        sortedOutputBuffers().forEach(entry -> outputBufferList.appendTag(entry.write()));
        data.setTag(OUTPUT_BUFFERS, outputBufferList);
        if (dataError != null) {
            data.setString(DATA_ERROR, dataError);
        }
        if (quarantinedData != null) {
            data.setTag(QUARANTINED_DATA, quarantinedData.copy());
        }
        return data;
    }

    @Override
    public void deserializeNBT(NBTTagCompound data) {
        Objects.requireNonNull(data, "Automation host data cannot be null");
        try {
            ParsedState parsed = parse(data);
            persistentDeviceUUID = parsed.deviceUUID;
            frequencyReference = parsed.frequency;
            enabledMode = parsed.enabledMode;
            clearModeWhenDrained = parsed.clearModeWhenDrained;
            state = parsed.state;
            configurationRevision = parsed.configurationRevision;
            managementPaused = data.getBoolean(MANAGEMENT_PAUSED);
            leases = parsed.leases;
            operationTokens = parsed.tokens;
            outputBuffers = parsed.outputBuffers;
            persistedCompletedOperations.clear();
            persistedTransferReceipts.clear();
            parsed.tokens.values().stream()
                  .filter(token -> token.state() == MachineOperationToken.State.COMPLETED)
                  .filter(token -> {
                      MachineOperationLease lease = parsed.leases.get(token.leaseId());
                      return lease != null && lease.state() == MachineOperationLease.State.RELEASED &&
                            lease.ownerOperationId().equals(token.operationId());
                  })
                  .map(MachineOperationToken::operationId)
                  .forEach(persistedCompletedOperations::add);
            parsed.tokens.values().stream().flatMap(token -> token.transferReceipts().stream())
                  .forEach(persistedTransferReceipts::add);
            dataError = parsed.dataError;
            quarantinedData = parsed.quarantinedData;
            activitySnapshots.clear();
            completePendingModeClearIfReady();
        } catch (RuntimeException e) {
            clearModeWhenDrained = false;
            managementPaused = false;
            leases = new LinkedHashMap<>();
            operationTokens = new LinkedHashMap<>();
            outputBuffers = new LinkedHashMap<>();
            persistedCompletedOperations.clear();
            persistedTransferReceipts.clear();
            activitySnapshots.clear();
            quarantinedData = data.copy();
            quarantinedData.removeTag(QUARANTINED_DATA);
            enterDataError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private ParsedState parse(NBTTagCompound data) {
        if (data.getInteger(SCHEMA) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported QIO automation host schema " + data.getInteger(SCHEMA));
        }
        if (!data.hasKey(DEVICE_UUID, NBT.TAG_STRING) ||
              !data.hasKey(STATE, NBT.TAG_STRING) ||
              !data.hasKey(CONFIGURATION_REVISION, NBT.TAG_LONG) ||
              !data.hasKey(MANAGEMENT_PAUSED, NBT.TAG_BYTE) ||
              !data.hasKey(LEASES, NBT.TAG_LIST) ||
              !data.hasKey(TOKENS, NBT.TAG_LIST) ||
              !data.hasKey(OUTPUT_BUFFERS, NBT.TAG_LIST)) {
            throw new IllegalArgumentException(
                  "QIO automation host is missing current-schema state");
        }
        UUID deviceUUID = parseUUID(data.getString(DEVICE_UUID), DEVICE_UUID);
        QIOFrequencyReference frequency = data.hasKey(FREQUENCY, NBT.TAG_COMPOUND) ?
              QIOFrequencyReference.read(data.getCompoundTag(FREQUENCY)) : null;
        QIOAutomationMode mode = data.hasKey(ENABLED_MODE, NBT.TAG_STRING) ?
              QIOAutomationMode.valueOf(data.getString(ENABLED_MODE)) : null;
        State parsedState = State.valueOf(data.getString(STATE));
        boolean parsedClearModeWhenDrained = data.getBoolean(
              CLEAR_MODE_WHEN_DRAINED);
        long revision = data.getLong(CONFIGURATION_REVISION);
        if (revision < 0 || frequency != null && mode == null) {
            throw new IllegalArgumentException("Invalid automation binding or configuration revision");
        }
        boolean detachedDrain = frequency == null && parsedState == State.DRAINING_CHANGE &&
              parsedClearModeWhenDrained && mode != null;
        if (frequency == null && parsedState != State.UNBOUND &&
            parsedState != State.IDENTITY_CONFLICT && parsedState != State.DATA_ERROR &&
            !detachedDrain) {
            throw new IllegalArgumentException("An unbound automation host has an invalid state");
        }
        if (frequency != null && mode != null && parsedState == State.UNBOUND) {
            throw new IllegalArgumentException("A bound automation host cannot be unbound");
        }
        if (parsedClearModeWhenDrained !=
              (parsedState == State.DRAINING_CHANGE) ||
            parsedClearModeWhenDrained && mode == null) {
            throw new IllegalArgumentException("Invalid pending automation mode removal");
        }

        NBTTagList leaseList = data.getTagList(LEASES, NBT.TAG_COMPOUND);
        NBTTagList tokenList = data.getTagList(TOKENS, NBT.TAG_COMPOUND);
        NBTTagList outputBufferList = data.getTagList(OUTPUT_BUFFERS, NBT.TAG_COMPOUND);
        if (leaseList.tagCount() > MAX_ACTIVE_LEASES || tokenList.tagCount() > MAX_OPERATION_TOKENS ||
            outputBufferList.tagCount() > MAX_OUTPUT_BUFFERS) {
            throw new IllegalArgumentException("Automation host contains too many operations");
        }
        Map<UUID, MachineOperationLease> parsedLeases = new LinkedHashMap<>();
        for (int index = 0; index < leaseList.tagCount(); index++) {
            MachineOperationLease lease = MachineOperationLease.read(leaseList.getCompoundTagAt(index));
            if (parsedLeases.put(lease.leaseId(), lease) != null) {
                throw new IllegalArgumentException("Duplicate machine lease id");
            }
        }
        Map<UUID, MachineOperationToken> parsedTokens = new LinkedHashMap<>();
        for (int index = 0; index < tokenList.tagCount(); index++) {
            MachineOperationToken token = MachineOperationToken.read(tokenList.getCompoundTagAt(index));
            if (parsedTokens.put(token.operationId(), token) != null) {
                throw new IllegalArgumentException("Duplicate machine operation id");
            }
            MachineOperationLease lease = parsedLeases.get(token.leaseId());
            if (lease == null || !lease.ownerOperationId().equals(token.operationId()) || lease.laneId() != token.laneId()) {
                throw new IllegalArgumentException("Machine operation token does not match its lease");
            }
            if (token.state() == MachineOperationToken.State.COMPLETED &&
                  lease.state() != MachineOperationLease.State.COMPLETED &&
                  lease.state() != MachineOperationLease.State.RELEASED) {
                throw new IllegalArgumentException("Completed machine operation has an active lease");
            }
        }
        Map<UUID, QIOOutputBufferEntry> parsedOutputBuffers = new LinkedHashMap<>();
        for (int index = 0; index < outputBufferList.tagCount(); index++) {
            QIOOutputBufferEntry entry = QIOOutputBufferEntry.read(outputBufferList.getCompoundTagAt(index));
            if (parsedOutputBuffers.put(entry.bufferId(), entry) != null) {
                throw new IllegalArgumentException("Duplicate QIO output buffer id");
            }
            MachineOperationToken token = parsedTokens.get(entry.operationId());
            MachineOperationLease lease = parsedLeases.get(entry.leaseId());
            if (token == null || lease == null || token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN ||
                !token.leaseId().equals(entry.leaseId()) || token.state() != MachineOperationToken.State.COLLECTING ||
                lease.state() != MachineOperationLease.State.COLLECTING) {
                throw new IllegalArgumentException("QIO output buffer does not match a collecting operation");
            }
        }
        String parsedError = data.hasKey(DATA_ERROR, NBT.TAG_STRING) ? data.getString(DATA_ERROR) : null;
        if (parsedState == State.DATA_ERROR && (parsedError == null || parsedError.isEmpty())) {
            throw new IllegalArgumentException("Data-error host is missing its diagnostic");
        }
        NBTTagCompound parsedQuarantine = data.hasKey(QUARANTINED_DATA, NBT.TAG_COMPOUND) ?
              data.getCompoundTag(QUARANTINED_DATA).copy() : null;
        // Every live binding is re-authorized after load before it can access QIO again.
        State restoredState = parsedState == State.ACTIVE ? State.WAITING_ACCESS : parsedState;
        return new ParsedState(deviceUUID, frequency, mode, parsedClearModeWhenDrained,
              restoredState, revision, parsedLeases, parsedTokens,
              parsedOutputBuffers, parsedError, parsedQuarantine);
    }

    private boolean hasUnsettledOperations() {
        if (!outputBuffers.isEmpty()) {
            return true;
        }
        for (MachineOperationLease lease : leases.values()) {
            if (lease.state() != MachineOperationLease.State.RELEASED) {
                return true;
            }
        }
        for (MachineOperationToken token : operationTokens.values()) {
            if (!token.state().isTerminal()) {
                return true;
            }
        }
        return false;
    }

    private void completePendingModeClearIfReady() {
        if (!clearModeWhenDrained || hasUnsettledOperations()) {
            return;
        }
        clearModeWhenDrained = false;
        enabledMode = null;
        frequencyReference = null;
        state = State.UNBOUND;
        incrementConfigurationRevision();
    }

    private boolean modeMatchesEnabledAutomation(MachineOperationLease.Mode mode) {
        return enabledMode == QIOAutomationMode.OUTPUT_ONLY ? mode == MachineOperationLease.Mode.OUTPUT_DRAIN :
              enabledMode != null && mode == MachineOperationLease.Mode.PROCESSING_EXCLUSIVE;
    }

    private boolean tokenMatchesLeaseMode(MachineOperationToken token, MachineOperationLease lease) {
        if (lease.mode() == MachineOperationLease.Mode.OUTPUT_DRAIN) {
            return token.kind() == MachineOperationToken.Kind.OUTPUT_DRAIN;
        }
        return enabledMode == QIOAutomationMode.SCHEDULED && token.kind() == MachineOperationToken.Kind.JOB ||
              enabledMode == QIOAutomationMode.PASSIVE && token.kind() == MachineOperationToken.Kind.PASSIVE;
    }

    private static boolean sharesPortGroup(Set<String> occupied, Collection<MachinePortBaseline> requested) {
        for (MachinePortBaseline baseline : requested) {
            if (occupied.contains(baseline.portGroupId())) {
                return true;
            }
        }
        return false;
    }

    private List<MachineOperationLease> sortedLeases() {
        List<MachineOperationLease> values = new ArrayList<>(leases.values());
        values.sort((first, second) -> first.leaseId().compareTo(second.leaseId()));
        return values;
    }

    private List<MachineOperationToken> sortedTokens() {
        List<MachineOperationToken> values = new ArrayList<>(operationTokens.values());
        values.sort((first, second) -> first.operationId().compareTo(second.operationId()));
        return values;
    }

    private List<QIOOutputBufferEntry> sortedOutputBuffers() {
        List<QIOOutputBufferEntry> values = new ArrayList<>(outputBuffers.values());
        values.sort((first, second) -> first.bufferId().compareTo(second.bufferId()));
        return values;
    }

    private void incrementConfigurationRevision() {
        if (configurationRevision == Long.MAX_VALUE) {
            enterDataError("QIO automation configuration revision overflow");
            return;
        }
        configurationRevision++;
        markDirty();
        if (tile != null) {
            QIOAutomationDeviceRegistry.INSTANCE.trackPending(this);
        }
    }

    void enterDataError(String reason) {
        dataError = reason == null || reason.isEmpty() ? "unknown automation host data error" :
              reason.substring(0, Math.min(512, reason.length()));
        state = State.DATA_ERROR;
        markDirty();
    }

    private void markDirty() {
        if (clearModeWhenDrained && !hasUnsettledOperations()) {
            completePendingModeClearIfReady();
            return;
        }
        if (tile != null) {
            tile.markDirty();
        }
    }

    private void wakeExecution() {
        if (frequencyReference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDevice(
                  frequencyReference.getFrequencyUUID(), persistentDeviceUUID);
        }
    }

    private static UUID parseUUID(String value, String key) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical UUID in " + key);
        }
        return parsed;
    }

    private static final class ParsedState {

        private final UUID deviceUUID;
        @Nullable
        private final QIOFrequencyReference frequency;
        @Nullable
        private final QIOAutomationMode enabledMode;
        private final boolean clearModeWhenDrained;
        private final State state;
        private final long configurationRevision;
        private final Map<UUID, MachineOperationLease> leases;
        private final Map<UUID, MachineOperationToken> tokens;
        private final Map<UUID, QIOOutputBufferEntry> outputBuffers;
        @Nullable
        private final String dataError;
        @Nullable
        private final NBTTagCompound quarantinedData;

        private ParsedState(UUID deviceUUID, @Nullable QIOFrequencyReference frequency,
              @Nullable QIOAutomationMode enabledMode, boolean clearModeWhenDrained,
              State state, long configurationRevision,
              Map<UUID, MachineOperationLease> leases, Map<UUID, MachineOperationToken> tokens,
              Map<UUID, QIOOutputBufferEntry> outputBuffers, @Nullable String dataError,
              @Nullable NBTTagCompound quarantinedData) {
            this.deviceUUID = deviceUUID;
            this.frequency = frequency;
            this.enabledMode = enabledMode;
            this.clearModeWhenDrained = clearModeWhenDrained;
            this.state = state;
            this.configurationRevision = configurationRevision;
            this.leases = leases;
            this.tokens = tokens;
            this.outputBuffers = outputBuffers;
            this.dataError = dataError;
            this.quarantinedData = quarantinedData;
        }
    }
}
