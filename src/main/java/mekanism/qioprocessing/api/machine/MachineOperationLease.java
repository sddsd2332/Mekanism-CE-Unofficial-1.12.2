package mekanism.qioprocessing.api.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persistent exclusive ownership of one machine lane and its shared port groups. */
public final class MachineOperationLease {

    public static final int MAX_BASELINES = 256;
    private static final String LEASE_ID = "leaseId";
    private static final String OWNER_OPERATION_ID = "ownerOperationId";
    private static final String MODE = "mode";
    private static final String LANE_ID = "laneId";
    private static final String CREATED_AT = "createdAt";
    private static final String STATE = "state";
    private static final String BASELINES = "baselines";
    private static final String CONTAMINATION_REASON = "contaminationReason";

    public enum Mode {
        PROCESSING_EXCLUSIVE,
        OUTPUT_DRAIN
    }

    public enum State {
        ACQUIRED,
        LOADING,
        ACTIVE,
        COLLECTING,
        COMPLETED,
        RELEASED,
        CONTAMINATED;

        public boolean isTerminal() {
            return this == RELEASED || this == CONTAMINATED;
        }
    }

    private final UUID leaseId;
    private final UUID ownerOperationId;
    private final Mode mode;
    private final long laneId;
    private final long createdAt;
    private final State state;
    private final List<MachinePortBaseline> baselines;
    @Nullable
    private final String contaminationReason;

    private MachineOperationLease(UUID leaseId, UUID ownerOperationId, Mode mode, long laneId, long createdAt,
          State state, Collection<MachinePortBaseline> baselines, @Nullable String contaminationReason) {
        this.leaseId = Objects.requireNonNull(leaseId, "Lease id cannot be null");
        this.ownerOperationId = Objects.requireNonNull(ownerOperationId, "Owner operation id cannot be null");
        this.mode = Objects.requireNonNull(mode, "Lease mode cannot be null");
        if (laneId < 0) {
            throw new IllegalArgumentException("Lease lane id cannot be negative");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("Lease creation tick cannot be negative");
        }
        this.laneId = laneId;
        this.createdAt = createdAt;
        this.state = Objects.requireNonNull(state, "Lease state cannot be null");
        this.baselines = copyBaselines(baselines);
        if (state == State.CONTAMINATED && (contaminationReason == null || contaminationReason.isEmpty())) {
            throw new IllegalArgumentException("A contaminated lease requires a diagnostic reason");
        }
        this.contaminationReason = contaminationReason;
    }

    @Nonnull
    public static MachineOperationLease acquire(@Nonnull UUID leaseId, @Nonnull UUID ownerOperationId,
          @Nonnull Mode mode, long laneId, long createdAt, @Nonnull Collection<MachinePortBaseline> baselines) {
        return new MachineOperationLease(leaseId, ownerOperationId, mode, laneId, createdAt, State.ACQUIRED,
              baselines, null);
    }

    @Nonnull
    public UUID leaseId() {
        return leaseId;
    }

    @Nonnull
    public UUID ownerOperationId() {
        return ownerOperationId;
    }

    @Nonnull
    public Mode mode() {
        return mode;
    }

    public long laneId() {
        return laneId;
    }

    public long createdAt() {
        return createdAt;
    }

    @Nonnull
    public State state() {
        return state;
    }

    @Nonnull
    public List<MachinePortBaseline> baselines() {
        return baselines;
    }

    @Nonnull
    public Set<String> portGroupIds() {
        Set<String> groups = new HashSet<>();
        for (MachinePortBaseline baseline : baselines) {
            groups.add(baseline.portGroupId());
        }
        return Collections.unmodifiableSet(groups);
    }

    @Nullable
    public String contaminationReason() {
        return contaminationReason;
    }

    @Nonnull
    public MachineOperationLease transition(@Nonnull State next) {
        Objects.requireNonNull(next, "Next lease state cannot be null");
        if (next == state) {
            return this;
        }
        if (!canTransition(state, next)) {
            throw new IllegalStateException("Invalid machine lease transition " + state + " -> " + next);
        }
        return new MachineOperationLease(leaseId, ownerOperationId, mode, laneId, createdAt, next, baselines,
              next == State.CONTAMINATED ? "unspecified contamination" : contaminationReason);
    }

