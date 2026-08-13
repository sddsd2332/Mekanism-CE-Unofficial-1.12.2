package mekanism.qioprocessing.common.content.policy;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Bounded immutable policy row used by management paging and conflict responses. */
public final class QIOPolicyEntrySnapshot {

    public enum Kind {
        GLOBAL_DEFAULT,
        GLOBAL_ROUTE,
        DEVICE_DEFAULT,
        DEVICE_ROUTE
    }

    private final long policyRevision;
    private final Kind kind;
    @Nullable
    private final UUID deviceUUID;
    @Nullable
    private final QIOPolicyCatalog.RouteIdentity route;
    private final QIOPolicyCatalog.Toggle configuredToggle;
    private final boolean hasConfiguredRoutePriority;
    private final long configuredRoutePriority;
    private final boolean hasConfiguredPassivePriority;
    private final long configuredPassivePriority;
    private final long machinePriority;
    private final long passivePriority;
    private final boolean hasEffectiveRoutePolicy;
    private final boolean effectiveEnabled;
    private final long effectiveRoutePriority;
    private final long effectivePassivePriority;
    @Nullable
    private final QIOPolicyCatalog.Source toggleSource;
    @Nullable
    private final QIOPolicyCatalog.Source prioritySource;
    @Nullable
    private final QIOPolicyCatalog.Source passivePrioritySource;

    private QIOPolicyEntrySnapshot(long policyRevision, Kind kind,
          @Nullable UUID deviceUUID, @Nullable QIOPolicyCatalog.RouteIdentity route,
          QIOPolicyCatalog.Toggle configuredToggle,
          boolean hasConfiguredRoutePriority, long configuredRoutePriority,
          boolean hasConfiguredPassivePriority, long configuredPassivePriority,
          long machinePriority, long passivePriority,
          boolean hasEffectiveRoutePolicy, boolean effectiveEnabled,
          long effectiveRoutePriority, long effectivePassivePriority,
          @Nullable QIOPolicyCatalog.Source toggleSource,
          @Nullable QIOPolicyCatalog.Source prioritySource,
          @Nullable QIOPolicyCatalog.Source passivePrioritySource) {
        this.policyRevision = QIOProcessingNbt.requireNonNegative(policyRevision,
              "policyRevision");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.deviceUUID = deviceUUID;
        this.route = route;
        this.configuredToggle = Objects.requireNonNull(configuredToggle,
              "configuredToggle");
        this.hasConfiguredRoutePriority = hasConfiguredRoutePriority;
        this.configuredRoutePriority = hasConfiguredRoutePriority ?
              configuredRoutePriority : 0;
        this.hasConfiguredPassivePriority = hasConfiguredPassivePriority;
        this.configuredPassivePriority = hasConfiguredPassivePriority ?
              configuredPassivePriority : 0;
        this.machinePriority = machinePriority;
        this.passivePriority = passivePriority;
        this.hasEffectiveRoutePolicy = hasEffectiveRoutePolicy;
        this.effectiveEnabled = effectiveEnabled;
        this.effectiveRoutePriority = hasEffectiveRoutePolicy ?
              effectiveRoutePriority : 0;
        this.effectivePassivePriority = hasEffectiveRoutePolicy ?
              effectivePassivePriority : 0;
        this.toggleSource = toggleSource;
        this.prioritySource = prioritySource;
        this.passivePrioritySource = passivePrioritySource;
        validateShape();
    }

    @Nonnull
    static QIOPolicyEntrySnapshot globalDefault(long revision,
          QIOPolicyCatalog.Toggle toggle, long routePriority,
          long passivePriority) {
        return new QIOPolicyEntrySnapshot(revision, Kind.GLOBAL_DEFAULT, null, null,
              toggle, true, routePriority, true, passivePriority, 0, 0, true,
              toggle != QIOPolicyCatalog.Toggle.DISABLED, routePriority,
              passivePriority,
              QIOPolicyCatalog.Source.GLOBAL_DEFAULT,
              QIOPolicyCatalog.Source.GLOBAL_DEFAULT,
              QIOPolicyCatalog.Source.GLOBAL_DEFAULT);
    }

