package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeIngredient;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Declaration-only workbench directory. Capture is performed in the first frozen-registry
 * event; index construction uses only detached data. No World, recipe, Ingredient, ItemStack,
 * item delegate, or capability provider is retained in a published declaration.
 * A declaration is never proof that a concrete nine-slot craft is valid.
 */
public final class QIOWorkbenchDeclarations {

    public enum Layout {SHAPED, SHAPELESS, ORDERED_UNKNOWN}

    private static final Set<String> ADAPTED_RECIPES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "net.minecraft.item.crafting.ShapedRecipes",
            "net.minecraft.item.crafting.ShapelessRecipes",
            "net.minecraftforge.oredict.ShapedOreRecipe",
            "net.minecraftforge.oredict.ShapelessOreRecipe",
            "mekanism.common.recipe.ShapedMekanismRecipe",
            "mekanism.common.recipe.ShapelessMekanismRecipe",
            "blusunrize.immersiveengineering.common.crafting.RecipeShapedIngredient",
            "blusunrize.immersiveengineering.common.crafting.RecipeShapelessIngredient",
            "com.blakebr0.cucumber.crafting.ShapedOreOutputRecipe",
            "ic2.core.recipe.AdvRecipe",
            "ic2.core.recipe.AdvShapelessRecipe")));

    private final Map<ResourceLocation, Recipe> recipes;
    private final Map<PortableResourceDescriptor, List<Recipe>> byOutput;
    private final Map<PortableResourceDescriptor, List<Recipe>> bySelection;
    private final Map<ResourceLocation, String> diagnostics;
    private final Map<PortableResourceDescriptor, List<String>> rejectedOutputs;
    private final String generation;

    private QIOWorkbenchDeclarations(Capture capture) {
        Map<ResourceLocation, Recipe> ordered = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        ordered.putAll(capture.recipes);
        recipes = Collections.unmodifiableMap(ordered);
        Map<PortableResourceDescriptor, List<Recipe>> outputs = new TreeMap<>();
        StringBuilder canonical = new StringBuilder("qio-workbench-declarations-v1\n");
        for (Recipe recipe : ordered.values()) {
            canonical.append(recipe.id).append('|').append(recipe.signature).append('\n');
            if (recipe.automatic) outputs.computeIfAbsent(recipe.output.resource,
                    ignored -> new ArrayList<>()).add(recipe);
        }
        Map<PortableResourceDescriptor, List<Recipe>> frozen = new LinkedHashMap<>();
        outputs.forEach((resource, values) -> frozen.put(resource,
                Collections.unmodifiableList(values)));
        byOutput = Collections.unmodifiableMap(frozen);
        Map<PortableResourceDescriptor, List<Recipe>> selections = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, List<Recipe>> entry : frozen.entrySet()) {
            PortableResourceDescriptor key = entry.getKey().withoutCapabilities();
            if (selections.containsKey(key)) selections.put(key, Collections.emptyList());
            else selections.put(key, entry.getValue());
        }
        bySelection = Collections.unmodifiableMap(selections);
        diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(capture.diagnostics));
        Map<PortableResourceDescriptor, List<String>> rejected = new LinkedHashMap<>();
        capture.rejectedOutputs.forEach((output, reasons) -> rejected.put(output,
                Collections.unmodifiableList(new ArrayList<>(reasons))));
        rejectedOutputs = Collections.unmodifiableMap(rejected);
        generation = QIOHashing.sha256(canonical);
    }

    public String getGeneration() {
        return generation;
    }

    public Collection<Recipe> getRecipes() {
        return recipes.values();
    }

    @Nullable
    public Recipe get(ResourceLocation id) {
        return recipes.get(id);
    }

    public List<Recipe> producing(PortableResourceDescriptor output) {
        return byOutput.getOrDefault(output, Collections.emptyList());
    }

    /**
     * A ghost output is a selection. A neutral fallback must identify exactly one declared type.
     */
    public List<Recipe> selecting(PortableResourceDescriptor output) {
        List<Recipe> exact = producing(output);
        if (!exact.isEmpty()) return exact;
        return bySelection.getOrDefault(output.withoutCapabilities(), Collections.emptyList());
    }

    public Map<ResourceLocation, String> getDiagnostics() {
        return diagnostics;
    }

    public List<String> rejectedSelecting(PortableResourceDescriptor output) {
        return rejectedOutputs.getOrDefault(output.withoutCapabilities(), Collections.emptyList());
    }

    /**
     * Executes registry/ingredient/capability access only on the caller's initialization thread.
     */
    public static Capture capture(Collection<? extends IRecipe> source) {
        Map<ResourceLocation, Recipe> recipes = new LinkedHashMap<>();
        Map<ResourceLocation, String> diagnostics = new LinkedHashMap<>();
        Map<PortableResourceDescriptor, List<String>> rejectedOutputs = new LinkedHashMap<>();
        long retainedBytes = 0;
        int scannedRecipes = 0;
        for (IRecipe recipe : source) {
            scannedRecipes++;
            if (recipe == null || recipe.getRegistryName() == null) continue;
            ResourceLocation id = recipe.getRegistryName();
            Stack output = null;
            try {
                if (ADAPTED_RECIPES.contains(recipe.getClass().getName()))
                    output = Stack.capture(recipe.getRecipeOutput());
                Recipe frozen = captureRecipe(recipe, output);
                retainedBytes += frozen.retainedBytes;
                recipes.put(id, frozen);
                if (!frozen.automatic) diagnostics.put(id, frozen.reason);
            } catch (RuntimeException | LinkageError error) {
                diagnostics.put(id, error instanceof QIOWorkbenchDeclarationAdapters.UnsupportedDeclarationException ?
                        error.getMessage() : error instanceof QIOWorkbenchMemory.LimitException ? error.getMessage() :
                        "DECLARATION_FAILED: " + error.getClass().getSimpleName());
            }
            if (output != null && !output.isEmpty() && diagnostics.containsKey(id)) {
                rejectedOutputs.computeIfAbsent(output.getResource().withoutCapabilities(), ignored -> new ArrayList<>())
                        .add(id + ": " + diagnostics.get(id));
            }
            // The directory is the complete process-wide recipe declaration index. Its size is
            // bounded by the per-stack/result/task limits below and by the JVM heap, so do not
            // reject valid installations solely because their complete recipe set exceeds an
            // arbitrary 256 MiB aggregate estimate. Publishing a truncated directory would make
            // automatic discovery silently omit recipes and leave existing configurations unable
            // to resolve their declarations.
        }
        return new Capture(recipes, diagnostics, rejectedOutputs, retainedBytes, scannedRecipes);
    }

    public static QIOWorkbenchDeclarations index(Capture capture) {
        return new QIOWorkbenchDeclarations(Objects.requireNonNull(capture, "capture"));
    }

    public static QIOWorkbenchDeclarations empty() {
        return index(new Capture(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0));
    }

    /**
     * Projects the immutable recipe scanner result into the legacy declaration model.
     */
    public static QIOWorkbenchDeclarations fromRecipeCatalog(RecipeCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        Map<ResourceLocation, Recipe> recipes = new LinkedHashMap<>();
        Map<ResourceLocation, String> diagnostics = new LinkedHashMap<>();
        for (RecipeDeclaration declaration : catalog.recipes()) {
            ResourceLocation id = new ResourceLocation(declaration.id());
            List<List<Choice>> slots = new ArrayList<>(9);
            for (int index = 0; index < 9; index++) slots.add(Collections.singletonList(Choice.EMPTY));
            List<RecipeIngredient> inputs = declaration.inputs();
            if (declaration.layout() == RecipeDeclaration.Layout.SHAPED) {
                for (int row = 0; row < declaration.height(); row++) {
                    for (int column = 0; column < declaration.width(); column++) {
                        int source = row * declaration.width() + column;
                        slots.set(row * 3 + column, choices(inputs.get(source)));
                    }
                }
            } else {
                for (int index = 0; index < inputs.size() && index < 9; index++) {
                    slots.set(index, choices(inputs.get(index)));
                }
            }
            Stack output = Stack.fromRecipeKey(declaration.output(),
                    declaration.outputCount());
            Layout layout = declaration.layout() == RecipeDeclaration.Layout.SHAPED ? Layout.SHAPED :
                    declaration.layout() == RecipeDeclaration.Layout.SHAPELESS ? Layout.SHAPELESS :
                            Layout.ORDERED_UNKNOWN;
            boolean automatic = declaration.hasDeclaredCandidates();
            String reason = automatic ? "" : "UNDECLARED_CANDIDATES";
            if (!automatic) diagnostics.put(id, reason);
            recipes.put(id, new Recipe(id, declaration.sourceType(), layout, declaration.width(),
                    declaration.height(), output, slots, declaration.signature(), automatic, reason,
                    1024 + output.getRetainedBytes()));
        }
        Capture capture = new Capture(recipes, diagnostics, Collections.emptyMap(),
                catalog.estimatedBytes(), catalog.scanned());
        return index(capture);
    }

    private static List<Choice> choices(RecipeIngredient ingredient) {
        if (ingredient == null || ingredient.isEmptySlot()) return Collections.singletonList(Choice.EMPTY);
        List<Choice> result = new ArrayList<>();
        for (RecipeItemKey key : ingredient.candidates()) {
            Stack stack = Stack.fromRecipeKey(key, 1);
            result.add(new Choice(stack, stack.getResource(), 1, false));
        }
        return result.isEmpty() ? Collections.singletonList(Choice.EMPTY) :
                Collections.unmodifiableList(result);
    }

    private static Recipe captureRecipe(IRecipe recipe, @Nullable Stack declaredOutput) {
        String type = recipe.getClass().getName();
        // Unknown implementations are diagnosed before invoking arbitrary declaration callbacks.
        if (!ADAPTED_RECIPES.contains(type)) {
            throw QIOWorkbenchDeclarationAdapters.unsupported("UNSUPPORTED_RECIPE_IMPLEMENTATION: " + type);
        }
        if (recipe.isDynamic()) throw QIOWorkbenchDeclarationAdapters.unsupported("DYNAMIC_RECIPE: " + type);
        boolean automatic = true;
        String reason = "";
        Stack output = Objects.requireNonNull(declaredOutput, "declaredOutput");
        if (output.isEmpty()) throw new IllegalArgumentException("Empty declared output");
        List<Ingredient> source = recipe.getIngredients();
        if (source == null || source.isEmpty() || source.size() > 9) {
            throw new IllegalArgumentException("Invalid declaration grid");
        }
        int width = 0;
        int height = 0;
        Layout layout = Layout.ORDERED_UNKNOWN;
        List<Ingredient> slots = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        if (recipe instanceof IShapedRecipe shaped) {
            width = shaped.getRecipeWidth();
            height = shaped.getRecipeHeight();
            if (width < 1 || height < 1 || width > 3 || height > 3) {
                throw new IllegalArgumentException("Invalid recipe dimensions");
            }
            layout = Layout.SHAPED;
            // IC2's AdvRecipe returns an already positioned physical nine-slot ingredient list,
            // even when inputWidth/inputHeight is smaller. Do not repack its mask a second time.
            if (type.equals("ic2.core.recipe.AdvRecipe") && source.size() == 9) {
                slots = new ArrayList<>(source);
            } else {
                if (source.size() != width * height) throw new IllegalArgumentException("Invalid shaped grid");
                for (int row = 0; row < height; row++)
                    for (int column = 0; column < width; column++) {
                        slots.set(row * 3 + column, source.get(row * width + column));
                    }
            }
        } else {
            if (type.equals("net.minecraft.item.crafting.ShapelessRecipes") ||
                    type.equals("net.minecraftforge.oredict.ShapelessOreRecipe") ||
                    type.equals("mekanism.common.recipe.ShapelessMekanismRecipe") ||
                    type.equals("blusunrize.immersiveengineering.common.crafting.RecipeShapelessIngredient") ||
                    type.equals("ic2.core.recipe.AdvShapelessRecipe")) layout = Layout.SHAPELESS;
            for (int slot = 0; slot < source.size(); slot++) slots.set(slot, source.get(slot));
        }
        List<List<Choice>> choices = new ArrayList<>(9);
        long retainedBytes = 1024 + output.getRetainedBytes();
        StringBuilder canonical = new StringBuilder(type).append('|').append(layout)
                .append('|').append(width).append('|').append(height).append('|').append(output.key);
        for (Ingredient ingredient : slots) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) {
                choices.add(Collections.singletonList(Choice.EMPTY));
                canonical.append("|empty");
                continue;
            }
            // Inspect nested implementation types before getMatchingStacks can trigger hidden
            // global scans. Remaining wildcards expand only their explicitly referenced item.
            List<ItemStack> matching = QIOWorkbenchDeclarationAdapters.matchingStacks(ingredient);
            Map<String, Choice> unique = new TreeMap<>();
            for (ItemStack stack : matching) {
                if (stack == null || stack.isEmpty()) continue;
                if (stack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                    automatic = false;
                    reason = "UNRESOLVED_WILDCARD";
                    continue;
                }
                Stack frozen = Stack.capture(stack, 1);
                Choice item = new Choice(frozen, frozen.resource, 1, false);
                if (unique.putIfAbsent(item.id, item) == null) retainedBytes += item.retainedBytes;
                FluidStack fluid = QIOFluidContainerSnapshot.capture(stack);
                if (fluid != null) {
                    Choice direct = new Choice(frozen, PortableResourceDescriptor.fluid(fluid), fluid.amount, true);
                    if (unique.putIfAbsent(direct.id, direct) == null) retainedBytes += direct.retainedBytes;
                }
                if (retainedBytes > QIOWorkbenchMemory.RESULT_LIMIT) {
                    throw new QIOWorkbenchMemory.LimitException("WORKBENCH_DECLARATION_MEMORY_LIMIT: " + recipe.getRegistryName());
                }
            }
            if (unique.isEmpty()) throw new IllegalArgumentException("No concrete declared candidates");
            canonical.append('|').append(ingredient.getClass().getName());
            for (Choice choice : unique.values()) canonical.append('|').append(choice.id);
            choices.add(Collections.unmodifiableList(new ArrayList<>(unique.values())));
        }
        return new Recipe(recipe.getRegistryName(), type, layout, width, height, output,
                choices, QIOHashing.sha256(canonical), automatic, reason, retainedBytes);
    }

    /**
     * Serialised stack data with no live capability dispatcher. Thaw only at the Forge boundary.
     */
    public static final class Stack {
        public static final Stack EMPTY = new Stack(new NBTTagCompound(), null, 0);
        private final NBTTagCompound data;
        @Nullable
        private final PortableResourceDescriptor resource;
        private final int amount;
        private final String key;
        private final long retainedBytes;

        private Stack(NBTTagCompound data, @Nullable PortableResourceDescriptor resource, int amount) {
            long dataBytes = QIOWorkbenchMemory.nbt(data);
            if (dataBytes > QIOWorkbenchMemory.STACK_LIMIT) {
                throw new QIOWorkbenchMemory.LimitException("WORKBENCH_STACK_MEMORY_LIMIT");
            }
            this.data = data.copy();
            this.resource = resource;
            this.amount = amount;
            key = resource == null ? "empty" : resource + "@" + amount;
            retainedBytes = 128 + dataBytes + QIOWorkbenchMemory.resource(resource) + QIOWorkbenchMemory.text(key);
            if (retainedBytes > QIOWorkbenchMemory.STACK_LIMIT) {
                throw new QIOWorkbenchMemory.LimitException("WORKBENCH_STACK_MEMORY_LIMIT");
            }
        }

        /**
         * Converts captured keys without creating ItemStack or invoking capabilities.
         */
        public static Stack fromRecipeKey(RecipeItemKey key, int amount) {
            if (key == null) return EMPTY;
            if (amount <= 0) throw new IllegalArgumentException("Non-positive frozen amount");
            NBTTagCompound data = key.displayNBT();
            data.setByte("Count", (byte) 1);
            return new Stack(data, describe(data), amount);
        }

        public static Stack capture(ItemStack stack) {
            return capture(stack, stack == null ? 0 : stack.getCount());
        }

        /**
         * Reconstruct detached item data from a stock/plan identity without any Forge callback.
         */
        public static Stack fromResource(PortableResourceDescriptor resource) {
            if (resource.getKind() != PortableResourceDescriptor.Kind.ITEM || resource.getDescriptor().getPersistedItemId().isEmpty()) {
                throw new IllegalArgumentException("Workbench state candidate must be a persisted item");
            }
            NBTTagCompound data = resource.getDescriptor().getPayload();
            data.setByte("Count", (byte) 1);
            return new Stack(data, resource, 1);
        }

        public static Stack capture(ItemStack stack, int amount) {
            if (stack == null || stack.isEmpty() || amount <= 0) return EMPTY;
            NBTTagCompound data = stack.writeToNBT(new NBTTagCompound());
            data.setByte("Count", (byte) 1);
            if (QIOWorkbenchMemory.nbt(data) > QIOWorkbenchMemory.STACK_LIMIT) {
                throw new QIOWorkbenchMemory.LimitException("WORKBENCH_STACK_MEMORY_LIMIT");
            }
            // Freeze the complete resource identity (including capability NBT) on the Forge
            // thread. Workers only compare its immutable descriptor and never resolve it.
            return new Stack(data, describe(data), amount);
        }

        public static Stack read(NBTTagCompound data) {
            NBTTagCompound item = data.getCompoundTag("item");
            int amount = data.getInteger("amount");
            if (amount == 0) return EMPTY;
            if (amount < 0 || !data.hasKey("resource", 10)) throw new IllegalArgumentException("Invalid frozen stack");
            PortableResourceDescriptor resource = PortableResourceDescriptor.read(data.getCompoundTag("resource"));
            if (!resource.equals(describe(item)))
                throw new IllegalArgumentException("Frozen stack payload identity mismatch");
            return new Stack(item, resource, amount);
        }

        private static PortableResourceDescriptor describe(NBTTagCompound item) {
            return PortableResourceDescriptor.fromDescriptor(mekanism.api.qio.resource.QIOResourceDescriptor.persisted(
                    mekanism.api.qio.resource.QIOResourceCodecs.ITEM_STACK_ID,
                    mekanism.api.qio.resource.QIOResourceCodecs.ITEM_FAMILY,
                    mekanism.api.qio.resource.QIOResourceCodecs.ITEM_STACK.getCodecVersion(),
                    mekanism.api.qio.resource.QIOResourceCodecs.ITEM_STACK.getStorageUnitsPerUnit(), item));
        }

        public NBTTagCompound write() {
            NBTTagCompound result = new NBTTagCompound();
            result.setInteger("amount", amount);
            if (!isEmpty()) {
                result.setTag("item", data.copy());
                result.setTag("resource", resource.write());
            }
            return result;
        }

        public boolean isEmpty() {
            return resource == null;
        }

        public PortableResourceDescriptor getResource() {
            return Objects.requireNonNull(resource, "empty stack");
        }

        public int getAmount() {
            return amount;
        }

        public String getKey() {
            return key;
        }

        public long getRetainedBytes() {
            return retainedBytes;
        }

        public ItemStack thaw() {
            if (isEmpty()) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(data.copy());
            stack.setCount(amount);
            return stack;
        }
    }

    public static final class Choice {
        private static final Choice EMPTY = new Choice(Stack.EMPTY, null, 0, false);
        private final Stack stack;
        @Nullable
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean fluid;
        private final String id;
        private final String legacyId;
        private final String selectionSortKey;
        private final long retainedBytes;

        private Choice(Stack stack, @Nullable PortableResourceDescriptor resource, long amount, boolean fluid) {
            this.stack = stack;
            this.resource = resource;
            this.amount = amount;
            this.fluid = fluid;
            id = resource == null ? "" : QIOHashing.sha256((fluid ? "F|" : "I|") + resource + '|' + amount + '|' + stack.key);
            selectionSortKey = resource == null ? "" : fluid ? "F|" + resource + '|' + amount + '|' +
                    stack.getResource().withoutCapabilities() : "I|" + resource.withoutCapabilities();
            legacyId = resource == null ? "" : QIOHashing.sha256(selectionSortKey);
            retainedBytes = 256 + stack.getRetainedBytes() + QIOWorkbenchMemory.resource(resource) +
                    QIOWorkbenchMemory.text(selectionSortKey);
        }

        public Stack getStack() {
            return stack;
        }

        public PortableResourceDescriptor getResource() {
            return Objects.requireNonNull(resource, "empty choice");
        }

        public long getAmount() {
            return amount;
        }

        public boolean isVirtualFluid() {
            return fluid;
        }

        public String getId() {
            return id;
        }

        public String getLegacyId() {
            return legacyId;
        }

        public String getCandidateId() {
            return resource == null ? "" : QIOHashing.sha256(fluid ?
                    "F|" + resource + '|' + amount + '|' + stack.getResource() : "I|" + resource);
        }

        public String getSelectionSortKey() {
            return selectionSortKey;
        }
    }

    public static final class Recipe {
        private final ResourceLocation id;
        private final String implementation;
        private final Layout layout;
        private final int width;
        private final int height;
        private final Stack output;
        private final List<List<Choice>> slots;
        private final String signature;
        private final boolean automatic;
        private final String reason;
        private final long retainedBytes;

        private Recipe(ResourceLocation id, String implementation, Layout layout, int width, int height,
                       Stack output, List<List<Choice>> slots, String signature, boolean automatic, String reason, long retainedBytes) {
            this.id = id;
            this.implementation = implementation;
            this.layout = layout;
            this.width = width;
            this.height = height;
            this.output = output;
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
            this.signature = signature;
            this.automatic = automatic;
            this.reason = reason;
            this.retainedBytes = retainedBytes;
        }

        public ResourceLocation getId() {
            return id;
        }

        public String getImplementation() {
            return implementation;
        }

        public Layout getLayout() {
            return layout;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public Stack getOutput() {
            return output;
        }

        public List<List<Choice>> getSlots() {
            return slots;
        }

        /**
         * Pure mapping from a validated physical grid to declaration occurrences.
         */
        @Nullable
        public List<List<Choice>> slotsForGrid(List<Stack> grid) {
            if (grid.size() != 9) return null;
            if (layout == Layout.SHAPED) {
                for (int y = 0; y <= 3 - height; y++)
                    for (int x = 0; x <= 3 - width; x++) {
                        for (boolean mirror : new boolean[]{false, true}) {
                            List<List<Choice>> aligned = new ArrayList<>(Collections.nCopies(9, Collections.singletonList(Choice.EMPTY)));
                            for (int row = 0; row < height; row++)
                                for (int column = 0; column < width; column++) {
                                    int target = (row + y) * 3 + x + (mirror ? width - column - 1 : column);
                                    aligned.set(target, slots.get(row * 3 + column));
                                }
                            boolean matches = true;
                            for (int slot = 0; slot < 9; slot++)
                                if (!represents(aligned.get(slot), grid.get(slot))) matches = false;
                            if (matches) return Collections.unmodifiableList(aligned);
                        }
                    }
                return null;
            }
            if (layout == Layout.SHAPELESS) {
                List<List<Choice>> aligned = new ArrayList<>(Collections.nCopies(9, Collections.singletonList(Choice.EMPTY)));
                return alignShapeless(0, grid, new boolean[9], aligned, new int[]{4096}) ?
                        Collections.unmodifiableList(aligned) : null;
            }
            return null;
        }

        private boolean alignShapeless(int slot, List<Stack> grid, boolean[] used, List<List<Choice>> aligned, int[] budget) {
            if (--budget[0] < 0) return false;
            if (slot == 9) return true;
            for (int ingredient = 0; ingredient < 9; ingredient++) {
                if (!used[ingredient] && represents(slots.get(ingredient), grid.get(slot))) {
                    used[ingredient] = true;
                    aligned.set(slot, slots.get(ingredient));
                    if (alignShapeless(slot + 1, grid, used, aligned, budget)) return true;
                    used[ingredient] = false;
                }
            }
            return false;
        }

        private static boolean represents(List<Choice> choices, Stack grid) {
            for (Choice choice : choices) {
                if (choice.stack.isEmpty() || grid.isEmpty()) {
                    if (choice.stack.isEmpty() && grid.isEmpty()) return true;
                } else if (choice.stack.resource.withoutCapabilities().equals(grid.resource.withoutCapabilities()))
                    return true;
            }
            return false;
        }

        public String getSignature() {
            return signature;
        }

        public boolean allowsAutomaticDiscovery() {
            return automatic;
        }

        public String getDiagnostic() {
            return reason;
        }
    }

    public static final class Capture {
        private final Map<ResourceLocation, Recipe> recipes;
        private final Map<ResourceLocation, String> diagnostics;
        private final Map<PortableResourceDescriptor, List<String>> rejectedOutputs;
        private final long retainedBytes;
        private final int scannedRecipes;

        private Capture(Map<ResourceLocation, Recipe> recipes, Map<ResourceLocation, String> diagnostics,
                        Map<PortableResourceDescriptor, List<String>> rejectedOutputs, long retainedBytes,
                        int scannedRecipes) {
            this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
            this.diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
            this.rejectedOutputs = rejectedOutputs;
            this.retainedBytes = retainedBytes;
            this.scannedRecipes = scannedRecipes;
        }

        /**
         * Conservative retained-data estimate; this is not a JVM heap measurement.
         */
        public long getRetainedBytes() {
            return retainedBytes;
        }

        public int getScannedRecipes() {
            return scannedRecipes;
        }

        public int getRejectedOutputCount() {
            return rejectedOutputs.size();
        }
    }
}
