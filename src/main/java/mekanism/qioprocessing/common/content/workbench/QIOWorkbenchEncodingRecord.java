package mekanism.qioprocessing.common.content.workbench;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosurePreview;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable encoding intent/draft. It is saved in the same network image as configuration changes,
 * so a confirmation receipt cannot be committed separately from its imported patterns.
 * In-flight computations are never resumed using old process validation credentials.
 */
public final class QIOWorkbenchEncodingRecord {
    public enum State {QUEUED, SEARCHING, VALIDATING, WAITING_WORLD, READY, PAUSED_RESTART, CONFIRMED, CANCELLED, FAILED}

    private final UUID id;
    private final UUID owner;
    private final UUID configuration;
    private final long configurationRevision;
    private final long catalogRevision;
    private final int dimension;
    private final NBTTagCompound request;
    private final QIOWorkbenchClosureMode mode;
    private final boolean skipCycles;
    private final boolean newStore;
    private final long created;
    private State state;
    private String diagnostic = "";
    private String generation = "";
    @Nullable
    private QIOWorkbenchConfiguration draft;
    @Nullable
    private QIOWorkbenchClosurePreview preview;
    @Nullable
    private String replacingRoot;
    private boolean applied;
    private final long requestFootprint;
    private long draftFootprint;
    private mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Snapshot metrics =
            mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Snapshot.EMPTY;

