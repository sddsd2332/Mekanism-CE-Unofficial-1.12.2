package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceCatalog;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileLayout;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.api.processing.MachineRecipeRoute;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persistent provider route semantics, separate from transient loaded-device availability. */
public final class QIOProviderCatalog {

    private static final int MAX_PERSISTED_ROUTES = 1_000_000;
    private static final int MAX_PERSISTED_DEVICES = 1_000_000;
    private static final int MAX_PERSISTED_ASSOCIATIONS = 4_000_000;
    private static final int MAX_PROFILE_SCOPE_ID_LENGTH = 1_536;

    private final Map<String, QIOPlanningRoute> routes = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> devicesByRoute = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> routesByDevice = new LinkedHashMap<>();
    private final Map<UUID, String> profileScopesByDevice = new LinkedHashMap<>();
    private final Map<UUID, QIOAutomationMode> modesByDevice = new LinkedHashMap<>();
    private final Set<UUID> availableDevices = new LinkedHashSet<>();
    private long revision;
    private long availabilityRevision;

    public long getRevision() {
        return revision;
    }

    public long getAvailabilityRevision() {
        return availabilityRevision;
    }

    public int size() {
        return routes.size();
    }

    public boolean observe(@Nonnull QIOProviderRouteCatalog.Snapshot online,
          @Nonnull QIOAutomationDeviceCatalog deviceCatalog, int maximumRoutes) {
        Objects.requireNonNull(online, "online");
        Objects.requireNonNull(deviceCatalog, "deviceCatalog");
        if (maximumRoutes <= 0) {
            throw new IllegalArgumentException("maximumRoutes must be positive");
        }
        Set<UUID> observed = online.getObservedDeviceIds();
        for (UUID deviceUUID : observed) {
            QIOAutomationDeviceSnapshot device = deviceCatalog.get(deviceUUID);
            if (device == null || !device.isOnline() ||
                  device.getKind() != QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE ||
                  device.getMode() != QIOAutomationMode.SCHEDULED) {
                throw new IllegalStateException("QIO provider snapshot references an unavailable device");
            }
        }
        Set<String> incomingRouteIds = new LinkedHashSet<>();
        for (QIOPlanningRoute route : online.getRoutes()) {
            incomingRouteIds.add(route.getStableId());
        }
        long additions = incomingRouteIds.stream().filter(id -> !routes.containsKey(id)).count();
        long removals = routes.keySet().stream().filter(id -> !incomingRouteIds.contains(id))
              .filter(id -> observed.containsAll(devicesByRoute.getOrDefault(id,
                    Collections.emptySet()))).count();
        long projectedSize = routes.size() + additions - removals;
        if (projectedSize > maximumRoutes && projectedSize > routes.size()) {
            throw new IllegalStateException("QIO provider route directory limit reached");
        }
        boolean structuralChanged = false;

        Map<UUID, Set<String>> advertisedByDevice = new LinkedHashMap<>();
        for (UUID deviceUUID : observed) {
            advertisedByDevice.put(deviceUUID, new LinkedHashSet<>());
        }
        for (QIOPlanningRoute route : online.getRoutes()) {
            String stableId = route.getStableId();
            QIOPlanningRoute previous = routes.get(stableId);
            if (previous != null && !previous.getSignature().equals(route.getSignature())) {
                removeRoute(stableId);
                structuralChanged = true;
            }
            QIOPlanningRoute current = routes.put(stableId, route.withPriority(0));
            structuralChanged |= current == null;
            for (UUID deviceUUID : online.getDeviceIds(stableId)) {
                advertisedByDevice.computeIfAbsent(deviceUUID, ignored -> new LinkedHashSet<>())
                      .add(stableId);
            }
        }
        for (Map.Entry<UUID, Set<String>> entry : advertisedByDevice.entrySet()) {
            QIOAutomationDeviceSnapshot device = deviceCatalog.get(entry.getKey());
            structuralChanged |= replaceDeviceRoutes(entry.getKey(), entry.getValue(),
                  Objects.requireNonNull(device, "observed device").getProfileScopeId(),
                  QIOAutomationMode.SCHEDULED);
        }
        for (QIOAutomationDeviceSnapshot device : deviceCatalog.getDevices()) {
            if (device.isOnline() && routesByDevice.containsKey(device.getDeviceUUID()) &&
                modesByDevice.get(device.getDeviceUUID()) == QIOAutomationMode.SCHEDULED &&
                (device.getKind() != QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE ||
                 device.getMode() != QIOAutomationMode.SCHEDULED)) {
                structuralChanged |= forgetDeviceInternal(device.getDeviceUUID());
            }
        }
        if (structuralChanged) {
            incrementRevision();
        }
        if (!availableDevices.equals(observed)) {
            availableDevices.clear();
            availableDevices.addAll(observed);
            incrementAvailabilityRevision();
            return true;
        }
        return structuralChanged;
    }