    @Nonnull
    static QIOPolicyEntrySnapshot globalRoute(long revision, String providerId,
          String routeId, String recipeKey, QIOPolicyCatalog.Toggle toggle,
          @Nullable Long routePriority, @Nullable Long passivePriority,
          boolean effectiveEnabled, long effectiveRoutePriority,
          long effectivePassivePriority, QIOPolicyCatalog.Source toggleSource,
          QIOPolicyCatalog.Source prioritySource,
          QIOPolicyCatalog.Source passivePrioritySource) {
        return new QIOPolicyEntrySnapshot(revision, Kind.GLOBAL_ROUTE, null,
              QIOPolicyCatalog.RouteIdentity.create(providerId, routeId, recipeKey),
              toggle, routePriority != null, routePriority == null ? 0 : routePriority,
              passivePriority != null, passivePriority == null ? 0 : passivePriority,
              0, 0, true, effectiveEnabled, effectiveRoutePriority,
              effectivePassivePriority, toggleSource, prioritySource,
              passivePrioritySource);
    }

    @Nonnull
    static QIOPolicyEntrySnapshot deviceDefault(long revision, UUID deviceUUID,
          QIOPolicyCatalog.Toggle toggle, long machinePriority, long passivePriority,
          boolean effectiveEnabled, QIOPolicyCatalog.Source toggleSource) {
        return new QIOPolicyEntrySnapshot(revision, Kind.DEVICE_DEFAULT, deviceUUID,
              null, toggle, false, 0, true, passivePriority, machinePriority,
              passivePriority, false, effectiveEnabled, 0, 0, toggleSource, null, null);
    }

    @Nonnull
    static QIOPolicyEntrySnapshot deviceRoute(long revision, UUID deviceUUID,
          String providerId, String routeId, String recipeKey,
          QIOPolicyCatalog.Toggle toggle, @Nullable Long routePriority,
          @Nullable Long passivePriority, boolean effectiveEnabled,
          long effectiveRoutePriority, long effectivePassivePriority,
          QIOPolicyCatalog.Source toggleSource,
          QIOPolicyCatalog.Source prioritySource,
          QIOPolicyCatalog.Source passivePrioritySource) {
        return new QIOPolicyEntrySnapshot(revision, Kind.DEVICE_ROUTE, deviceUUID,
              QIOPolicyCatalog.RouteIdentity.create(providerId, routeId, recipeKey),
              toggle, routePriority != null, routePriority == null ? 0 : routePriority,
              passivePriority != null, passivePriority == null ? 0 : passivePriority,
              0, 0, true, effectiveEnabled, effectiveRoutePriority,
              effectivePassivePriority, toggleSource, prioritySource,
              passivePrioritySource);
    }

    public long getPolicyRevision() {
        return policyRevision;
    }

    @Nonnull
    public Kind getKind() {
        return kind;
    }

    @Nullable
    public UUID getDeviceUUID() {
        return deviceUUID;
    }

    @Nullable
    public QIOPolicyCatalog.RouteIdentity getRoute() {
        return route;
    }

    @Nonnull
    public QIOPolicyCatalog.Toggle getConfiguredToggle() {
        return configuredToggle;
    }

    @Nullable
    public Long getConfiguredRoutePriority() {
        return hasConfiguredRoutePriority ? configuredRoutePriority : null;
    }

    @Nullable
    public Long getConfiguredPassivePriority() {
        return hasConfiguredPassivePriority ? configuredPassivePriority : null;
    }

    public long getMachinePriority() {
        return machinePriority;
    }

    public long getPassivePriority() {
        return passivePriority;
    }

    public boolean hasEffectiveRoutePolicy() {
        return hasEffectiveRoutePolicy;
    }

    public boolean isEffectiveEnabled() {
        return effectiveEnabled;
    }

