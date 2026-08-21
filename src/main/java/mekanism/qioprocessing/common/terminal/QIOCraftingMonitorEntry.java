package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Bounded read-only row for jobs, passive production, and automatic output. */
/**
 * QIO 处理模块中的 QIOCraftingMonitorEntry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingMonitorEntry {

    public enum Kind {
        JOB,
        PASSIVE_OPERATION,
        AUTOMATIC_OUTPUT
    }

    private final Kind kind;
    private final UUID entryId;
    private final String source;
    private final String state;
    @Nullable
    private final PortableResourceDescriptor rootResource;
    private final long rootAmount;
    private final long deliveredAmount;
    private final int planRevision;
    private final long runtimeRevision;
    private final long basePriority;
    private final long totalOperations;
    private final long completedOperations;
    private final int activeOperations;
    private final int missingResourceTypes;
    private final boolean ownsExecutionSlot;
    @Nullable
    private final UUID deviceUUID;
    private final long laneId;
    private final String routeId;
    private final String diagnostic;

    public QIOCraftingMonitorEntry(@Nonnull Kind kind, @Nonnull UUID entryId,
          @Nonnull String source, @Nonnull String state,
          @Nullable PortableResourceDescriptor rootResource, long rootAmount,
          long deliveredAmount, int planRevision, long runtimeRevision,
          long basePriority, long totalOperations, long completedOperations, int activeOperations,
          int missingResourceTypes, boolean ownsExecutionSlot,
          @Nullable UUID deviceUUID, long laneId, @Nullable String routeId,
          @Nullable String diagnostic) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.source = bounded(source, 64);
        this.state = bounded(state, 64);
        this.rootResource = rootResource;
        this.rootAmount = nonNegative(rootAmount, "rootAmount");
        this.deliveredAmount = nonNegative(deliveredAmount, "deliveredAmount");
        if (planRevision < 0 || deliveredAmount > rootAmount) {
            throw new IllegalArgumentException("Invalid monitor plan progress");
        }
        this.planRevision = planRevision;
        this.runtimeRevision = nonNegative(runtimeRevision, "runtimeRevision");
        this.basePriority = basePriority;
        this.totalOperations = nonNegative(totalOperations, "totalOperations");
        this.completedOperations = nonNegative(completedOperations, "completedOperations");
        if (completedOperations > totalOperations || activeOperations < 0 ||
            missingResourceTypes < 0 || laneId < -1) {
            throw new IllegalArgumentException("Invalid monitor operation progress");
        }
        this.activeOperations = activeOperations;
        this.missingResourceTypes = missingResourceTypes;
        this.ownsExecutionSlot = ownsExecutionSlot;
        this.deviceUUID = deviceUUID;
        this.laneId = laneId;
        this.routeId = bounded(routeId, 512);
        this.diagnostic = bounded(diagnostic, 512);
        validateShape();
    }

    @Nonnull public Kind getKind() { return kind; }
    @Nonnull public UUID getEntryId() { return entryId; }
    @Nonnull public String getSource() { return source; }
    @Nonnull public String getState() { return state; }
    @Nullable public PortableResourceDescriptor getRootResource() { return rootResource; }
    public long getRootAmount() { return rootAmount; }
    public long getDeliveredAmount() { return deliveredAmount; }
    public int getPlanRevision() { return planRevision; }
    public long getRuntimeRevision() { return runtimeRevision; }
    public long getBasePriority() { return basePriority; }
    public long getTotalOperations() { return totalOperations; }
    public long getCompletedOperations() { return completedOperations; }
    public int getActiveOperations() { return activeOperations; }
    public int getMissingResourceTypes() { return missingResourceTypes; }
    public boolean ownsExecutionSlot() { return ownsExecutionSlot; }
    @Nullable public UUID getDeviceUUID() { return deviceUUID; }
    public long getLaneId() { return laneId; }
    @Nonnull public String getRouteId() { return routeId; }
    @Nonnull public String getDiagnostic() { return diagnostic; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", kind.name());
        QIOProcessingNbt.writeUUID(data, "entryId", entryId);
        data.setString("source", source);
        data.setString("state", state);
        if (rootResource != null) {
            data.setTag("rootResource", rootResource.write());
        }
        data.setLong("rootAmount", rootAmount);
        data.setLong("deliveredAmount", deliveredAmount);
        data.setInteger("planRevision", planRevision);
        data.setLong("runtimeRevision", runtimeRevision);
        data.setLong("basePriority", basePriority);
        data.setLong("totalOperations", totalOperations);
        data.setLong("completedOperations", completedOperations);
        data.setInteger("activeOperations", activeOperations);
        data.setInteger("missingResourceTypes", missingResourceTypes);
        data.setBoolean("ownsExecutionSlot", ownsExecutionSlot);
        if (deviceUUID != null) {
            QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
            data.setLong("laneId", laneId);
        }
        if (!routeId.isEmpty()) data.setString("routeId", routeId);
        if (!diagnostic.isEmpty()) data.setString("diagnostic", diagnostic);
        return data;
    }

    @Nonnull
    public static QIOCraftingMonitorEntry read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            return new QIOCraftingMonitorEntry(
                  QIOProcessingNbt.readEnum(data, "kind", Kind.class),
                  QIOProcessingNbt.readUUID(data, "entryId"),
                  data.getString("source"), data.getString("state"),
                  data.hasKey("rootResource", NBT.TAG_COMPOUND) ?
                        PortableResourceDescriptor.read(data.getCompoundTag("rootResource")) : null,
                  data.getLong("rootAmount"), data.getLong("deliveredAmount"),
                  data.getInteger("planRevision"), data.getLong("runtimeRevision"),
                  data.getLong("basePriority"),
                  data.getLong("totalOperations"), data.getLong("completedOperations"),
                  data.getInteger("activeOperations"), data.getInteger("missingResourceTypes"),
                  data.getBoolean("ownsExecutionSlot"),
                  data.hasKey("deviceUUID", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readUUID(data, "deviceUUID") : null,
                  data.hasKey("deviceUUID", NBT.TAG_STRING) ? data.getLong("laneId") : -1,
                  data.getString("routeId"), data.getString("diagnostic"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid crafting monitor entry", e);
        }
    }

    private void validateShape() {
        if ((kind == Kind.JOB) != (rootResource != null) ||
            kind == Kind.JOB && (deviceUUID != null || laneId != -1) ||
            kind != Kind.JOB && deviceUUID == null) {
            throw new IllegalArgumentException("Monitor entry identity does not match its kind");
        }
    }

    private static long nonNegative(long value, String name) {
        return QIOProcessingNbt.requireNonNegative(value, name);
    }

    private static String bounded(@Nullable String value, int maximum) {
        String checked = value == null ? "" : value.trim();
        return checked.substring(0, Math.min(maximum, checked.length()));
    }
}
