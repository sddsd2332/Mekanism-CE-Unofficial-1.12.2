package mekanism.qioprocessing.common.content.profile;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One ordered route-selection profile used by a QIO automation provider. */
public final class QIOAutomationRecipeProfile {

    public static final long DEFAULT_CRAFT_AMOUNT = 1;
    public static final long MAX_CRAFT_AMOUNT = Long.MAX_VALUE;
    private static final int SCHEMA_VERSION = 1;
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
    private long craftAmount = DEFAULT_CRAFT_AMOUNT;

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

    public boolean isEmpty() {
        return enabledRoutes.isEmpty() && disabledRoutes.isEmpty() && orderedProducts.isEmpty() &&
              orderedRoutes.isEmpty() && routeCraftAmounts.isEmpty() &&
              craftAmount == DEFAULT_CRAFT_AMOUNT;
    }

    public boolean hasCustomOrder() {
        return !orderedProducts.isEmpty() || !orderedRoutes.isEmpty();
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
              checkedKey(routeKey, "routeKey"), craftAmount), maximum);
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
        if (checkedAmount == craftAmount) {
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
        boolean changed = orderedRoutes.remove(checkedProduct) != null;
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
        boolean changed = !isEmpty();
        enabledRoutes.clear();
        disabledRoutes.clear();
        orderedProducts.clear();
        orderedRoutes.clear();
        routeCraftAmounts.clear();
        craftAmount = DEFAULT_CRAFT_AMOUNT;
        return changed;
    }

    public boolean prune(@Nonnull QIOAutomationRecipeProfileLayout layout) {
        Objects.requireNonNull(layout, "layout");
        Set<String> validRoutes = layout.getRouteKeys();
        Set<String> validProducts = new LinkedHashSet<>(layout.getDefaultProductOrder());
        boolean changed = enabledRoutes.removeIf(key -> !validRoutes.contains(key));
        changed |= disabledRoutes.removeIf(key -> !validRoutes.contains(key));
        changed |= orderedProducts.removeIf(key -> !validProducts.contains(key));

        List<String> products = getCurrentProductOrder(layout.getDefaultProductOrder());
        if (products.equals(layout.getDefaultProductOrder())) {
            if (!orderedProducts.isEmpty()) {
                orderedProducts.clear();
                changed = true;
            }
        } else if (!products.equals(orderedProducts)) {
            orderedProducts.clear();
            orderedProducts.addAll(products);
            changed = true;
        }

        Iterator<Map.Entry<String, List<String>>> orderIterator = orderedRoutes.entrySet().iterator();
        while (orderIterator.hasNext()) {
            Map.Entry<String, List<String>> entry = orderIterator.next();
            List<String> defaults = layout.getDefaultRouteOrder(entry.getKey());
            if (defaults.isEmpty()) {
                orderIterator.remove();
                changed = true;
                continue;
            }
            List<String> normalized = normalizedOrder(entry.getValue(), defaults);
            if (normalized.equals(defaults)) {
                orderIterator.remove();
                changed = true;
            } else if (!normalized.equals(entry.getValue())) {
                entry.setValue(normalized);
                changed = true;
            }
        }

        long clampedGlobal = clampCraftAmount(craftAmount, layout.getMaximumCraftAmount());
        if (clampedGlobal != craftAmount) {
            craftAmount = clampedGlobal;
            changed = true;
        }
        Iterator<Map.Entry<String, Long>> amountIterator = routeCraftAmounts.entrySet().iterator();
        while (amountIterator.hasNext()) {
            Map.Entry<String, Long> entry = amountIterator.next();
            QIOAutomationRecipeProfileLayout.Route route = layout.getRoute(entry.getKey());
            if (route == null) {
                amountIterator.remove();
                changed = true;
                continue;
            }
            long clamped = clampCraftAmount(entry.getValue(), route.getMaximumCraftAmount());
            if (clamped == craftAmount) {
                amountIterator.remove();
                changed = true;
            } else if (clamped != entry.getValue()) {
                entry.setValue(clamped);
                changed = true;
            }
        }
        return changed;
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
        return copy;
    }

    /** Replaces this profile while preserving its immutable filter identity. */
    public boolean replaceWith(@Nonnull QIOAutomationRecipeProfile other) {
        Objects.requireNonNull(other, "other");
        if (routeFilterMode != other.routeFilterMode) {
            throw new IllegalArgumentException("QIO automation profile filter modes do not match");
        }
        if (craftAmount == other.craftAmount && enabledRoutes.equals(other.enabledRoutes) &&
            disabledRoutes.equals(other.disabledRoutes) &&
            orderedProducts.equals(other.orderedProducts) &&
            orderedRoutes.equals(other.orderedRoutes) &&
            routeCraftAmounts.equals(other.routeCraftAmounts)) {
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
                !data.hasKey("routeCraftAmounts", NBT.TAG_LIST)) {
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
        List<String> defaults = normalizedOrder(null, defaultOrder);
        List<String> normalized = normalizedOrder(requested, defaults);
        if (normalized.equals(defaults)) {
            if (orderedProducts.isEmpty()) {
                return false;
            }
            orderedProducts.clear();
            return true;
        }
        if (normalized.equals(orderedProducts)) {
            return false;
        }
        orderedProducts.clear();
        orderedProducts.addAll(normalized);
        return true;
    }

    private boolean setRouteOrder(String productKey, Collection<String> defaultOrder,
          List<String> requested) {
        List<String> defaults = normalizedOrder(null, defaultOrder);
        List<String> normalized = normalizedOrder(requested, defaults);
        List<String> previous = orderedRoutes.get(productKey);
        if (normalized.equals(defaults)) {
            return orderedRoutes.remove(productKey) != null;
        }
        if (normalized.equals(previous)) {
            return false;
        }
        orderedRoutes.put(productKey, normalized);
        return true;
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