    public long getEffectiveRoutePriority() {
        return effectiveRoutePriority;
    }

    public long getEffectivePassivePriority() {
        return effectivePassivePriority;
    }

    @Nullable
    public QIOPolicyCatalog.Source getToggleSource() {
        return toggleSource;
    }

    @Nullable
    public QIOPolicyCatalog.Source getPrioritySource() {
        return prioritySource;
    }

    @Nullable
    public QIOPolicyCatalog.Source getPassivePrioritySource() {
        return passivePrioritySource;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("policyRevision", policyRevision);
        data.setString("kind", kind.name());
        if (deviceUUID != null) {
            QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        }
        if (route != null) {
            data.setString("providerId", route.getProviderId());
            data.setString("routeId", route.getRouteId());
            data.setString("recipeKey", route.getRecipeKey());
        }
        data.setString("configuredToggle", configuredToggle.name());
        data.setBoolean("hasConfiguredRoutePriority", hasConfiguredRoutePriority);
        if (hasConfiguredRoutePriority) {
            data.setLong("configuredRoutePriority", configuredRoutePriority);
        }
        data.setBoolean("hasConfiguredPassivePriority", hasConfiguredPassivePriority);
        if (hasConfiguredPassivePriority) {
            data.setLong("configuredPassivePriority", configuredPassivePriority);
        }
        if (kind == Kind.DEVICE_DEFAULT) {
            data.setLong("machinePriority", machinePriority);
            data.setLong("passivePriority", passivePriority);
        }
        data.setBoolean("hasEffectiveRoutePolicy", hasEffectiveRoutePolicy);
        data.setBoolean("effectiveEnabled", effectiveEnabled);
        if (hasEffectiveRoutePolicy) {
            data.setLong("effectiveRoutePriority", effectiveRoutePriority);
            data.setLong("effectivePassivePriority", effectivePassivePriority);
            data.setString("toggleSource", toggleSource.name());
            data.setString("prioritySource", prioritySource.name());
            data.setString("passivePrioritySource", passivePrioritySource.name());
        } else if (toggleSource != null) {
            data.setString("toggleSource", toggleSource.name());
        }
        return data;
    }

