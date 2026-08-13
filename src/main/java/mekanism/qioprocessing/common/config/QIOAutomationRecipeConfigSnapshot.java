package mekanism.qioprocessing.common.config;

import mekanism.api.processing.MachineResourceStack;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One bounded server-authoritative page of the active machine recipe profile. */
public final class QIOAutomationRecipeConfigSnapshot {

    public static final int MAX_PAGE_SIZE = 64;
    private static final int SCHEMA_VERSION = 3;
    private static final int MAX_STACKS_PER_ROUTE = 32;
    private static final int MAX_STRING_LENGTH = 8_192;
    private static final int MAX_QUERY_LENGTH = 64;

    private final QIOAutomationRecipeConfigType type;
    private final UUID frequencyUUID;
    private final UUID deviceUUID;
    private final String providerId;
    private final long profileRevision;
    private final long policyRevision;
    private final int offset;
    private final int totalSize;
    private final String query;
    private final boolean individualProfile;
    private final int globalProfileSlot;
    private final RouteFilterMode routeFilterMode;
    private final boolean routeFilterMutable;
    private final boolean editable;
    private final long craftAmount;
    private final long maximumCraftAmount;
    private final List<Route> routes;

    public QIOAutomationRecipeConfigSnapshot(@Nonnull QIOAutomationRecipeConfigType type,
          @Nonnull UUID frequencyUUID, @Nonnull UUID deviceUUID,
          @Nonnull String providerId, long profileRevision, long policyRevision,
          int offset, int totalSize, @Nonnull String query, boolean individualProfile,
          int globalProfileSlot, @Nonnull RouteFilterMode routeFilterMode,
          boolean routeFilterMutable, boolean editable, long craftAmount,
          long maximumCraftAmount,
          @Nonnull List<Route> routes) {
        this.type = Objects.requireNonNull(type, "type");
        this.frequencyUUID = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.providerId = bounded(providerId, "providerId", false);
        if (profileRevision < 0 || policyRevision < 0 || offset < 0 || totalSize < 0 ||
            offset > totalSize || routes.size() > MAX_PAGE_SIZE ||
            routes.size() > totalSize - offset || globalProfileSlot < 1 ||
            globalProfileSlot > 10 || craftAmount <= 0 || maximumCraftAmount <= 0 ||
            craftAmount > maximumCraftAmount) {
            throw new IllegalArgumentException("Invalid QIO automation config snapshot bounds");
        }
        this.profileRevision = profileRevision;
        this.policyRevision = policyRevision;
        this.offset = offset;
        this.totalSize = totalSize;
        this.query = boundedQuery(query);
        this.individualProfile = individualProfile;
        this.globalProfileSlot = globalProfileSlot;
        this.routeFilterMode = Objects.requireNonNull(routeFilterMode, "routeFilterMode");
        this.routeFilterMutable = routeFilterMutable;
        if (!routeFilterMutable && routeFilterMode != RouteFilterMode.WHITELIST) {
            throw new IllegalArgumentException("A fixed QIO route filter must be a whitelist");
        }
        this.editable = editable;
        this.craftAmount = craftAmount;
        this.maximumCraftAmount = maximumCraftAmount;
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
    }

    @Nonnull
    public QIOAutomationRecipeConfigType getType() { return type; }

    @Nonnull
    public UUID getFrequencyUUID() { return frequencyUUID; }

    @Nonnull
    public UUID getDeviceUUID() { return deviceUUID; }

    @Nonnull
    public String getProviderId() { return providerId; }

    public long getProfileRevision() { return profileRevision; }

    public long getPolicyRevision() { return policyRevision; }

    public int getOffset() { return offset; }

    public int getTotalSize() { return totalSize; }

    @Nonnull
    public String getQuery() { return query; }

    public boolean isIndividualProfile() { return individualProfile; }

    public int getGlobalProfileSlot() { return globalProfileSlot; }

    @Nonnull
    public RouteFilterMode getRouteFilterMode() { return routeFilterMode; }

    public boolean isRouteFilterMutable() { return routeFilterMutable; }

    public boolean isEditable() { return editable; }

    public long getCraftAmount() { return craftAmount; }

    public long getMaximumCraftAmount() { return maximumCraftAmount; }

    @Nonnull
    public List<Route> getRoutes() { return routes; }

    public boolean hasPreviousPage() { return offset > 0; }

    public boolean hasNextPage() { return offset + routes.size() < totalSize; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("type", type.name());
        QIOProcessingNbt.writeUUID(data, "frequencyUUID", frequencyUUID);
        QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        data.setString("providerId", providerId);
        data.setLong("profileRevision", profileRevision);
        data.setLong("policyRevision", policyRevision);
        data.setInteger("offset", offset);
        data.setInteger("totalSize", totalSize);
        data.setString("query", query);
        data.setBoolean("individualProfile", individualProfile);
        data.setInteger("globalProfileSlot", globalProfileSlot);
        data.setString("routeFilterMode", routeFilterMode.name());
        data.setBoolean("routeFilterMutable", routeFilterMutable);
        data.setBoolean("editable", editable);
        data.setLong("craftAmount", craftAmount);
        data.setLong("maximumCraftAmount", maximumCraftAmount);
        NBTTagList routeList = new NBTTagList();
        routes.forEach(route -> routeList.appendTag(route.write()));
        data.setTag("routes", routeList);
        return data;
    }

