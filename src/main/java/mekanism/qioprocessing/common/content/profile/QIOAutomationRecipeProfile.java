package mekanism.qioprocessing.common.content.profile;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * One ordered route-selection profile used by a QIO automation provider.
 */

/**
 * QIO 处理模块中的 QIOAutomationRecipeProfile 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationRecipeProfile {

    public static final long DEFAULT_CRAFT_AMOUNT = 1;
    public static final long MAX_CRAFT_AMOUNT = Long.MAX_VALUE;
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_ENTRIES = 1_000_000;
    private static final int MAX_KEY_LENGTH = 8_192;

    public enum RouteFilterMode {
        BLACKLIST,
        WHITELIST;

        @Nonnull
        public RouteFilterMode next() {
            return this == BLACKLIST ? WHITELIST : BLACKLIST;
        }
    }

    private final RouteFilterMode routeFilterMode;
    private final Set<String> enabledRoutes = new LinkedHashSet<>();
    private final Set<String> disabledRoutes = new LinkedHashSet<>();
    private final List<String> orderedProducts = new ArrayList<>();
    private final Map<String, List<String>> orderedRoutes = new LinkedHashMap<>();
    private final Map<String, Long> routeCraftAmounts = new LinkedHashMap<>();
    // Batch defaults are shared by recipe family, never by block type or factory tier.
    private final Map<String, Long> familyCraftAmounts = new LinkedHashMap<>();
    private long craftAmount = DEFAULT_CRAFT_AMOUNT;
    private Set<String> customOrderedFamilies;

    public QIOAutomationRecipeProfile(@Nonnull RouteFilterMode routeFilterMode) {
        this.routeFilterMode = Objects.requireNonNull(routeFilterMode, "routeFilterMode");
    }

    @Nonnull
    public RouteFilterMode getRouteFilterMode() {
        return routeFilterMode;
    }

    public long getCraftAmount() {
        return craftAmount;
    }

    public long getCraftAmount(@Nonnull String family) {
        return familyCraftAmounts.getOrDefault(checkedKey(family, "recipeFamily"), craftAmount);
    }

    public boolean setCraftAmount(@Nonnull String family, long amount) {
        String checked = checkedKey(family, "recipeFamily");
        long value = clampCraftAmount(amount, MAX_CRAFT_AMOUNT);
        if (getCraftAmount(checked) == value) return false;
        if (value == craftAmount) familyCraftAmounts.remove(checked);
        else familyCraftAmounts.put(checked, value);
        String prefix = familyPrefix(checked);
        routeCraftAmounts.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix) &&
                entry.getValue() == value);
        return true;
    }

    private static String familyPrefix(String family) {
        return family.length() + ":" + family;
    }

    private long defaultAmountForRoute(String key) {
        int separator = key.indexOf(':');
        if (separator > 0 && separator < 6) {
            try {
                int length = Integer.parseInt(key.substring(0, separator));
                int end = separator + 1 + length;
                if (length > 0 && end <= key.length()) {
                    return familyCraftAmounts.getOrDefault(key.substring(separator + 1, end), craftAmount);
                }
            } catch (NumberFormatException ignored) {
                // Unscoped test keys have only the profile default.
            }
        }
        return craftAmount;
    }

    public boolean isEmpty() {
        return enabledRoutes.isEmpty() && disabledRoutes.isEmpty() && orderedProducts.isEmpty() &&
                orderedRoutes.isEmpty() && routeCraftAmounts.isEmpty() &&
                familyCraftAmounts.isEmpty() && craftAmount == DEFAULT_CRAFT_AMOUNT;
    }

    public boolean hasCustomOrder() {
        return !orderedProducts.isEmpty() || !orderedRoutes.isEmpty();
    }

    public boolean hasCustomOrder(@Nonnull String family) {
        String checked = checkedKey(family, "recipeFamily");
        if (customOrderedFamilies == null) {
            Set<String> indexed = new HashSet<>();
            for (String key : orderedProducts) indexOrderFamily(indexed, key);
            for (String key : orderedRoutes.keySet()) indexOrderFamily(indexed, key);
            customOrderedFamilies = indexed;
        }
        return customOrderedFamilies.contains(checked);
    }

    private static void indexOrderFamily(Set<String> families, String key) {
        int separator = key.indexOf(':');
        if (separator <= 0 || separator >= 6) return;
        try {
            int length = Integer.parseInt(key.substring(0, separator));
            int end = separator + 1 + length;
            if (length > 0 && end <= key.length()) families.add(key.substring(separator + 1, end));
        } catch (NumberFormatException ignored) {
            // The retained unscoped helper API has no family-specific order.
        }
    }

    public boolean isRouteEnabled(@Nonnull String routeKey) {
        String checked = checkedKey(routeKey, "routeKey");
        return routeFilterMode == RouteFilterMode.WHITELIST ? enabledRoutes.contains(checked) :
                !disabledRoutes.contains(checked);
    }

    public boolean setRouteEnabled(@Nonnull String routeKey, boolean enabled) {
        String checked = checkedKey(routeKey, "routeKey");
        return routeFilterMode == RouteFilterMode.WHITELIST ?
                enabled ? enabledRoutes.add(checked) : enabledRoutes.remove(checked) :
                enabled ? disabledRoutes.remove(checked) : disabledRoutes.add(checked);
    }

    public boolean setRoutesEnabled(@Nonnull Collection<String> routeKeys, boolean enabled) {
        boolean changed = false;
        for (String routeKey : routeKeys) {
            changed |= setRouteEnabled(routeKey, enabled);
        }
        return changed;
    }

    public long getEffectiveCraftAmount(@Nonnull String routeKey, long maximum) {
        return clampCraftAmount(routeCraftAmounts.getOrDefault(
                checkedKey(routeKey, "routeKey"), defaultAmountForRoute(routeKey)), maximum);
    }

    public boolean hasRouteCraftAmount(@Nonnull String routeKey) {
        return routeCraftAmounts.containsKey(checkedKey(routeKey, "routeKey"));
    }

    public boolean setCraftAmount(long amount, long maximum) {
        long checked = clampCraftAmount(amount, maximum);
        if (craftAmount == checked) {
            return false;
        }
        craftAmount = checked;
        routeCraftAmounts.entrySet().removeIf(entry -> entry.getValue() == craftAmount);
        return true;
    }

    public boolean setRouteCraftAmount(@Nonnull String routeKey, long amount, long maximum) {
        String checkedKey = checkedKey(routeKey, "routeKey");
        long checkedAmount = clampCraftAmount(amount, maximum);
        if (checkedAmount == defaultAmountForRoute(checkedKey)) {
            return routeCraftAmounts.remove(checkedKey) != null;
        }
        Long previous = routeCraftAmounts.put(checkedKey, checkedAmount);
        return previous == null || previous != checkedAmount;
    }

    public boolean clearRouteCraftAmount(@Nonnull String routeKey) {
        return routeCraftAmounts.remove(checkedKey(routeKey, "routeKey")) != null;
    }

    @Nonnull
    public List<String> getCurrentProductOrder(@Nonnull Collection<String> defaultOrder) {
        return normalizedOrder(orderedProducts, defaultOrder);
    }

    @Nonnull
    public List<String> getCurrentRouteOrder(@Nonnull String productKey,
                                             @Nonnull Collection<String> defaultOrder) {
        return normalizedOrder(orderedRoutes.get(checkedKey(productKey, "productKey")),
                defaultOrder);
    }

    public boolean moveProduct(@Nonnull Collection<String> defaultOrder,
                               @Nonnull String productKey, int direction) {
        return move(defaultOrder, productKey, direction, false, false);
    }

    public boolean moveProductToEdge(@Nonnull Collection<String> defaultOrder,
                                     @Nonnull String productKey, boolean top) {
        return move(defaultOrder, productKey, 0, true, top);
    }

    public boolean moveRoute(@Nonnull String productKey,
                             @Nonnull Collection<String> defaultOrder, @Nonnull String routeKey, int direction) {
        return moveRouteInternal(productKey, defaultOrder, routeKey, direction, false, false);
    }

    public boolean moveRouteToEdge(@Nonnull String productKey,
                                   @Nonnull Collection<String> defaultOrder, @Nonnull String routeKey, boolean top) {
        return moveRouteInternal(productKey, defaultOrder, routeKey, 0, true, top);
    }

    public boolean resetProduct(@Nonnull String productKey,
                                @Nonnull Collection<String> defaultRouteOrder,
                                @Nonnull Collection<String> defaultProductOrder) {
        String checkedProduct = checkedKey(productKey, "productKey");
        boolean changed = removeRouteOrder(checkedProduct, defaultRouteOrder);
        changed |= enabledRoutes.removeAll(defaultRouteOrder);
        changed |= disabledRoutes.removeAll(defaultRouteOrder);
        changed |= routeCraftAmounts.keySet().removeAll(defaultRouteOrder);
        if (orderedProducts.contains(checkedProduct)) {
            List<String> current = getCurrentProductOrder(defaultProductOrder);
            current.remove(checkedProduct);
            List<String> defaults = normalizedOrder(null, defaultProductOrder);
            int defaultIndex = defaults.indexOf(checkedProduct);
            int insertion = current.size();
            for (int index = 0; index < current.size(); index++) {
                int candidate = defaults.indexOf(current.get(index));
                if (candidate > defaultIndex) {
                    insertion = index;
                    break;
                }
            }
            current.add(insertion, checkedProduct);
            changed |= setProductOrder(defaultProductOrder, current);
        }
        return changed;
    }

    public boolean resetAll() {
        customOrderedFamilies = null;
        boolean changed = !isEmpty();
        enabledRoutes.clear();
        disabledRoutes.clear();
        orderedProducts.clear();
        orderedRoutes.clear();
        routeCraftAmounts.clear();
        familyCraftAmounts.clear();
        craftAmount = DEFAULT_CRAFT_AMOUNT;
        return changed;
    }

    /** Reset the displayed capability projection, not the frequency-wide table. */
    public boolean reset(@Nonnull QIOAutomationRecipeProfileLayout layout) {
        customOrderedFamilies = null;
        Set<String> keys = layout.getRouteKeys();
        boolean changed = enabledRoutes.removeAll(keys);
        changed |= disabledRoutes.removeAll(keys);
        changed |= routeCraftAmounts.keySet().removeAll(keys);
        changed |= orderedProducts.removeAll(layout.getDefaultProductOrder());
        for (String product : layout.getDefaultProductOrder()) {
            changed |= removeRouteOrder(product, layout.getDefaultRouteOrder(product));
        }
        changed |= familyCraftAmounts.remove(layout.getRecipeFamily()) != null;
        return changed;
    }

    private boolean removeRouteOrder(String product, Collection<String> routes) {
        customOrderedFamilies = null;
        List<String> order = orderedRoutes.get(product);
        if (order == null) return false;
        boolean changed = order.removeAll(routes);
        if (order.isEmpty()) orderedRoutes.remove(product);
        return changed;
    }

    /** Restrict a detached card/snapshot only. Never prune the live shared table. */
    public boolean prune(@Nonnull QIOAutomationRecipeProfileLayout layout) {
        customOrderedFamilies = null;
        Objects.requireNonNull(layout, "layout");
        Set<String> routes = layout.getRouteKeys();
        Set<String> products = new LinkedHashSet<>(layout.getDefaultProductOrder());
        boolean changed = enabledRoutes.removeIf(key -> !routes.contains(key));
        changed |= disabledRoutes.removeIf(key -> !routes.contains(key));
        changed |= routeCraftAmounts.keySet().removeIf(key -> !routes.contains(key));
        changed |= orderedProducts.removeIf(key -> !products.contains(key));
        Iterator<Map.Entry<String, List<String>>> iterator = orderedRoutes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<String>> entry = iterator.next();
            changed |= entry.getValue().removeIf(key -> !routes.contains(key));
            if (entry.getValue().isEmpty()) {
                iterator.remove();
                changed = true;
            }
        }
        changed |= familyCraftAmounts.keySet().removeIf(key -> !key.equals(layout.getRecipeFamily()));
        return changed;
    }

    /** Snapshot one declared family without unrelated frequency settings. */
    @Nonnull
    public QIOAutomationRecipeProfile copyForFamily(@Nonnull String family) {
        String prefix = familyPrefix(checkedKey(family, "recipeFamily"));
        QIOAutomationRecipeProfile result = copy();
        result.enabledRoutes.removeIf(key -> !key.startsWith(prefix));
        result.disabledRoutes.removeIf(key -> !key.startsWith(prefix));
        result.routeCraftAmounts.keySet().removeIf(key -> !key.startsWith(prefix));
        result.orderedProducts.removeIf(key -> !key.startsWith(prefix));
        result.orderedRoutes.keySet().removeIf(key -> !key.startsWith(prefix));
        result.familyCraftAmounts.keySet().removeIf(key -> !key.equals(family));
        return result;
    }

    /** Replace only validated visible entries. Other families and absent routes survive. */
    public boolean mergeProjection(@Nonnull QIOAutomationRecipeProfile source,
                                   @Nonnull QIOAutomationRecipeProfileLayout layout) {
        if (routeFilterMode != source.routeFilterMode) {
            throw new IllegalArgumentException("QIO automation profile filter modes do not match");
        }
        NBTTagCompound before = write();
        for (String key : layout.getRouteKeys()) {
            setRouteEnabled(key, source.isRouteEnabled(key));
            routeCraftAmounts.remove(key);
            if (source.routeCraftAmounts.containsKey(key)) {
                routeCraftAmounts.put(key, source.routeCraftAmounts.get(key));
            }
        }
        String family = layout.getRecipeFamily();
        long importedDefault = source.getCraftAmount(family);
        if (importedDefault == craftAmount) familyCraftAmounts.remove(family);
        else familyCraftAmounts.put(family, importedDefault);
        setProductOrder(layout.getDefaultProductOrder(),
                source.getCurrentProductOrder(layout.getDefaultProductOrder()));
        for (String product : layout.getDefaultProductOrder()) {
            List<String> routes = layout.getDefaultRouteOrder(product);
            setRouteOrder(product, routes, source.getCurrentRouteOrder(product, routes));
        }
        return !before.equals(write());
    }

    @Nonnull
    public QIOAutomationRecipeProfile copy() {
        QIOAutomationRecipeProfile copy = new QIOAutomationRecipeProfile(routeFilterMode);
        copy.enabledRoutes.addAll(enabledRoutes);
        copy.disabledRoutes.addAll(disabledRoutes);
        copy.orderedProducts.addAll(orderedProducts);
        orderedRoutes.forEach((key, value) -> copy.orderedRoutes.put(key,
                new ArrayList<>(value)));
        copy.routeCraftAmounts.putAll(routeCraftAmounts);
        copy.craftAmount = craftAmount;
        copy.familyCraftAmounts.putAll(familyCraftAmounts);
        return copy;
    }

    /** Replaces this profile while preserving its immutable filter identity. */
    public boolean replaceWith(@Nonnull QIOAutomationRecipeProfile other) {
        customOrderedFamilies = null;
        Objects.requireNonNull(other, "other");
        if (routeFilterMode != other.routeFilterMode) {
            throw new IllegalArgumentException("QIO automation profile filter modes do not match");
        }
        if (craftAmount == other.craftAmount && enabledRoutes.equals(other.enabledRoutes) &&
                disabledRoutes.equals(other.disabledRoutes) &&
                orderedProducts.equals(other.orderedProducts) &&
                orderedRoutes.equals(other.orderedRoutes) &&
                routeCraftAmounts.equals(other.routeCraftAmounts) &&
                familyCraftAmounts.equals(other.familyCraftAmounts)) {
            return false;
        }
        enabledRoutes.clear();
        enabledRoutes.addAll(other.enabledRoutes);
        disabledRoutes.clear();
        disabledRoutes.addAll(other.disabledRoutes);
        orderedProducts.clear();
        orderedProducts.addAll(other.orderedProducts);
        orderedRoutes.clear();
        other.orderedRoutes.forEach((key, value) -> orderedRoutes.put(key,
                new ArrayList<>(value)));
        routeCraftAmounts.clear();
        routeCraftAmounts.putAll(other.routeCraftAmounts);
        craftAmount = other.craftAmount;
        familyCraftAmounts.clear();
        familyCraftAmounts.putAll(other.familyCraftAmounts);
        return true;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("routeFilterMode", routeFilterMode.name());
        data.setLong("craftAmount", craftAmount);
        data.setTag("enabledRoutes", writeStrings(enabledRoutes));
        data.setTag("disabledRoutes", writeStrings(disabledRoutes));
        data.setTag("orderedProducts", writeStrings(orderedProducts));
        NBTTagList routeOrders = new NBTTagList();
        orderedRoutes.forEach((productKey, routes) -> {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("productKey", productKey);
            entry.setTag("routes", writeStrings(routes));
            routeOrders.appendTag(entry);
        });
        data.setTag("orderedRoutes", routeOrders);
        NBTTagList amounts = new NBTTagList();
        routeCraftAmounts.forEach((routeKey, amount) -> {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("routeKey", routeKey);
            entry.setLong("amount", amount);
            amounts.appendTag(entry);
        });
        data.setTag("routeCraftAmounts", amounts);
        NBTTagList familyAmounts = new NBTTagList();
        familyCraftAmounts.forEach((family, amount) -> {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("family", family);
            entry.setLong("amount", amount);
            familyAmounts.appendTag(entry);
        });
        data.setTag("familyCraftAmounts", familyAmounts);
        return data;
    }

    @Nonnull
    public static QIOAutomationRecipeProfile read(@Nonnull NBTTagCompound data,
                                                  @Nonnull RouteFilterMode expectedMode) throws QIOProcessingDataException {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(expectedMode, "expectedMode");
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                    !data.hasKey("routeFilterMode", NBT.TAG_STRING) ||
                    !data.hasKey("craftAmount", NBT.TAG_LONG) ||
                    !data.hasKey("enabledRoutes", NBT.TAG_LIST) ||
                    !data.hasKey("disabledRoutes", NBT.TAG_LIST) ||
                    !data.hasKey("orderedProducts", NBT.TAG_LIST) ||
                    !data.hasKey("orderedRoutes", NBT.TAG_LIST) ||
                    !data.hasKey("routeCraftAmounts", NBT.TAG_LIST) ||
                    !data.hasKey("familyCraftAmounts", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("QIO automation profile is incomplete");
            }
            RouteFilterMode storedMode = RouteFilterMode.valueOf(
                    data.getString("routeFilterMode"));
            if (storedMode != expectedMode) {
                throw new QIOProcessingDataException("QIO automation profile filter identity disagrees with its contents");
            }
            QIOAutomationRecipeProfile profile = new QIOAutomationRecipeProfile(expectedMode);
            profile.craftAmount = requirePositive(data.getLong("craftAmount"),
                    "profileCraftAmount");
            profile.enabledRoutes.addAll(readStrings(data, "enabledRoutes"));
            profile.disabledRoutes.addAll(readStrings(data, "disabledRoutes"));
            profile.orderedProducts.addAll(readStrings(data, "orderedProducts"));
            if (!Collections.disjoint(profile.enabledRoutes, profile.disabledRoutes)) {
                throw new QIOProcessingDataException("QIO automation profile contains contradictory route filters");
            }
            NBTTagList routeOrders = boundedList(data, "orderedRoutes");
            long associations = 0;
            for (int index = 0; index < routeOrders.tagCount(); index++) {
                NBTTagCompound entry = routeOrders.getCompoundTagAt(index);
                String productKey = checkedKey(entry.getString("productKey"), "productKey");
                List<String> routes = readStrings(entry, "routes");
                associations += routes.size();
                if (associations > MAX_ENTRIES || profile.orderedRoutes.put(productKey,
                        routes) != null) {
                    throw new QIOProcessingDataException("QIO automation profile route order is invalid");
                }
            }
            NBTTagList amounts = boundedList(data, "routeCraftAmounts");
            for (int index = 0; index < amounts.tagCount(); index++) {
                NBTTagCompound entry = amounts.getCompoundTagAt(index);
                String routeKey = checkedKey(entry.getString("routeKey"), "routeKey");
                long amount = requirePositive(entry.getLong("amount"),
                        "routeCraftAmount");
                if (profile.routeCraftAmounts.put(routeKey, amount) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO route craft amount");
                }
            }
            NBTTagList familyAmounts = boundedList(data, "familyCraftAmounts");
            for (int index = 0; index < familyAmounts.tagCount(); index++) {
                NBTTagCompound entry = familyAmounts.getCompoundTagAt(index);
                String family = checkedKey(entry.getString("family"), "recipeFamily");
                if (profile.familyCraftAmounts.put(family,
                        requirePositive(entry.getLong("amount"), "familyCraftAmount")) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO recipe family amount");
                }
            }
            return profile;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO automation profile", e);
        }
    }

    public static long clampCraftAmount(long amount, long maximum) {
        return Math.max(DEFAULT_CRAFT_AMOUNT,
                Math.min(Math.max(DEFAULT_CRAFT_AMOUNT, maximum), amount));
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private boolean move(Collection<String> defaultOrder, String key, int direction,
                         boolean edge, boolean top) {
        String checked = checkedKey(key, "productKey");
        List<String> current = getCurrentProductOrder(defaultOrder);
        int index = current.indexOf(checked);
        int target = edge ? top ? 0 : current.size() - 1 : index + direction;
        if (index < 0 || target < 0 || target >= current.size() || index == target) {
            return false;
        }
        current.remove(index);
        current.add(target, checked);
        return setProductOrder(defaultOrder, current);
    }

    private boolean moveRouteInternal(String productKey, Collection<String> defaultOrder,
                                      String routeKey, int direction, boolean edge, boolean top) {
        String checkedProduct = checkedKey(productKey, "productKey");
        String checkedRoute = checkedKey(routeKey, "routeKey");
        List<String> current = getCurrentRouteOrder(checkedProduct, defaultOrder);
        int index = current.indexOf(checkedRoute);
        int target = edge ? top ? 0 : current.size() - 1 : index + direction;
        if (index < 0 || target < 0 || target >= current.size() || index == target) {
            return false;
        }
        current.remove(index);
        current.add(target, checkedRoute);
        return setRouteOrder(checkedProduct, defaultOrder, current);
    }

    private boolean setProductOrder(Collection<String> defaultOrder, List<String> requested) {
        customOrderedFamilies = null;
        List<String> merged = mergeOrder(orderedProducts, defaultOrder, requested);
        if (merged.equals(orderedProducts)) return false;
        orderedProducts.clear();
        orderedProducts.addAll(merged);
        return true;
    }

    private boolean setRouteOrder(String productKey, Collection<String> defaultOrder,
                                  List<String> requested) {
        customOrderedFamilies = null;
        List<String> previous = orderedRoutes.getOrDefault(productKey, Collections.emptyList());
        List<String> merged = mergeOrder(previous, defaultOrder, requested);
        if (merged.equals(previous)) return false;
        if (merged.isEmpty()) orderedRoutes.remove(productKey);
        else orderedRoutes.put(productKey, merged);
        return true;
    }

    /** Preserve every out-of-view key and its relative order. */
    private static List<String> mergeOrder(List<String> stored, Collection<String> domain,
                                           List<String> requested) {
        List<String> defaults = normalizedOrder(null, domain);
        Set<String> visible = new HashSet<>(defaults);
        List<String> desired = normalizedOrder(requested, defaults);
        if (desired.equals(defaults)) {
            List<String> result = new ArrayList<>(stored);
            result.removeIf(visible::contains);
            return result;
        }
        List<String> result = new ArrayList<>();
        int index = 0;
        for (String key : stored) {
            if (visible.contains(key)) {
                if (index < desired.size()) result.add(desired.get(index++));
            } else result.add(key);
        }
        while (index < desired.size()) result.add(desired.get(index++));
        return result;
    }

    private static List<String> normalizedOrder(Collection<String> preferred,
                                                Collection<String> defaultOrder) {
        LinkedHashSet<String> defaults = new LinkedHashSet<>();
        for (String key : defaultOrder) {
            defaults.add(checkedKey(key, "orderKey"));
        }
        List<String> result = new ArrayList<>(defaults.size());
        Set<String> seen = new HashSet<>();
        if (preferred != null) {
            for (String key : preferred) {
                String checked = checkedKey(key, "orderKey");
                if (defaults.contains(checked) && seen.add(checked)) {
                    result.add(checked);
                }
            }
        }
        for (String key : defaults) {
            if (seen.add(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private static NBTTagList writeStrings(Collection<String> values) {
        NBTTagList list = new NBTTagList();
        for (String value : values) {
            list.appendTag(new NBTTagString(checkedKey(value, "storedKey")));
        }
        return list;
    }

    private static List<String> readStrings(NBTTagCompound data, String key)
            throws QIOProcessingDataException {
        NBTTagList list = data.getTagList(key, NBT.TAG_STRING);
        if (list.tagCount() > MAX_ENTRIES) {
            throw new QIOProcessingDataException(key + " exceeds the QIO profile safety limit");
        }
        List<String> result = new ArrayList<>(list.tagCount());
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < list.tagCount(); index++) {
            String value = checkedKey(list.getStringTagAt(index), key);
            if (!unique.add(value)) {
                throw new QIOProcessingDataException("Duplicate value in QIO profile " + key);
            }
            result.add(value);
        }
        return result;
    }

    private static NBTTagList boundedList(NBTTagCompound data, String key)
            throws QIOProcessingDataException {
        if (!data.hasKey(key, NBT.TAG_LIST)) {
            throw new QIOProcessingDataException("Missing QIO profile list " + key);
        }
        NBTTagList list = data.getTagList(key, NBT.TAG_COMPOUND);
        if (list.tagCount() > MAX_ENTRIES) {
            throw new QIOProcessingDataException(key + " exceeds the QIO profile safety limit");
        }
        return list;
    }

    private static String checkedKey(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty() || checked.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(name + " must contain 1.." + MAX_KEY_LENGTH + " characters");
        }
        return checked;
    }
}