    /**
     * Retains the last known route semantics for one configurable machine. Availability is
     * tracked separately, so management terminals can edit an unloaded machine's profile.
     */
    public boolean observeDeviceRoutes(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId,
          @Nonnull ResourceLocation providerId, @Nonnull List<MachineRecipeRoute> machineRoutes,
          int maximumRoutes) {
        Objects.requireNonNull(deviceUUID, "deviceUUID");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(profileScopeId, "profileScopeId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(machineRoutes, "machineRoutes");
        if (mode == QIOAutomationMode.OUTPUT_ONLY) {
            throw new IllegalArgumentException("Output-only QIO devices do not expose routes");
        }
        if (maximumRoutes <= 0) {
            throw new IllegalArgumentException("maximumRoutes must be positive");
        }
        QIOProviderRouteCatalog.Snapshot captured = new QIOProviderRouteCatalog.Builder(
              (ignoredProvider, ignoredRoute, ignoredRecipe) -> 0)
              .addDevice(deviceUUID, providerId, machineRoutes).build();
        if (!captured.getDiagnostics().isEmpty()) {
            throw new IllegalStateException(String.join("; ", captured.getDiagnostics()));
        }
        Map<String, QIOPlanningRoute> incoming = new LinkedHashMap<>();
        Set<String> advertised = new LinkedHashSet<>();
        for (QIOPlanningRoute route : captured.getRoutes()) {
            QIOPlanningRoute existing = routes.get(route.getStableId());
            if (existing != null && !existing.getSignature().equals(route.getSignature())) {
                throw new IllegalStateException("Conflicting QIO route identity " +
                      route.getStableId());
            }
            incoming.put(route.getStableId(), route.withPriority(0));
            advertised.add(route.getStableId());
        }

        Set<String> previous = routesByDevice.getOrDefault(deviceUUID,
              Collections.emptySet());
        long orphaned = previous.stream().filter(id -> !advertised.contains(id))
              .filter(id -> devicesByRoute.getOrDefault(id, Collections.emptySet()).size() == 1)
              .count();
        long additions = advertised.stream().filter(id -> !routes.containsKey(id)).count();
        long projectedSize = routes.size() - orphaned + additions;
        if (projectedSize > maximumRoutes && projectedSize > routes.size()) {
            throw new IllegalStateException("QIO provider route directory limit reached");
        }
        incoming.forEach(routes::putIfAbsent);
        boolean changed = replaceDeviceRoutes(deviceUUID, advertised,
              checkedProfileScopeId(profileScopeId), mode);
        if (changed) {
            incrementRevision();
        }
        return changed;
    }

    public boolean forgetDeviceRoutes(@Nonnull UUID deviceUUID) {
        Objects.requireNonNull(deviceUUID, "deviceUUID");
        if (!forgetDeviceInternal(deviceUUID)) {
            return false;
        }
        incrementRevision();
        return true;
    }

    @Nonnull
    public List<QIOPlanningRoute> getDeviceRoutes(@Nonnull UUID deviceUUID) {
        Set<String> routeIds = routesByDevice.getOrDefault(
              Objects.requireNonNull(deviceUUID, "deviceUUID"), Collections.emptySet());
        List<QIOPlanningRoute> result = new ArrayList<>(routeIds.size());
        for (String routeId : routeIds) {
            QIOPlanningRoute route = routes.get(routeId);
            if (route != null) {
                result.add(route);
            }
        }
        result.sort(Comparator.comparing(QIOPlanningRoute::getStableId));
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public QIOAutomationMode getDeviceMode(@Nonnull UUID deviceUUID) {
        return modesByDevice.get(Objects.requireNonNull(deviceUUID, "deviceUUID"));
    }

    @Nullable
    public String getDeviceProfileScopeId(@Nonnull UUID deviceUUID) {
        return profileScopesByDevice.get(Objects.requireNonNull(deviceUUID, "deviceUUID"));
    }

    public boolean forgetDevice(@Nonnull UUID deviceUUID) {
        Objects.requireNonNull(deviceUUID, "deviceUUID");
        boolean structuralChanged = forgetDeviceInternal(deviceUUID);
        boolean availabilityChanged = availableDevices.remove(deviceUUID);
        if (!structuralChanged && !availabilityChanged) {
            return false;
        }
        if (structuralChanged) {
            incrementRevision();
        }
        if (availabilityChanged) {
            incrementAvailabilityRevision();
        }
        return true;
    }

    @Nonnull
    public List<QIOPlanningRoute> getRoutes(@Nonnull QIOPolicyCatalog policies) {
        return getRoutes(policies, null);
    }

    /** Resolves administrator policy and the active per-machine recipe profile together. */
    @Nonnull
    public List<QIOPlanningRoute> getRoutes(@Nonnull QIOPolicyCatalog policies,
          QIOAutomationRecipeProfileCatalog profiles) {
        Objects.requireNonNull(policies, "policies");
        Map<String, ProviderOrder> profileScopeOrders = profiles == null ?
              Collections.emptyMap() : buildProfileScopeOrders();
        List<QIOPlanningRoute> result = new ArrayList<>();
        for (QIOPlanningRoute route : routes.values()) {
            Long priority = null;
            for (UUID deviceUUID : devicesByRoute.getOrDefault(route.getStableId(),
                  Collections.emptySet())) {
                if (modesByDevice.get(deviceUUID) == QIOAutomationMode.SCHEDULED &&
                      availableDevices.contains(deviceUUID) &&
                      policies.isRouteEnabled(deviceUUID, route.getProviderId(),
                      route.getRouteId(), route.getRecipeKey()) &&
                      (profiles == null || profileEnabled(profiles, deviceUUID,
                            profileScope(deviceUUID), route))) {
                    long candidate = effectivePriority(policies, profiles,
                          profileScopeOrders, deviceUUID, profileScope(deviceUUID), route);
                    priority = priority == null ? candidate : Math.max(priority, candidate);
                }
            }
            if (priority != null) {
                result.add(route.withPriority(priority));
            }
        }
        result.sort(Comparator.comparing(QIOPlanningRoute::getStableId));
        return Collections.unmodifiableList(result);
    }

    private Map<String, ProviderOrder> buildProfileScopeOrders() {
        Map<String, Map<String, QIOPlanningRoute>> byScope = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : routesByDevice.entrySet()) {
            if (modesByDevice.get(entry.getKey()) != QIOAutomationMode.SCHEDULED) {
                continue;
            }
            String profileScopeId = profileScope(entry.getKey());
            Map<String, QIOPlanningRoute> scopedRoutes = byScope.computeIfAbsent(
                  profileScopeId, ignored -> new LinkedHashMap<>());
            for (String routeId : entry.getValue()) {
                QIOPlanningRoute route = routes.get(routeId);
                if (route != null) {
                    scopedRoutes.putIfAbsent(routeId, route);
                }
            }
        }
        Map<String, ProviderOrder> orders = new LinkedHashMap<>();
        byScope.forEach((scope, scopedRoutes) ->
              orders.put(scope, new ProviderOrder(new ArrayList<>(scopedRoutes.values()))));
        return orders;
    }

    private static boolean profileEnabled(QIOAutomationRecipeProfileCatalog profiles,
          UUID deviceUUID, String profileScopeId, QIOPlanningRoute route) {
        QIOAutomationRecipeProfile profile = profiles.getActiveProfile(deviceUUID,
              QIOAutomationMode.SCHEDULED, profileScopeId);
        return profile.isRouteEnabled(QIOAutomationRecipeProfileLayout.routeKey(
              route.getRouteId(), route.getRecipeKey()));
    }

    private static long effectivePriority(QIOPolicyCatalog policies,
          QIOAutomationRecipeProfileCatalog profiles,
          Map<String, ProviderOrder> profileScopeOrders, UUID deviceUUID,
          String profileScopeId, QIOPlanningRoute route) {
        long policyPriority = policies.routePriority(deviceUUID, route.getProviderId(),
              route.getRouteId(), route.getRecipeKey());
        if (profiles == null) {
            return policyPriority;
        }
        QIOAutomationRecipeProfile profile = profiles.getActiveProfile(deviceUUID,
              QIOAutomationMode.SCHEDULED, profileScopeId);
        if (!profile.hasCustomOrder()) {
            return policyPriority;
        }
        ProviderOrder order = profileScopeOrders.get(profileScopeId);
        long rank = order == null ? Integer.MAX_VALUE : order.rank(profile, route);
        return Long.MAX_VALUE - Math.max(0, rank);
    }

    private static final class ProviderOrder {

        private final List<String> products;
        private final Map<String, List<String>> routesByProduct;
        private final Map<String, String> productByRoute;

        private ProviderOrder(List<QIOPlanningRoute> source) {
            List<QIOPlanningRoute> ordered = new ArrayList<>(source);
            ordered.sort(Comparator.comparing(QIOPlanningRoute::getStableId));
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            Map<String, String> productsByRoute = new LinkedHashMap<>();
            for (QIOPlanningRoute route : ordered) {
                PortableResourceDescriptor output = route.getGuaranteedOutputs().keySet()
                      .stream().sorted().findFirst().orElse(null);
                if (output == null) {
                    continue;
                }
                String product = output.toString();
                String routeKey = QIOAutomationRecipeProfileLayout.routeKey(
                      route.getRouteId(), route.getRecipeKey());
                grouped.computeIfAbsent(product, ignored -> new ArrayList<>()).add(routeKey);
                productsByRoute.put(routeKey, product);
            }
            products = Collections.unmodifiableList(new ArrayList<>(grouped.keySet()));
            Map<String, List<String>> immutable = new LinkedHashMap<>();
            grouped.forEach((key, value) -> immutable.put(key,
                  Collections.unmodifiableList(new ArrayList<>(value))));
            routesByProduct = Collections.unmodifiableMap(immutable);
            productByRoute = Collections.unmodifiableMap(productsByRoute);
        }

        private long rank(QIOAutomationRecipeProfile profile, QIOPlanningRoute route) {
            String routeKey = QIOAutomationRecipeProfileLayout.routeKey(route.getRouteId(),
                  route.getRecipeKey());
            String product = productByRoute.get(routeKey);
            if (product == null) {
                return Integer.MAX_VALUE;
            }
            List<String> productOrder = profile.getCurrentProductOrder(products);
            int productIndex = productOrder.indexOf(product);
            List<String> routeOrder = profile.getCurrentRouteOrder(product,
                  routesByProduct.getOrDefault(product, Collections.emptyList()));
            int routeIndex = routeOrder.indexOf(routeKey);
            return ((long) Math.max(0, productIndex) << 32) |
                  (Math.max(0, routeIndex) & 0xFFFFFFFFL);
        }
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("revision", revision);
        data.setLong("availabilityRevision", availabilityRevision);
        NBTTagList storedRoutes = new NBTTagList();
        routes.values().stream().sorted(Comparator.comparing(QIOPlanningRoute::getStableId))
              .forEach(route -> storedRoutes.appendTag(route.write()));
        data.setTag("routes", storedRoutes);
        NBTTagList storedDevices = new NBTTagList();
        routesByDevice.entrySet().stream().sorted(Map.Entry.comparingByKey(
              Comparator.comparing(UUID::toString))).forEach(entry -> {
            NBTTagCompound storedDevice = new NBTTagCompound();
            QIOProcessingNbt.writeUUID(storedDevice, "deviceUUID", entry.getKey());
            storedDevice.setString("profileScopeId", profileScope(entry.getKey()));
            storedDevice.setString("mode", deviceMode(entry.getKey()).name());
            NBTTagList routeIds = new NBTTagList();
            entry.getValue().stream().sorted().forEach(id -> {
                NBTTagCompound routeId = new NBTTagCompound();
                routeId.setString("stableId", id);
                routeIds.appendTag(routeId);
            });
            storedDevice.setTag("routeIds", routeIds);
            storedDevices.appendTag(storedDevice);
        });
        data.setTag("devices", storedDevices);
        return data;
    }

    @Nonnull
    public static QIOProviderCatalog read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            QIOProviderCatalog catalog = new QIOProviderCatalog();
            catalog.revision = QIOProcessingNbt.requireNonNegative(data.getLong("revision"),
                  "providerCatalogRevision");
            catalog.availabilityRevision = QIOProcessingNbt.requireNonNegative(
                  data.getLong("availabilityRevision"), "providerAvailabilityRevision");
            NBTTagList storedRoutes = data.getTagList("routes", NBT.TAG_COMPOUND);
            if (storedRoutes.tagCount() > MAX_PERSISTED_ROUTES) {
                throw new QIOProcessingDataException("QIO provider route directory is too large");
            }
            for (int index = 0; index < storedRoutes.tagCount(); index++) {
                QIOPlanningRoute route = QIOPlanningRoute.read(storedRoutes.getCompoundTagAt(index));
                if (catalog.routes.put(route.getStableId(), route) != null) {
                    throw new QIOProcessingDataException("Duplicate persisted QIO provider route");
                }
            }
            NBTTagList storedDevices = data.getTagList("devices", NBT.TAG_COMPOUND);
            if (storedDevices.tagCount() > MAX_PERSISTED_DEVICES) {
                throw new QIOProcessingDataException("QIO provider device directory is too large");
            }
            int associations = 0;
            for (int index = 0; index < storedDevices.tagCount(); index++) {
                NBTTagCompound storedDevice = storedDevices.getCompoundTagAt(index);
                UUID deviceUUID = QIOProcessingNbt.readUUID(storedDevice, "deviceUUID");
                if (!storedDevice.hasKey("profileScopeId", NBT.TAG_STRING)) {
                    throw new QIOProcessingDataException(
                          "Persisted QIO provider device has no profile scope");
                }
                String profileScopeId = storedDevice.getString("profileScopeId").trim();
                if (profileScopeId.isEmpty() ||
                    profileScopeId.length() > MAX_PROFILE_SCOPE_ID_LENGTH) {
                    throw new QIOProcessingDataException(
                          "Persisted QIO provider device has an empty profile scope");
                }
                if (!storedDevice.hasKey("mode", NBT.TAG_STRING)) {
                    throw new QIOProcessingDataException(
                          "Persisted QIO provider device has no automation mode");
                }
                QIOAutomationMode mode;
                try {
                    mode = QIOAutomationMode.valueOf(storedDevice.getString("mode"));
                } catch (RuntimeException e) {
                    throw new QIOProcessingDataException(
                          "Persisted QIO provider device has an invalid automation mode", e);
                }
                if (mode == QIOAutomationMode.OUTPUT_ONLY) {
                    throw new QIOProcessingDataException(
                          "Output-only QIO provider device has persisted routes");
                }
                NBTTagList routeIds = storedDevice.getTagList("routeIds", NBT.TAG_COMPOUND);
                associations = Math.addExact(associations, routeIds.tagCount());
                if (associations > MAX_PERSISTED_ASSOCIATIONS) {
                    throw new QIOProcessingDataException("QIO provider association directory is too large");
                }
                Set<String> ids = new LinkedHashSet<>();
                for (int routeIndex = 0; routeIndex < routeIds.tagCount(); routeIndex++) {
                    String stableId = routeIds.getCompoundTagAt(routeIndex).getString("stableId");
                    if (!catalog.routes.containsKey(stableId) || !ids.add(stableId)) {
                        throw new QIOProcessingDataException("Invalid persisted QIO provider association");
                    }
                    catalog.devicesByRoute.computeIfAbsent(stableId,
                          ignored -> new LinkedHashSet<>()).add(deviceUUID);
                }
                if (ids.isEmpty() || catalog.routesByDevice.put(deviceUUID, ids) != null) {
                    throw new QIOProcessingDataException("Invalid persisted QIO provider device");
                }
                catalog.profileScopesByDevice.put(deviceUUID, profileScopeId);
                catalog.modesByDevice.put(deviceUUID, mode);
            }
            if (!catalog.devicesByRoute.keySet().equals(catalog.routes.keySet())) {
                throw new QIOProcessingDataException("Persisted QIO provider route has no device");
            }
            return catalog;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO provider catalog", e);
        }
    }

    public void validateDevices(@Nonnull QIOAutomationDeviceCatalog deviceCatalog)
          throws QIOProcessingDataException {
        Objects.requireNonNull(deviceCatalog, "deviceCatalog");
        for (UUID deviceUUID : routesByDevice.keySet()) {
            QIOAutomationDeviceSnapshot device = deviceCatalog.get(deviceUUID);
            if (device == null ||
                  device.getKind() != QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE) {
                throw new QIOProcessingDataException(
                      "Persisted QIO provider route references an unknown machine device " +
                            deviceUUID);
            }
            if (!profileScope(deviceUUID).equals(device.getProfileScopeId())) {
                throw new QIOProcessingDataException(
                      "Persisted QIO provider profile scope does not match machine device " +
                            deviceUUID);
            }
            QIOAutomationMode storedMode = modesByDevice.get(deviceUUID);
            if (storedMode == null || storedMode == QIOAutomationMode.OUTPUT_ONLY) {
                throw new QIOProcessingDataException(
                      "Persisted QIO provider route has no configurable device mode " +
                            deviceUUID);
            }
        }
    }

    private boolean replaceDeviceRoutes(UUID deviceUUID, Set<String> advertised,
          String profileScopeId, QIOAutomationMode mode) {
        Set<String> previous = routesByDevice.getOrDefault(deviceUUID, Collections.emptySet());
        boolean scopeChanged = !profileScopeId.equals(profileScopesByDevice.get(deviceUUID));
        boolean modeChanged = mode != modesByDevice.get(deviceUUID);
        if (previous.equals(advertised) && !scopeChanged && !modeChanged) {
            return false;
        }
        if (!previous.isEmpty()) {
            for (String stableId : new ArrayList<>(previous)) {
                Set<UUID> devices = devicesByRoute.get(stableId);
                if (devices != null) {
                    devices.remove(deviceUUID);
                    if (devices.isEmpty()) {
                        devicesByRoute.remove(stableId);
                        if (!advertised.contains(stableId)) {
                            routes.remove(stableId);
                        }
                    }
                }
            }
        }
        if (advertised.isEmpty()) {
            routesByDevice.remove(deviceUUID);
            profileScopesByDevice.remove(deviceUUID);
            modesByDevice.remove(deviceUUID);
        } else {
            Set<String> copy = new LinkedHashSet<>(advertised);
            routesByDevice.put(deviceUUID, copy);
            profileScopesByDevice.put(deviceUUID, profileScopeId);
            modesByDevice.put(deviceUUID, mode);
            for (String stableId : copy) {
                devicesByRoute.computeIfAbsent(stableId, ignored -> new LinkedHashSet<>())
                      .add(deviceUUID);
            }
        }
        return true;
    }

    private boolean forgetDeviceInternal(UUID deviceUUID) {
        Set<String> previous = routesByDevice.remove(deviceUUID);
        String previousScope = profileScopesByDevice.remove(deviceUUID);
        QIOAutomationMode previousMode = modesByDevice.remove(deviceUUID);
        if (previous == null) {
            return previousScope != null || previousMode != null;
        }
        for (String stableId : previous) {
            Set<UUID> devices = devicesByRoute.get(stableId);
            if (devices != null) {
                devices.remove(deviceUUID);
                if (devices.isEmpty()) {
                    devicesByRoute.remove(stableId);
                    routes.remove(stableId);
                }
            }
        }
        return true;
    }

    private void removeRoute(String stableId) {
        routes.remove(stableId);
        Set<UUID> devices = devicesByRoute.remove(stableId);
        if (devices != null) {
            for (UUID deviceUUID : devices) {
                Set<String> ids = routesByDevice.get(deviceUUID);
                if (ids != null) {
                    ids.remove(stableId);
                    if (ids.isEmpty()) {
                        routesByDevice.remove(deviceUUID);
                        profileScopesByDevice.remove(deviceUUID);
                        modesByDevice.remove(deviceUUID);
                    }
                }
            }
        }
    }

    private void incrementRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO provider catalog revision exhausted");
        }
        revision++;
    }

    private String profileScope(UUID deviceUUID) {
        String profileScopeId = profileScopesByDevice.get(deviceUUID);
        if (profileScopeId == null || profileScopeId.isEmpty()) {
            throw new IllegalStateException("QIO provider device has no profile scope " +
                  deviceUUID);
        }
        return profileScopeId;
    }

    private QIOAutomationMode deviceMode(UUID deviceUUID) {
        QIOAutomationMode mode = modesByDevice.get(deviceUUID);
        if (mode == null || mode == QIOAutomationMode.OUTPUT_ONLY) {
            throw new IllegalStateException("QIO provider device has no configurable mode " +
                  deviceUUID);
        }
        return mode;
    }

    private static String checkedProfileScopeId(String profileScopeId) {
        String checked = Objects.requireNonNull(profileScopeId, "profileScopeId").trim();
        if (checked.isEmpty() || checked.length() > MAX_PROFILE_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException("profileScopeId has an invalid length");
        }
        return checked;
    }

    private void incrementAvailabilityRevision() {
        if (availabilityRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO provider availability revision exhausted");
        }
        availabilityRevision++;
    }
}
