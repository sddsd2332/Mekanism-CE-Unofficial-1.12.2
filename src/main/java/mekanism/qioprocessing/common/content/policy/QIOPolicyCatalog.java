package mekanism.qioprocessing.common.content.policy;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent four-level frequency, route, and machine policy overrides. */
public final class QIOPolicyCatalog {

    private static final int MAX_ROUTE_POLICIES = 1_000_000;
    private static final int MAX_DEVICE_POLICIES = 1_000_000;
    private static final int MAX_DEVICE_ROUTE_POLICIES = 1_000_000;
    private static final int MAX_KEY_LENGTH = 1_536;
    private static final int MAX_COMPONENT_LENGTH = MAX_KEY_LENGTH;

    public enum Toggle {
        INHERIT,
        ENABLED,
        DISABLED
    }

    public enum Source {
        DEVICE_ROUTE,
        DEVICE_DEFAULT,
        GLOBAL_ROUTE,
        GLOBAL_DEFAULT
    }

    private long revision;
    private Toggle globalDefaultToggle = Toggle.ENABLED;
    private long globalDefaultRoutePriority;
    private long globalDefaultPassivePriority;
    private final Map<RouteIdentity, RoutePolicy> globalRoutePolicies =
          new LinkedHashMap<>();
    private final List<RouteIdentity> globalRouteOrder = new ArrayList<>();
    private final Map<UUID, DevicePolicy> devicePolicies = new LinkedHashMap<>();
    private final List<UUID> deviceOrder = new ArrayList<>();
    private final Map<DeviceRouteKey, RoutePolicy> deviceRoutePolicies =
          new LinkedHashMap<>();
    private final List<DeviceRouteKey> deviceRouteOrder = new ArrayList<>();

    public long getRevision() {
        return revision;
    }

    @Nonnull
    public Toggle getGlobalDefaultToggle() {
        return globalDefaultToggle;
    }

    public long getGlobalDefaultRoutePriority() {
        return globalDefaultRoutePriority;
    }

    public long getGlobalDefaultPassivePriority() {
        return globalDefaultPassivePriority;
    }

    @Nonnull
    public Map<String, RoutePolicy> getRoutePolicies() {
        Map<String, RoutePolicy> policies = new LinkedHashMap<>();
        globalRoutePolicies.forEach((key, value) -> policies.put(key.asKey(), value));
        return Collections.unmodifiableMap(policies);
    }