    @Nonnull
    public MachineOperationLease contaminate(@Nonnull String reason) {
        Objects.requireNonNull(reason, "Contamination reason cannot be null");
        if (reason.isEmpty() || reason.length() > 512) {
            throw new IllegalArgumentException("Contamination reason must contain 1..512 characters");
        }
        if (state == State.RELEASED) {
            throw new IllegalStateException("A released lease cannot become contaminated");
        }
        return new MachineOperationLease(leaseId, ownerOperationId, mode, laneId, createdAt, State.CONTAMINATED,
              baselines, reason);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(LEASE_ID, leaseId.toString());
        data.setString(OWNER_OPERATION_ID, ownerOperationId.toString());
        data.setString(MODE, mode.name());
        data.setLong(LANE_ID, laneId);
        data.setLong(CREATED_AT, createdAt);
        data.setString(STATE, state.name());
        NBTTagList baselineList = new NBTTagList();
        for (MachinePortBaseline baseline : baselines) {
            baselineList.appendTag(baseline.write());
        }
        data.setTag(BASELINES, baselineList);
        if (contaminationReason != null) {
            data.setString(CONTAMINATION_REASON, contaminationReason);
        }
        return data;
    }

    @Nonnull
    public static MachineOperationLease read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "Lease data cannot be null");
        try {
            NBTTagList list = data.getTagList(BASELINES, NBT.TAG_COMPOUND);
            if (list.tagCount() == 0 || list.tagCount() > MAX_BASELINES) {
                throw new IllegalArgumentException("Lease baseline count is outside 1.." + MAX_BASELINES);
            }
            List<MachinePortBaseline> baselines = new ArrayList<>(list.tagCount());
            for (int index = 0; index < list.tagCount(); index++) {
                baselines.add(MachinePortBaseline.read(list.getCompoundTagAt(index)));
            }
            return new MachineOperationLease(readUUID(data, LEASE_ID), readUUID(data, OWNER_OPERATION_ID),
                  Mode.valueOf(data.getString(MODE)), data.getLong(LANE_ID), data.getLong(CREATED_AT),
                  State.valueOf(data.getString(STATE)), baselines,
                  data.hasKey(CONTAMINATION_REASON, NBT.TAG_STRING) ? data.getString(CONTAMINATION_REASON) : null);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid machine operation lease", e);
        }
    }

    private static boolean canTransition(State current, State next) {
        return switch (current) {
            case ACQUIRED -> next == State.LOADING || next == State.COLLECTING || next == State.RELEASED ||
                             next == State.CONTAMINATED;
            case LOADING -> next == State.ACTIVE || next == State.RELEASED || next == State.CONTAMINATED;
            case ACTIVE -> next == State.COLLECTING || next == State.RELEASED ||
                           next == State.CONTAMINATED;
            case COLLECTING -> next == State.COMPLETED || next == State.CONTAMINATED;
            case COMPLETED, CONTAMINATED -> next == State.RELEASED;
            case RELEASED -> false;
        };
    }

    private static List<MachinePortBaseline> copyBaselines(Collection<MachinePortBaseline> values) {
        Objects.requireNonNull(values, "Lease baselines cannot be null");
        if (values.isEmpty() || values.size() > MAX_BASELINES) {
            throw new IllegalArgumentException("Lease baseline count must be within 1.." + MAX_BASELINES);
        }
        List<MachinePortBaseline> copy = new ArrayList<>(values.size());
        Set<String> ports = new HashSet<>();
        for (MachinePortBaseline baseline : values) {
            MachinePortBaseline value = Objects.requireNonNull(baseline, "Lease baseline cannot be null");
            if (!ports.add(value.portId())) {
                throw new IllegalArgumentException("Duplicate lease baseline port " + value.portId());
            }
            copy.add(value);
        }
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    private static UUID readUUID(NBTTagCompound data, String key) {
        String value = data.getString(key);
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical UUID in " + key);
        }
        return parsed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MachineOperationLease other)) {
            return false;
        }
        return laneId == other.laneId && createdAt == other.createdAt && leaseId.equals(other.leaseId) &&
              ownerOperationId.equals(other.ownerOperationId) && mode == other.mode && state == other.state &&
              baselines.equals(other.baselines) && Objects.equals(contaminationReason, other.contaminationReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leaseId, ownerOperationId, mode, laneId, createdAt, state, baselines, contaminationReason);
    }
}
