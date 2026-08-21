package mekanism.qioprocessing.common.content.profile;

import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Stable product grouping and default ordering for one provider route directory. */
/**
 * QIO 处理模块中的 QIOAutomationRecipeProfileLayout 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationRecipeProfileLayout {

    private final List<String> defaultProductOrder;
    private final Map<String, List<String>> defaultRoutesByProduct;
    private final Map<String, Route> routesByKey;
    private final Set<String> routeKeys;
    private final long maximumCraftAmount;

    public QIOAutomationRecipeProfileLayout(@Nonnull Collection<MachineRecipeRoute> source) {
        Objects.requireNonNull(source, "source");
        Map<String, MachineRecipeRoute> logical = new LinkedHashMap<>();
        for (MachineRecipeRoute route : source) {
            if (route != null) {
                logical.putIfAbsent(routeKey(route.routeId(), route.logicalRecipeKey()), route);
            }
        }
        List<Route> routes = new ArrayList<>(logical.size());
        for (MachineRecipeRoute route : logical.values()) {
            routes.add(new Route(route));
        }
        routes.sort(Comparator.comparing(Route::getProductKey)
              .thenComparing(Route::getRouteId)
              .thenComparing(Route::getRecipeKey));

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        Map<String, Route> indexed = new LinkedHashMap<>();
        long maximum = QIOAutomationRecipeProfile.MAX_CRAFT_AMOUNT;
        for (Route route : routes) {
            grouped.computeIfAbsent(route.productKey, ignored -> new ArrayList<>())
                  .add(route.routeKey);
            indexed.put(route.routeKey, route);
            maximum = Math.min(maximum, route.maximumCraftAmount);
        }
        List<String> products = new ArrayList<>(grouped.keySet());
        Map<String, List<String>> immutableGroups = new LinkedHashMap<>();
        grouped.forEach((key, value) -> immutableGroups.put(key,
              Collections.unmodifiableList(new ArrayList<>(value))));
        defaultProductOrder = Collections.unmodifiableList(products);
        defaultRoutesByProduct = Collections.unmodifiableMap(immutableGroups);
        routesByKey = Collections.unmodifiableMap(indexed);
        routeKeys = Collections.unmodifiableSet(new LinkedHashSet<>(indexed.keySet()));
        maximumCraftAmount = Math.max(QIOAutomationRecipeProfile.DEFAULT_CRAFT_AMOUNT,
              maximum);
    }

    @Nonnull
    public List<String> getDefaultProductOrder() {
        return defaultProductOrder;
    }

    @Nonnull
    public List<String> getDefaultRouteOrder(@Nonnull String productKey) {
        return defaultRoutesByProduct.getOrDefault(productKey, Collections.emptyList());
    }

    @Nonnull
    public Set<String> getRouteKeys() {
        return routeKeys;
    }

    @Nullable
    public Route getRoute(@Nonnull String routeKey) {
        return routesByKey.get(routeKey);
    }

    public long getMaximumCraftAmount() {
        return maximumCraftAmount;
    }

    public int size() {
        return routesByKey.size();
    }

    public boolean contains(@Nonnull String routeId, @Nonnull String recipeKey) {
        return routesByKey.containsKey(routeKey(routeId, recipeKey));
    }

    @Nonnull
    public List<Route> ordered(@Nonnull QIOAutomationRecipeProfile profile,
          boolean enabledOnly) {
        Objects.requireNonNull(profile, "profile");
        List<Route> ordered = new ArrayList<>(routesByKey.size());
        for (String productKey : profile.getCurrentProductOrder(defaultProductOrder)) {
            for (String routeKey : profile.getCurrentRouteOrder(productKey,
                  getDefaultRouteOrder(productKey))) {
                Route route = routesByKey.get(routeKey);
                if (route != null && (!enabledOnly || profile.isRouteEnabled(routeKey))) {
                    ordered.add(route);
                }
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    @Nonnull
    public static String routeKey(@Nonnull String routeId, @Nonnull String recipeKey) {
        String checkedRoute = Objects.requireNonNull(routeId, "routeId");
        String checkedRecipe = Objects.requireNonNull(recipeKey, "recipeKey");
        return checkedRoute.length() + ":" + checkedRoute + checkedRecipe;
    }

    @Nonnull
    public static PortableResourceDescriptor describe(@Nonnull MachineResourceStack stack) {
        Objects.requireNonNull(stack, "stack");
        return switch (stack.kind()) {
            case ITEM -> PortableResourceDescriptor.item(stack.itemStack());
            case FLUID -> PortableResourceDescriptor.fluid(
                  Objects.requireNonNull(stack.fluidStack(), "route fluid"));
            case GAS -> PortableResourceDescriptor.gas(
                  Objects.requireNonNull(stack.gasStack(), "route gas"));
        };
    }

    public static final class Route {

        private final MachineRecipeRoute route;
        private final String routeKey;
        private final PortableResourceDescriptor product;
        private final String productKey;
        private final long maximumCraftAmount;

        private Route(MachineRecipeRoute route) {
            this.route = Objects.requireNonNull(route, "route");
            routeKey = routeKey(route.routeId(), route.logicalRecipeKey());
            product = describe(route.guaranteedOutputs().get(0));
            productKey = product.toString();
            long maximum = QIOAutomationRecipeProfile.MAX_CRAFT_AMOUNT;
            List<MachineResourceStack> stacks = new ArrayList<>();
            stacks.addAll(route.inputs());
            stacks.addAll(route.guaranteedOutputs());
            stacks.addAll(route.optionalOutputs());
            for (MachineResourceStack stack : stacks) {
                maximum = Math.min(maximum, Integer.MAX_VALUE / stack.amount());
            }
            // Optional outputs are not guaranteed to accumulate linearly, so they remain single-operation routes.
            if (!route.optionalOutputs().isEmpty()) {
                maximum = 1;
            }
            maximumCraftAmount = Math.max(1, maximum);
        }

        @Nonnull
        public MachineRecipeRoute getMachineRoute() {
            return route;
        }

        @Nonnull
        public String getRouteKey() {
            return routeKey;
        }

        @Nonnull
        public String getRouteId() {
            return route.routeId();
        }

        @Nonnull
        public String getRecipeKey() {
            return route.logicalRecipeKey();
        }

        @Nonnull
        public PortableResourceDescriptor getProduct() {
            return product;
        }

        @Nonnull
        public String getProductKey() {
            return productKey;
        }

        public long getMaximumCraftAmount() {
            return maximumCraftAmount;
        }
    }
}
