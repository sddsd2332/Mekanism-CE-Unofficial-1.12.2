package mekanism.qioprocessing.common.content.profile;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent global-slot and per-machine automation recipe profiles for one QIO frequency. */
/**
 * QIO 处理模块中的 QIOAutomationRecipeProfileCatalog 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationRecipeProfileCatalog {

    public static final int MIN_GLOBAL_SLOT = 1;
    public static final int MAX_GLOBAL_SLOT = 10;
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_PROFILES = 1_000_000;
    private static final int MAX_SELECTIONS = 1_000_000;
    private static final int MAX_PROFILE_SCOPE_ID_LENGTH = 1_536;

    private final Map<ProfileKey, QIOAutomationRecipeProfile> profiles = new LinkedHashMap<>();
    private final Map<SelectionKey, SelectionState> selections = new LinkedHashMap<>();
    private long revision;

    public long getRevision() {
        return revision;
    }

    @Nonnull
    public Selection getSelection(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId) {
        SelectionState state = selections.get(SelectionKey.create(deviceUUID, mode,
              profileScopeId));
        return state == null ? Selection.defaults(mode) : state.snapshot();
    }

    @Nonnull
    public QIOAutomationRecipeProfile getActiveProfile(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId) {
        Selection selection = getSelection(deviceUUID, mode, profileScopeId);
        QIOAutomationRecipeProfile profile = profiles.get(ProfileKey.active(deviceUUID, mode,
              profileScopeId, selection));
        return profile == null ? new QIOAutomationRecipeProfile(selection.filterMode) : profile;
    }

    public boolean toggleProfileMode(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId) {
        SelectionKey key = SelectionKey.create(deviceUUID, mode, profileScopeId);
        SelectionState state = selections.computeIfAbsent(key, ignored ->
              SelectionState.defaults(mode));
        boolean individual = !state.individual;
        if (individual) {
            ProfileKey individualKey = ProfileKey.individual(deviceUUID, mode, profileScopeId,
                  state.filterMode);
            profiles.computeIfAbsent(individualKey, ignored -> globalProfile(mode,
                  profileScopeId,
                  state.filterMode, state.globalSlot).copy());
        }
        state.individual = individual;
        incrementRevision();
        return true;
    }

    public boolean cycleGlobalSlot(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, int direction) {
        if (direction == 0) {
            return false;
        }
        SelectionKey key = SelectionKey.create(deviceUUID, mode, profileScopeId);
        SelectionState state = selections.computeIfAbsent(key, ignored ->
              SelectionState.defaults(mode));
        if (state.individual) {
            return false;
        }
        int normalized = direction > 0 ? 1 : -1;
        int next = state.globalSlot + normalized;
        if (next > MAX_GLOBAL_SLOT) {
            next = MIN_GLOBAL_SLOT;
        } else if (next < MIN_GLOBAL_SLOT) {
            next = MAX_GLOBAL_SLOT;
        }
        if (next == state.globalSlot) {
            return false;
        }
        state.globalSlot = next;
        incrementRevision();
        return true;
    }

    public boolean toggleRouteFilterMode(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId) {
        if (mode != QIOAutomationMode.SCHEDULED) {
            return false;
        }
        SelectionKey key = SelectionKey.create(deviceUUID, mode, profileScopeId);
        SelectionState state = selections.computeIfAbsent(key, ignored ->
              SelectionState.defaults(mode));
        RouteFilterMode next = state.filterMode.next();
        if (state.individual) {
            ProfileKey individual = ProfileKey.individual(deviceUUID, mode, profileScopeId,
                  next);
            profiles.computeIfAbsent(individual, ignored -> globalProfile(mode,
                  profileScopeId,
                  next, state.globalSlot).copy());
        }
        state.filterMode = next;
        incrementRevision();
        return true;
    }

    public boolean setRouteEnabled(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          @Nonnull String routeKey, boolean enabled) {
        if (layout.getRoute(routeKey) == null) {
            return false;
        }
        return mutateProfile(deviceUUID, mode, profileScopeId,
              profile -> profile.setRouteEnabled(routeKey, enabled));
    }

    public boolean toggleRoute(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          @Nonnull String routeKey) {
        if (layout.getRoute(routeKey) == null) {
            return false;
        }
        QIOAutomationRecipeProfile profile = mutableActiveProfile(deviceUUID, mode,
              profileScopeId);
        boolean changed = profile.setRouteEnabled(routeKey, !profile.isRouteEnabled(routeKey));
        return finishMutation(changed);
    }

    public boolean toggleProduct(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          @Nonnull String productKey) {
        List<String> routes = layout.getDefaultRouteOrder(productKey);
        if (routes.isEmpty()) {
            return false;
        }
        QIOAutomationRecipeProfile profile = mutableActiveProfile(deviceUUID, mode,
              profileScopeId);
        boolean allDisabled = routes.stream().noneMatch(profile::isRouteEnabled);
        return finishMutation(profile.setRoutesEnabled(routes, allDisabled));
    }

    public boolean setProductEnabled(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          @Nonnull String productKey, boolean enabled) {
        List<String> routes = layout.getDefaultRouteOrder(productKey);
        return routes.isEmpty() ? false : mutateProfile(deviceUUID, mode, profileScopeId,
              profile -> profile.setRoutesEnabled(routes, enabled));
    }

    public boolean setAllRoutesEnabled(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          boolean enabled) {
        return mutateProfile(deviceUUID, mode, profileScopeId,
              profile -> profile.setRoutesEnabled(layout.getRouteKeys(), enabled));
    }

    public boolean moveProduct(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          @Nonnull String productKey, int direction, boolean edge, boolean top) {
        if (!layout.getDefaultProductOrder().contains(productKey)) {
            return false;
        }
        return mutateProfile(deviceUUID, mode, profileScopeId, profile -> edge ?
              profile.moveProductToEdge(layout.getDefaultProductOrder(), productKey, top) :
              profile.moveProduct(layout.getDefaultProductOrder(), productKey, direction));
    }

    public boolean moveRoute(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          @Nonnull String productKey, @Nonnull String routeKey, int direction,
          boolean edge, boolean top) {
        List<String> routes = layout.getDefaultRouteOrder(productKey);
        if (!routes.contains(routeKey)) {
            return false;
        }
        return mutateProfile(deviceUUID, mode, profileScopeId, profile -> edge ?
              profile.moveRouteToEdge(productKey, routes, routeKey, top) :
              profile.moveRoute(productKey, routes, routeKey, direction));
    }

    public boolean setCraftAmount(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          long amount) {
        return mutateProfile(deviceUUID, mode, profileScopeId,
              profile -> profile.setCraftAmount(amount, layout.getMaximumCraftAmount()));
    }

    public boolean setRouteCraftAmount(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId,
          @Nonnull QIOAutomationRecipeProfileLayout layout, @Nonnull String routeKey,
          long amount) {
        QIOAutomationRecipeProfileLayout.Route route = layout.getRoute(routeKey);
        return route != null && mutateProfile(deviceUUID, mode, profileScopeId,
              profile -> profile.setRouteCraftAmount(routeKey, amount,
                    route.getMaximumCraftAmount()));
    }

    public boolean clearRouteCraftAmount(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId,
          @Nonnull QIOAutomationRecipeProfileLayout layout, @Nonnull String routeKey) {
        return layout.getRoute(routeKey) != null && mutateProfile(deviceUUID, mode,
              profileScopeId,
              profile -> profile.clearRouteCraftAmount(routeKey));
    }

    public boolean resetProduct(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout,
          @Nonnull String productKey) {
        List<String> routes = layout.getDefaultRouteOrder(productKey);
        return !routes.isEmpty() && mutateProfile(deviceUUID, mode, profileScopeId,
              profile -> profile.resetProduct(productKey, routes,
                    layout.getDefaultProductOrder()));
    }

    public boolean resetAll(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId) {
        return mutateProfile(deviceUUID, mode, profileScopeId,
              QIOAutomationRecipeProfile::resetAll);
    }

    public boolean pruneActive(@Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, @Nonnull QIOAutomationRecipeProfileLayout layout) {
        ProfileKey key = activeKey(deviceUUID, mode, profileScopeId);
        QIOAutomationRecipeProfile profile = profiles.get(key);
        return profile != null && finishMutation(profile.prune(layout));
    }

    /** Applies one configuration-card snapshot as a single catalog revision. */
    public boolean replaceSelectionAndActiveProfile(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId,
          boolean individual, int globalSlot, @Nonnull RouteFilterMode filterMode,
          @Nonnull QIOAutomationRecipeProfile profile) {
        Objects.requireNonNull(profile, "profile");
        requireConfigurableMode(mode);
        if (globalSlot < MIN_GLOBAL_SLOT || globalSlot > MAX_GLOBAL_SLOT ||
            mode == QIOAutomationMode.PASSIVE && filterMode != RouteFilterMode.WHITELIST ||
            profile.getRouteFilterMode() != filterMode) {
            throw new IllegalArgumentException("Invalid imported QIO automation profile selection");
        }
        SelectionKey selectionKey = SelectionKey.create(deviceUUID, mode, profileScopeId);
        SelectionState state = selections.get(selectionKey);
        Selection current = state == null ? Selection.defaults(mode) : state.snapshot();
        boolean selectionChanged = current.individual != individual ||
              current.globalSlot != globalSlot || current.filterMode != filterMode;
        if (selectionChanged) {
            if (state == null) {
                state = SelectionState.defaults(mode);
                selections.put(selectionKey, state);
            }
            state.individual = individual;
            state.globalSlot = globalSlot;
            state.filterMode = filterMode;
        }

        ProfileKey profileKey = individual ? ProfileKey.individual(deviceUUID, mode,
              profileScopeId, filterMode) : ProfileKey.global(mode, profileScopeId, filterMode,
              globalSlot);
        QIOAutomationRecipeProfile existing = profiles.get(profileKey);
        boolean profileChanged;
        if (profile.isEmpty()) {
            profileChanged = existing != null;
            profiles.remove(profileKey);
        } else if (existing == null) {
            profiles.put(profileKey, profile.copy());
            profileChanged = true;
        } else {
            profileChanged = existing.replaceWith(profile);
        }
        return finishMutation(selectionChanged || profileChanged);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setLong("revision", revision);
        NBTTagList storedProfiles = new NBTTagList();
        profiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound stored = entry.getKey().write();
            stored.setTag("profile", entry.getValue().write());
            storedProfiles.appendTag(stored);
        });
        data.setTag("profiles", storedProfiles);
        NBTTagList storedSelections = new NBTTagList();
        selections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound stored = entry.getKey().write();
            entry.getValue().write(stored);
            storedSelections.appendTag(stored);
        });
        data.setTag("selections", storedSelections);
        return data;
    }

    @Nonnull
    public static QIOAutomationRecipeProfileCatalog read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        Objects.requireNonNull(data, "data");
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("revision", NBT.TAG_LONG) ||
                !data.hasKey("profiles", NBT.TAG_LIST) ||
                !data.hasKey("selections", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("QIO automation profile catalog is incomplete");
            }
            QIOAutomationRecipeProfileCatalog catalog = new QIOAutomationRecipeProfileCatalog();
            catalog.revision = QIOProcessingNbt.requireNonNegative(data.getLong("revision"),
                  "automationProfileRevision");
            NBTTagList storedProfiles = data.getTagList("profiles", NBT.TAG_COMPOUND);
            if (storedProfiles.tagCount() > MAX_PROFILES) {
                throw new QIOProcessingDataException("QIO automation profile catalog is too large");
            }
            for (int index = 0; index < storedProfiles.tagCount(); index++) {
                NBTTagCompound stored = storedProfiles.getCompoundTagAt(index);
                ProfileKey key = ProfileKey.read(stored);
                if (!stored.hasKey("profile", NBT.TAG_COMPOUND) ||
                    catalog.profiles.put(key, QIOAutomationRecipeProfile.read(
                          stored.getCompoundTag("profile"), key.filterMode)) != null) {
                    throw new QIOProcessingDataException("Duplicate or incomplete QIO automation profile");
                }
            }
            NBTTagList storedSelections = data.getTagList("selections", NBT.TAG_COMPOUND);
            if (storedSelections.tagCount() > MAX_SELECTIONS) {
                throw new QIOProcessingDataException("QIO automation profile selection catalog is too large");
            }
            for (int index = 0; index < storedSelections.tagCount(); index++) {
                NBTTagCompound stored = storedSelections.getCompoundTagAt(index);
                SelectionKey key = SelectionKey.read(stored);
                SelectionState state = SelectionState.read(stored, key.mode);
                if (catalog.selections.put(key, state) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO automation profile selection");
                }
            }
            return catalog;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO automation profile catalog", e);
        }
    }

    private boolean mutateProfile(UUID deviceUUID, QIOAutomationMode mode,
          String profileScopeId,
          java.util.function.Predicate<QIOAutomationRecipeProfile> mutation) {
        return finishMutation(mutation.test(mutableActiveProfile(deviceUUID, mode,
              profileScopeId)));
    }

    private boolean finishMutation(boolean changed) {
        if (changed) {
            incrementRevision();
        }
        return changed;
    }

    private QIOAutomationRecipeProfile mutableActiveProfile(UUID deviceUUID,
          QIOAutomationMode mode, String profileScopeId) {
        ProfileKey key = activeKey(deviceUUID, mode, profileScopeId);
        return profiles.computeIfAbsent(key, ignored -> new QIOAutomationRecipeProfile(
              key.filterMode));
    }

    private ProfileKey activeKey(UUID deviceUUID, QIOAutomationMode mode,
          String profileScopeId) {
        Selection selection = getSelection(deviceUUID, mode, profileScopeId);
        return ProfileKey.active(deviceUUID, mode, profileScopeId, selection);
    }

    private QIOAutomationRecipeProfile globalProfile(QIOAutomationMode mode,
          String profileScopeId, RouteFilterMode filterMode, int globalSlot) {
        ProfileKey key = ProfileKey.global(mode, profileScopeId, filterMode, globalSlot);
        return profiles.computeIfAbsent(key, ignored -> new QIOAutomationRecipeProfile(filterMode));
    }

    private void incrementRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO automation profile revision exhausted");
        }
        revision++;
    }

    @Nonnull
    public static RouteFilterMode defaultFilterMode(@Nonnull QIOAutomationMode mode) {
        return mode == QIOAutomationMode.PASSIVE ? RouteFilterMode.WHITELIST :
              RouteFilterMode.BLACKLIST;
    }

    private static String checkedProfileScopeId(String profileScopeId) {
        String checked = Objects.requireNonNull(profileScopeId, "profileScopeId").trim();
        if (checked.isEmpty() || checked.length() > MAX_PROFILE_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException("profileScopeId has an invalid length");
        }
        return checked;
    }

    public static final class Selection {

        private final boolean individual;
        private final int globalSlot;
        private final RouteFilterMode filterMode;

        private Selection(boolean individual, int globalSlot, RouteFilterMode filterMode) {
            this.individual = individual;
            this.globalSlot = globalSlot;
            this.filterMode = filterMode;
        }

        private static Selection defaults(QIOAutomationMode mode) {
            return new Selection(false, MIN_GLOBAL_SLOT, defaultFilterMode(mode));
        }

        public boolean isIndividual() {
            return individual;
        }

        public int getGlobalSlot() {
            return globalSlot;
        }

        @Nonnull
        public RouteFilterMode getFilterMode() {
            return filterMode;
        }
    }

    private static final class SelectionState {

        private boolean individual;
        private int globalSlot;
        private RouteFilterMode filterMode;

        private SelectionState(boolean individual, int globalSlot, RouteFilterMode filterMode) {
            this.individual = individual;
            this.globalSlot = globalSlot;
            this.filterMode = filterMode;
        }

        private static SelectionState defaults(QIOAutomationMode mode) {
            return new SelectionState(false, MIN_GLOBAL_SLOT, defaultFilterMode(mode));
        }

        private Selection snapshot() {
            return new Selection(individual, globalSlot, filterMode);
        }

        private void write(NBTTagCompound data) {
            data.setBoolean("individual", individual);
            data.setInteger("globalSlot", globalSlot);
            data.setString("filterMode", filterMode.name());
        }

        private static SelectionState read(NBTTagCompound data, QIOAutomationMode mode)
              throws QIOProcessingDataException {
            int slot = data.getInteger("globalSlot");
            RouteFilterMode filter = RouteFilterMode.valueOf(data.getString("filterMode"));
            if (slot < MIN_GLOBAL_SLOT || slot > MAX_GLOBAL_SLOT ||
                mode == QIOAutomationMode.PASSIVE && filter != RouteFilterMode.WHITELIST) {
                throw new QIOProcessingDataException("Invalid QIO automation profile selection");
            }
            return new SelectionState(data.getBoolean("individual"), slot, filter);
        }
    }

    private static final class SelectionKey implements Comparable<SelectionKey> {

        private final UUID deviceUUID;
        private final QIOAutomationMode mode;
        private final String profileScopeId;

        private SelectionKey(UUID deviceUUID, QIOAutomationMode mode,
              String profileScopeId) {
            this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
            this.mode = requireConfigurableMode(mode);
            this.profileScopeId = checkedProfileScopeId(profileScopeId);
        }

        private static SelectionKey create(UUID deviceUUID, QIOAutomationMode mode,
              String profileScopeId) {
            return new SelectionKey(deviceUUID, mode, profileScopeId);
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
            data.setString("mode", mode.name());
            data.setString("profileScopeId", profileScopeId);
            return data;
        }

        private static SelectionKey read(NBTTagCompound data)
              throws QIOProcessingDataException {
            return create(QIOProcessingNbt.readUUID(data, "deviceUUID"),
                  QIOAutomationMode.valueOf(data.getString("mode")),
                  data.getString("profileScopeId"));
        }

        @Override
        public int compareTo(SelectionKey other) {
            int scope = profileScopeId.compareTo(other.profileScopeId);
            if (scope != 0) return scope;
            int modeOrder = mode.compareTo(other.mode);
            return modeOrder != 0 ? modeOrder : deviceUUID.toString().compareTo(
                  other.deviceUUID.toString());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SelectionKey other && deviceUUID.equals(other.deviceUUID) &&
                  mode == other.mode && profileScopeId.equals(other.profileScopeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceUUID, mode, profileScopeId);
        }
    }

    private static final class ProfileKey implements Comparable<ProfileKey> {

        private final QIOAutomationMode mode;
        private final String profileScopeId;
        private final RouteFilterMode filterMode;
        private final boolean individual;
        private final int globalSlot;
        private final UUID deviceUUID;

        private ProfileKey(QIOAutomationMode mode, String profileScopeId,
              RouteFilterMode filterMode, boolean individual, int globalSlot, UUID deviceUUID) {
            this.mode = requireConfigurableMode(mode);
            this.profileScopeId = checkedProfileScopeId(profileScopeId);
            this.filterMode = Objects.requireNonNull(filterMode, "filterMode");
            this.individual = individual;
            this.globalSlot = globalSlot;
            this.deviceUUID = deviceUUID;
            if (mode == QIOAutomationMode.PASSIVE && filterMode != RouteFilterMode.WHITELIST ||
                individual != (deviceUUID != null) || individual && globalSlot != 0 ||
                !individual && (globalSlot < MIN_GLOBAL_SLOT || globalSlot > MAX_GLOBAL_SLOT)) {
                throw new IllegalArgumentException("Invalid QIO automation profile identity");
            }
        }

        private static ProfileKey active(UUID deviceUUID, QIOAutomationMode mode,
              String profileScopeId, Selection selection) {
            return selection.individual ? individual(deviceUUID, mode, profileScopeId,
                  selection.filterMode) : global(mode, profileScopeId, selection.filterMode,
                  selection.globalSlot);
        }

        private static ProfileKey global(QIOAutomationMode mode, String profileScopeId,
              RouteFilterMode filterMode, int slot) {
            return new ProfileKey(mode, profileScopeId, filterMode, false, slot, null);
        }

        private static ProfileKey individual(UUID deviceUUID, QIOAutomationMode mode,
              String profileScopeId, RouteFilterMode filterMode) {
            return new ProfileKey(mode, profileScopeId, filterMode, true, 0,
                  Objects.requireNonNull(deviceUUID, "deviceUUID"));
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("mode", mode.name());
            data.setString("profileScopeId", profileScopeId);
            data.setString("filterMode", filterMode.name());
            data.setBoolean("individual", individual);
            data.setInteger("globalSlot", globalSlot);
            if (deviceUUID != null) {
                QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
            }
            return data;
        }

        private static ProfileKey read(NBTTagCompound data)
              throws QIOProcessingDataException {
            boolean individual = data.getBoolean("individual");
            return new ProfileKey(QIOAutomationMode.valueOf(data.getString("mode")),
                  data.getString("profileScopeId"), RouteFilterMode.valueOf(
                  data.getString("filterMode")), individual,
                  data.getInteger("globalSlot"), individual ?
                  QIOProcessingNbt.readUUID(data, "deviceUUID") : null);
        }

        @Override
        public int compareTo(ProfileKey other) {
            int scopeId = profileScopeId.compareTo(other.profileScopeId);
            if (scopeId != 0) return scopeId;
            int modeOrder = mode.compareTo(other.mode);
            if (modeOrder != 0) return modeOrder;
            int filter = filterMode.compareTo(other.filterMode);
            if (filter != 0) return filter;
            int scope = Boolean.compare(individual, other.individual);
            if (scope != 0) return scope;
            return individual ? deviceUUID.toString().compareTo(other.deviceUUID.toString()) :
                  Integer.compare(globalSlot, other.globalSlot);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ProfileKey other && mode == other.mode &&
                  profileScopeId.equals(other.profileScopeId) &&
                  filterMode == other.filterMode &&
                  individual == other.individual && globalSlot == other.globalSlot &&
                  Objects.equals(deviceUUID, other.deviceUUID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mode, profileScopeId, filterMode, individual, globalSlot,
                  deviceUUID);
        }
    }

    private static QIOAutomationMode requireConfigurableMode(QIOAutomationMode mode) {
        QIOAutomationMode checked = Objects.requireNonNull(mode, "mode");
        if (checked == QIOAutomationMode.OUTPUT_ONLY) {
            throw new IllegalArgumentException("Output-only automation has no recipe profile");
        }
        return checked;
    }
}
