package mekanism.api.processing;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.*;

/**
 * Durable identity and phase marker for one real machine operation.
 */
public final class MachineOperationToken {
    public static final int MAX_TRANSFER_RECEIPTS = 512;

    public enum State {
        ALLOCATED,
        LOADING,
        PROCESSING,
        COLLECTING,
        COMPLETED,
        CANCELLED,
        CONTAMINATED;

        public boolean isTerminal() {
            return this == COMPLETED || this == CANCELLED || this == CONTAMINATED;
        }
    }

    private final UUID operationId;
    private final String sourceId;
    private final String routeId;
    private final String recipeKey;
    private final int configurationRevision;
    private final long laneId;
    private final State state;
    private final Set<UUID> transferReceipts;

    private MachineOperationToken(UUID operationId, String sourceId, String routeId, String recipeKey,
                                  int configurationRevision, long laneId, State state, Set<UUID> transferReceipts) {
        this.operationId = Objects.requireNonNull(operationId, "Operation id cannot be null");
        this.sourceId = requireText(sourceId, "Source id");
        this.routeId = requireText(routeId, "Route id");
        this.recipeKey = requireText(recipeKey, "Recipe key");
        if (configurationRevision < 0 || laneId < MachinePort.SHARED_LANE) {
            throw new IllegalArgumentException("Invalid machine operation revision or lane");
        }
        this.configurationRevision = configurationRevision;
        this.laneId = laneId;
        this.state = Objects.requireNonNull(state, "Operation state cannot be null");
        this.transferReceipts = copyReceipts(transferReceipts);
    }

    public static MachineOperationToken allocate(UUID operationId, String sourceId, String routeId,
                                                 String recipeKey, int configurationRevision, long laneId) {
        return new MachineOperationToken(operationId, sourceId, routeId, recipeKey,
                configurationRevision, laneId, State.ALLOCATED, Collections.emptySet());
    }

    public UUID operationId() {
        return operationId;
    }

    public String sourceId() {
        return sourceId;
    }

    public String routeId() {
        return routeId;
    }

    public String recipeKey() {
        return recipeKey;
    }

    public int configurationRevision() {
        return configurationRevision;
    }

    public long laneId() {
        return laneId;
    }

    public State state() {
        return state;
    }

    public Set<UUID> transferReceipts() {
        return transferReceipts;
    }

    public boolean hasTransferReceipt(UUID transferId) {
        return transferId != null && transferReceipts.contains(transferId);
    }

    public MachineOperationToken transition(State next) {
        Objects.requireNonNull(next, "Next operation state cannot be null");
        if (next == state) return this;
        if (!canTransition(state, next)) {
            throw new IllegalStateException("Invalid machine operation transition " + state + " -> " + next);
        }
        return copy(next, transferReceipts);
    }

    public MachineOperationToken withTransferReceipt(UUID transferId) {
        Objects.requireNonNull(transferId, "Transfer id cannot be null");
        if (transferReceipts.contains(transferId)) return this;
        if (transferReceipts.size() >= MAX_TRANSFER_RECEIPTS) {
            return copy(State.CONTAMINATED, transferReceipts);
        }
        Set<UUID> receipts = new LinkedHashSet<>(transferReceipts);
        receipts.add(transferId);
        return copy(state, receipts);
    }

    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("operationId", operationId.toString());
        data.setString("sourceId", sourceId);
        data.setString("routeId", routeId);
        data.setString("recipeKey", recipeKey);
        data.setInteger("configurationRevision", configurationRevision);
        data.setLong("laneId", laneId);
        data.setString("state", state.name());
        NBTTagList receipts = new NBTTagList();
        transferReceipts.stream().map(UUID::toString).sorted()
                .forEach(id -> receipts.appendTag(new NBTTagString(id)));
        data.setTag("transferReceipts", receipts);
        return data;
    }

    public static MachineOperationToken read(NBTTagCompound data) {
        Objects.requireNonNull(data, "Operation token data cannot be null");
        try {
            NBTTagList stored = data.getTagList("transferReceipts", NBT.TAG_STRING);
            if (stored.tagCount() > MAX_TRANSFER_RECEIPTS) {
                throw new IllegalArgumentException("Too many operation transfer receipts");
            }
            Set<UUID> receipts = new LinkedHashSet<>();
            for (int index = 0; index < stored.tagCount(); index++) {
                UUID receipt = parseUUID(stored.getStringTagAt(index), "transfer receipt");
                if (!receipts.add(receipt)) throw new IllegalArgumentException("Duplicate transfer receipt");
            }
            return new MachineOperationToken(parseUUID(data.getString("operationId"), "operationId"),
                    data.getString("sourceId"), data.getString("routeId"), data.getString("recipeKey"),
                    data.getInteger("configurationRevision"), data.getLong("laneId"),
                    State.valueOf(data.getString("state")), receipts);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid machine operation token", error);
        }
    }

    private MachineOperationToken copy(State next, Set<UUID> receipts) {
        return new MachineOperationToken(operationId, sourceId, routeId, recipeKey,
                configurationRevision, laneId, next, receipts);
    }

    private static boolean canTransition(State current, State next) {
        switch (current) {
            case ALLOCATED:
                return next == State.LOADING || next == State.CANCELLED || next == State.CONTAMINATED;
            case LOADING:
                return next == State.PROCESSING || next == State.CANCELLED || next == State.CONTAMINATED;
            case PROCESSING:
                return next == State.COLLECTING || next == State.CANCELLED || next == State.CONTAMINATED;
            case COLLECTING:
                return next == State.COMPLETED || next == State.CONTAMINATED;
            default:
                return false;
        }
    }

    private static Set<UUID> copyReceipts(Set<UUID> values) {
        Objects.requireNonNull(values, "Transfer receipts cannot be null");
        if (values.size() > MAX_TRANSFER_RECEIPTS) throw new IllegalArgumentException("Too many transfer receipts");
        List<UUID> sorted = new ArrayList<>(values.size());
        for (UUID value : values) sorted.add(Objects.requireNonNull(value, "Transfer receipt cannot be null"));
        Collections.sort(sorted);
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.trim().isEmpty() || value.length() > 512)
            throw new IllegalArgumentException(name + " has invalid length");
        return value;
    }

    private static UUID parseUUID(String value, String field) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) throw new IllegalArgumentException("Non-canonical UUID in " + field);
        return parsed;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof MachineOperationToken)) return false;
        MachineOperationToken other = (MachineOperationToken) value;
        return configurationRevision == other.configurationRevision && laneId == other.laneId
                && operationId.equals(other.operationId) && sourceId.equals(other.sourceId)
                && routeId.equals(other.routeId) && recipeKey.equals(other.recipeKey)
                && state == other.state && transferReceipts.equals(other.transferReceipts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId, sourceId, routeId, recipeKey, configurationRevision,
                laneId, state, transferReceipts);
    }
}