    @Nonnull
    public static QIOPolicyEntrySnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            Kind kind = QIOProcessingNbt.readEnum(data, "kind", Kind.class);
            UUID deviceUUID = data.hasKey("deviceUUID", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readUUID(data, "deviceUUID") : null;
            QIOPolicyCatalog.RouteIdentity route = data.hasKey("providerId", NBT.TAG_STRING) ||
                  data.hasKey("routeId", NBT.TAG_STRING) ||
                  data.hasKey("recipeKey", NBT.TAG_STRING) ?
                  QIOPolicyCatalog.RouteIdentity.create(data.getString("providerId"),
                        data.getString("routeId"), data.getString("recipeKey")) : null;
            QIOPolicyCatalog.Toggle toggle = QIOProcessingNbt.readEnum(data,
                  "configuredToggle", QIOPolicyCatalog.Toggle.class);
            boolean hasPriority = data.getBoolean("hasConfiguredRoutePriority");
            boolean hasPassivePriority = data.getBoolean("hasConfiguredPassivePriority");
            boolean hasEffective = data.getBoolean("hasEffectiveRoutePolicy");
            QIOPolicyCatalog.Source toggleSource = data.hasKey("toggleSource", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readEnum(data, "toggleSource",
                        QIOPolicyCatalog.Source.class) : null;
            QIOPolicyCatalog.Source prioritySource =
                  data.hasKey("prioritySource", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readEnum(data, "prioritySource",
                              QIOPolicyCatalog.Source.class) : null;
            QIOPolicyCatalog.Source passivePrioritySource =
                  data.hasKey("passivePrioritySource", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readEnum(data, "passivePrioritySource",
                              QIOPolicyCatalog.Source.class) : null;
            return new QIOPolicyEntrySnapshot(data.getLong("policyRevision"), kind,
                  deviceUUID, route, toggle, hasPriority,
                  hasPriority ? data.getLong("configuredRoutePriority") : 0,
                  hasPassivePriority,
                  hasPassivePriority ? data.getLong("configuredPassivePriority") : 0,
                  data.getLong("machinePriority"), data.getLong("passivePriority"),
                  hasEffective, data.getBoolean("effectiveEnabled"),
                  hasEffective ? data.getLong("effectiveRoutePriority") : 0,
                  hasEffective ? data.getLong("effectivePassivePriority") : 0,
                  toggleSource, prioritySource, passivePrioritySource);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO policy entry snapshot", e);
        }
    }

    private void validateShape() {
        boolean deviceKind = kind == Kind.DEVICE_DEFAULT || kind == Kind.DEVICE_ROUTE;
        boolean routeKind = kind == Kind.GLOBAL_ROUTE || kind == Kind.DEVICE_ROUTE;
        if (deviceKind != (deviceUUID != null) || routeKind != (route != null)) {
            throw new IllegalArgumentException("QIO policy entry identity does not match its kind");
        }
        if (kind == Kind.GLOBAL_DEFAULT &&
            configuredToggle == QIOPolicyCatalog.Toggle.INHERIT) {
            throw new IllegalArgumentException("Global default policy cannot inherit");
        }
        if (kind == Kind.GLOBAL_DEFAULT && !hasConfiguredRoutePriority ||
            kind == Kind.DEVICE_DEFAULT && hasConfiguredRoutePriority) {
            throw new IllegalArgumentException("QIO policy priority does not match its kind");
        }
        if (kind == Kind.GLOBAL_DEFAULT && !hasConfiguredPassivePriority) {
            throw new IllegalArgumentException("Global default needs passive priority");
        }
        if (kind != Kind.DEVICE_DEFAULT &&
            (machinePriority != 0 || passivePriority != 0)) {
            throw new IllegalArgumentException("Only device defaults have machine priorities");
        }
        if (hasEffectiveRoutePolicy != (toggleSource != null && prioritySource != null)) {
            throw new IllegalArgumentException("Incomplete effective QIO route policy source");
        }
        if (hasEffectiveRoutePolicy != (passivePrioritySource != null)) {
            throw new IllegalArgumentException("Incomplete effective passive priority source");
        }
        if (!hasEffectiveRoutePolicy && kind != Kind.DEVICE_DEFAULT) {
            throw new IllegalArgumentException("Route policies require effective values");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof QIOPolicyEntrySnapshot other)) {
            return false;
        }
        return policyRevision == other.policyRevision && kind == other.kind &&
              Objects.equals(deviceUUID, other.deviceUUID) &&
              Objects.equals(route, other.route) &&
              configuredToggle == other.configuredToggle &&
              hasConfiguredRoutePriority == other.hasConfiguredRoutePriority &&
              configuredRoutePriority == other.configuredRoutePriority &&
              hasConfiguredPassivePriority == other.hasConfiguredPassivePriority &&
              configuredPassivePriority == other.configuredPassivePriority &&
              machinePriority == other.machinePriority &&
              passivePriority == other.passivePriority &&
              hasEffectiveRoutePolicy == other.hasEffectiveRoutePolicy &&
              effectiveEnabled == other.effectiveEnabled &&
              effectiveRoutePriority == other.effectiveRoutePriority &&
              effectivePassivePriority == other.effectivePassivePriority &&
              toggleSource == other.toggleSource && prioritySource == other.prioritySource &&
              passivePrioritySource == other.passivePrioritySource;
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyRevision, kind, deviceUUID, route, configuredToggle,
              hasConfiguredRoutePriority, configuredRoutePriority,
              hasConfiguredPassivePriority, configuredPassivePriority, machinePriority,
              passivePriority, hasEffectiveRoutePolicy, effectiveEnabled,
              effectiveRoutePriority, effectivePassivePriority, toggleSource,
              prioritySource, passivePrioritySource);
    }
}
