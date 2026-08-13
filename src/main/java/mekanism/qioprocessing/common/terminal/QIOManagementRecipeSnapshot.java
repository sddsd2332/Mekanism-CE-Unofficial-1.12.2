package mekanism.qioprocessing.common.terminal;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
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

/** One bounded remote machine-profile page for the management terminal. */
public final class QIOManagementRecipeSnapshot {

    public static final int MAX_PAGE_SIZE = 64;
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_RESOURCES_PER_ROUTE = 4_096;

    public enum PageKind {
        PRODUCTS,
        ROUTES
    }

    private final PageKind pageKind;
    private final UUID deviceUUID;
    private final QIOAutomationMode mode;
    private final String providerId;
    private final String profileScopeId;
    private final long routeDirectoryRevision;
    private final long profileRevision;
    private final long policyRevision;
    private final boolean individualProfile;
    private final int globalProfileSlot;
    private final RouteFilterMode routeFilterMode;
    private final boolean editable;
    private final long craftAmount;
    private final long maximumCraftAmount;
    private final int offset;
    private final int totalSize;
    private final String query;
    private final String productKey;
    private final List<Product> products;
    private final List<Route> routes;

    public QIOManagementRecipeSnapshot(@Nonnull PageKind pageKind,
          @Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode mode,
          @Nonnull String providerId, @Nonnull String profileScopeId,
          long routeDirectoryRevision, long profileRevision, long policyRevision,
          boolean individualProfile, int globalProfileSlot,
          @Nonnull RouteFilterMode routeFilterMode, boolean editable,
          long craftAmount, long maximumCraftAmount, int offset, int totalSize,
          @Nonnull String query, @Nonnull String productKey,
          @Nonnull List<Product> products, @Nonnull List<Route> routes) {
        this.pageKind = Objects.requireNonNull(pageKind, "pageKind");
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.mode = Objects.requireNonNull(mode, "mode");
        if (mode == QIOAutomationMode.OUTPUT_ONLY) {
            throw new IllegalArgumentException("Output-only devices have no recipe profile");
        }
        this.providerId = requireText(providerId, "providerId", false);
        this.profileScopeId = requireText(profileScopeId, "profileScopeId", false);
        if (routeDirectoryRevision < 0 || profileRevision < 0 || policyRevision < 0 ||
            globalProfileSlot < 1 || globalProfileSlot > 10 || craftAmount <= 0 ||
            maximumCraftAmount <= 0 || craftAmount > maximumCraftAmount || offset < 0 ||
            totalSize < 0 || offset > totalSize) {
            throw new IllegalArgumentException("Invalid remote recipe snapshot bounds");
        }
        this.routeDirectoryRevision = routeDirectoryRevision;
        this.profileRevision = profileRevision;
        this.policyRevision = policyRevision;
        this.individualProfile = individualProfile;
        this.globalProfileSlot = globalProfileSlot;
        this.routeFilterMode = Objects.requireNonNull(routeFilterMode, "routeFilterMode");
        this.editable = editable;
        this.craftAmount = craftAmount;
        this.maximumCraftAmount = maximumCraftAmount;
        this.offset = offset;
        this.totalSize = totalSize;
        this.query = requireText(query, "query", true);
        this.productKey = requireText(productKey, "productKey", true);
        this.products = immutableBounded(products, "products");
        this.routes = immutableBounded(routes, "routes");
        int pageEntries = pageKind == PageKind.PRODUCTS ? this.products.size() :
              this.routes.size();
        if (pageKind == PageKind.PRODUCTS && !this.routes.isEmpty() ||
            pageKind == PageKind.ROUTES && (!this.products.isEmpty() || productKey.isEmpty()) ||
            pageEntries > totalSize - offset) {
            throw new IllegalArgumentException("Remote recipe page shape disagrees with its kind");
        }
    }

    @Nonnull public PageKind getPageKind() { return pageKind; }
    @Nonnull public UUID getDeviceUUID() { return deviceUUID; }
    @Nonnull public QIOAutomationMode getMode() { return mode; }
    @Nonnull public String getProviderId() { return providerId; }
    @Nonnull public String getProfileScopeId() { return profileScopeId; }
    public long getRouteDirectoryRevision() { return routeDirectoryRevision; }
    public long getProfileRevision() { return profileRevision; }
    public long getPolicyRevision() { return policyRevision; }
    public boolean isIndividualProfile() { return individualProfile; }
    public int getGlobalProfileSlot() { return globalProfileSlot; }
    @Nonnull public RouteFilterMode getRouteFilterMode() { return routeFilterMode; }
    public boolean isEditable() { return editable; }
    public long getCraftAmount() { return craftAmount; }
    public long getMaximumCraftAmount() { return maximumCraftAmount; }
    public int getOffset() { return offset; }
    public int getTotalSize() { return totalSize; }
    @Nonnull public String getQuery() { return query; }
    @Nonnull public String getProductKey() { return productKey; }
    @Nonnull public List<Product> getProducts() { return products; }
    @Nonnull public List<Route> getRoutes() { return routes; }
    public boolean hasPreviousPage() { return offset > 0; }
    public boolean hasNextPage() { return offset + pageSize() < totalSize; }