    @Nonnull
    public static QIOAutomationRecipeConfigSnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("type", NBT.TAG_STRING) ||
                !data.hasKey("frequencyUUID", NBT.TAG_STRING) ||
                !data.hasKey("deviceUUID", NBT.TAG_STRING) ||
                !data.hasKey("providerId", NBT.TAG_STRING) ||
                !data.hasKey("profileRevision", NBT.TAG_LONG) ||
                !data.hasKey("policyRevision", NBT.TAG_LONG) ||
                !data.hasKey("offset", NBT.TAG_INT) ||
                !data.hasKey("totalSize", NBT.TAG_INT) ||
                !data.hasKey("query", NBT.TAG_STRING) ||
                !data.hasKey("individualProfile", NBT.TAG_BYTE) ||
                !data.hasKey("globalProfileSlot", NBT.TAG_INT) ||
                !data.hasKey("routeFilterMode", NBT.TAG_STRING) ||
                !data.hasKey("routeFilterMutable", NBT.TAG_BYTE) ||
                !data.hasKey("editable", NBT.TAG_BYTE) ||
                !data.hasKey("craftAmount", NBT.TAG_LONG) ||
                !data.hasKey("maximumCraftAmount", NBT.TAG_LONG) ||
                !data.hasKey("routes", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("QIO automation config snapshot is incomplete");
            }
            NBTTagList storedRoutes = data.getTagList("routes", NBT.TAG_COMPOUND);
            if (storedRoutes.tagCount() > MAX_PAGE_SIZE) {
                throw new QIOProcessingDataException("QIO automation config page is too large");
            }
            List<Route> routes = new ArrayList<>(storedRoutes.tagCount());
            for (int index = 0; index < storedRoutes.tagCount(); index++) {
                routes.add(Route.read(storedRoutes.getCompoundTagAt(index)));
            }
            return new QIOAutomationRecipeConfigSnapshot(
                  QIOAutomationRecipeConfigType.valueOf(data.getString("type")),
                  QIOProcessingNbt.readUUID(data, "frequencyUUID"),
                  QIOProcessingNbt.readUUID(data, "deviceUUID"), data.getString("providerId"),
                  data.getLong("profileRevision"), data.getLong("policyRevision"),
                  data.getInteger("offset"), data.getInteger("totalSize"),
                  data.getString("query"), data.getBoolean("individualProfile"),
                  data.getInteger("globalProfileSlot"), RouteFilterMode.valueOf(
                  data.getString("routeFilterMode")), data.getBoolean("routeFilterMutable"),
                  data.getBoolean("editable"),
                  data.getLong("craftAmount"), data.getLong("maximumCraftAmount"), routes);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO automation config snapshot", e);
        }
    }

    private static String bounded(String value, String name, boolean emptyAllowed) {
        String checked = Objects.requireNonNull(value, name);
        if ((!emptyAllowed && checked.isEmpty()) || checked.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        return checked;
    }

    private static String boundedQuery(String value) {
        String checked = Objects.requireNonNull(value, "query");
        if (checked.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query has an invalid length");
        }
        return checked;
    }

    public static final class Route {

        private final String productKey;
        private final String routeKey;
        private final String routeId;
        private final String recipeKey;
        private final List<MachineResourceStack> inputs;
        private final List<MachineResourceStack> outputs;
        private final boolean profileEnabled;
        private final boolean policyAllowed;
        private final int productOrder;
        private final int routeOrder;
        private final long craftAmount;
        private final long maximumCraftAmount;
        private final boolean craftAmountOverride;

        public Route(@Nonnull String productKey, @Nonnull String routeKey,
              @Nonnull String routeId, @Nonnull String recipeKey,
              @Nonnull List<MachineResourceStack> inputs,
              @Nonnull List<MachineResourceStack> outputs, boolean profileEnabled,
              boolean policyAllowed, int productOrder, int routeOrder, long craftAmount,
              long maximumCraftAmount, boolean craftAmountOverride) {
            this.productKey = bounded(productKey, "productKey", false);
            this.routeKey = bounded(routeKey, "routeKey", false);
            this.routeId = bounded(routeId, "routeId", false);
            this.recipeKey = bounded(recipeKey, "recipeKey", false);
            this.inputs = boundedStacks(inputs, "inputs");
            this.outputs = boundedStacks(outputs, "outputs");
            if (productOrder < 0 || routeOrder < 0 || craftAmount <= 0 ||
                maximumCraftAmount <= 0 || craftAmount > maximumCraftAmount) {
                throw new IllegalArgumentException("Invalid QIO automation route profile values");
            }
            this.profileEnabled = profileEnabled;
            this.policyAllowed = policyAllowed;
            this.productOrder = productOrder;
            this.routeOrder = routeOrder;
            this.craftAmount = craftAmount;
            this.maximumCraftAmount = maximumCraftAmount;
            this.craftAmountOverride = craftAmountOverride;
        }

        @Nonnull public String getProductKey() { return productKey; }
        @Nonnull public String getRouteKey() { return routeKey; }
        @Nonnull public String getRouteId() { return routeId; }
        @Nonnull public String getRecipeKey() { return recipeKey; }
        @Nonnull public List<MachineResourceStack> getInputs() { return inputs; }
        @Nonnull public List<MachineResourceStack> getOutputs() { return outputs; }
        public boolean isProfileEnabled() { return profileEnabled; }
        public boolean isPolicyAllowed() { return policyAllowed; }
        public boolean isEffectiveEnabled() { return profileEnabled && policyAllowed; }
        public int getProductOrder() { return productOrder; }
        public int getRouteOrder() { return routeOrder; }
        public long getCraftAmount() { return craftAmount; }
        public long getMaximumCraftAmount() { return maximumCraftAmount; }
        public boolean hasCraftAmountOverride() { return craftAmountOverride; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("productKey", productKey);
            data.setString("routeKey", routeKey);
            data.setString("routeId", routeId);
            data.setString("recipeKey", recipeKey);
            data.setTag("inputs", writeStacks(inputs));
            data.setTag("outputs", writeStacks(outputs));
            data.setBoolean("profileEnabled", profileEnabled);
            data.setBoolean("policyAllowed", policyAllowed);
            data.setInteger("productOrder", productOrder);
            data.setInteger("routeOrder", routeOrder);
            data.setLong("craftAmount", craftAmount);
            data.setLong("maximumCraftAmount", maximumCraftAmount);
            data.setBoolean("craftAmountOverride", craftAmountOverride);
            return data;
        }

        private static Route read(NBTTagCompound data) throws QIOProcessingDataException {
            if (!data.hasKey("productKey", NBT.TAG_STRING) ||
                !data.hasKey("routeKey", NBT.TAG_STRING) ||
                !data.hasKey("routeId", NBT.TAG_STRING) ||
                !data.hasKey("recipeKey", NBT.TAG_STRING) ||
                !data.hasKey("inputs", NBT.TAG_LIST) ||
                !data.hasKey("outputs", NBT.TAG_LIST) ||
                !data.hasKey("profileEnabled", NBT.TAG_BYTE) ||
                !data.hasKey("policyAllowed", NBT.TAG_BYTE) ||
                !data.hasKey("productOrder", NBT.TAG_INT) ||
                !data.hasKey("routeOrder", NBT.TAG_INT) ||
                !data.hasKey("craftAmount", NBT.TAG_LONG) ||
                !data.hasKey("maximumCraftAmount", NBT.TAG_LONG) ||
                !data.hasKey("craftAmountOverride", NBT.TAG_BYTE)) {
                throw new QIOProcessingDataException("QIO automation route snapshot is incomplete");
            }
            return new Route(data.getString("productKey"), data.getString("routeKey"),
                  data.getString("routeId"), data.getString("recipeKey"),
                  readStacks(data.getTagList("inputs", NBT.TAG_COMPOUND)),
                  readStacks(data.getTagList("outputs", NBT.TAG_COMPOUND)),
                  data.getBoolean("profileEnabled"), data.getBoolean("policyAllowed"),
                  data.getInteger("productOrder"), data.getInteger("routeOrder"),
                  data.getLong("craftAmount"), data.getLong("maximumCraftAmount"),
                  data.getBoolean("craftAmountOverride"));
        }

        private static List<MachineResourceStack> boundedStacks(
              List<MachineResourceStack> stacks, String name) {
            Objects.requireNonNull(stacks, name);
            if (stacks.isEmpty() || stacks.size() > MAX_STACKS_PER_ROUTE) {
                throw new IllegalArgumentException(name + " has an invalid size");
            }
            List<MachineResourceStack> copy = new ArrayList<>(stacks.size());
            stacks.forEach(stack -> copy.add(Objects.requireNonNull(stack, name + " stack")));
            return Collections.unmodifiableList(copy);
        }

        private static NBTTagList writeStacks(List<MachineResourceStack> stacks) {
            NBTTagList list = new NBTTagList();
            stacks.forEach(stack -> list.appendTag(stack.write(new NBTTagCompound())));
            return list;
        }

        private static List<MachineResourceStack> readStacks(NBTTagList stored)
              throws QIOProcessingDataException {
            if (stored.tagCount() <= 0 || stored.tagCount() > MAX_STACKS_PER_ROUTE) {
                throw new QIOProcessingDataException("QIO automation route stack list is invalid");
            }
            List<MachineResourceStack> stacks = new ArrayList<>(stored.tagCount());
            for (int index = 0; index < stored.tagCount(); index++) {
                MachineResourceStack stack = MachineResourceStack.read(
                      stored.getCompoundTagAt(index));
                if (stack == null) {
                    throw new QIOProcessingDataException("QIO automation route stack is invalid");
                }
                stacks.add(stack);
            }
            return stacks;
        }
    }
}
