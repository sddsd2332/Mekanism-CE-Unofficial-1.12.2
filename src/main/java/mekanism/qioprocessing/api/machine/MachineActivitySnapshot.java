package mekanism.qioprocessing.api.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Immutable server-authoritative progress sample for one machine lane. */
public final class MachineActivitySnapshot {

    private static final String LANE_ID = "laneId";
    private static final String OPERATION_ID = "operationId";
    private static final String RECIPE_KEY = "recipeKey";
    private static final String STATE = "state";
    private static final String CURRENT_TICK = "currentTick";
    private static final String TOTAL_TICKS = "totalTicks";
    private static final String SAMPLED_AT = "sampledAt";
    private static final String BLOCKED_REASON = "blockedReason";

    public enum State {
        IDLE,
        LOADING,
        RUNNING,
        COLLECTING,
        OUTPUT_BLOCKED,
        COMPLETED,
        CONTAMINATED,
        UNAVAILABLE
    }

    private final long laneId;
    @Nullable
    private final UUID operationId;
    private final String recipeKey;
    private final State state;
    private final long currentTick;
    private final long totalTicks;
    private final long sampledAt;
    @Nullable
    private final String blockedReason;

    public MachineActivitySnapshot(long laneId, @Nullable UUID operationId, @Nonnull String recipeKey,
          @Nonnull State state, long currentTick, long totalTicks, long sampledAt, @Nullable String blockedReason) {
        if (laneId < 0 || currentTick < 0 || totalTicks < 0 || sampledAt < 0 || currentTick > totalTicks && totalTicks > 0) {
            throw new IllegalArgumentException("Invalid machine activity counters");
        }
        this.laneId = laneId;
        this.operationId = operationId;
        this.recipeKey = Objects.requireNonNull(recipeKey, "Recipe key cannot be null");
        if (recipeKey.length() > 512) {
            throw new IllegalArgumentException("Recipe key is too long");
        }
        this.state = Objects.requireNonNull(state, "Activity state cannot be null");
        if (state == State.IDLE && operationId != null || state != State.IDLE && state != State.UNAVAILABLE && operationId == null) {
            throw new IllegalArgumentException("Activity state and operation identity disagree");
        }
        if (blockedReason != null && (blockedReason.isEmpty() || blockedReason.length() > 512)) {
            throw new IllegalArgumentException("Blocked reason has an invalid length");
        }
        this.currentTick = currentTick;
        this.totalTicks = totalTicks;
        this.sampledAt = sampledAt;
        this.blockedReason = blockedReason;
    }

    public long laneId() {
        return laneId;
    }

    @Nullable
    public UUID operationId() {
        return operationId;
    }

    @Nonnull
    public String recipeKey() {
        return recipeKey;
    }

    @Nonnull
    public State state() {
        return state;
    }

    public long currentTick() {
        return currentTick;
    }

    public long totalTicks() {
        return totalTicks;
    }

    public long sampledAt() {
        return sampledAt;
    }

    @Nullable
    public String blockedReason() {
        return blockedReason;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong(LANE_ID, laneId);
        if (operationId != null) {
            data.setString(OPERATION_ID, operationId.toString());
        }
        data.setString(RECIPE_KEY, recipeKey);
        data.setString(STATE, state.name());
        data.setLong(CURRENT_TICK, currentTick);
        data.setLong(TOTAL_TICKS, totalTicks);
        data.setLong(SAMPLED_AT, sampledAt);
        if (blockedReason != null) {
            data.setString(BLOCKED_REASON, blockedReason);
        }
        return data;
    }

    @Nonnull
    public static MachineActivitySnapshot read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "Activity snapshot data cannot be null");
        try {
            UUID operationId = data.hasKey(OPERATION_ID, NBT.TAG_STRING) ?
                  parseUUID(data.getString(OPERATION_ID)) : null;
            return new MachineActivitySnapshot(data.getLong(LANE_ID), operationId, data.getString(RECIPE_KEY),
                  State.valueOf(data.getString(STATE)), data.getLong(CURRENT_TICK), data.getLong(TOTAL_TICKS),
                  data.getLong(SAMPLED_AT),
                  data.hasKey(BLOCKED_REASON, NBT.TAG_STRING) ? data.getString(BLOCKED_REASON) : null);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid machine activity snapshot", e);
        }
    }

    private static UUID parseUUID(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical activity operation UUID");
        }
        return parsed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MachineActivitySnapshot other)) {
            return false;
        }
        return laneId == other.laneId && currentTick == other.currentTick && totalTicks == other.totalTicks &&
              sampledAt == other.sampledAt && Objects.equals(operationId, other.operationId) &&
              recipeKey.equals(other.recipeKey) && state == other.state && Objects.equals(blockedReason, other.blockedReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(laneId, operationId, recipeKey, state, currentTick, totalTicks, sampledAt, blockedReason);
    }
}