    public int pageSize() {
        return pageKind == PageKind.PRODUCTS ? products.size() : routes.size();
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("pageKind", pageKind.name());
        QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        data.setString("mode", mode.name());
        data.setString("providerId", providerId);
        data.setString("profileScopeId", profileScopeId);
        data.setLong("routeDirectoryRevision", routeDirectoryRevision);
        data.setLong("profileRevision", profileRevision);
        data.setLong("policyRevision", policyRevision);
        data.setBoolean("individualProfile", individualProfile);
        data.setInteger("globalProfileSlot", globalProfileSlot);
        data.setString("routeFilterMode", routeFilterMode.name());
        data.setBoolean("editable", editable);
        data.setLong("craftAmount", craftAmount);
        data.setLong("maximumCraftAmount", maximumCraftAmount);
        data.setInteger("offset", offset);
        data.setInteger("totalSize", totalSize);
        data.setString("query", query);
        data.setString("productKey", productKey);
        NBTTagList productList = new NBTTagList();
        products.forEach(product -> productList.appendTag(product.write()));
        data.setTag("products", productList);
        NBTTagList routeList = new NBTTagList();
        routes.forEach(route -> routeList.appendTag(route.write()));
        data.setTag("routes", routeList);
        return data;
    }

    @Nonnull
    public static QIOManagementRecipeSnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            int schema = data.getInteger("schema");
            if (schema != 1 && schema != SCHEMA_VERSION ||
                !data.hasKey("products", NBT.TAG_LIST) ||
                !data.hasKey("routes", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException(
                      "Remote QIO recipe snapshot is incomplete");
            }
            NBTTagList storedProducts = data.getTagList("products", NBT.TAG_COMPOUND);
            NBTTagList storedRoutes = data.getTagList("routes", NBT.TAG_COMPOUND);
            if (storedProducts.tagCount() > MAX_PAGE_SIZE ||
                storedRoutes.tagCount() > MAX_PAGE_SIZE) {
                throw new QIOProcessingDataException("Remote QIO recipe page is too large");
            }
            List<Product> products = new ArrayList<>(storedProducts.tagCount());
            for (int index = 0; index < storedProducts.tagCount(); index++) {
                products.add(Product.read(storedProducts.getCompoundTagAt(index)));
            }
            List<Route> routes = new ArrayList<>(storedRoutes.tagCount());
            for (int index = 0; index < storedRoutes.tagCount(); index++) {
                routes.add(Route.read(storedRoutes.getCompoundTagAt(index), schema));
            }
            return new QIOManagementRecipeSnapshot(
                  QIOProcessingNbt.readEnum(data, "pageKind", PageKind.class),
                  QIOProcessingNbt.readUUID(data, "deviceUUID"),
                  QIOProcessingNbt.readEnum(data, "mode", QIOAutomationMode.class),
                  data.getString("providerId"), data.getString("profileScopeId"),
                  data.getLong("routeDirectoryRevision"), data.getLong("profileRevision"),
                  data.getLong("policyRevision"), data.getBoolean("individualProfile"),
                  data.getInteger("globalProfileSlot"),
                  QIOProcessingNbt.readEnum(data, "routeFilterMode", RouteFilterMode.class),
                  data.getBoolean("editable"), data.getLong("craftAmount"),
                  data.getLong("maximumCraftAmount"), data.getInteger("offset"),
                  data.getInteger("totalSize"), data.getString("query"),
                  data.getString("productKey"), products, routes);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid remote QIO recipe snapshot", e);
        }
    }

    public static final class Product {

        private final String productKey;
        private final ResourceAmount product;
        private final int routeCount;
        private final int profileEnabledCount;
        private final int effectiveEnabledCount;
        private final int order;

        public Product(@Nonnull String productKey, @Nonnull ResourceAmount product,
              int routeCount, int profileEnabledCount, int effectiveEnabledCount, int order) {
            this.productKey = requireText(productKey, "productKey", false);
            this.product = Objects.requireNonNull(product, "product");
            if (routeCount <= 0 || profileEnabledCount < 0 ||
                profileEnabledCount > routeCount || effectiveEnabledCount < 0 ||
                effectiveEnabledCount > profileEnabledCount || order < 0) {
                throw new IllegalArgumentException("Invalid remote recipe product summary");
            }
            this.routeCount = routeCount;
            this.profileEnabledCount = profileEnabledCount;
            this.effectiveEnabledCount = effectiveEnabledCount;
            this.order = order;
        }

        @Nonnull public String getProductKey() { return productKey; }
        @Nonnull public ResourceAmount getProduct() { return product; }
        public int getRouteCount() { return routeCount; }
        public int getProfileEnabledCount() { return profileEnabledCount; }
        public int getEffectiveEnabledCount() { return effectiveEnabledCount; }
        public int getOrder() { return order; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("productKey", productKey);
            data.setTag("product", product.write());
            data.setInteger("routeCount", routeCount);
            data.setInteger("profileEnabledCount", profileEnabledCount);
            data.setInteger("effectiveEnabledCount", effectiveEnabledCount);
            data.setInteger("order", order);
            return data;
        }

        private static Product read(NBTTagCompound data) {
            return new Product(data.getString("productKey"),
                  ResourceAmount.read(data.getCompoundTag("product")),
                  data.getInteger("routeCount"), data.getInteger("profileEnabledCount"),
                  data.getInteger("effectiveEnabledCount"), data.getInteger("order"));
        }
    }

    public static final class Route {

        private final String productKey;
        private final String routeKey;
        private final String routeId;
        private final String recipeKey;
        private final List<ResourceAmount> configurationInputs;
        private final List<ResourceAmount> inputs;
        private final List<ResourceAmount> outputs;
        private final List<ResourceAmount> optionalOutputs;
        private final boolean profileEnabled;
        private final boolean policyAllowed;
        private final int order;
        private final long craftAmount;
        private final long maximumCraftAmount;
        private final boolean craftAmountOverride;

        public Route(@Nonnull String productKey, @Nonnull String routeKey,
              @Nonnull String routeId, @Nonnull String recipeKey,
              @Nonnull List<ResourceAmount> inputs, @Nonnull List<ResourceAmount> outputs,
              @Nonnull List<ResourceAmount> optionalOutputs, boolean profileEnabled,
              boolean policyAllowed, int order, long craftAmount,
              long maximumCraftAmount, boolean craftAmountOverride) {
            this(productKey, routeKey, routeId, recipeKey, Collections.emptyList(), inputs,
                  outputs, optionalOutputs, profileEnabled, policyAllowed, order, craftAmount,
                  maximumCraftAmount, craftAmountOverride);
        }

        public Route(@Nonnull String productKey, @Nonnull String routeKey,
              @Nonnull String routeId, @Nonnull String recipeKey,
              @Nonnull List<ResourceAmount> configurationInputs,
              @Nonnull List<ResourceAmount> inputs, @Nonnull List<ResourceAmount> outputs,
              @Nonnull List<ResourceAmount> optionalOutputs, boolean profileEnabled,
              boolean policyAllowed, int order, long craftAmount,
              long maximumCraftAmount, boolean craftAmountOverride) {
            this.productKey = requireText(productKey, "productKey", false);
            this.routeKey = requireText(routeKey, "routeKey", false);
            this.routeId = requireText(routeId, "routeId", false);
            this.recipeKey = requireText(recipeKey, "recipeKey", false);
            this.configurationInputs = immutableResources(configurationInputs,
                  "configurationInputs", true);
            this.inputs = immutableResources(inputs, "inputs", false);
            this.outputs = immutableResources(outputs, "outputs", false);
            this.optionalOutputs = immutableResources(optionalOutputs, "optionalOutputs", true);
            if (order < 0 || craftAmount <= 0 || maximumCraftAmount <= 0 ||
                craftAmount > maximumCraftAmount) {
                throw new IllegalArgumentException("Invalid remote recipe route summary");
            }
            this.profileEnabled = profileEnabled;
            this.policyAllowed = policyAllowed;
            this.order = order;
            this.craftAmount = craftAmount;
            this.maximumCraftAmount = maximumCraftAmount;
            this.craftAmountOverride = craftAmountOverride;
        }

        @Nonnull public String getProductKey() { return productKey; }
        @Nonnull public String getRouteKey() { return routeKey; }
        @Nonnull public String getRouteId() { return routeId; }
        @Nonnull public String getRecipeKey() { return recipeKey; }
        @Nonnull public List<ResourceAmount> getConfigurationInputs() { return configurationInputs; }
        @Nonnull public List<ResourceAmount> getInputs() { return inputs; }
        @Nonnull public List<ResourceAmount> getOutputs() { return outputs; }
        @Nonnull public List<ResourceAmount> getOptionalOutputs() { return optionalOutputs; }
        public boolean isProfileEnabled() { return profileEnabled; }
        public boolean isPolicyAllowed() { return policyAllowed; }
        public boolean isEffectiveEnabled() { return profileEnabled && policyAllowed; }
        public int getOrder() { return order; }
        public long getCraftAmount() { return craftAmount; }
        public long getMaximumCraftAmount() { return maximumCraftAmount; }
        public boolean hasCraftAmountOverride() { return craftAmountOverride; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("productKey", productKey);
            data.setString("routeKey", routeKey);
            data.setString("routeId", routeId);
            data.setString("recipeKey", recipeKey);
            data.setTag("configurationInputs", writeResources(configurationInputs));
            data.setTag("inputs", writeResources(inputs));
            data.setTag("outputs", writeResources(outputs));
            data.setTag("optionalOutputs", writeResources(optionalOutputs));
            data.setBoolean("profileEnabled", profileEnabled);
            data.setBoolean("policyAllowed", policyAllowed);
            data.setInteger("order", order);
            data.setLong("craftAmount", craftAmount);
            data.setLong("maximumCraftAmount", maximumCraftAmount);
            data.setBoolean("craftAmountOverride", craftAmountOverride);
            return data;
        }

        private static Route read(NBTTagCompound data, int schema) {
            return new Route(data.getString("productKey"), data.getString("routeKey"),
                  data.getString("routeId"), data.getString("recipeKey"),
                  schema >= 2 ? readResources(data.getTagList("configurationInputs",
                        NBT.TAG_COMPOUND)) : Collections.emptyList(),
                  readResources(data.getTagList("inputs", NBT.TAG_COMPOUND)),
                  readResources(data.getTagList("outputs", NBT.TAG_COMPOUND)),
                  readResources(data.getTagList("optionalOutputs", NBT.TAG_COMPOUND)),
                  data.getBoolean("profileEnabled"), data.getBoolean("policyAllowed"),
                  data.getInteger("order"), data.getLong("craftAmount"),
                  data.getLong("maximumCraftAmount"),
                  data.getBoolean("craftAmountOverride"));
        }
    }

    public static final class ResourceAmount {

        private final PortableResourceDescriptor resource;
        private final long amount;

        public ResourceAmount(@Nonnull PortableResourceDescriptor resource, long amount) {
            this.resource = Objects.requireNonNull(resource, "resource");
            if (amount <= 0) {
                throw new IllegalArgumentException("Resource amount must be positive");
            }
            this.amount = amount;
        }

        @Nonnull public PortableResourceDescriptor getResource() { return resource; }
        public long getAmount() { return amount; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setTag("resource", resource.write());
            data.setLong("amount", amount);
            return data;
        }

        private static ResourceAmount read(NBTTagCompound data) {
            return new ResourceAmount(PortableResourceDescriptor.read(
                  data.getCompoundTag("resource")), data.getLong("amount"));
        }
    }

    private static <T> List<T> immutableBounded(List<T> source, String name) {
        Objects.requireNonNull(source, name);
        if (source.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(name + " exceeds the page limit");
        }
        List<T> copy = new ArrayList<>(source.size());
        source.forEach(value -> copy.add(Objects.requireNonNull(value, name + " entry")));
        return Collections.unmodifiableList(copy);
    }

    private static List<ResourceAmount> immutableResources(List<ResourceAmount> source,
          String name, boolean emptyAllowed) {
        Objects.requireNonNull(source, name);
        if ((!emptyAllowed && source.isEmpty()) || source.size() > MAX_RESOURCES_PER_ROUTE) {
            throw new IllegalArgumentException(name + " has an invalid size");
        }
        List<ResourceAmount> copy = new ArrayList<>(source.size());
        source.forEach(value -> copy.add(Objects.requireNonNull(value, name + " entry")));
        return Collections.unmodifiableList(copy);
    }

    private static NBTTagList writeResources(List<ResourceAmount> resources) {
        NBTTagList list = new NBTTagList();
        resources.forEach(resource -> list.appendTag(resource.write()));
        return list;
    }

    private static List<ResourceAmount> readResources(NBTTagList stored) {
        if (stored.tagCount() > MAX_RESOURCES_PER_ROUTE) {
            throw new IllegalArgumentException("Remote recipe resource list is too large");
        }
        List<ResourceAmount> resources = new ArrayList<>(stored.tagCount());
        for (int index = 0; index < stored.tagCount(); index++) {
            resources.add(ResourceAmount.read(stored.getCompoundTagAt(index)));
        }
        return resources;
    }

    private static String requireText(String value, String name, boolean emptyAllowed) {
        String checked = Objects.requireNonNull(value, name).trim();
        if ((!emptyAllowed && checked.isEmpty()) || checked.length() > 8_192) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        return checked;
    }
}
