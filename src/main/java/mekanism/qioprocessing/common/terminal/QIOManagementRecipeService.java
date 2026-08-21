package mekanism.qioprocessing.common.terminal;

import mekanism.api.gas.GasStack;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation.Action;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog.Selection;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileLayout;
import mekanism.qioprocessing.common.planning.QIOPlanningRoute;
import mekanism.qioprocessing.common.planning.QIOProviderCatalog;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.ResourceAmount;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Route;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-authoritative remote recipe-profile access for management terminals. */
/**
 * QIO 处理模块中的 QIOManagementRecipeService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOManagementRecipeService {

    public static final int MAX_QUERY_LENGTH = 64;

    public enum ProductFilter {
        ALL,
        ENABLED,
        DISABLED,
        PARTIAL
    }

    public enum MutationStatus {
        APPLIED,
        UNCHANGED,
        REVISION_CONFLICT,
        INVALID_TARGET,
        READ_ONLY
    }

    private QIOManagementRecipeService() {
    }

    @Nullable
    /** 打开管理配方查询上下文。 */
    public static Context open(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID playerUUID, @Nonnull UUID deviceUUID) {
        validateSession(session, network, currentAccessRevision);
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(deviceUUID, "deviceUUID");
        if (!session.getPlayerUUID().equals(playerUUID)) {
            throw new SecurityException("QIO management recipe player changed");
        }
        QIOAutomationDeviceDirectoryCleanupService.pruneLoadedStaleRecords(network);
        QIOAutomationDeviceSnapshot device = network.getAutomationDevices().get(deviceUUID);
        if (device == null ||
            device.getKind() != QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE) {
            return null;
        }
        QIOAutomationMode mode = device.getMode();
        if (mode == null || mode == QIOAutomationMode.OUTPUT_ONLY) {
            return null;
        }
        QIOProviderCatalog providerCatalog = network.getProviderCatalog();
        if (providerCatalog.getDeviceMode(deviceUUID) != mode ||
            !device.getProfileScopeId().equals(
                  providerCatalog.getDeviceProfileScopeId(deviceUUID))) {
            return null;
        }
        List<QIOPlanningRoute> routes = providerCatalog.getDeviceRoutes(deviceUUID);
        if (routes.isEmpty()) {
            return null;
        }
        RouteDirectory directory;
        try {
            directory = new RouteDirectory(device.getProviderId(), routes);
        } catch (RuntimeException e) {
            return null;
        }
        UUID owner = network.getLastKnownFrequencyIdentity().getOwnerUUID();
        boolean editable = owner != null && owner.equals(playerUUID);
        return new Context(network, device, mode, directory, editable);
    }

    @Nonnull
    /** 查询管理端产品目录分页。 */
    public static QIOManagementRecipeSnapshot products(@Nonnull Context context,
          int requestedOffset, int requestedPageSize, @Nonnull String query,
          @Nonnull ProductFilter filter) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(filter, "filter");
        String normalizedQuery = normalizeQuery(query);
        ProfileView profileView = profileView(context);
        List<QIOAutomationRecipeProfileLayout.Route> ordered =
              context.directory.layout.ordered(profileView.profile, false);
        Map<String, ProductBuilder> grouped = new LinkedHashMap<>();
        QIOPolicyCatalog policies = context.network.getPolicies();
        int order = 0;
        for (QIOAutomationRecipeProfileLayout.Route layoutRoute : ordered) {
            QIOPlanningRoute route = context.directory.routesByKey.get(
                  layoutRoute.getRouteKey());
            if (route == null) {
                continue;
            }
            ProductBuilder builder = grouped.get(layoutRoute.getProductKey());
            if (builder == null) {
                PortableResourceDescriptor product = layoutRoute.getProduct();
                long amount = route.getGuaranteedOutputs().getOrDefault(product, 1L);
                builder = new ProductBuilder(layoutRoute.getProductKey(),
                      new ResourceAmount(product, Math.max(1, amount)), order++);
                grouped.put(layoutRoute.getProductKey(), builder);
            }
            boolean profileEnabled = profileView.profile.isRouteEnabled(
                  layoutRoute.getRouteKey());
            boolean policyAllowed = policies.isRouteEnabled(context.device.getDeviceUUID(),
                  context.directory.providerId, route.getRouteId(), route.getRecipeKey());
            builder.add(profileEnabled, policyAllowed);
        }
        List<Product> matches = new ArrayList<>();
        for (ProductBuilder builder : grouped.values()) {
            Product product = builder.build();
            if (matches(filter, product) && matchesProductQuery(normalizedQuery, product)) {
                matches.add(product);
            }
        }
        int pageSize = checkedPageSize(requestedPageSize);
        int offset = Math.max(0, Math.min(requestedOffset, matches.size()));
        int end = Math.min(matches.size(), offset + pageSize);
        return snapshot(context, profileView, PageKind.PRODUCTS, offset, matches.size(),
              normalizedQuery, "", matches.subList(offset, end), Collections.emptyList());
    }

    @Nonnull
    /** 查询指定产品的路线分页。 */
    public static QIOManagementRecipeSnapshot routes(@Nonnull Context context,
          @Nonnull String productKey, int requestedOffset, int requestedPageSize,
          @Nonnull String query) {
        Objects.requireNonNull(context, "context");
        String checkedProduct = Objects.requireNonNull(productKey, "productKey").trim();
        String normalizedQuery = normalizeQuery(query);
        ProfileView profileView = profileView(context);
        List<String> orderedRouteKeys = profileView.profile.getCurrentRouteOrder(
              checkedProduct, context.directory.layout.getDefaultRouteOrder(checkedProduct));
        List<Route> matches = new ArrayList<>();
        QIOPolicyCatalog policies = context.network.getPolicies();
        int order = 0;
        for (String routeKey : orderedRouteKeys) {
            QIOPlanningRoute route = context.directory.routesByKey.get(routeKey);
            QIOAutomationRecipeProfileLayout.Route layoutRoute =
                  context.directory.layout.getRoute(routeKey);
            if (route == null || layoutRoute == null ||
                !checkedProduct.equals(layoutRoute.getProductKey())) {
                continue;
            }
            Route snapshot = new Route(checkedProduct, routeKey, route.getRouteId(),
                  route.getRecipeKey(), amounts(route.getConfigurationInputs()),
                  amounts(route.getExactInputs()),
                  amounts(route.getGuaranteedOutputs()), amounts(route.getOptionalOutputs()),
                  profileView.profile.isRouteEnabled(routeKey),
                  policies.isRouteEnabled(context.device.getDeviceUUID(),
                        context.directory.providerId, route.getRouteId(), route.getRecipeKey()),
                  order++, profileView.profile.getEffectiveCraftAmount(routeKey,
                        layoutRoute.getMaximumCraftAmount()),
                  layoutRoute.getMaximumCraftAmount(),
                  profileView.profile.hasRouteCraftAmount(routeKey));
            if (matchesRouteQuery(normalizedQuery, snapshot)) {
                matches.add(snapshot);
            }
        }
        int pageSize = checkedPageSize(requestedPageSize);
        int offset = Math.max(0, Math.min(requestedOffset, matches.size()));
        int end = Math.min(matches.size(), offset + pageSize);
        return snapshot(context, profileView, PageKind.ROUTES, offset, matches.size(),
              normalizedQuery, checkedProduct, Collections.emptyList(),
              matches.subList(offset, end));
    }

    @Nonnull
    /** 修改管理端配方配置并校验目标版本。 */
    public static MutationStatus mutate(@Nonnull Context context, long expectedRevision,
          @Nonnull QIOAutomationRecipeProfileMutation mutation) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mutation, "mutation");
        if (!context.editable) {
            return MutationStatus.READ_ONLY;
        }
        QIOAutomationRecipeProfileCatalog catalog =
              context.network.getAutomationRecipeProfiles();
        if (expectedRevision < 0 || catalog.getRevision() != expectedRevision) {
            return MutationStatus.REVISION_CONFLICT;
        }
        if (!validTarget(context, mutation)) {
            return MutationStatus.INVALID_TARGET;
        }
        long before = catalog.getRevision();
        try {
            apply(context, mutation);
        } catch (RuntimeException e) {
            return MutationStatus.INVALID_TARGET;
        }
        context.network.markAutomationRecipeProfilesChanged(before);
        return catalog.getRevision() == before ? MutationStatus.UNCHANGED :
              MutationStatus.APPLIED;
    }

    private static QIOManagementRecipeSnapshot snapshot(Context context,
          ProfileView profileView, PageKind kind, int offset, int totalSize, String query,
          String productKey, List<Product> products, List<Route> routes) {
        return new QIOManagementRecipeSnapshot(kind, context.device.getDeviceUUID(),
              context.mode, context.directory.providerId,
              context.device.getProfileScopeId(),
              context.network.getProviderCatalog().getRevision(),
              context.network.getAutomationRecipeProfiles().getRevision(),
              context.network.getPolicies().getRevision(),
              profileView.selection.isIndividual(), profileView.selection.getGlobalSlot(),
              profileView.selection.getFilterMode(), context.editable,
              QIOAutomationRecipeProfile.clampCraftAmount(
                    profileView.profile.getCraftAmount(),
                    context.directory.layout.getMaximumCraftAmount()),
              context.directory.layout.getMaximumCraftAmount(), offset, totalSize, query,
              productKey, products, routes);
    }

    private static ProfileView profileView(Context context) {
        QIOAutomationRecipeProfileCatalog catalog =
              context.network.getAutomationRecipeProfiles();
        UUID deviceUUID = context.device.getDeviceUUID();
        String scope = context.device.getProfileScopeId();
        long beforePrune = catalog.getRevision();
        catalog.pruneActive(deviceUUID, context.mode, scope, context.directory.layout);
        context.network.markAutomationRecipeProfilesChanged(beforePrune);
        Selection selection = catalog.getSelection(deviceUUID, context.mode, scope);
        QIOAutomationRecipeProfile profile = catalog.getActiveProfile(deviceUUID,
              context.mode, scope);
        return new ProfileView(selection, profile);
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
              context.mode == QIOAutomationMode.SCHEDULED;
    }

    private static void apply(Context context,
          QIOAutomationRecipeProfileMutation mutation) {
        QIOAutomationRecipeProfileCatalog catalog =
              context.network.getAutomationRecipeProfiles();
        UUID device = context.device.getDeviceUUID();
        QIOAutomationMode mode = context.mode;
        String scope = context.device.getProfileScopeId();
        QIOAutomationRecipeProfileLayout layout = context.directory.layout;
        switch (mutation.getAction()) {
            case TOGGLE_ROUTE -> catalog.toggleRoute(device, mode, scope, layout,
                  mutation.getRouteKey());
            case TOGGLE_PRODUCT -> catalog.toggleProduct(device, mode, scope, layout,
                  mutation.getProductKey());
            case DISABLE_PRODUCT -> catalog.setProductEnabled(device, mode, scope, layout,
                  mutation.getProductKey(), false);
            case MOVE_PRODUCT -> catalog.moveProduct(device, mode, scope, layout,
                  mutation.getProductKey(), mutation.getDirection(), false, false);
            case MOVE_ROUTE -> catalog.moveRoute(device, mode, scope, layout,
                  mutation.getProductKey(), mutation.getRouteKey(), mutation.getDirection(),
                  false, false);
            case MOVE_PRODUCT_TO_TOP -> catalog.moveProduct(device, mode, scope, layout,
                  mutation.getProductKey(), 0, true, true);
            case MOVE_PRODUCT_TO_BOTTOM -> catalog.moveProduct(device, mode, scope, layout,
                  mutation.getProductKey(), 0, true, false);
            case MOVE_ROUTE_TO_TOP -> catalog.moveRoute(device, mode, scope, layout,
                  mutation.getProductKey(), mutation.getRouteKey(), 0, true, true);
            case MOVE_ROUTE_TO_BOTTOM -> catalog.moveRoute(device, mode, scope, layout,
                  mutation.getProductKey(), mutation.getRouteKey(), 0, true, false);
            case SET_GLOBAL_CRAFT_AMOUNT -> catalog.setCraftAmount(device, mode, scope, layout,
                  mutation.getAmount());
            case SET_ROUTE_CRAFT_AMOUNT -> catalog.setRouteCraftAmount(device, mode, scope,
                  layout, mutation.getRouteKey(), mutation.getAmount());
            case CLEAR_ROUTE_CRAFT_AMOUNT -> catalog.clearRouteCraftAmount(device, mode, scope,
                  layout, mutation.getRouteKey());
            case RESET_PRODUCT -> catalog.resetProduct(device, mode, scope, layout,
                  mutation.getProductKey());
            case RESET_ALL -> catalog.resetAll(device, mode, scope);
            case TOGGLE_PROFILE_MODE -> catalog.toggleProfileMode(device, mode, scope);
            case CYCLE_GLOBAL_PROFILE -> catalog.cycleGlobalSlot(device, mode, scope,
                  mutation.getDirection());
            case TOGGLE_ROUTE_FILTER_MODE -> catalog.toggleRouteFilterMode(device, mode, scope);
            case SET_ALL_ROUTES_ENABLED -> catalog.setAllRoutesEnabled(device, mode, scope,
                  layout, mutation.getDirection() > 0);
        }
    }

    private static boolean matches(ProductFilter filter, Product product) {
        return switch (filter) {
            case ALL -> true;
            case ENABLED -> product.getProfileEnabledCount() > 0;
            case DISABLED -> product.getProfileEnabledCount() == 0;
            case PARTIAL -> product.getProfileEnabledCount() > 0 &&
                  product.getProfileEnabledCount() < product.getRouteCount();
        };
    }

    private static boolean matchesProductQuery(String query, Product product) {
        if (query.isEmpty()) {
            return true;
        }
        PortableResourceDescriptor resource = product.getProduct().getResource();
        return product.getProductKey().toLowerCase(Locale.ROOT).contains(query) ||
              resource.getRegistryName().toLowerCase(Locale.ROOT).contains(query) ||
              displayName(resource).contains(query);
    }

    private static boolean matchesRouteQuery(String query, Route route) {
        if (query.isEmpty() || route.getRouteId().toLowerCase(Locale.ROOT).contains(query) ||
            route.getRecipeKey().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (ResourceAmount amount : route.getInputs()) {
            if (matchesResource(query, amount.getResource())) {
                return true;
            }
        }
        for (ResourceAmount amount : route.getConfigurationInputs()) {
            if (matchesResource(query, amount.getResource())) {
                return true;
            }
        }
        for (ResourceAmount amount : route.getOutputs()) {
            if (matchesResource(query, amount.getResource())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesResource(String query,
          PortableResourceDescriptor resource) {
        return resource.getRegistryName().toLowerCase(Locale.ROOT).contains(query) ||
              displayName(resource).contains(query);
    }

    private static String displayName(PortableResourceDescriptor resource) {
        try {
            return switch (resource.getKind()) {
                case ITEM -> {
                    ItemStack stack = resource.resolveItem();
                    yield stack.isEmpty() ? "" : stack.getDisplayName().toLowerCase(Locale.ROOT);
                }
                case FLUID -> {
                    FluidStack stack = resource.resolveFluid();
                    yield stack == null ? "" : stack.getLocalizedName().toLowerCase(Locale.ROOT);
                }
                case GAS -> {
                    GasStack stack = resource.resolveGas();
                    yield stack == null ? "" : stack.getGas().getName().toLowerCase(Locale.ROOT);
                }
            };
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static List<ResourceAmount> amounts(
          Map<PortableResourceDescriptor, Long> source) {
        List<ResourceAmount> result = new ArrayList<>(source.size());
        source.forEach((resource, amount) -> result.add(new ResourceAmount(resource, amount)));
        return Collections.unmodifiableList(result);
    }

    private static MachineResourceStack machineStack(PortableResourceDescriptor resource,
          long amount, String portId) {
        return switch (resource.getKind()) {
            case ITEM -> MachineResourceStack.item(portId, requireItem(resource), amount);
            case FLUID -> MachineResourceStack.fluid(portId, requireFluid(resource), amount);
            case GAS -> MachineResourceStack.gas(portId, requireGas(resource), amount);
        };
    }

    private static ItemStack requireItem(PortableResourceDescriptor resource) {
        ItemStack stack = resource.resolveItem();
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Unresolved route item " + resource);
        }
        return stack;
    }

    private static FluidStack requireFluid(PortableResourceDescriptor resource) {
        FluidStack stack = resource.resolveFluid();
        if (stack == null) {
            throw new IllegalArgumentException("Unresolved route fluid " + resource);
        }
        return stack;
    }

    private static GasStack requireGas(PortableResourceDescriptor resource) {
        GasStack stack = resource.resolveGas();
        if (stack == null) {
            throw new IllegalArgumentException("Unresolved route gas " + resource);
        }
        return stack;
    }

    private static int checkedPageSize(int requested) {
        return Math.max(1, Math.min(QIOManagementRecipeSnapshot.MAX_PAGE_SIZE, requested));
    }

    private static String normalizeQuery(String query) {
        String checked = Objects.requireNonNull(query, "query").trim();
        if (checked.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Remote recipe query is too long");
        }
        return checked.toLowerCase(Locale.ROOT);
    }

    private static void validateSession(QIOProcessingTerminalSession session,
          QIOProcessingNetworkData network, long currentAccessRevision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        if (!session.isOpen() ||
            session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT ||
            session.getFrequencyUUID() == null ||
            !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("Invalid QIO management recipe session");
        }
        if (currentAccessRevision < 0 ||
            session.getAccessRevision() != currentAccessRevision) {
            throw new IllegalStateException("QIO frequency access changed during the session");
        }
    }

    public static final class Context {

        private final QIOProcessingNetworkData network;
        private final QIOAutomationDeviceSnapshot device;
        private final QIOAutomationMode mode;
        private final RouteDirectory directory;
        private final boolean editable;

        private Context(QIOProcessingNetworkData network,
              QIOAutomationDeviceSnapshot device, QIOAutomationMode mode,
              RouteDirectory directory, boolean editable) {
            this.network = network;
            this.device = device;
            this.mode = mode;
            this.directory = directory;
            this.editable = editable;
        }

        public boolean isEditable() { return editable; }
        @Nonnull public UUID getDeviceUUID() { return device.getDeviceUUID(); }
        @Nonnull public QIOAutomationMode getMode() { return mode; }
    }

    private static final class ProfileView {

        private final Selection selection;
        private final QIOAutomationRecipeProfile profile;

        private ProfileView(Selection selection, QIOAutomationRecipeProfile profile) {
            this.selection = selection;
            this.profile = profile;
        }
    }

    private static final class ProductBuilder {

        private final String productKey;
        private final ResourceAmount product;
        private final int order;
        private int routeCount;
        private int profileEnabledCount;
        private int effectiveEnabledCount;

        private ProductBuilder(String productKey, ResourceAmount product, int order) {
            this.productKey = productKey;
            this.product = product;
            this.order = order;
        }

        private void add(boolean profileEnabled, boolean policyAllowed) {
            routeCount++;
            if (profileEnabled) {
                profileEnabledCount++;
                if (policyAllowed) {
                    effectiveEnabledCount++;
                }
            }
        }

        private Product build() {
            return new Product(productKey, product, routeCount, profileEnabledCount,
                  effectiveEnabledCount, order);
        }
    }

    private static final class RouteDirectory {

        private final String providerId;
        private final QIOAutomationRecipeProfileLayout layout;
        private final Map<String, QIOPlanningRoute> routesByKey;

        private RouteDirectory(String providerId, List<QIOPlanningRoute> source) {
            this.providerId = Objects.requireNonNull(providerId, "providerId");
            Map<String, QIOPlanningRoute> indexed = new LinkedHashMap<>();
            List<MachineRecipeRoute> machineRoutes = new ArrayList<>();
            for (QIOPlanningRoute route : source) {
                if (!providerId.equals(route.getProviderId())) {
                    throw new IllegalArgumentException("Device route belongs to another provider");
                }
                MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder(route.getRouteId())
                      .recipeKey(route.getRecipeKey()).logicalRecipeKey(route.getRecipeKey());
                int index = 0;
                for (Map.Entry<PortableResourceDescriptor, Long> input :
                      route.getExactInputs().entrySet()) {
                    builder.input(machineStack(input.getKey(), input.getValue(),
                          "input_" + index++));
                }
                index = 0;
                for (Map.Entry<PortableResourceDescriptor, Long> output :
                      route.getGuaranteedOutputs().entrySet()) {
                    builder.output(machineStack(output.getKey(), output.getValue(),
                          "output_" + index++));
                }
                index = 0;
                for (Map.Entry<PortableResourceDescriptor, Long> output :
                      route.getOptionalOutputs().entrySet()) {
                    builder.optionalOutput(machineStack(output.getKey(), output.getValue(),
                          "optional_" + index++));
                }
                MachineRecipeRoute machineRoute = builder.build();
                String routeKey = QIOAutomationRecipeProfileLayout.routeKey(
                      machineRoute.routeId(), machineRoute.logicalRecipeKey());
                if (indexed.putIfAbsent(routeKey, route) == null) {
                    machineRoutes.add(machineRoute);
                }
            }
            if (machineRoutes.isEmpty()) {
                throw new IllegalArgumentException("Device has no valid retained routes");
            }
            layout = new QIOAutomationRecipeProfileLayout(machineRoutes);
            routesByKey = Collections.unmodifiableMap(indexed);
        }
    }
}