    @Nonnull
    public Map<RouteIdentity, RoutePolicy> getGlobalRoutePolicies() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(globalRoutePolicies));
    }

    @Nonnull
    public Map<UUID, DevicePolicy> getDevicePolicies() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(devicePolicies));
    }

    @Nonnull
    public Map<DeviceRouteKey, RoutePolicy> getDeviceRoutePolicies() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(deviceRoutePolicies));
    }

    @Nullable
    public RoutePolicy getGlobalRoutePolicy(@Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey) {
        return globalRoutePolicies.get(RouteIdentity.create(providerId, routeId, recipeKey));
    }

    @Nullable
    public DevicePolicy getDevicePolicy(@Nullable UUID deviceUUID) {
        return deviceUUID == null ? null : devicePolicies.get(deviceUUID);
    }

    @Nullable
    public RoutePolicy getDeviceRoutePolicy(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        return deviceRoutePolicies.get(DeviceRouteKey.create(deviceUUID, providerId,
              routeId, recipeKey));
    }

    public boolean hasPoliciesForDevice(@Nonnull UUID deviceUUID) {
        UUID checked = Objects.requireNonNull(deviceUUID, "deviceUUID");
        if (devicePolicies.containsKey(checked)) {
            return true;
        }
        for (DeviceRouteKey key : deviceRoutePolicies.keySet()) {
            if (checked.equals(key.deviceUUID)) {
                return true;
            }
        }
        return false;
    }

    /** Resolves the frequency-global route layer over the frequency default layer. */
    public boolean isRouteEnabled(@Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        return resolveRoutePolicy(null, providerId, routeId, recipeKey).isEnabled();
    }

    /** Resolves device-route, device-default, global-route, then global-default. */
    public boolean isRouteEnabled(@Nonnull UUID deviceUUID, @Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey) {
        return resolveRoutePolicy(deviceUUID, providerId, routeId, recipeKey).isEnabled();
    }

    public long routePriority(@Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        return resolveRoutePolicy(null, providerId, routeId, recipeKey).getRoutePriority();
    }

    public long routePriority(@Nonnull UUID deviceUUID, @Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey) {
        return resolveRoutePolicy(deviceUUID, providerId, routeId, recipeKey)
              .getRoutePriority();
    }

    public long passiveRoutePriority(@Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        return resolveRoutePolicy(null, providerId, routeId, recipeKey)
              .getPassivePriority();
    }

    public long passiveRoutePriority(@Nonnull UUID deviceUUID, @Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey) {
        return resolveRoutePolicy(deviceUUID, providerId, routeId, recipeKey)
              .getPassivePriority();
    }

    @Nonnull
    public EffectiveRoutePolicy resolveRoutePolicy(@Nullable UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        RouteIdentity route = RouteIdentity.create(providerId, routeId, recipeKey);
        RoutePolicy globalRoute = globalRoutePolicies.get(route);
        DevicePolicy device = deviceUUID == null ? null : devicePolicies.get(deviceUUID);
        RoutePolicy deviceRoute = deviceUUID == null ? null :
              deviceRoutePolicies.get(new DeviceRouteKey(deviceUUID, route));

        Toggle resolvedToggle;
        Source toggleSource;
        if (deviceRoute != null && deviceRoute.toggle != Toggle.INHERIT) {
            resolvedToggle = deviceRoute.toggle;
            toggleSource = Source.DEVICE_ROUTE;
        } else if (device != null && device.toggle != Toggle.INHERIT) {
            resolvedToggle = device.toggle;
            toggleSource = Source.DEVICE_DEFAULT;
        } else if (globalRoute != null && globalRoute.toggle != Toggle.INHERIT) {
            resolvedToggle = globalRoute.toggle;
            toggleSource = Source.GLOBAL_ROUTE;
        } else {
            resolvedToggle = globalDefaultToggle;
            toggleSource = Source.GLOBAL_DEFAULT;
        }

        long priority;
        Source prioritySource;
        if (deviceRoute != null && deviceRoute.hasPriority) {
            priority = deviceRoute.priority;
            prioritySource = Source.DEVICE_ROUTE;
        } else if (globalRoute != null && globalRoute.hasPriority) {
            priority = globalRoute.priority;
            prioritySource = Source.GLOBAL_ROUTE;
        } else {
            priority = globalDefaultRoutePriority;
            prioritySource = Source.GLOBAL_DEFAULT;
        }

        long passivePriority;
        Source passivePrioritySource;
        if (deviceRoute != null && deviceRoute.hasPassivePriority) {
            passivePriority = deviceRoute.passivePriority;
            passivePrioritySource = Source.DEVICE_ROUTE;
        } else if (device != null) {
            passivePriority = device.passivePriority;
            passivePrioritySource = Source.DEVICE_DEFAULT;
        } else if (globalRoute != null && globalRoute.hasPassivePriority) {
            passivePriority = globalRoute.passivePriority;
            passivePrioritySource = Source.GLOBAL_ROUTE;
        } else {
            passivePriority = globalDefaultPassivePriority;
            passivePrioritySource = Source.GLOBAL_DEFAULT;
        }
        return new EffectiveRoutePolicy(resolvedToggle != Toggle.DISABLED, priority,
              passivePriority, toggleSource, prioritySource, passivePrioritySource);
    }

    public boolean isDeviceEnabled(@Nonnull UUID deviceUUID) {
        DevicePolicy policy = devicePolicies.get(Objects.requireNonNull(deviceUUID,
              "deviceUUID"));
        Toggle toggle = policy == null ? Toggle.INHERIT : policy.toggle;
        return (toggle == Toggle.INHERIT ? globalDefaultToggle : toggle) != Toggle.DISABLED;
    }

    public long machinePriority(@Nonnull UUID deviceUUID) {
        DevicePolicy policy = devicePolicies.get(Objects.requireNonNull(deviceUUID,
              "deviceUUID"));
        return policy == null ? 0 : policy.machinePriority;
    }

    public long passivePriority(@Nonnull UUID deviceUUID) {
        DevicePolicy policy = devicePolicies.get(Objects.requireNonNull(deviceUUID,
              "deviceUUID"));
        return policy == null ? 0 : policy.passivePriority;
    }

    public void setGlobalDefaultPolicy(@Nonnull Toggle toggle, long routePriority) {
        setGlobalDefaultPolicy(toggle, routePriority, 0);
    }

    public void setGlobalDefaultPolicy(@Nonnull Toggle toggle, long routePriority,
          long passivePriority) {
        Toggle checked = Objects.requireNonNull(toggle, "toggle");
        if (checked == Toggle.INHERIT) {
            throw new IllegalArgumentException("The frequency default policy cannot inherit");
        }
        if (globalDefaultToggle != checked ||
            globalDefaultRoutePriority != routePriority ||
            globalDefaultPassivePriority != passivePriority) {
            requireMutableRevision();
            globalDefaultToggle = checked;
            globalDefaultRoutePriority = routePriority;
            globalDefaultPassivePriority = passivePriority;
            revision++;
        }
    }

    public void setGlobalRoutePolicy(@Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull Toggle toggle, @Nullable Long priority) {
        setGlobalRoutePolicy(providerId, routeId, recipeKey, toggle, priority, null);
    }

    public void setGlobalRoutePolicy(@Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull Toggle toggle, @Nullable Long priority,
          @Nullable Long passivePriority) {
        RouteIdentity key = RouteIdentity.create(providerId, routeId, recipeKey);
        putRoutePolicy(globalRoutePolicies, globalRouteOrder, key, toggle, priority,
              passivePriority, MAX_ROUTE_POLICIES, "global route");
    }

    public void setDeviceDefaultPolicy(@Nonnull UUID deviceUUID, @Nonnull Toggle toggle,
          long machinePriority, long passivePriority) {
        UUID key = Objects.requireNonNull(deviceUUID, "deviceUUID");
        DevicePolicy next = new DevicePolicy(Objects.requireNonNull(toggle, "toggle"),
              machinePriority, passivePriority);
        DevicePolicy previous = devicePolicies.get(key);
        DevicePolicy stored = next.isDefault() ? null : next;
        if (Objects.equals(previous, stored)) {
            return;
        }
        if (stored != null && previous == null &&
            devicePolicies.size() >= MAX_DEVICE_POLICIES) {
            throw new IllegalStateException("QIO device policy limit reached");
        }
        requireMutableRevision();
        if (stored == null) {
            devicePolicies.remove(key);
            deviceOrder.remove(key);
        } else {
            devicePolicies.put(key, stored);
            if (previous == null) {
                deviceOrder.add(key);
            }
        }
        revision++;
    }

    public void setDeviceRoutePolicy(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull Toggle toggle,
          @Nullable Long priority) {
        setDeviceRoutePolicy(deviceUUID, providerId, routeId, recipeKey, toggle,
              priority, null);
    }

    public void setDeviceRoutePolicy(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull Toggle toggle,
          @Nullable Long priority, @Nullable Long passivePriority) {
        DeviceRouteKey key = DeviceRouteKey.create(deviceUUID, providerId, routeId,
              recipeKey);
        putRoutePolicy(deviceRoutePolicies, deviceRouteOrder, key, toggle, priority,
              passivePriority, MAX_DEVICE_ROUTE_POLICIES, "device route");
    }

    @Nonnull
    public List<QIOPolicyEntrySnapshot> getPolicyEntries() {
        return Collections.unmodifiableList(new ArrayList<>(getPolicyEntryView()));
    }

    /** Indexed lazy view; pagination only materializes DTOs for the requested page. */
    @Nonnull
    public List<QIOPolicyEntrySnapshot> getPolicyEntryView() {
        return Collections.unmodifiableList(new AbstractList<>() {
            @Override
            public QIOPolicyEntrySnapshot get(int index) {
                if (index < 0 || index >= size()) {
                    throw new IndexOutOfBoundsException("QIO policy entry index " + index);
                }
                if (index == 0) {
                    return snapshotGlobalDefault();
                }
                int remaining = index - 1;
                if (remaining < globalRouteOrder.size()) {
                    return snapshotGlobalRoute(globalRouteOrder.get(remaining));
                }
                remaining -= globalRouteOrder.size();
                if (remaining < deviceOrder.size()) {
                    return snapshotDeviceDefault(deviceOrder.get(remaining));
                }
                remaining -= deviceOrder.size();
                return snapshotDeviceRoute(deviceRouteOrder.get(remaining));
            }

            @Override
            public int size() {
                return 1 + globalRouteOrder.size() + deviceOrder.size() +
                      deviceRouteOrder.size();
            }
        });
    }

    @Nonnull
    public QIOPolicyEntrySnapshot snapshotGlobalDefault() {
        return QIOPolicyEntrySnapshot.globalDefault(revision, globalDefaultToggle,
              globalDefaultRoutePriority, globalDefaultPassivePriority);
    }

    @Nonnull
    public QIOPolicyEntrySnapshot snapshotGlobalRoute(@Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey) {
        return snapshotGlobalRoute(RouteIdentity.create(providerId, routeId, recipeKey));
    }

    @Nonnull
    public QIOPolicyEntrySnapshot snapshotDeviceDefault(@Nonnull UUID deviceUUID) {
        UUID checked = Objects.requireNonNull(deviceUUID, "deviceUUID");
        DevicePolicy raw = devicePolicies.get(checked);
        Toggle toggle = raw == null ? Toggle.INHERIT : raw.toggle;
        long machinePriority = raw == null ? 0 : raw.machinePriority;
        long passivePriority = raw == null ? 0 : raw.passivePriority;
        Toggle effective = toggle == Toggle.INHERIT ? globalDefaultToggle : toggle;
        Source source = toggle == Toggle.INHERIT ? Source.GLOBAL_DEFAULT :
              Source.DEVICE_DEFAULT;
        return QIOPolicyEntrySnapshot.deviceDefault(revision, checked, toggle,
              machinePriority, passivePriority, effective != Toggle.DISABLED, source);
    }

    @Nonnull
    public QIOPolicyEntrySnapshot snapshotDeviceRoute(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        return snapshotDeviceRoute(DeviceRouteKey.create(deviceUUID, providerId,
              routeId, recipeKey));
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("revision", revision);
        data.setString("globalDefaultToggle", globalDefaultToggle.name());
        data.setLong("globalDefaultRoutePriority", globalDefaultRoutePriority);
        data.setLong("globalDefaultPassivePriority", globalDefaultPassivePriority);

        NBTTagList routes = new NBTTagList();
        globalRoutePolicies.entrySet().stream().sorted(Map.Entry.comparingByKey())
              .forEach(entry -> {
                  NBTTagCompound stored = entry.getValue().write();
                  entry.getKey().write(stored);
                  routes.appendTag(stored);
              });
        data.setTag("routes", routes);

        NBTTagList devices = new NBTTagList();
        devicePolicies.entrySet().stream().sorted(Comparator.comparing(entry ->
              entry.getKey().toString())).forEach(entry -> {
                  NBTTagCompound stored = entry.getValue().write();
                  QIOProcessingNbt.writeUUID(stored, "deviceUUID", entry.getKey());
                  devices.appendTag(stored);
              });
        data.setTag("devices", devices);

        NBTTagList deviceRoutes = new NBTTagList();
        deviceRoutePolicies.entrySet().stream().sorted(Map.Entry.comparingByKey())
              .forEach(entry -> {
                  NBTTagCompound stored = entry.getValue().write();
                  QIOProcessingNbt.writeUUID(stored, "deviceUUID",
                        entry.getKey().deviceUUID);
                  entry.getKey().route.write(stored);
                  deviceRoutes.appendTag(stored);
              });
        data.setTag("deviceRoutes", deviceRoutes);
        return data;
    }

    @Nonnull
    public static QIOPolicyCatalog read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (!data.hasKey("revision", NBT.TAG_LONG) ||
                  !data.hasKey("globalDefaultToggle", NBT.TAG_STRING) ||
                  !data.hasKey("globalDefaultRoutePriority", NBT.TAG_LONG) ||
                  !data.hasKey("globalDefaultPassivePriority", NBT.TAG_LONG) ||
                  !data.hasKey("routes", NBT.TAG_LIST) ||
                  !data.hasKey("devices", NBT.TAG_LIST) ||
                  !data.hasKey("deviceRoutes", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException(
                      "QIO policy catalog is missing current-schema fields");
            }
            QIOPolicyCatalog catalog = new QIOPolicyCatalog();
            catalog.revision = QIOProcessingNbt.requireNonNegative(data.getLong("revision"),
                  "policyRevision");
            catalog.globalDefaultToggle = QIOProcessingNbt.readEnum(data,
                  "globalDefaultToggle", Toggle.class);
            if (catalog.globalDefaultToggle == Toggle.INHERIT) {
                throw new IllegalArgumentException("Global default policy cannot inherit");
            }
            catalog.globalDefaultRoutePriority = data.getLong("globalDefaultRoutePriority");
            catalog.globalDefaultPassivePriority = data.getLong("globalDefaultPassivePriority");

            NBTTagList routes = data.getTagList("routes", NBT.TAG_COMPOUND);
            if (routes.tagCount() > MAX_ROUTE_POLICIES) {
                throw new QIOProcessingDataException("QIO policy catalog has too many routes");
            }
            for (int index = 0; index < routes.tagCount(); index++) {
                NBTTagCompound stored = routes.getCompoundTagAt(index);
                RouteIdentity key = RouteIdentity.read(stored);
                RoutePolicy policy = RoutePolicy.read(stored);
                if (policy.isDefault() || catalog.globalRoutePolicies.put(key, policy) != null) {
                    throw new QIOProcessingDataException(
                          "Invalid duplicate/default QIO global route policy");
                }
                catalog.globalRouteOrder.add(key);
            }

            NBTTagList devices = data.getTagList("devices", NBT.TAG_COMPOUND);
            if (devices.tagCount() > MAX_DEVICE_POLICIES) {
                throw new QIOProcessingDataException("QIO policy catalog has too many devices");
            }
            for (int index = 0; index < devices.tagCount(); index++) {
                NBTTagCompound stored = devices.getCompoundTagAt(index);
                UUID uuid = QIOProcessingNbt.readUUID(stored, "deviceUUID");
                DevicePolicy policy = DevicePolicy.read(stored);
                if (policy.isDefault() || catalog.devicePolicies.put(uuid, policy) != null) {
                    throw new QIOProcessingDataException(
                          "Invalid duplicate/default QIO device policy");
                }
                catalog.deviceOrder.add(uuid);
            }

            NBTTagList deviceRoutes = data.getTagList("deviceRoutes", NBT.TAG_COMPOUND);
            if (deviceRoutes.tagCount() > MAX_DEVICE_ROUTE_POLICIES) {
                throw new QIOProcessingDataException(
                      "QIO policy catalog has too many device routes");
            }
            for (int index = 0; index < deviceRoutes.tagCount(); index++) {
                NBTTagCompound stored = deviceRoutes.getCompoundTagAt(index);
                DeviceRouteKey key = new DeviceRouteKey(
                      QIOProcessingNbt.readUUID(stored, "deviceUUID"),
                      RouteIdentity.read(stored));
                RoutePolicy policy = RoutePolicy.read(stored);
                if (policy.isDefault() ||
                    catalog.deviceRoutePolicies.put(key, policy) != null) {
                    throw new QIOProcessingDataException(
                          "Invalid duplicate/default QIO device route policy");
                }
                catalog.deviceRouteOrder.add(key);
            }
            return catalog;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO policy catalog", e);
        }
    }

    @Nonnull
    public static String routeKey(@Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        return RouteIdentity.create(providerId, routeId, recipeKey).asKey();
    }

    private QIOPolicyEntrySnapshot snapshotGlobalRoute(RouteIdentity route) {
        RoutePolicy raw = globalRoutePolicies.get(route);
        Toggle toggle = raw == null ? Toggle.INHERIT : raw.toggle;
        Long priority = raw == null || !raw.hasPriority ? null : raw.priority;
        Long passivePriority = raw == null || !raw.hasPassivePriority ? null :
              raw.passivePriority;
        EffectiveRoutePolicy effective = resolveRoutePolicy(null, route.providerId,
              route.routeId, route.recipeKey);
        return QIOPolicyEntrySnapshot.globalRoute(revision, route.providerId,
              route.routeId, route.recipeKey, toggle, priority,
              passivePriority, effective.isEnabled(), effective.getRoutePriority(),
              effective.getPassivePriority(), effective.getToggleSource(),
              effective.getPrioritySource(), effective.getPassivePrioritySource());
    }

    private QIOPolicyEntrySnapshot snapshotDeviceRoute(DeviceRouteKey key) {
        RoutePolicy raw = deviceRoutePolicies.get(key);
        Toggle toggle = raw == null ? Toggle.INHERIT : raw.toggle;
        Long priority = raw == null || !raw.hasPriority ? null : raw.priority;
        Long passivePriority = raw == null || !raw.hasPassivePriority ? null :
              raw.passivePriority;
        EffectiveRoutePolicy effective = resolveRoutePolicy(key.deviceUUID,
              key.route.providerId, key.route.routeId, key.route.recipeKey);
        return QIOPolicyEntrySnapshot.deviceRoute(revision, key.deviceUUID,
              key.route.providerId, key.route.routeId, key.route.recipeKey,
              toggle, priority, passivePriority, effective.isEnabled(),
              effective.getRoutePriority(), effective.getPassivePriority(),
              effective.getToggleSource(), effective.getPrioritySource(),
              effective.getPassivePrioritySource());
    }

    private <K> void putRoutePolicy(Map<K, RoutePolicy> policies, List<K> order,
          K key, Toggle toggle, @Nullable Long priority,
          @Nullable Long passivePriority, int maximum, String name) {
        RoutePolicy next = new RoutePolicy(Objects.requireNonNull(toggle, "toggle"),
              priority != null, priority == null ? 0 : priority,
              passivePriority != null, passivePriority == null ? 0 : passivePriority);
        RoutePolicy previous = policies.get(key);
        RoutePolicy stored = next.isDefault() ? null : next;
        if (Objects.equals(previous, stored)) {
            return;
        }
        if (stored != null && previous == null && policies.size() >= maximum) {
            throw new IllegalStateException("QIO " + name + " policy limit reached");
        }
        requireMutableRevision();
        if (stored == null) {
            policies.remove(key);
            order.remove(key);
        } else {
            policies.put(key, stored);
            if (previous == null) {
                order.add(key);
            }
        }
        revision++;
    }

    private void requireMutableRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO policy revision exhausted");
        }
    }

    private static String checkedComponent(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty() || checked.length() > MAX_COMPONENT_LENGTH) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        return checked;
    }

    public static final class EffectiveRoutePolicy {

        private final boolean enabled;
        private final long routePriority;
        private final long passivePriority;
        private final Source toggleSource;
        private final Source prioritySource;
        private final Source passivePrioritySource;

        private EffectiveRoutePolicy(boolean enabled, long routePriority,
              long passivePriority, Source toggleSource, Source prioritySource,
              Source passivePrioritySource) {
            this.enabled = enabled;
            this.routePriority = routePriority;
            this.passivePriority = passivePriority;
            this.toggleSource = toggleSource;
            this.prioritySource = prioritySource;
            this.passivePrioritySource = passivePrioritySource;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public long getRoutePriority() {
            return routePriority;
        }

        public long getPassivePriority() {
            return passivePriority;
        }

        @Nonnull
        public Source getToggleSource() {
            return toggleSource;
        }

        @Nonnull
        public Source getPrioritySource() {
            return prioritySource;
        }

        @Nonnull
        public Source getPassivePrioritySource() {
            return passivePrioritySource;
        }
    }

    public static final class RouteIdentity implements Comparable<RouteIdentity> {

        private final String providerId;
        private final String routeId;
        private final String recipeKey;

        private RouteIdentity(String providerId, String routeId, String recipeKey) {
            this.providerId = providerId;
            this.routeId = routeId;
            this.recipeKey = recipeKey;
        }

        @Nonnull
        public static RouteIdentity create(@Nonnull String providerId,
              @Nonnull String routeId, @Nonnull String recipeKey) {
            String checkedProvider = checkedComponent(providerId, "providerId");
            String checkedRoute = checkedComponent(routeId, "routeId");
            String checkedRecipe = checkedComponent(recipeKey, "recipeKey");
            String combined = checkedProvider + '|' + checkedRoute + '|' + checkedRecipe;
            if (combined.length() > MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("QIO policy key is too long");
            }
            return new RouteIdentity(checkedProvider, checkedRoute, checkedRecipe);
        }

        @Nonnull
        public String getProviderId() {
            return providerId;
        }

        @Nonnull
        public String getRouteId() {
            return routeId;
        }

        @Nonnull
        public String getRecipeKey() {
            return recipeKey;
        }

        @Nonnull
        public String asKey() {
            return providerId + '|' + routeId + '|' + recipeKey;
        }

        private void write(NBTTagCompound data) {
            data.setString("providerId", providerId);
            data.setString("routeId", routeId);
            data.setString("recipeKey", recipeKey);
        }

        private static RouteIdentity read(NBTTagCompound data) {
            if (!data.hasKey("providerId", NBT.TAG_STRING) ||
                !data.hasKey("routeId", NBT.TAG_STRING) ||
                !data.hasKey("recipeKey", NBT.TAG_STRING)) {
                throw new IllegalArgumentException("QIO route policy identity is incomplete");
            }
            return create(data.getString("providerId"), data.getString("routeId"),
                  data.getString("recipeKey"));
        }

        @Override
        public int compareTo(@Nonnull RouteIdentity other) {
            int comparison = providerId.compareTo(other.providerId);
            if (comparison == 0) {
                comparison = routeId.compareTo(other.routeId);
            }
            return comparison == 0 ? recipeKey.compareTo(other.recipeKey) : comparison;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RouteIdentity other && providerId.equals(other.providerId) &&
                  routeId.equals(other.routeId) && recipeKey.equals(other.recipeKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerId, routeId, recipeKey);
        }
    }

    public static final class DeviceRouteKey implements Comparable<DeviceRouteKey> {

        private final UUID deviceUUID;
        private final RouteIdentity route;

        private DeviceRouteKey(UUID deviceUUID, RouteIdentity route) {
            this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
            this.route = Objects.requireNonNull(route, "route");
        }

        @Nonnull
        public static DeviceRouteKey create(@Nonnull UUID deviceUUID,
              @Nonnull String providerId, @Nonnull String routeId,
              @Nonnull String recipeKey) {
            return new DeviceRouteKey(deviceUUID,
                  RouteIdentity.create(providerId, routeId, recipeKey));
        }

        @Nonnull
        public UUID getDeviceUUID() {
            return deviceUUID;
        }

        @Nonnull
        public RouteIdentity getRoute() {
            return route;
        }

        @Override
        public int compareTo(@Nonnull DeviceRouteKey other) {
            int comparison = deviceUUID.toString().compareTo(other.deviceUUID.toString());
            return comparison == 0 ? route.compareTo(other.route) : comparison;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DeviceRouteKey other && deviceUUID.equals(other.deviceUUID) &&
                  route.equals(other.route);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceUUID, route);
        }
    }

    public static final class RoutePolicy {

        private final Toggle toggle;
        private final boolean hasPriority;
        private final long priority;
        private final boolean hasPassivePriority;
        private final long passivePriority;

        private RoutePolicy(Toggle toggle, boolean hasPriority, long priority,
              boolean hasPassivePriority, long passivePriority) {
            this.toggle = toggle;
            this.hasPriority = hasPriority;
            this.priority = hasPriority ? priority : 0;
            this.hasPassivePriority = hasPassivePriority;
            this.passivePriority = hasPassivePriority ? passivePriority : 0;
        }

        @Nonnull
        public Toggle getToggle() {
            return toggle;
        }

        @Nullable
        public Long getPriority() {
            return hasPriority ? priority : null;
        }

        @Nullable
        public Long getPassivePriority() {
            return hasPassivePriority ? passivePriority : null;
        }

        private boolean isDefault() {
            return toggle == Toggle.INHERIT && !hasPriority && !hasPassivePriority;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("toggle", toggle.name());
            data.setBoolean("hasPriority", hasPriority);
            if (hasPriority) {
                data.setLong("priority", priority);
            }
            data.setBoolean("hasPassivePriority", hasPassivePriority);
            if (hasPassivePriority) {
                data.setLong("passivePriority", passivePriority);
            }
            return data;
        }

        private static RoutePolicy read(NBTTagCompound data)
              throws QIOProcessingDataException {
            if (!data.hasKey("toggle", NBT.TAG_STRING) ||
                !data.hasKey("hasPriority", NBT.TAG_BYTE) ||
                !data.hasKey("hasPassivePriority", NBT.TAG_BYTE)) {
                throw new QIOProcessingDataException(
                      "QIO route policy is missing current-schema fields");
            }
            boolean hasPriority = data.getBoolean("hasPriority");
            boolean hasPassivePriority = data.getBoolean("hasPassivePriority");
            if ((hasPriority && !data.hasKey("priority", NBT.TAG_LONG)) ||
                (hasPassivePriority && !data.hasKey("passivePriority", NBT.TAG_LONG))) {
                throw new QIOProcessingDataException(
                      "QIO route policy priority is incomplete");
            }
            return new RoutePolicy(QIOProcessingNbt.readEnum(data, "toggle", Toggle.class),
                  hasPriority, hasPriority ? data.getLong("priority") : 0,
                  hasPassivePriority,
                  hasPassivePriority ? data.getLong("passivePriority") : 0);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RoutePolicy other && toggle == other.toggle &&
                  hasPriority == other.hasPriority && priority == other.priority &&
                  hasPassivePriority == other.hasPassivePriority &&
                  passivePriority == other.passivePriority;
        }

        @Override
        public int hashCode() {
            return Objects.hash(toggle, hasPriority, priority, hasPassivePriority,
                  passivePriority);
        }
    }

    public static final class DevicePolicy {

        private final Toggle toggle;
        private final long machinePriority;
        private final long passivePriority;

        private DevicePolicy(Toggle toggle, long machinePriority, long passivePriority) {
            this.toggle = toggle;
            this.machinePriority = machinePriority;
            this.passivePriority = passivePriority;
        }

        @Nonnull
        public Toggle getToggle() {
            return toggle;
        }

        public long getMachinePriority() {
            return machinePriority;
        }

        public long getPassivePriority() {
            return passivePriority;
        }

        private boolean isDefault() {
            return toggle == Toggle.INHERIT && machinePriority == 0 && passivePriority == 0;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("toggle", toggle.name());
            data.setLong("machinePriority", machinePriority);
            data.setLong("passivePriority", passivePriority);
            return data;
        }

        private static DevicePolicy read(NBTTagCompound data)
              throws QIOProcessingDataException {
            if (!data.hasKey("toggle", NBT.TAG_STRING) ||
                !data.hasKey("machinePriority", NBT.TAG_LONG) ||
                !data.hasKey("passivePriority", NBT.TAG_LONG)) {
                throw new QIOProcessingDataException(
                      "QIO device policy is missing current-schema fields");
            }
            return new DevicePolicy(QIOProcessingNbt.readEnum(data, "toggle", Toggle.class),
                  data.getLong("machinePriority"), data.getLong("passivePriority"));
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DevicePolicy other && toggle == other.toggle &&
                  machinePriority == other.machinePriority &&
                  passivePriority == other.passivePriority;
        }

        @Override
        public int hashCode() {
            return Objects.hash(toggle, machinePriority, passivePriority);
        }
    }
}
