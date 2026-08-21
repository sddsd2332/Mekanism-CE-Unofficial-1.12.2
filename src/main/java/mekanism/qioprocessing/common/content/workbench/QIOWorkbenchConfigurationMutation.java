package mekanism.qioprocessing.common.content.workbench;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One compare-and-set mutation against a frequency workbench configuration. */
/**
 * QIO 处理模块中的 QIOWorkbenchConfigurationMutation 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchConfigurationMutation {

    private static final int SCHEMA_VERSION = 5;
    private static final int MAX_RECIPE_ID_LENGTH = 256;
    private static final int HASH_LENGTH = 64;
    public static final int MAX_BATCH_TARGETS = 128;

    public enum Action {
        TOGGLE_RECIPE,
        MOVE_RECIPE,
        MOVE_RECIPE_TO_TOP,
        MOVE_RECIPE_TO_BOTTOM,
        TOGGLE_CANDIDATE,
        MOVE_CANDIDATE,
        MOVE_CANDIDATE_TO_TOP,
        MOVE_CANDIDATE_TO_BOTTOM,
        MOVE_CANDIDATE_TO_INDEX,
        SYNC_EQUIVALENT_CANDIDATES,
        RESET_PRODUCT,
        RESET_RECIPE,
        RESET_ALL,
        ENCODE_PATTERN,
        ENCODE_TARGETS,
        DELETE_PRODUCT,
        DELETE_PATTERN,
        BATCH_DELETE_PRODUCTS,
        BATCH_SET_RECIPES_ENABLED,
        BATCH_MOVE_RECIPES,
        BATCH_DELETE_PATTERNS
    }

    private final Action action;
    private final String productKey;
    private final String recipeId;
    private final String recipeSignature;
    private final int ingredientSlot;
    private final String candidateId;
    private final int direction;
    private final int targetIndex;
    private final List<ItemStack> grid;
    private final List<ItemStack> targets;
    private final List<String> productKeys;
    private final List<RecipeTarget> recipeTargets;
    private final boolean enabled;

    private QIOWorkbenchConfigurationMutation(Action action, String productKey,
          String recipeId, String recipeSignature, int ingredientSlot,
          String candidateId, int direction) {
        this(action, productKey, recipeId, recipeSignature, ingredientSlot, candidateId,
              direction, -1, Collections.emptyList(), Collections.emptyList(),
              Collections.emptyList(), Collections.emptyList(), false);
    }

    private QIOWorkbenchConfigurationMutation(Action action, String productKey,
          String recipeId, String recipeSignature, int ingredientSlot,
          String candidateId, int direction, List<ItemStack> grid) {
        this(action, productKey, recipeId, recipeSignature, ingredientSlot, candidateId,
              direction, -1, grid, Collections.emptyList(), Collections.emptyList(),
              Collections.emptyList(), false);
    }

    private QIOWorkbenchConfigurationMutation(Action action, String productKey,
          String recipeId, String recipeSignature, int ingredientSlot,
          String candidateId, int direction, int targetIndex, List<ItemStack> grid,
          List<ItemStack> targets, List<String> productKeys,
          List<RecipeTarget> recipeTargets, boolean enabled) {
        this.action = Objects.requireNonNull(action, "action");
        this.productKey = checkedHash(productKey, true, "productKey");
        this.recipeId = checkedText(recipeId, MAX_RECIPE_ID_LENGTH, true, "recipeId");
        this.recipeSignature = checkedHash(recipeSignature, true, "recipeSignature");
        this.ingredientSlot = ingredientSlot;
        this.candidateId = checkedHash(candidateId, true, "candidateId");
        this.direction = Integer.signum(direction);
        this.targetIndex = targetIndex;
        this.grid = checkedGrid(grid);
        this.targets = checkedTargets(targets);
        this.productKeys = checkedProductKeys(productKeys);
        this.recipeTargets = checkedRecipeTargets(recipeTargets);
        this.enabled = enabled;
        if (!shape()) {
            throw new IllegalArgumentException("Invalid QIO workbench mutation shape");
        }
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation recipe(Action action,
          String productKey, String recipeId, String recipeSignature) {
        return new QIOWorkbenchConfigurationMutation(action, productKey, recipeId,
              recipeSignature, -1, "", action == Action.MOVE_RECIPE ? 1 : 0);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation moveRecipe(String productKey,
          String recipeId, String recipeSignature, int direction) {
        return new QIOWorkbenchConfigurationMutation(Action.MOVE_RECIPE, productKey,
              recipeId, recipeSignature, -1, "", direction);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation candidate(Action action,
          String productKey, String recipeId, String recipeSignature, int slot,
          String candidateId) {
        return new QIOWorkbenchConfigurationMutation(action, productKey, recipeId,
              recipeSignature, slot, candidateId,
              action == Action.MOVE_CANDIDATE ? 1 : 0);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation moveCandidate(String productKey,
          String recipeId, String recipeSignature, int slot, String candidateId,
          int direction) {
        return new QIOWorkbenchConfigurationMutation(Action.MOVE_CANDIDATE, productKey,
              recipeId, recipeSignature, slot, candidateId, direction);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation moveCandidateToIndex(String productKey,
          String recipeId, String recipeSignature, int slot, String candidateId,
          int targetIndex) {
        return new QIOWorkbenchConfigurationMutation(Action.MOVE_CANDIDATE_TO_INDEX,
              productKey, recipeId, recipeSignature, slot, candidateId, 0, targetIndex,
              Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
              Collections.emptyList(), false);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation syncEquivalentCandidates(
          String productKey, String recipeId, String recipeSignature, int slot) {
        return new QIOWorkbenchConfigurationMutation(Action.SYNC_EQUIVALENT_CANDIDATES,
              productKey, recipeId, recipeSignature, slot, "", 0, -1,
              Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
              Collections.emptyList(), false);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation resetProduct(String productKey) {
        return new QIOWorkbenchConfigurationMutation(Action.RESET_PRODUCT, productKey, "",
              "", -1, "", 0);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation resetAll() {
        return new QIOWorkbenchConfigurationMutation(Action.RESET_ALL, "", "", "", -1,
              "", 0);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation encodePattern(List<ItemStack> grid) {
        return new QIOWorkbenchConfigurationMutation(Action.ENCODE_PATTERN, "", "", "", -1,
              "", 0, grid);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation encodeTargets(List<ItemStack> targets) {
        return new QIOWorkbenchConfigurationMutation(Action.ENCODE_TARGETS, "", "", "", -1,
              "", 0, -1, Collections.emptyList(), targets, Collections.emptyList(),
              Collections.emptyList(), false);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation deletePattern(String recipeId,
          String recipeSignature) {
        return new QIOWorkbenchConfigurationMutation(Action.DELETE_PATTERN, "", recipeId,
              recipeSignature, -1, "", 0);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation deleteProduct(String productKey) {
        return new QIOWorkbenchConfigurationMutation(Action.DELETE_PRODUCT, productKey, "",
              "", -1, "", 0);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation batchDeleteProducts(
          List<String> productKeys) {
        return new QIOWorkbenchConfigurationMutation(Action.BATCH_DELETE_PRODUCTS, "", "",
              "", -1, "", 0, -1, Collections.emptyList(), Collections.emptyList(),
              productKeys, Collections.emptyList(), false);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation batchSetRecipesEnabled(
          String productKey, List<RecipeTarget> recipes, boolean enabled) {
        return new QIOWorkbenchConfigurationMutation(Action.BATCH_SET_RECIPES_ENABLED,
              productKey, "", "", -1, "", 0, -1, Collections.emptyList(),
              Collections.emptyList(), Collections.emptyList(), recipes, enabled);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation batchMoveRecipes(String productKey,
          List<RecipeTarget> recipes, int direction, boolean edge) {
        int targetIndex = edge ? direction < 0 ? 0 : Integer.MAX_VALUE : -1;
        return new QIOWorkbenchConfigurationMutation(Action.BATCH_MOVE_RECIPES,
              productKey, "", "", -1, "", direction, targetIndex,
              Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
              recipes, false);
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation batchDeletePatterns(String productKey,
          List<RecipeTarget> recipes) {
        return new QIOWorkbenchConfigurationMutation(Action.BATCH_DELETE_PATTERNS,
              productKey, "", "", -1, "", 0, -1, Collections.emptyList(),
              Collections.emptyList(), Collections.emptyList(), recipes, false);
    }

    @Nonnull public Action getAction() { return action; }
    @Nonnull public String getProductKey() { return productKey; }
    @Nonnull public String getRecipeId() { return recipeId; }
    @Nonnull public String getRecipeSignature() { return recipeSignature; }
    public int getIngredientSlot() { return ingredientSlot; }
    @Nonnull public String getCandidateId() { return candidateId; }
    public int getDirection() { return direction; }
    public int getTargetIndex() { return targetIndex; }
    @Nonnull public List<ItemStack> getGrid() {
        List<ItemStack> copy = new ArrayList<>(grid.size());
        grid.forEach(stack -> copy.add(stack.copy()));
        return Collections.unmodifiableList(copy);
    }
    @Nonnull public List<ItemStack> getTargets() {
        List<ItemStack> copy = new ArrayList<>(targets.size());
        targets.forEach(stack -> copy.add(stack.copy()));
        return Collections.unmodifiableList(copy);
    }
    @Nonnull public List<String> getProductKeys() { return productKeys; }
    @Nonnull public List<RecipeTarget> getRecipeTargets() { return recipeTargets; }
    public boolean isEnabled() { return enabled; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("action", action.name());
        data.setString("productKey", productKey);
        data.setString("recipeId", recipeId);
        data.setString("recipeSignature", recipeSignature);
        data.setInteger("ingredientSlot", ingredientSlot);
        data.setString("candidateId", candidateId);
        data.setInteger("direction", direction);
        data.setInteger("targetIndex", targetIndex);
        NBTTagList storedGrid = new NBTTagList();
        grid.forEach(stack -> storedGrid.appendTag(stack.isEmpty() ? new NBTTagCompound() :
              stack.writeToNBT(new NBTTagCompound())));
        data.setTag("grid", storedGrid);
        NBTTagList storedTargets = new NBTTagList();
        targets.forEach(stack -> storedTargets.appendTag(
              stack.writeToNBT(new NBTTagCompound())));
        data.setTag("targets", storedTargets);
        NBTTagList storedProductKeys = new NBTTagList();
        productKeys.forEach(key -> storedProductKeys.appendTag(new NBTTagString(key)));
        data.setTag("productKeys", storedProductKeys);
        NBTTagList storedRecipeTargets = new NBTTagList();
        recipeTargets.forEach(target -> storedRecipeTargets.appendTag(target.write()));
        data.setTag("recipeTargets", storedRecipeTargets);
        data.setBoolean("enabled", enabled);
        return data;
    }

    @Nonnull
    public static QIOWorkbenchConfigurationMutation read(NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("action", NBT.TAG_STRING) ||
                !data.hasKey("productKey", NBT.TAG_STRING) ||
                !data.hasKey("recipeId", NBT.TAG_STRING) ||
                !data.hasKey("recipeSignature", NBT.TAG_STRING) ||
                !data.hasKey("ingredientSlot", NBT.TAG_INT) ||
                !data.hasKey("candidateId", NBT.TAG_STRING) ||
                !data.hasKey("direction", NBT.TAG_INT) ||
                !data.hasKey("targetIndex", NBT.TAG_INT) ||
                !data.hasKey("grid", NBT.TAG_LIST) ||
                !data.hasKey("targets", NBT.TAG_LIST) ||
                !data.hasKey("productKeys", NBT.TAG_LIST) ||
                !data.hasKey("recipeTargets", NBT.TAG_LIST) ||
                !data.hasKey("enabled", NBT.TAG_BYTE)) {
                throw new QIOProcessingDataException("Incomplete workbench mutation");
            }
            return new QIOWorkbenchConfigurationMutation(Action.valueOf(
                  data.getString("action")), data.getString("productKey"),
                  data.getString("recipeId"), data.getString("recipeSignature"),
                  data.getInteger("ingredientSlot"), data.getString("candidateId"),
                  data.getInteger("direction"), data.getInteger("targetIndex"),
                  readGrid(data.getTagList("grid",
                        NBT.TAG_COMPOUND)), readTargets(data.getTagList("targets",
                        NBT.TAG_COMPOUND)), readProductKeys(data.getTagList("productKeys",
                        NBT.TAG_STRING)), readRecipeTargets(data.getTagList("recipeTargets",
                        NBT.TAG_COMPOUND)), data.getBoolean("enabled"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid workbench mutation", e);
        }
    }

    private boolean shape() {
        boolean recipe = !productKey.isEmpty() && !recipeId.isEmpty() &&
              !recipeSignature.isEmpty();
        boolean deletion = productKey.isEmpty() && !recipeId.isEmpty() &&
              !recipeSignature.isEmpty();
        boolean candidate = recipe && ingredientSlot >= 0 && ingredientSlot < 9 &&
              !candidateId.isEmpty();
        boolean noBatch = productKeys.isEmpty() && recipeTargets.isEmpty() && !enabled;
        boolean emptyBase = recipeId.isEmpty() && recipeSignature.isEmpty() &&
              ingredientSlot == -1 && candidateId.isEmpty() && grid.isEmpty() &&
              targets.isEmpty();
        return switch (action) {
            case TOGGLE_RECIPE, MOVE_RECIPE_TO_TOP, MOVE_RECIPE_TO_BOTTOM,
                 RESET_RECIPE -> recipe && ingredientSlot == -1 && candidateId.isEmpty() &&
                  direction == 0 && targetIndex == -1 && grid.isEmpty() && targets.isEmpty() &&
                  noBatch;
            case MOVE_RECIPE -> recipe && ingredientSlot == -1 && candidateId.isEmpty() &&
                  direction != 0 && targetIndex == -1 && grid.isEmpty() && targets.isEmpty() &&
                  noBatch;
            case TOGGLE_CANDIDATE, MOVE_CANDIDATE_TO_TOP,
                 MOVE_CANDIDATE_TO_BOTTOM -> candidate && direction == 0 && grid.isEmpty() &&
                  targetIndex == -1 && targets.isEmpty() && noBatch;
            case MOVE_CANDIDATE -> candidate && direction != 0 && grid.isEmpty() &&
                  targetIndex == -1 && targets.isEmpty() && noBatch;
            case MOVE_CANDIDATE_TO_INDEX -> candidate && direction == 0 &&
                  targetIndex >= 0 && targetIndex < 64 && grid.isEmpty() && targets.isEmpty() &&
                  noBatch;
            case SYNC_EQUIVALENT_CANDIDATES -> recipe && ingredientSlot >= 0 &&
                  ingredientSlot < 9 && candidateId.isEmpty() && direction == 0 &&
                  targetIndex == -1 && grid.isEmpty() && targets.isEmpty() && noBatch;
            case RESET_PRODUCT, DELETE_PRODUCT -> !productKey.isEmpty() && recipeId.isEmpty() &&
                  recipeSignature.isEmpty() && ingredientSlot == -1 &&
                  candidateId.isEmpty() && direction == 0 && targetIndex == -1 &&
                  grid.isEmpty() && targets.isEmpty() && noBatch;
            case RESET_ALL -> productKey.isEmpty() && recipeId.isEmpty() &&
                  recipeSignature.isEmpty() && ingredientSlot == -1 &&
                  candidateId.isEmpty() && direction == 0 && targetIndex == -1 &&
                  grid.isEmpty() && targets.isEmpty() && noBatch;
            case ENCODE_PATTERN -> productKey.isEmpty() && recipeId.isEmpty() &&
                  recipeSignature.isEmpty() && ingredientSlot == -1 &&
                  candidateId.isEmpty() && direction == 0 && targetIndex == -1 &&
                  grid.size() == 9 && targets.isEmpty() && noBatch;
            case ENCODE_TARGETS -> productKey.isEmpty() && recipeId.isEmpty() &&
                  recipeSignature.isEmpty() && ingredientSlot == -1 &&
                  candidateId.isEmpty() && direction == 0 && targetIndex == -1 &&
                  grid.isEmpty() &&
                  !targets.isEmpty() && noBatch;
            case DELETE_PATTERN -> deletion && ingredientSlot == -1 && candidateId.isEmpty() &&
                  direction == 0 && targetIndex == -1 && grid.isEmpty() && targets.isEmpty() &&
                  noBatch;
            case BATCH_DELETE_PRODUCTS -> productKey.isEmpty() && emptyBase &&
                  direction == 0 && targetIndex == -1 && !productKeys.isEmpty() &&
                  recipeTargets.isEmpty() && !enabled;
            case BATCH_SET_RECIPES_ENABLED -> !productKey.isEmpty() && emptyBase &&
                  direction == 0 && targetIndex == -1 && productKeys.isEmpty() &&
                  !recipeTargets.isEmpty();
            case BATCH_MOVE_RECIPES -> !productKey.isEmpty() && emptyBase &&
                  direction != 0 && (targetIndex == -1 || targetIndex == 0 ||
                  targetIndex == Integer.MAX_VALUE) && productKeys.isEmpty() &&
                  !recipeTargets.isEmpty() && !enabled;
            case BATCH_DELETE_PATTERNS -> !productKey.isEmpty() && emptyBase &&
                  direction == 0 && targetIndex == -1 && productKeys.isEmpty() &&
                  !recipeTargets.isEmpty() && !enabled;
        };
    }

    private static List<ItemStack> checkedGrid(List<ItemStack> grid) {
        if (grid == null || grid.isEmpty()) {
            return Collections.emptyList();
        }
        if (grid.size() != 9) {
            throw new IllegalArgumentException("Workbench pattern grid must contain nine slots");
        }
        List<ItemStack> copy = new ArrayList<>(9);
        boolean hasInput = false;
        for (ItemStack stack : grid) {
            ItemStack checked = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (!checked.isEmpty()) {
                checked.setCount(1);
                hasInput = true;
            }
            copy.add(checked);
        }
        if (!hasInput) {
            throw new IllegalArgumentException("Workbench pattern grid has no inputs");
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<ItemStack> readGrid(NBTTagList stored) {
        if (stored.tagCount() == 0) {
            return Collections.emptyList();
        }
        if (stored.tagCount() != 9) {
            throw new IllegalArgumentException("Workbench pattern grid must contain nine slots");
        }
        List<ItemStack> grid = new ArrayList<>(9);
        for (int index = 0; index < stored.tagCount(); index++) {
            NBTTagCompound slot = stored.getCompoundTagAt(index);
            grid.add(slot.isEmpty() ? ItemStack.EMPTY : new ItemStack(slot));
        }
        return grid;
    }

    private static List<ItemStack> checkedTargets(List<ItemStack> targets) {
        if (targets == null || targets.isEmpty()) return Collections.emptyList();
        if (targets.size() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException("Too many workbench batch targets");
        }
        Map<PortableResourceDescriptor, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack stack : targets) {
            if (stack == null || stack.isEmpty()) {
                throw new IllegalArgumentException("Workbench batch target is empty");
            }
            ItemStack copy = stack.copy();
            copy.setCount(1);
            unique.putIfAbsent(PortableResourceDescriptor.item(copy), copy);
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Workbench batch target list is empty");
        }
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    private static List<ItemStack> readTargets(NBTTagList stored) {
        if (stored.tagCount() == 0) return Collections.emptyList();
        if (stored.tagCount() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException("Too many workbench batch targets");
        }
        List<ItemStack> targets = new ArrayList<>(stored.tagCount());
        for (int index = 0; index < stored.tagCount(); index++) {
            targets.add(new ItemStack(stored.getCompoundTagAt(index)));
        }
        return targets;
    }

    private static List<String> checkedProductKeys(List<String> productKeys) {
        if (productKeys == null || productKeys.isEmpty()) return Collections.emptyList();
        if (productKeys.size() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException("Too many workbench product targets");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String productKey : productKeys) {
            unique.add(checkedHash(productKey, false, "productKey"));
        }
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static List<String> readProductKeys(NBTTagList stored) {
        if (stored.tagCount() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException("Too many workbench product targets");
        }
        List<String> productKeys = new ArrayList<>(stored.tagCount());
        for (int index = 0; index < stored.tagCount(); index++) {
            productKeys.add(stored.getStringTagAt(index));
        }
        return productKeys;
    }

    private static List<RecipeTarget> checkedRecipeTargets(List<RecipeTarget> targets) {
        if (targets == null || targets.isEmpty()) return Collections.emptyList();
        if (targets.size() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException("Too many workbench recipe targets");
        }
        Map<String, RecipeTarget> unique = new LinkedHashMap<>();
        for (RecipeTarget target : targets) {
            RecipeTarget checked = Objects.requireNonNull(target, "recipeTarget");
            RecipeTarget previous = unique.putIfAbsent(checked.recipeId, checked);
            if (previous != null && !previous.recipeSignature.equals(
                  checked.recipeSignature)) {
                throw new IllegalArgumentException(
                      "Conflicting workbench recipe target signatures");
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    private static List<RecipeTarget> readRecipeTargets(NBTTagList stored) {
        if (stored.tagCount() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException("Too many workbench recipe targets");
        }
        List<RecipeTarget> targets = new ArrayList<>(stored.tagCount());
        for (int index = 0; index < stored.tagCount(); index++) {
            targets.add(RecipeTarget.read(stored.getCompoundTagAt(index)));
        }
        return targets;
    }

    public static final class RecipeTarget {

        private final String recipeId;
        private final String recipeSignature;

        public RecipeTarget(String recipeId, String recipeSignature) {
            this.recipeId = checkedText(recipeId, MAX_RECIPE_ID_LENGTH, false,
                  "recipeId");
            this.recipeSignature = checkedHash(recipeSignature, false,
                  "recipeSignature");
        }

        @Nonnull public String getRecipeId() { return recipeId; }
        @Nonnull public String getRecipeSignature() { return recipeSignature; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("recipeId", recipeId);
            data.setString("recipeSignature", recipeSignature);
            return data;
        }

        private static RecipeTarget read(NBTTagCompound data) {
            if (!data.hasKey("recipeId", NBT.TAG_STRING) ||
                !data.hasKey("recipeSignature", NBT.TAG_STRING)) {
                throw new IllegalArgumentException("Incomplete workbench recipe target");
            }
            return new RecipeTarget(data.getString("recipeId"),
                  data.getString("recipeSignature"));
        }
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
}
