package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Bounded workbench configuration page sent to one management terminal. */
/**
 * QIO 处理模块中的 QIOWorkbenchConfigurationSnapshot 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchConfigurationSnapshot {

    private static final int SCHEMA_VERSION = 3;
    public static final int MAX_PAGE_SIZE = 64;
    private static final int MAX_RECIPE_ID_LENGTH = 256;
    private static final int HASH_LENGTH = 64;

    public enum PageKind {
        PRODUCTS,
        RECIPES,
        CANDIDATES
    }

    private final PageKind pageKind;
    private final UUID configUUID;
    private final UUID originUUID;
    private final long configurationRevision;
    private final long catalogRevision;
    private final boolean editable;
    private final int recoveryPatternCount;
    private final int offset;
    private final int totalSize;
    private final String query;
    private final String productKey;
    private final String recipeId;
    private final String recipeSignature;
    private final int ingredientSlot;
    private final List<Product> products;
    private final List<Recipe> recipes;
    private final List<Candidate> candidates;

    public QIOWorkbenchConfigurationSnapshot(@Nonnull PageKind pageKind,
          @Nonnull UUID configUUID, @Nonnull UUID originUUID,
          long configurationRevision, long catalogRevision, boolean editable,
          int recoveryPatternCount,
          int offset, int totalSize, @Nonnull String query,
          @Nonnull String productKey, @Nonnull String recipeId,
          @Nonnull String recipeSignature, int ingredientSlot,
          @Nonnull List<Product> products, @Nonnull List<Recipe> recipes,
          @Nonnull List<Candidate> candidates) {
        this.pageKind = Objects.requireNonNull(pageKind, "pageKind");
        this.configUUID = Objects.requireNonNull(configUUID, "configUUID");
        this.originUUID = Objects.requireNonNull(originUUID, "originUUID");
        if (configurationRevision < 0 || catalogRevision < 0 || offset < 0 ||
            totalSize < 0 || offset > totalSize) {
            throw new IllegalArgumentException("Invalid workbench snapshot revision/page");
        }
        this.configurationRevision = configurationRevision;
        this.catalogRevision = catalogRevision;
        this.editable = editable;
        if (recoveryPatternCount < 0 || recoveryPatternCount > 65_536) {
            throw new IllegalArgumentException("Invalid workbench recovery pattern count");
        }
        this.recoveryPatternCount = recoveryPatternCount;
        this.offset = offset;
        this.totalSize = totalSize;
        this.query = checkedText(query, 128, true, "query");
        this.productKey = checkedHash(productKey, pageKind == PageKind.PRODUCTS,
              "productKey");
        this.recipeId = checkedText(recipeId, MAX_RECIPE_ID_LENGTH,
              pageKind != PageKind.CANDIDATES, "recipeId");
        this.recipeSignature = checkedHash(recipeSignature,
              pageKind != PageKind.CANDIDATES, "recipeSignature");
        if (pageKind == PageKind.CANDIDATES ? ingredientSlot < 0 || ingredientSlot >= 9 :
            ingredientSlot != -1) {
            throw new IllegalArgumentException("Invalid workbench ingredient slot");
        }
        this.ingredientSlot = ingredientSlot;
        this.products = immutable(products, MAX_PAGE_SIZE, "products");
        this.recipes = immutable(recipes, MAX_PAGE_SIZE, "recipes");
        this.candidates = immutable(candidates, MAX_PAGE_SIZE, "candidates");
        int pageCount = switch (pageKind) {
            case PRODUCTS -> this.products.size();
            case RECIPES -> this.recipes.size();
            case CANDIDATES -> this.candidates.size();
        };
        if (pageCount > totalSize - offset ||
            pageKind != PageKind.PRODUCTS && !this.products.isEmpty() ||
            pageKind != PageKind.RECIPES && !this.recipes.isEmpty() ||
            pageKind != PageKind.CANDIDATES && !this.candidates.isEmpty()) {
            throw new IllegalArgumentException("Workbench snapshot page contents disagree");
        }
    }

    @Nonnull public PageKind getPageKind() { return pageKind; }
    @Nonnull public UUID getConfigUUID() { return configUUID; }
    @Nonnull public UUID getOriginUUID() { return originUUID; }
    public long getConfigurationRevision() { return configurationRevision; }
    public long getCatalogRevision() { return catalogRevision; }
    public boolean isEditable() { return editable; }
    public int getRecoveryPatternCount() { return recoveryPatternCount; }
    public int getOffset() { return offset; }
    public int getTotalSize() { return totalSize; }
    @Nonnull public String getQuery() { return query; }
    @Nonnull public String getProductKey() { return productKey; }
    @Nonnull public String getRecipeId() { return recipeId; }
    @Nonnull public String getRecipeSignature() { return recipeSignature; }
    public int getIngredientSlot() { return ingredientSlot; }
    @Nonnull public List<Product> getProducts() { return products; }
    @Nonnull public List<Recipe> getRecipes() { return recipes; }
    @Nonnull public List<Candidate> getCandidates() { return candidates; }
    public int pageSize() { return switch (pageKind) {
        case PRODUCTS -> products.size();
        case RECIPES -> recipes.size();
        case CANDIDATES -> candidates.size();
    }; }
    public boolean hasPreviousPage() { return offset > 0; }
    public boolean hasNextPage() { return offset + pageSize() < totalSize; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("pageKind", pageKind.name());
        QIOProcessingNbt.writeUUID(data, "configUUID", configUUID);
        QIOProcessingNbt.writeUUID(data, "originUUID", originUUID);
        data.setLong("configurationRevision", configurationRevision);
        data.setLong("catalogRevision", catalogRevision);
        data.setBoolean("editable", editable);
        data.setInteger("recoveryPatternCount", recoveryPatternCount);
        data.setInteger("offset", offset);
        data.setInteger("totalSize", totalSize);
        data.setString("query", query);
        data.setString("productKey", productKey);
        data.setString("recipeId", recipeId);
        data.setString("recipeSignature", recipeSignature);
        data.setInteger("ingredientSlot", ingredientSlot);
        data.setTag("products", writeList(products, Product::write));
        data.setTag("recipes", writeList(recipes, Recipe::write));
        data.setTag("candidates", writeList(candidates, Candidate::write));
        return data;
    }

    @Nonnull
    public static QIOWorkbenchConfigurationSnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("pageKind", NBT.TAG_STRING) ||
                !data.hasKey("configUUID", NBT.TAG_STRING) ||
                !data.hasKey("originUUID", NBT.TAG_STRING) ||
                !data.hasKey("configurationRevision", NBT.TAG_LONG) ||
                !data.hasKey("catalogRevision", NBT.TAG_LONG) ||
                !data.hasKey("editable", NBT.TAG_BYTE) ||
                !data.hasKey("recoveryPatternCount", NBT.TAG_INT) ||
                !data.hasKey("offset", NBT.TAG_INT) ||
                !data.hasKey("totalSize", NBT.TAG_INT) ||
                !data.hasKey("query", NBT.TAG_STRING) ||
                !data.hasKey("productKey", NBT.TAG_STRING) ||
                !data.hasKey("recipeId", NBT.TAG_STRING) ||
                !data.hasKey("recipeSignature", NBT.TAG_STRING) ||
                !data.hasKey("ingredientSlot", NBT.TAG_INT) ||
                !data.hasKey("products", NBT.TAG_LIST) ||
                !data.hasKey("recipes", NBT.TAG_LIST) ||
                !data.hasKey("candidates", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("Incomplete workbench snapshot");
            }
            NBTTagList products = bounded(data, "products");
            NBTTagList recipes = bounded(data, "recipes");
            NBTTagList candidates = bounded(data, "candidates");
            List<Product> productList = new ArrayList<>(products.tagCount());
            for (int index = 0; index < products.tagCount(); index++) {
                productList.add(Product.read(products.getCompoundTagAt(index)));
            }
            List<Recipe> recipeList = new ArrayList<>(recipes.tagCount());
            for (int index = 0; index < recipes.tagCount(); index++) {
                recipeList.add(Recipe.read(recipes.getCompoundTagAt(index)));
            }
            List<Candidate> candidateList = new ArrayList<>(candidates.tagCount());
            for (int index = 0; index < candidates.tagCount(); index++) {
                candidateList.add(Candidate.read(candidates.getCompoundTagAt(index)));
            }
            return new QIOWorkbenchConfigurationSnapshot(PageKind.valueOf(
                  data.getString("pageKind")),
                  QIOProcessingNbt.readUUID(data, "configUUID"),
                  QIOProcessingNbt.readUUID(data, "originUUID"),
                  data.getLong("configurationRevision"), data.getLong("catalogRevision"),
                  data.getBoolean("editable"), data.getInteger("recoveryPatternCount"),
                  data.getInteger("offset"),
                  data.getInteger("totalSize"), data.getString("query"),
                  data.getString("productKey"), data.getString("recipeId"),
                  data.getString("recipeSignature"), data.getInteger("ingredientSlot"),
                  productList, recipeList, candidateList);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid workbench snapshot", e);
        }
    }

    public static final class Product {

        private final String productKey;
        private final PortableResourceDescriptor output;
        private final int recipeCount;
        private final int enabledRecipeCount;

        public Product(String productKey, PortableResourceDescriptor output, int recipeCount,
              int enabledRecipeCount) {
            this.productKey = checkedHash(productKey, false, "productKey");
            this.output = Objects.requireNonNull(output, "output").withoutCapabilities();
            if (output.getKind() != PortableResourceDescriptor.Kind.ITEM || recipeCount <= 0 ||
                enabledRecipeCount < 0 || enabledRecipeCount > recipeCount) {
                throw new IllegalArgumentException("Invalid workbench product summary");
            }
            this.recipeCount = recipeCount;
            this.enabledRecipeCount = enabledRecipeCount;
        }

        @Nonnull public String getProductKey() { return productKey; }
        @Nonnull public PortableResourceDescriptor getOutput() { return output; }
        public int getRecipeCount() { return recipeCount; }
        public int getEnabledRecipeCount() { return enabledRecipeCount; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("productKey", productKey);
            data.setTag("output", output.write());
            data.setInteger("recipeCount", recipeCount);
            data.setInteger("enabledRecipeCount", enabledRecipeCount);
            return data;
        }

        private static Product read(NBTTagCompound data)
              throws QIOProcessingDataException {
            if (!data.hasKey("productKey", NBT.TAG_STRING) ||
                !data.hasKey("output", NBT.TAG_COMPOUND) ||
                !data.hasKey("recipeCount", NBT.TAG_INT) ||
                !data.hasKey("enabledRecipeCount", NBT.TAG_INT)) {
                throw new QIOProcessingDataException("Incomplete workbench product summary");
            }
            return new Product(data.getString("productKey"), PortableResourceDescriptor.read(
                  data.getCompoundTag("output")), data.getInteger("recipeCount"),
                  data.getInteger("enabledRecipeCount"));
        }
    }

    public static final class Recipe {

        private final String recipeId;
        private final String signature;
        private final PortableResourceDescriptor output;
        private final int outputAmount;
        private final boolean enabled;
        private final int order;
        private final boolean shaped;
        private final int width;
        private final int height;
        private final List<Ingredient> ingredients;

        public Recipe(String recipeId, String signature, PortableResourceDescriptor output,
              int outputAmount, boolean enabled, int order, boolean shaped, int width,
              int height, List<Ingredient> ingredients) {
            this.recipeId = checkedText(recipeId, MAX_RECIPE_ID_LENGTH, false, "recipeId");
            this.signature = checkedHash(signature, false, "recipeSignature");
            this.output = Objects.requireNonNull(output, "output").withoutCapabilities();
            if (outputAmount <= 0 || order < 0 || ingredients.size() != 9 ||
                shaped && (width <= 0 || width > 3 || height <= 0 || height > 3) ||
                !shaped && (width != 0 || height != 0)) {
                throw new IllegalArgumentException("Invalid logical workbench recipe");
            }
            this.outputAmount = outputAmount;
            this.enabled = enabled;
            this.order = order;
            this.shaped = shaped;
            this.width = width;
            this.height = height;
            List<Ingredient> copied = immutable(ingredients, 9, "ingredients");
            Set<Integer> slots = new HashSet<>();
            for (Ingredient ingredient : copied) {
                if (!slots.add(ingredient.getSlot())) {
                    throw new IllegalArgumentException("Duplicate workbench ingredient slot");
                }
            }
            this.ingredients = copied;
        }

        @Nonnull public String getRecipeId() { return recipeId; }
        @Nonnull public String getSignature() { return signature; }
        @Nonnull public PortableResourceDescriptor getOutput() { return output; }
        public int getOutputAmount() { return outputAmount; }
        public boolean isEnabled() { return enabled; }
        public int getOrder() { return order; }
        public boolean isShaped() { return shaped; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        @Nonnull public List<Ingredient> getIngredients() { return ingredients; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("recipeId", recipeId);
            data.setString("signature", signature);
            data.setTag("output", output.write());
            data.setInteger("outputAmount", outputAmount);
            data.setBoolean("enabled", enabled);
            data.setInteger("order", order);
            data.setBoolean("shaped", shaped);
            data.setInteger("width", width);
            data.setInteger("height", height);
            data.setTag("ingredients", writeList(ingredients, Ingredient::write));
            return data;
        }

        private static Recipe read(NBTTagCompound data)
              throws QIOProcessingDataException {
            if (!data.hasKey("recipeId", NBT.TAG_STRING) ||
                !data.hasKey("signature", NBT.TAG_STRING) ||
                !data.hasKey("output", NBT.TAG_COMPOUND) ||
                !data.hasKey("outputAmount", NBT.TAG_INT) ||
                !data.hasKey("enabled", NBT.TAG_BYTE) ||
                !data.hasKey("order", NBT.TAG_INT) ||
                !data.hasKey("shaped", NBT.TAG_BYTE) ||
                !data.hasKey("width", NBT.TAG_INT) ||
                !data.hasKey("height", NBT.TAG_INT) ||
                !data.hasKey("ingredients", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("Incomplete logical workbench recipe");
            }
            NBTTagList ingredients = bounded(data, "ingredients", 9);
            List<Ingredient> values = new ArrayList<>(ingredients.tagCount());
            for (int index = 0; index < ingredients.tagCount(); index++) {
                values.add(Ingredient.read(ingredients.getCompoundTagAt(index)));
            }
            return new Recipe(data.getString("recipeId"), data.getString("signature"),
                  PortableResourceDescriptor.read(data.getCompoundTag("output")),
                  data.getInteger("outputAmount"), data.getBoolean("enabled"),
                  data.getInteger("order"), data.getBoolean("shaped"),
                  data.getInteger("width"), data.getInteger("height"), values);
        }
    }

    public static final class Ingredient {

        private final int slot;
        private final int candidateCount;
        private final int enabledCandidateCount;
        private final ItemStack representative;
        @Nullable private final PortableResourceDescriptor virtualFluid;
        private final long virtualFluidAmount;

        public Ingredient(int slot, int candidateCount, int enabledCandidateCount,
              ItemStack representative, @Nullable PortableResourceDescriptor virtualFluid,
              long virtualFluidAmount) {
            if (slot < 0 || slot >= 9 || candidateCount < 0 ||
                enabledCandidateCount < 0 || enabledCandidateCount > candidateCount ||
                candidateCount == 0 != representative.isEmpty() ||
                candidateCount > 0 && enabledCandidateCount == 0 ||
                (virtualFluid == null) != (virtualFluidAmount == 0) ||
                virtualFluid != null && (candidateCount == 0 || virtualFluidAmount <= 0 ||
                      virtualFluid.getKind() != PortableResourceDescriptor.Kind.FLUID)) {
                throw new IllegalArgumentException("Invalid workbench ingredient summary");
            }
            this.slot = slot;
            this.candidateCount = candidateCount;
            this.enabledCandidateCount = enabledCandidateCount;
            this.representative = QIORecipeStackUtils.copyForRecipeSelection(representative);
            this.virtualFluid = virtualFluid;
            this.virtualFluidAmount = virtualFluidAmount;
        }

        public int getSlot() { return slot; }
        public int getCandidateCount() { return candidateCount; }
        public int getEnabledCandidateCount() { return enabledCandidateCount; }
        public boolean isEmpty() { return candidateCount == 0; }
        @Nonnull public ItemStack getRepresentative() {
            return QIORecipeStackUtils.copyForRecipeSelection(representative);
        }
        public boolean isVirtualFluid() { return virtualFluid != null; }
        @Nullable public PortableResourceDescriptor getVirtualFluid() { return virtualFluid; }
        public long getVirtualFluidAmount() { return virtualFluidAmount; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("slot", slot);
            data.setInteger("candidateCount", candidateCount);
            data.setInteger("enabledCandidateCount", enabledCandidateCount);
            data.setTag("representative", writeStack(representative));
            if (virtualFluid != null) {
                data.setTag("virtualFluid", virtualFluid.write());
                data.setLong("virtualFluidAmount", virtualFluidAmount);
            }
            return data;
        }

        private static Ingredient read(NBTTagCompound data)
              throws QIOProcessingDataException {
            if (!data.hasKey("slot", NBT.TAG_INT) ||
                !data.hasKey("candidateCount", NBT.TAG_INT) ||
                !data.hasKey("enabledCandidateCount", NBT.TAG_INT) ||
                !data.hasKey("representative", NBT.TAG_COMPOUND)) {
                throw new QIOProcessingDataException("Incomplete workbench ingredient summary");
            }
            boolean hasVirtualFluid = data.hasKey("virtualFluid", NBT.TAG_COMPOUND);
            if (hasVirtualFluid != data.hasKey("virtualFluidAmount", NBT.TAG_LONG)) {
                throw new QIOProcessingDataException(
                      "Incomplete workbench virtual fluid summary");
            }
            return new Ingredient(data.getInteger("slot"), data.getInteger("candidateCount"),
                  data.getInteger("enabledCandidateCount"), readStack(
                        data.getCompoundTag("representative")), hasVirtualFluid ?
                        PortableResourceDescriptor.read(data.getCompoundTag("virtualFluid")) :
                        null, hasVirtualFluid ? data.getLong("virtualFluidAmount") : 0);
        }
    }

    public static final class Candidate {

        private final String candidateId;
        private final ItemStack displayStack;
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean virtualFluid;
        private final boolean enabled;
        private final int order;

        public Candidate(String candidateId, ItemStack displayStack,
              PortableResourceDescriptor resource, long amount, boolean virtualFluid,
              boolean enabled, int order) {
            this.candidateId = checkedHash(candidateId, false, "candidateId");
            if (displayStack.isEmpty() || amount <= 0 || order < 0) {
                throw new IllegalArgumentException("Invalid workbench candidate summary");
            }
            this.displayStack = QIORecipeStackUtils.copyForRecipeSelection(displayStack);
            this.resource = Objects.requireNonNull(resource, "resource");
            this.amount = amount;
            this.virtualFluid = virtualFluid;
            this.enabled = enabled;
            this.order = order;
        }

        @Nonnull public String getCandidateId() { return candidateId; }
        @Nonnull public ItemStack getDisplayStack() {
            return QIORecipeStackUtils.copyForRecipeSelection(displayStack);
        }
        @Nonnull public PortableResourceDescriptor getResource() { return resource; }
        public long getAmount() { return amount; }
        public boolean isVirtualFluid() { return virtualFluid; }
        public boolean isEnabled() { return enabled; }
        public int getOrder() { return order; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("candidateId", candidateId);
            data.setTag("displayStack", writeStack(displayStack));
            data.setTag("resource", resource.write());
            data.setLong("amount", amount);
            data.setBoolean("virtualFluid", virtualFluid);
            data.setBoolean("enabled", enabled);
            data.setInteger("order", order);
            return data;
        }

        private static Candidate read(NBTTagCompound data)
              throws QIOProcessingDataException {
            if (!data.hasKey("candidateId", NBT.TAG_STRING) ||
                !data.hasKey("displayStack", NBT.TAG_COMPOUND) ||
                !data.hasKey("resource", NBT.TAG_COMPOUND) ||
                !data.hasKey("amount", NBT.TAG_LONG) ||
                !data.hasKey("virtualFluid", NBT.TAG_BYTE) ||
                !data.hasKey("enabled", NBT.TAG_BYTE) ||
                !data.hasKey("order", NBT.TAG_INT)) {
                throw new QIOProcessingDataException("Incomplete workbench candidate summary");
            }
            return new Candidate(data.getString("candidateId"), readStack(
                  data.getCompoundTag("displayStack")), PortableResourceDescriptor.read(
                  data.getCompoundTag("resource")), data.getLong("amount"),
                  data.getBoolean("virtualFluid"), data.getBoolean("enabled"),
                  data.getInteger("order"));
        }
    }

    private static <T> List<T> immutable(List<T> values, int maximum, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid workbench " + name + " list");
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String checkedText(String value, int maximum, boolean empty,
          String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.length() > maximum || !empty && checked.isEmpty()) {
            throw new IllegalArgumentException("Invalid workbench " + name);
        }
        return checked;
    }

    private static String checkedHash(String value, boolean empty, String name) {
        String checked = checkedText(value, HASH_LENGTH, empty, name).toLowerCase(
              java.util.Locale.ROOT);
        if (!checked.isEmpty() && (checked.length() != HASH_LENGTH ||
            !checked.chars().allMatch(character -> character >= '0' && character <= '9' ||
                  character >= 'a' && character <= 'f'))) {
            throw new IllegalArgumentException("Invalid workbench " + name);
        }
        return checked;
    }

    private static NBTTagCompound writeStack(ItemStack stack) {
        return QIORecipeStackUtils.writeForRecipeSelection(stack);
    }

    private static ItemStack readStack(NBTTagCompound data) {
        return QIORecipeStackUtils.readForRecipeSelection(data);
    }

    private static <T> NBTTagList writeList(List<T> values,
          java.util.function.Function<T, NBTTagCompound> writer) {
        NBTTagList list = new NBTTagList();
        values.forEach(value -> list.appendTag(writer.apply(value)));
        return list;
    }

    private static NBTTagList bounded(NBTTagCompound data, String key)
          throws QIOProcessingDataException {
        return bounded(data, key, MAX_PAGE_SIZE);
    }

    private static NBTTagList bounded(NBTTagCompound data, String key, int maximum)
          throws QIOProcessingDataException {
        if (!data.hasKey(key, NBT.TAG_LIST)) {
            throw new QIOProcessingDataException("Missing workbench snapshot list " + key);
        }
        NBTTagList list = (NBTTagList) data.getTag(key);
        if (!list.isEmpty() && list.getTagType() != NBT.TAG_COMPOUND) {
            throw new QIOProcessingDataException("Workbench snapshot list " + key +
                  " has the wrong element type");
        }
        if (list.tagCount() > maximum) {
            throw new QIOProcessingDataException("Workbench snapshot page is too large");
        }
        return list;
    }
}
