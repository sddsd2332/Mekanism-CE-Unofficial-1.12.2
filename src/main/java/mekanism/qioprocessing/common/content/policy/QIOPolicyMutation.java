package mekanism.qioprocessing.common.content.policy;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** One typed, bounded central-policy edit submitted by a management terminal. */
public final class QIOPolicyMutation {

    private final QIOPolicyEntrySnapshot.Kind kind;
    @Nullable
    private final UUID deviceUUID;
    @Nullable
    private final QIOPolicyCatalog.RouteIdentity route;
    private final QIOPolicyCatalog.Toggle toggle;
    private final boolean hasRoutePriority;
    private final long routePriority;
    private final boolean hasPassivePriority;
    private final long passiveRoutePriority;
    private final long machinePriority;
    private final long passivePriority;

    private QIOPolicyMutation(QIOPolicyEntrySnapshot.Kind kind,
          @Nullable UUID deviceUUID, @Nullable QIOPolicyCatalog.RouteIdentity route,
          QIOPolicyCatalog.Toggle toggle, boolean hasRoutePriority, long routePriority,
          boolean hasPassivePriority, long passiveRoutePriority,
          long machinePriority, long passivePriority) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.deviceUUID = deviceUUID;
        this.route = route;
        this.toggle = Objects.requireNonNull(toggle, "toggle");
        this.hasRoutePriority = hasRoutePriority;
        this.routePriority = hasRoutePriority ? routePriority : 0;
        this.hasPassivePriority = hasPassivePriority;
        this.passiveRoutePriority = hasPassivePriority ? passiveRoutePriority : 0;
        this.machinePriority = machinePriority;
        this.passivePriority = passivePriority;
        validateShape();
    }

    @Nonnull
    public static QIOPolicyMutation globalDefault(@Nonnull QIOPolicyCatalog.Toggle toggle,
          long routePriority) {
        return globalDefault(toggle, routePriority, 0);
    }

    @Nonnull
    public static QIOPolicyMutation globalDefault(@Nonnull QIOPolicyCatalog.Toggle toggle,
          long routePriority, long passivePriority) {
        return new QIOPolicyMutation(QIOPolicyEntrySnapshot.Kind.GLOBAL_DEFAULT,
              null, null, toggle, true, routePriority, true, passivePriority, 0, 0);
    }

    @Nonnull
    public static QIOPolicyMutation globalRoute(@Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey,
          @Nonnull QIOPolicyCatalog.Toggle toggle, @Nullable Long routePriority) {
        return new QIOPolicyMutation(QIOPolicyEntrySnapshot.Kind.GLOBAL_ROUTE,
              null, QIOPolicyCatalog.RouteIdentity.create(providerId, routeId, recipeKey),
              toggle, routePriority != null, routePriority == null ? 0 : routePriority,
              false, 0, 0, 0);
    }

    @Nonnull
    public static QIOPolicyMutation globalRoute(@Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey,
          @Nonnull QIOPolicyCatalog.Toggle toggle, @Nullable Long routePriority,
          @Nullable Long passivePriority) {
        return new QIOPolicyMutation(QIOPolicyEntrySnapshot.Kind.GLOBAL_ROUTE,
              null, QIOPolicyCatalog.RouteIdentity.create(providerId, routeId, recipeKey),
              toggle, routePriority != null, routePriority == null ? 0 : routePriority,
              passivePriority != null, passivePriority == null ? 0 : passivePriority,
              0, 0);
    }

    @Nonnull
    public static QIOPolicyMutation deviceDefault(@Nonnull UUID deviceUUID,
          @Nonnull QIOPolicyCatalog.Toggle toggle, long machinePriority,
          long passivePriority) {
        return new QIOPolicyMutation(QIOPolicyEntrySnapshot.Kind.DEVICE_DEFAULT,
              deviceUUID, null, toggle, false, 0, true, 0, machinePriority,
              passivePriority);
    }

    @Nonnull
    public static QIOPolicyMutation deviceRoute(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull QIOPolicyCatalog.Toggle toggle,
          @Nullable Long routePriority) {
        return new QIOPolicyMutation(QIOPolicyEntrySnapshot.Kind.DEVICE_ROUTE,
              deviceUUID, QIOPolicyCatalog.RouteIdentity.create(providerId, routeId,
                    recipeKey), toggle, routePriority != null,
              routePriority == null ? 0 : routePriority, false, 0, 0, 0);
    }

    @Nonnull
    public static QIOPolicyMutation deviceRoute(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull QIOPolicyCatalog.Toggle toggle,
          @Nullable Long routePriority, @Nullable Long passivePriority) {
        return new QIOPolicyMutation(QIOPolicyEntrySnapshot.Kind.DEVICE_ROUTE,
              deviceUUID, QIOPolicyCatalog.RouteIdentity.create(providerId, routeId,
                    recipeKey), toggle, routePriority != null,
              routePriority == null ? 0 : routePriority,
              passivePriority != null, passivePriority == null ? 0 : passivePriority,
              0, 0);
    }

    @Nonnull
    public QIOPolicyEntrySnapshot.Kind getKind() {
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
    public QIOPolicyCatalog.Toggle getToggle() {
        return toggle;
    }

    @Nullable
    public Long getRoutePriority() {
        return hasRoutePriority ? routePriority : null;
    }

    @Nullable
    public Long getPassiveRoutePriority() {
        return hasPassivePriority ? passiveRoutePriority : null;
    }

    public long getMachinePriority() {
        return machinePriority;
    }

    public long getPassivePriority() {
        return passivePriority;
    }

    @Nonnull
    public QIOPolicyEntrySnapshot snapshot(@Nonnull QIOPolicyCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        return switch (kind) {
            case GLOBAL_DEFAULT -> catalog.snapshotGlobalDefault();
            case GLOBAL_ROUTE -> catalog.snapshotGlobalRoute(route.getProviderId(),
                  route.getRouteId(), route.getRecipeKey());
            case DEVICE_DEFAULT -> catalog.snapshotDeviceDefault(deviceUUID);
            case DEVICE_ROUTE -> catalog.snapshotDeviceRoute(deviceUUID,
                  route.getProviderId(), route.getRouteId(), route.getRecipeKey());
        };
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", kind.name());
        if (deviceUUID != null) {
            QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        }
        if (route != null) {
            data.setString("providerId", route.getProviderId());
            data.setString("routeId", route.getRouteId());
            data.setString("recipeKey", route.getRecipeKey());
        }
        data.setString("toggle", toggle.name());
        data.setBoolean("hasRoutePriority", hasRoutePriority);
        if (hasRoutePriority) {
            data.setLong("routePriority", routePriority);
        }
        data.setBoolean("hasPassivePriority", hasPassivePriority);
        if (hasPassivePriority) {
            data.setLong("passiveRoutePriority", passiveRoutePriority);
        }
        if (kind == QIOPolicyEntrySnapshot.Kind.DEVICE_DEFAULT) {
            data.setLong("machinePriority", machinePriority);
            data.setLong("passivePriority", passivePriority);
        }
        return data;
    }

    @Nonnull
    public static QIOPolicyMutation read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            QIOPolicyEntrySnapshot.Kind kind = QIOProcessingNbt.readEnum(data, "kind",
                  QIOPolicyEntrySnapshot.Kind.class);
            UUID deviceUUID = data.hasKey("deviceUUID", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readUUID(data, "deviceUUID") : null;
            QIOPolicyCatalog.RouteIdentity route = data.hasKey("providerId", NBT.TAG_STRING) ||
                  data.hasKey("routeId", NBT.TAG_STRING) ||
                  data.hasKey("recipeKey", NBT.TAG_STRING) ?
                  QIOPolicyCatalog.RouteIdentity.create(data.getString("providerId"),
                        data.getString("routeId"), data.getString("recipeKey")) : null;
            QIOPolicyCatalog.Toggle toggle = QIOProcessingNbt.readEnum(data, "toggle",
                  QIOPolicyCatalog.Toggle.class);
            boolean hasPriority = data.getBoolean("hasRoutePriority");
            boolean hasPassivePriority = data.getBoolean("hasPassivePriority");
            return new QIOPolicyMutation(kind, deviceUUID, route, toggle, hasPriority,
                  hasPriority ? data.getLong("routePriority") : 0,
                  hasPassivePriority,
                  hasPassivePriority ? data.getLong("passiveRoutePriority") : 0,
                  data.getLong("machinePriority"), data.getLong("passivePriority"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO policy mutation", e);
        }
    }

    private void validateShape() {
        boolean deviceKind = kind == QIOPolicyEntrySnapshot.Kind.DEVICE_DEFAULT ||
              kind == QIOPolicyEntrySnapshot.Kind.DEVICE_ROUTE;
        boolean routeKind = kind == QIOPolicyEntrySnapshot.Kind.GLOBAL_ROUTE ||
              kind == QIOPolicyEntrySnapshot.Kind.DEVICE_ROUTE;
        if (deviceKind != (deviceUUID != null) || routeKind != (route != null)) {
            throw new IllegalArgumentException("QIO policy mutation identity mismatch");
        }
        if (kind == QIOPolicyEntrySnapshot.Kind.GLOBAL_DEFAULT &&
            (toggle == QIOPolicyCatalog.Toggle.INHERIT || !hasRoutePriority)) {
            throw new IllegalArgumentException("Global default policy must be explicit");
        }
        if (kind == QIOPolicyEntrySnapshot.Kind.DEVICE_DEFAULT && hasRoutePriority) {
            throw new IllegalArgumentException("Device default has no route priority");
        }
        if (kind == QIOPolicyEntrySnapshot.Kind.GLOBAL_DEFAULT && !hasPassivePriority) {
            throw new IllegalArgumentException("Global default needs passive priority");
        }
        if (kind != QIOPolicyEntrySnapshot.Kind.DEVICE_DEFAULT &&
            (machinePriority != 0 || passivePriority != 0)) {
            throw new IllegalArgumentException("Machine priorities require a device default");
        }
    }
}
