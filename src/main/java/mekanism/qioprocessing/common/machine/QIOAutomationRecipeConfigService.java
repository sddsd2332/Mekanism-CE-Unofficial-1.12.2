package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.ProviderConformanceReport;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigSnapshot;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation.Action;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog.Selection;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileLayout;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-side authorization, paging, and CAS mutation for QIO machine recipe profiles. */
/**
 * QIO 处理模块中的 QIOAutomationRecipeConfigService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationRecipeConfigService {

    public static final int MAX_QUERY_LENGTH = 64;
    public static final int MAX_PAGE_SIZE = QIOAutomationRecipeConfigSnapshot.MAX_PAGE_SIZE;
    private static final int MAX_ROUTE_DIRECTORY_CACHE_ENTRIES = 128;
    private static final int MAX_CACHED_ROUTE_COUNT = 250_000;
    private static final Map<RouteDirectoryKey, RouteDirectory> ROUTE_DIRECTORY_CACHE =
          new LinkedHashMap<>(16, 0.75F, true);
    private static long cachedRouteCount;

    public enum MutationStatus {
        APPLIED,
        UNCHANGED,
        REVISION_CONFLICT,
        INVALID_TARGET,
        UNAVAILABLE
    }

    private QIOAutomationRecipeConfigService() {
    }

    public static synchronized void clearRouteDirectoryCache() {
        ROUTE_DIRECTORY_CACHE.clear();
        cachedRouteCount = 0;
    }

    @Nullable
    public static Context open(@Nonnull EntityPlayer player, @Nonnull TileEntity tile,
          @Nonnull QIOAutomationRecipeConfigType type) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(type, "type");
        if (!(tile instanceof TileEntityContainerBlock containerTile) ||
            !type.isInstalledIn(containerTile) || !SecurityUtils.canAccess(player, tile) ||
            !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return null;
        }
        QIOAutomationHost host = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST,
              null);
        if (host == null || host.getEnabledMode() != type.getMode() ||
            host.getState() == QIOAutomationHost.State.DRAINING_CHANGE ||
            host.getState() == QIOAutomationHost.State.IDENTITY_CONFLICT) {
            return null;
        }
        QIOFrequencyReference reference = host.getFrequencyReference();
        if (reference == null || !QIOFrequencyStorageAccess.INSTANCE.canAccess(reference,
              player.getUniqueID())) {
            return null;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              reference.getFrequencyUUID());
        MachineRecipeProviderRegistry.BoundProvider provider =
              MachineRecipeProviderRegistry.find(tile);
        if (network == null || provider == null) {
            return null;
        }
        RouteDirectory directory = routeDirectory(provider, host.getPersistentDeviceUUID(),
              type.getMode());
        return directory.conformant ? new Context(player, tile, host, reference, network,
              type, directory, canEditConfiguration(player, tile, reference)) : null;
    }

    @Nonnull
    public static QIOAutomationRecipeConfigSnapshot page(@Nonnull Context context,
          int offset, int requestedPageSize, @Nonnull String query) {
        Objects.requireNonNull(context, "context");
        String normalizedQuery = normalizeQuery(query);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedPageSize));
        QIOAutomationRecipeProfileCatalog catalog = context.network.getAutomationRecipeProfiles();
        long beforePrune = catalog.getRevision();
        catalog.pruneActive(context.host.getPersistentDeviceUUID(), context.type.getMode(),
              context.directory.profileScopeId, context.directory.layout);
        context.network.markAutomationRecipeProfilesChanged(beforePrune);

        UUID deviceUUID = context.host.getPersistentDeviceUUID();
        Selection selection = catalog.getSelection(deviceUUID, context.type.getMode(),
              context.directory.profileScopeId);
        QIOAutomationRecipeProfile profile = catalog.getActiveProfile(deviceUUID,
              context.type.getMode(), context.directory.profileScopeId);
        List<QIOAutomationRecipeProfileLayout.Route> ordered =
              context.directory.layout.ordered(profile, false);
        List<QIOAutomationRecipeProfileLayout.Route> matches = normalizedQuery.isEmpty() ?
              ordered : context.directory.matches(ordered, normalizedQuery);
        int total = matches.size();
        int checkedOffset = Math.max(0, Math.min(offset, total));
        int end = (int) Math.min((long) total, (long) checkedOffset + pageSize);

        Map<String, Integer> productOrders = new LinkedHashMap<>();
        Map<String, Integer> routeOrders = new LinkedHashMap<>();
        int productIndex = -1;
        String lastProduct = null;
        int routeIndex = 0;
        for (QIOAutomationRecipeProfileLayout.Route route : ordered) {
            if (!route.getProductKey().equals(lastProduct)) {
                lastProduct = route.getProductKey();
                productIndex++;
                routeIndex = 0;
                productOrders.put(lastProduct, productIndex);
            }
            routeOrders.put(route.getRouteKey(), routeIndex++);
        }

        QIOPolicyCatalog policies = context.network.getPolicies();
        List<QIOAutomationRecipeConfigSnapshot.Route> rows = new ArrayList<>(
              end - checkedOffset);
        for (int index = checkedOffset; index < end; index++) {
            QIOAutomationRecipeProfileLayout.Route entry = matches.get(index);
            MachineRecipeRoute route = entry.getMachineRoute();
            boolean policyAllowed = policies.isRouteEnabled(deviceUUID,
                  context.directory.providerId, route.routeId(), route.logicalRecipeKey());
            rows.add(new QIOAutomationRecipeConfigSnapshot.Route(entry.getProductKey(),
                  entry.getRouteKey(), route.routeId(), route.logicalRecipeKey(),
                  route.inputs(), route.guaranteedOutputs(),
                  profile.isRouteEnabled(entry.getRouteKey()), policyAllowed,
                  productOrders.getOrDefault(entry.getProductKey(), 0),
                  routeOrders.getOrDefault(entry.getRouteKey(), 0),
                  profile.getEffectiveCraftAmount(entry.getRouteKey(),
                        entry.getMaximumCraftAmount()), entry.getMaximumCraftAmount(),
                  profile.hasRouteCraftAmount(entry.getRouteKey())));
        }
        return new QIOAutomationRecipeConfigSnapshot(context.type,
              context.reference.getFrequencyUUID(), deviceUUID, context.directory.providerId,
              catalog.getRevision(), policies.getRevision(), checkedOffset, total,
              normalizedQuery, selection.isIndividual(), selection.getGlobalSlot(),
              selection.getFilterMode(), context.type == QIOAutomationRecipeConfigType.SCHEDULED,
              context.editable,
              QIOAutomationRecipeProfile.clampCraftAmount(profile.getCraftAmount(),
                    context.directory.layout.getMaximumCraftAmount()),
              context.directory.layout.getMaximumCraftAmount(), rows);
    }

    @Nonnull
    public static MutationResult mutate(@Nonnull Context context, long expectedRevision,
          @Nonnull QIOAutomationRecipeProfileMutation mutation) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mutation, "mutation");
        if (!context.editable) {
            return new MutationResult(MutationStatus.UNCHANGED);
        }
        QIOAutomationRecipeProfileCatalog catalog = context.network.getAutomationRecipeProfiles();
        if (expectedRevision < 0 || catalog.getRevision() != expectedRevision) {
            return new MutationResult(MutationStatus.REVISION_CONFLICT);
        }
        if (!validTarget(context, mutation)) {
            return new MutationResult(MutationStatus.INVALID_TARGET);
        }
        long before = catalog.getRevision();
        try {
            apply(context, mutation);
        } catch (RuntimeException e) {
            return new MutationResult(MutationStatus.INVALID_TARGET);
        }
        context.network.markAutomationRecipeProfilesChanged(before);
        return new MutationResult(catalog.getRevision() == before ? MutationStatus.UNCHANGED :
              MutationStatus.APPLIED);
    }

    private static boolean validTarget(Context context,
          QIOAutomationRecipeProfileMutation mutation) {
        Action action = mutation.getAction();
        boolean needsProduct = switch (action) {
            case TOGGLE_PRODUCT, DISABLE_PRODUCT, MOVE_PRODUCT, MOVE_PRODUCT_TO_TOP,
                 MOVE_PRODUCT_TO_BOTTOM, RESET_PRODUCT, MOVE_ROUTE, MOVE_ROUTE_TO_TOP,
                 MOVE_ROUTE_TO_BOTTOM, TOGGLE_ROUTE -> true;
            default -> false;
        };
        boolean needsRoute = switch (action) {
            case TOGGLE_ROUTE, MOVE_ROUTE, MOVE_ROUTE_TO_TOP, MOVE_ROUTE_TO_BOTTOM,
                 SET_ROUTE_CRAFT_AMOUNT, CLEAR_ROUTE_CRAFT_AMOUNT -> true;
            default -> false;
        };
        if (needsProduct && !context.directory.layout.getDefaultProductOrder().contains(
              mutation.getProductKey())) {
            return false;
        }
        if (needsRoute) {
            QIOAutomationRecipeProfileLayout.Route route =
                  context.directory.layout.getRoute(mutation.getRouteKey());
            if (route == null || needsProduct && !route.getProductKey().equals(
                  mutation.getProductKey())) {
                return false;
            }
        }
        return action != Action.TOGGLE_ROUTE_FILTER_MODE ||
              context.type == QIOAutomationRecipeConfigType.SCHEDULED;
    }

    private static void apply(Context context, QIOAutomationRecipeProfileMutation mutation) {
        QIOAutomationRecipeProfileCatalog catalog =
              context.network.getAutomationRecipeProfiles();
        UUID device = context.host.getPersistentDeviceUUID();
        QIOAutomationMode mode = context.type.getMode();
        String profileScopeId = context.directory.profileScopeId;
        QIOAutomationRecipeProfileLayout layout = context.directory.layout;
        switch (mutation.getAction()) {
            case TOGGLE_ROUTE -> catalog.toggleRoute(device, mode, profileScopeId, layout,
                  mutation.getRouteKey());
            case TOGGLE_PRODUCT -> catalog.toggleProduct(device, mode, profileScopeId, layout,
                  mutation.getProductKey());
            case DISABLE_PRODUCT -> catalog.setProductEnabled(device, mode, profileScopeId,
                  layout,
                  mutation.getProductKey(), false);
            case MOVE_PRODUCT -> catalog.moveProduct(device, mode, profileScopeId, layout,
                  mutation.getProductKey(), mutation.getDirection(), false, false);
            case MOVE_ROUTE -> catalog.moveRoute(device, mode, profileScopeId, layout,
                  mutation.getProductKey(), mutation.getRouteKey(), mutation.getDirection(),
                  false, false);
            case MOVE_PRODUCT_TO_TOP -> catalog.moveProduct(device, mode, profileScopeId, layout,
                  mutation.getProductKey(), 0, true, true);
            case MOVE_PRODUCT_TO_BOTTOM -> catalog.moveProduct(device, mode, profileScopeId,
                  layout,
                  mutation.getProductKey(), 0, true, false);
            case MOVE_ROUTE_TO_TOP -> catalog.moveRoute(device, mode, profileScopeId, layout,
                  mutation.getProductKey(), mutation.getRouteKey(), 0, true, true);
            case MOVE_ROUTE_TO_BOTTOM -> catalog.moveRoute(device, mode, profileScopeId, layout,
                  mutation.getProductKey(), mutation.getRouteKey(), 0, true, false);
            case SET_GLOBAL_CRAFT_AMOUNT -> catalog.setCraftAmount(device, mode,
                  profileScopeId,
                  layout, mutation.getAmount());
            case SET_ROUTE_CRAFT_AMOUNT -> catalog.setRouteCraftAmount(device, mode,
                  profileScopeId, layout, mutation.getRouteKey(), mutation.getAmount());
            case CLEAR_ROUTE_CRAFT_AMOUNT -> catalog.clearRouteCraftAmount(device, mode,
                  profileScopeId, layout, mutation.getRouteKey());
            case RESET_PRODUCT -> catalog.resetProduct(device, mode, profileScopeId, layout,
                  mutation.getProductKey());
            case RESET_ALL -> catalog.resetAll(device, mode, profileScopeId);
            case TOGGLE_PROFILE_MODE -> catalog.toggleProfileMode(device, mode,
                  profileScopeId);
            case CYCLE_GLOBAL_PROFILE -> catalog.cycleGlobalSlot(device, mode, profileScopeId,
                  mutation.getDirection());
            case TOGGLE_ROUTE_FILTER_MODE -> catalog.toggleRouteFilterMode(device, mode,
                  profileScopeId);
            case SET_ALL_ROUTES_ENABLED -> catalog.setAllRoutesEnabled(device, mode,
                  profileScopeId, layout, mutation.getDirection() > 0);
        }
    }

    @Nonnull
    private static List<MachineRecipeRoute> logicalRoutes(List<MachineRecipeRoute> source) {
        Map<String, MachineRecipeRoute> unique = new LinkedHashMap<>();
        for (MachineRecipeRoute route : source) {
            if (route != null) {
                unique.putIfAbsent(QIOAutomationRecipeProfileLayout.routeKey(route.routeId(),
                      route.logicalRecipeKey()), route);
            }
        }
        List<MachineRecipeRoute> routes = new ArrayList<>(unique.values());
        routes.sort(Comparator.comparing(MachineRecipeRoute::logicalRecipeKey)
              .thenComparing(MachineRecipeRoute::routeId)
              .thenComparing(MachineRecipeRoute::recipeKey));
        return Collections.unmodifiableList(routes);
    }

    @Nonnull
    private static RouteDirectory routeDirectory(
          @Nonnull MachineRecipeProviderRegistry.BoundProvider provider,
          @Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode) {
        String providerId;
        String profileScopeId;
        int providerRevision;
        try {
            providerId = provider.id().toString();
            profileScopeId = QIOAutomationRecipeProfileScope.resolve(provider);
            providerRevision = provider.getConfigurationRevision();
        } catch (RuntimeException ignored) {
            return RouteDirectory.unavailable();
        }
        RouteDirectoryKey key = new RouteDirectoryKey(deviceUUID, providerId, profileScopeId,
              providerRevision, mode);
        synchronized (QIOAutomationRecipeConfigService.class) {
            RouteDirectory cached = ROUTE_DIRECTORY_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        List<MachineRecipeRoute> routes;
        boolean conformant;
        try {
            routes = logicalRoutes(provider.getRecipeRoutes());
            ProviderConformanceReport report = provider.validateQIOConformance(mode);
            conformant = report.isConformant();
        } catch (RuntimeException ignored) {
            return RouteDirectory.unavailable();
        }
        RouteDirectory directory = new RouteDirectory(providerId, profileScopeId, routes,
              conformant);
        synchronized (QIOAutomationRecipeConfigService.class) {
            RouteDirectory previous = ROUTE_DIRECTORY_CACHE.put(key, directory);
            if (previous != null) {
                cachedRouteCount -= previous.layout.size();
            }
            cachedRouteCount += directory.layout.size();
            while (ROUTE_DIRECTORY_CACHE.size() > MAX_ROUTE_DIRECTORY_CACHE_ENTRIES ||
                  cachedRouteCount > MAX_CACHED_ROUTE_COUNT && ROUTE_DIRECTORY_CACHE.size() > 1) {
                RouteDirectory removed = ROUTE_DIRECTORY_CACHE.remove(
                      ROUTE_DIRECTORY_CACHE.keySet().iterator().next());
                if (removed != null) {
                    cachedRouteCount -= removed.layout.size();
                }
            }
        }
        return directory;
    }

    @Nonnull
    private static String normalizeQuery(@Nonnull String query) {
        String checked = Objects.requireNonNull(query, "query").trim();
        if (checked.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("QIO automation config query is too long");
        }
        return checked.toLowerCase(Locale.ROOT);
    }

    /** Matches AE profile ownership: access may be shared, configuration ownership is not. */
    public static boolean canEditConfiguration(@Nonnull EntityPlayer player,
          @Nonnull TileEntity tile, @Nonnull QIOFrequencyReference reference) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(reference, "reference");
        UUID owner = reference.getOwnerUUID();
        if (owner == null && tile instanceof ISecurityTile securityTile) {
            owner = securityTile.getSecurity().getOwnerUUID();
        }
        if (owner == null) {
            owner = reference.getBindingPlayerUUID();
        }
        return owner != null && owner.equals(player.getUniqueID());
    }

    public static final class Context {

        private final EntityPlayer player;
        private final TileEntity tile;
        private final QIOAutomationHost host;
        private final QIOFrequencyReference reference;
        private final QIOProcessingNetworkData network;
        private final QIOAutomationRecipeConfigType type;
        private final RouteDirectory directory;
        private final boolean editable;

        private Context(EntityPlayer player, TileEntity tile, QIOAutomationHost host,
              QIOFrequencyReference reference, QIOProcessingNetworkData network,
              QIOAutomationRecipeConfigType type, RouteDirectory directory,
              boolean editable) {
            this.player = player;
            this.tile = tile;
            this.host = host;
            this.reference = reference;
            this.network = network;
            this.type = type;
            this.directory = directory;
            this.editable = editable;
        }

        public EntityPlayer getPlayer() { return player; }
        public TileEntity getTile() { return tile; }
        boolean isEditable() { return editable; }
        QIOAutomationHost getHost() { return host; }
        QIOProcessingNetworkData getNetwork() { return network; }
        QIOAutomationRecipeConfigType getType() { return type; }
        String getProviderId() { return directory.providerId; }
        String getProfileScopeId() { return directory.profileScopeId; }
        QIOAutomationRecipeProfileLayout getLayout() { return directory.layout; }
    }

    public static final class MutationResult {

        private final MutationStatus status;

        private MutationResult(MutationStatus status) { this.status = status; }

        @Nonnull
        public MutationStatus getStatus() { return status; }
    }

    private static final class RouteDirectory {

        private final String providerId;
        private final String profileScopeId;
        private final QIOAutomationRecipeProfileLayout layout;
        private final Map<String, String> searchValues;
        private final boolean conformant;

        private RouteDirectory(String providerId, String profileScopeId,
              List<MachineRecipeRoute> routes, boolean conformant) {
            this.providerId = providerId;
            this.profileScopeId = profileScopeId;
            this.layout = new QIOAutomationRecipeProfileLayout(routes);
            this.conformant = conformant;
            Map<String, String> searches = new LinkedHashMap<>();
            for (QIOAutomationRecipeProfileLayout.Route route : layout.ordered(
                  new QIOAutomationRecipeProfile(
                        QIOAutomationRecipeProfile.RouteFilterMode.BLACKLIST), false)) {
                MachineRecipeRoute machineRoute = route.getMachineRoute();
                searches.put(route.getRouteKey(), (route.getProductKey() + '\u0000' +
                      machineRoute.routeId() + '\u0000' + machineRoute.logicalRecipeKey() +
                      '\u0000' + machineRoute.recipeKey()).toLowerCase(Locale.ROOT));
            }
            searchValues = Collections.unmodifiableMap(searches);
        }

        private static RouteDirectory unavailable() {
            return new RouteDirectory("", "", Collections.emptyList(), false);
        }

        private boolean contains(String routeId, String recipeKey) {
            return layout.contains(routeId, recipeKey);
        }

        private List<QIOAutomationRecipeProfileLayout.Route> matches(
              List<QIOAutomationRecipeProfileLayout.Route> routes, String query) {
            List<QIOAutomationRecipeProfileLayout.Route> matches = new ArrayList<>();
            for (QIOAutomationRecipeProfileLayout.Route route : routes) {
                if (searchValues.getOrDefault(route.getRouteKey(), "").contains(query)) {
                    matches.add(route);
                }
            }
            return matches;
        }
    }

    private static final class RouteDirectoryKey {

        private final UUID deviceUUID;
        private final String providerId;
        private final String profileScopeId;
        private final int providerRevision;
        private final QIOAutomationMode mode;

        private RouteDirectoryKey(UUID deviceUUID, String providerId, String profileScopeId,
              int providerRevision, QIOAutomationMode mode) {
            this.deviceUUID = deviceUUID;
            this.providerId = providerId;
            this.profileScopeId = profileScopeId;
            this.providerRevision = providerRevision;
            this.mode = mode;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RouteDirectoryKey other &&
                  providerRevision == other.providerRevision && mode == other.mode &&
                  deviceUUID.equals(other.deviceUUID) && providerId.equals(other.providerId) &&
                  profileScopeId.equals(other.profileScopeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceUUID, providerId, profileScopeId, providerRevision, mode);
        }
    }
}