    public void recordMetrics(mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Snapshot metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    public mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Snapshot getMetrics() {
        return metrics;
    }

    public QIOWorkbenchEncodingRecord(UUID id, UUID owner, UUID configuration, long configurationRevision,
                                      long catalogRevision, int dimension, NBTTagCompound request, QIOWorkbenchClosureMode mode, boolean skipCycles) {
        this(id, owner, configuration, configurationRevision, catalogRevision, dimension, request, mode, skipCycles,
                false, System.currentTimeMillis());
    }

    public QIOWorkbenchEncodingRecord(UUID id, UUID owner, UUID configuration, long configurationRevision,
                                      long catalogRevision, int dimension, NBTTagCompound request, QIOWorkbenchClosureMode mode,
                                      boolean skipCycles, boolean newStore) {
        this(id, owner, configuration, configurationRevision, catalogRevision, dimension, request, mode,
                skipCycles, newStore, System.currentTimeMillis());
    }

    private QIOWorkbenchEncodingRecord(UUID id, UUID owner, UUID configuration, long configurationRevision,
                                       long catalogRevision, int dimension, NBTTagCompound request, QIOWorkbenchClosureMode mode,
                                       boolean skipCycles, boolean newStore, long created) {
        this.id = Objects.requireNonNull(id);
        this.owner = Objects.requireNonNull(owner);
        this.configuration = Objects.requireNonNull(configuration);
        if (configurationRevision < 0 || catalogRevision < 0)
            throw new IllegalArgumentException("Negative encoding revision");
        this.configurationRevision = configurationRevision;
        this.catalogRevision = catalogRevision;
        this.dimension = dimension;
        this.request = request.copy();
        requestFootprint = mekanism.qioprocessing.common.util.QIONbtUtils.boundedSize(this.request, 1024L * 1024);
        if (requestFootprint == Long.MAX_VALUE)
            throw new IllegalArgumentException("Encoding request exceeds 1 MiB budget");
        this.mode = Objects.requireNonNull(mode);
        this.skipCycles = skipCycles;
        this.newStore = newStore;
        this.created = created;
        this.state = State.QUEUED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public UUID getConfiguration() {
        return configuration;
    }

    public long getConfigurationRevision() {
        return configurationRevision;
    }

    public long getCatalogRevision() {
        return catalogRevision;
    }

    public int getDimension() {
        return dimension;
    }

    public NBTTagCompound getRequest() {
        return request.copy();
    }

    public QIOWorkbenchClosureMode getMode() {
        return mode;
    }

    public boolean skipsCycles() {
        return skipCycles;
    }

    public boolean isNewStore() {
        return newStore;
    }

    public long getCreated() {
        return created;
    }

    public State getState() {
        return state;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public String getGeneration() {
        return generation;
    }

    @Nullable
    public QIOWorkbenchConfiguration getDraft() {
        return draft == null ? null : draft.copy();
    }

    @Nullable
    public QIOWorkbenchClosurePreview getPreview() {
        return preview;
    }

    @Nullable
    public String getReplacingRoot() {
        return replacingRoot;
    }

    public boolean wasApplied() {
        return applied;
    }

    public long getStorageFootprint() {
        return requestFootprint + draftFootprint + 64L * 1024;
    }

    public boolean isTerminal() {
        return state == State.CONFIRMED || state == State.CANCELLED || state == State.FAILED;
    }

    public void phase(State next, String diagnostic) {
        if (isTerminal()) throw new IllegalStateException("Encoding job already finished");
        state = Objects.requireNonNull(next);
        this.diagnostic = diagnostic == null ? "" : diagnostic.substring(0, Math.min(1024, diagnostic.length()));
        if (next == State.FAILED) {
            draft = null;
            draftFootprint = 0;
        }
    }

    public void ready(QIOWorkbenchConfiguration draft, QIOWorkbenchClosurePreview preview,
                      @Nullable String replacingRoot, String generation) {
        if (isTerminal()) throw new IllegalStateException("Encoding job already finished");
        if (!id.equals(preview.getConfirmationNonce())) throw new IllegalArgumentException("Wrong draft receipt");
        long footprint = mekanism.qioprocessing.common.util.QIONbtUtils.boundedSize(draft.write(), 16L * 1024 * 1024);
        if (footprint == Long.MAX_VALUE) throw new IllegalArgumentException("Encoding draft exceeds 16 MiB budget");
        this.draft = draft.copy();
        draftFootprint = footprint;
        this.preview = preview;
        this.replacingRoot = replacingRoot;
        this.generation = Objects.requireNonNull(generation);
        state = State.READY;
        diagnostic = "";
    }

    public void readyNewStore(QIOWorkbenchClosurePreview preview, String generation) {
        if (isTerminal()) throw new IllegalStateException("Encoding job already finished");
        if (!newStore) throw new IllegalStateException("Encoding job is not a new-store job");
        if (!id.equals(preview.getConfirmationNonce())) throw new IllegalArgumentException("Wrong draft receipt");
        this.draft = null;
        draftFootprint = 0;
        this.preview = Objects.requireNonNull(preview);
        this.replacingRoot = null;
        this.generation = Objects.requireNonNull(generation);
        state = State.READY;
        diagnostic = "";
    }

    public void confirmed(boolean changed) {
        if (state != State.READY) throw new IllegalStateException("Draft is not ready");
        applied = changed;
        state = State.CONFIRMED;
        // Retain the small receipt and root request, not a second large copy of imported patterns.
        draft = null;
        draftFootprint = 0;
    }

    public void cancel() {
        if (state == State.CONFIRMED) throw new IllegalStateException("Cannot cancel an imported job");
        state = State.CANCELLED;
        diagnostic = "Cancelled by owner";
        draft = null;
        draftFootprint = 0;
    }

    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", 1);
        data.setString("id", id.toString());
        data.setString("owner", owner.toString());
        data.setString("configuration", configuration.toString());
        data.setLong("configurationRevision", configurationRevision);
        data.setLong("catalogRevision", catalogRevision);
        data.setInteger("dimension", dimension);
        data.setTag("request", request.copy());
        data.setString("mode", mode.name());
        data.setBoolean("skipCycles", skipCycles);
        data.setBoolean("newStore", newStore);
        data.setLong("created", created);
        data.setString("state", state.name());
        data.setString("diagnostic", diagnostic);
        data.setTag("metrics", metrics.write());
        data.setString("generation", generation);
        if (draft != null) data.setTag("draft", draft.write());
        if (preview != null) data.setTag("preview", preview.write());
        if (replacingRoot != null) data.setString("replacingRoot", replacingRoot);
        data.setBoolean("applied", applied);
        return data;
    }

    public static QIOWorkbenchEncodingRecord read(NBTTagCompound data) throws QIOProcessingDataException {
        try {
            if (data.getInteger("schema") != 1 || !data.hasKey("request", 10))
                throw new IllegalArgumentException("Invalid encoding record");
            QIOWorkbenchEncodingRecord record = new QIOWorkbenchEncodingRecord(UUID.fromString(data.getString("id")),
                    UUID.fromString(data.getString("owner")), UUID.fromString(data.getString("configuration")),
                    data.getLong("configurationRevision"), data.getLong("catalogRevision"), data.getInteger("dimension"),
                    data.getCompoundTag("request"), QIOWorkbenchClosureMode.valueOf(data.getString("mode")),
                    data.getBoolean("skipCycles"), data.getBoolean("newStore"), data.getLong("created"));
            record.state = State.valueOf(data.getString("state"));
            record.diagnostic = data.getString("diagnostic");
            record.metrics = mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Snapshot.read(data.getCompoundTag("metrics"));
            if (record.diagnostic.length() > 1024) throw new IllegalArgumentException("Oversized encoding diagnostic");
            record.generation = data.getString("generation");
            record.draft = data.hasKey("draft", 10) ? QIOWorkbenchConfiguration.read(data.getCompoundTag("draft")) : null;
            record.draftFootprint = record.draft == null ? 0 :
                    mekanism.qioprocessing.common.util.QIONbtUtils.boundedSize(data.getCompoundTag("draft"), 16L * 1024 * 1024);
            if (record.draftFootprint == Long.MAX_VALUE) throw new IllegalArgumentException("Oversized encoding draft");
            record.preview = data.hasKey("preview", 10) ? QIOWorkbenchClosurePreview.read(data.getCompoundTag("preview")) : null;
            record.replacingRoot = data.hasKey("replacingRoot", 8) ? data.getString("replacingRoot") : null;
            record.applied = data.getBoolean("applied");
            if (record.preview != null && !record.id.equals(record.preview.getConfirmationNonce())) {
                throw new IllegalArgumentException("Encoding receipt mismatch");
            }
            if (record.state == State.READY && (record.preview == null || !record.newStore && record.draft == null)) {
                throw new IllegalArgumentException("Incomplete encoding draft");
            }
            // Even a completed draft must receive a new main-thread credential after restart.
            if (!record.isTerminal()) record.state = State.PAUSED_RESTART;
            return record;
        } catch (RuntimeException error) {
            throw new QIOProcessingDataException("Invalid workbench encoding job", error);
        }
    }
}
