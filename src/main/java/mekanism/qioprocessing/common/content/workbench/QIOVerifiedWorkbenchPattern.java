package mekanism.qioprocessing.common.content.workbench;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Stack;
import mekanism.qioprocessing.common.planning.QIOWorkbenchMemory;
import mekanism.qioprocessing.common.planning.QIOWorkbenchValidationService;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import java.util.*;

/**
 * A concrete validated grid with exact state and physical remainders. This representation can
 * cross worker/persistence boundaries without constructing ItemStacks or consulting registries.
 * Loading the record never grants a validation credential for the new process generation.
 */
public final class QIOVerifiedWorkbenchPattern {
    private static final int SCHEMA = 1;
    private final UUID id;
    private final ResourceLocation recipeId;
    private final String declarationSignature;
    private final String catalogGeneration;
    private final QIOWorkbenchDeclarations.Layout layout;
    private final List<Stack> grid;
    private final Stack output;
    private final List<Stack> remainders;
    private final List<PortableResourceDescriptor> consumedResources;
    private final long[] consumedAmounts;
    private final boolean[] virtualSlots;
    private final Map<PortableResourceDescriptor, Long> inputs;
    private final Map<PortableResourceDescriptor, Long> outputs;
    private final String contentSignature;
    private final long retainedBytes;

    private QIOVerifiedWorkbenchPattern(UUID id, ResourceLocation recipeId, String declarationSignature,
                                        String catalogGeneration, QIOWorkbenchDeclarations.Layout layout, List<Stack> grid,
                                        Stack output, List<Stack> remainders, List<PortableResourceDescriptor> consumedResources,
                                        long[] consumedAmounts, boolean[] virtualSlots) {
        this.id = Objects.requireNonNull(id, "id");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.declarationSignature = hash(declarationSignature);
        this.catalogGeneration = hash(catalogGeneration);
        this.layout = Objects.requireNonNull(layout, "layout");
        if (grid.size() != 9 || remainders.size() != 9 || consumedResources.size() != 9 ||
                consumedAmounts.length != 9 || virtualSlots.length != 9 || output.isEmpty()) {
            throw new IllegalArgumentException("Invalid verified nine-slot pattern");
        }
        this.grid = immutableStacks(grid);
        this.output = output;
        this.remainders = immutableStacks(remainders);
        this.consumedResources = Collections.unmodifiableList(new ArrayList<>(consumedResources));
        this.consumedAmounts = consumedAmounts.clone();
        this.virtualSlots = virtualSlots.clone();
        Map<PortableResourceDescriptor, Long> inputs = new LinkedHashMap<>();
        Map<PortableResourceDescriptor, Long> outputs = new LinkedHashMap<>();
        outputs.put(output.getResource(), (long) output.getAmount());
        StringBuilder canonical = new StringBuilder(recipeId.toString()).append('|')
                .append(declarationSignature).append('|').append(layout).append('|').append(output.getKey());
        for (int slot = 0; slot < 9; slot++) {
            Stack stack = grid.get(slot);
            PortableResourceDescriptor consumed = consumedResources.get(slot);
            long amount = consumedAmounts[slot];
            if (stack.isEmpty()) {
                if (consumed != null || amount != 0 || virtualSlots[slot]) {
                    throw new IllegalArgumentException("Consumption assigned to an empty slot");
                }
            } else {
                if (consumed == null || amount <= 0 || !virtualSlots[slot] &&
                        (!consumed.equals(stack.getResource()) || amount != stack.getAmount()) ||
                        virtualSlots[slot] && consumed.getKind() != PortableResourceDescriptor.Kind.FLUID) {
                    throw new IllegalArgumentException("Input/virtual fluid slot identity mismatch");
                }
                inputs.merge(consumed, amount, Math::addExact);
            }
            Stack remainder = remainders.get(slot);
            if (!virtualSlots[slot] && !remainder.isEmpty()) {
                outputs.merge(remainder.getResource(), (long) remainder.getAmount(), Math::addExact);
            }
            canonical.append('|').append(slot).append('=').append(stack.getKey())
                    .append('>').append(consumed).append('@').append(amount).append('/')
                    .append(virtualSlots[slot]).append('>').append(remainder.getKey());
        }
        if (inputs.isEmpty()) throw new IllegalArgumentException("Pattern must consume an input");
        this.inputs = Collections.unmodifiableMap(inputs);
        this.outputs = Collections.unmodifiableMap(outputs);
        contentSignature = QIOHashing.sha256(canonical);
        retainedBytes = 1024 + QIOWorkbenchMemory.stacks(this.grid) + QIOWorkbenchMemory.stacks(this.remainders) +
                output.getRetainedBytes() + QIOWorkbenchMemory.resources(this.inputs) + QIOWorkbenchMemory.resources(this.outputs);
        if (retainedBytes > QIOWorkbenchMemory.RESULT_LIMIT) {
            throw new QIOWorkbenchMemory.LimitException("WORKBENCH_PATTERN_MEMORY_LIMIT");
        }
    }

    public static QIOVerifiedWorkbenchPattern verified(UUID id, QIOWorkbenchDeclarations.Recipe recipe,
                                                       String generation, List<QIOWorkbenchDeclarations.Choice> choices,
                                                       QIOWorkbenchValidationService.Result result) {
        if (!result.isValid() || choices.size() != 9 || !recipe.getId().equals(result.getRecipeId())) {
            throw new IllegalArgumentException("Pattern is not validated for this recipe");
        }
        List<PortableResourceDescriptor> resources = new ArrayList<>(9);
        long[] amounts = new long[9];
        boolean[] virtual = new boolean[9];
        boolean[] validatedVirtual = result.getVirtualSlots();
        for (int slot = 0; slot < 9; slot++) {
            QIOWorkbenchDeclarations.Choice choice = choices.get(slot);
            if (!choice.getStack().getKey().equals(result.getInputs().get(slot).getKey()) ||
                    choice.isVirtualFluid() != validatedVirtual[slot]) {
                throw new IllegalArgumentException("Validation belongs to another grid");
            }
            resources.add(choice.getStack().isEmpty() ? null : choice.getResource());
            amounts[slot] = choice.getAmount();
            virtual[slot] = choice.isVirtualFluid();
        }
        QIOVerifiedWorkbenchPattern pattern = new QIOVerifiedWorkbenchPattern(id, recipe.getId(), recipe.getSignature(), generation,
                recipe.getLayout(), result.getInputs(), result.getOutput(), result.getRemainders(), resources, amounts, virtual);
        if (!pattern.inputs.equals(result.getConsumedInputs()))
            throw new IllegalArgumentException("Declared consumption differs from verified container contents");
        return pattern;
    }

    public UUID getId() {
        return id;
    }

    public long getRetainedBytes() {
        return retainedBytes;
    }

    public ResourceLocation getRecipeId() {
        return recipeId;
    }

    public String getDeclarationSignature() {
        return declarationSignature;
    }

    public String getCatalogGeneration() {
        return catalogGeneration;
    }

    public String getContentSignature() {
        return contentSignature;
    }

    public QIOWorkbenchDeclarations.Layout getLayout() {
        return layout;
    }

    public List<Stack> getGrid() {
        return grid;
    }

    public Stack getOutput() {
        return output;
    }

    public List<Stack> getRemainders() {
        return remainders;
    }

    public Map<PortableResourceDescriptor, Long> getInputs() {
        return inputs;
    }

    public Map<PortableResourceDescriptor, Long> getOutputs() {
        return outputs;
    }

    public boolean[] getVirtualSlots() {
        return virtualSlots.clone();
    }

    @javax.annotation.Nullable
    public PortableResourceDescriptor getConsumedResource(int slot) {
        return consumedResources.get(slot);
    }

    public long getConsumedAmount(int slot) {
        return consumedAmounts[slot];
    }

    public QIOVerifiedWorkbenchPattern withId(UUID nextId) {
        return new QIOVerifiedWorkbenchPattern(nextId, recipeId, declarationSignature, catalogGeneration,
                layout, grid, output, remainders, consumedResources, consumedAmounts, virtualSlots);
    }

    public QIOVerifiedWorkbenchPattern withDeclaration(String signature, QIOWorkbenchDeclarations.Layout nextLayout) {
        return new QIOVerifiedWorkbenchPattern(id, recipeId, signature, catalogGeneration, nextLayout, grid, output,
                remainders, consumedResources, consumedAmounts, virtualSlots);
    }

    public QIOVerifiedWorkbenchPattern revalidated(String generation, QIOWorkbenchValidationService.Result result) {
        if (!result.isValid() || !recipeId.equals(result.getRecipeId()) || !inputs.equals(result.getConsumedInputs()) ||
                !sameGrid(result.getInputs(), grid) || !java.util.Arrays.equals(virtualSlots, result.getVirtualSlots())) {
            throw new IllegalArgumentException("Validation does not describe this concrete pattern");
        }
        return new QIOVerifiedWorkbenchPattern(id, recipeId, declarationSignature, generation, layout,
                grid, result.getOutput(), result.getRemainders(), consumedResources, consumedAmounts, virtualSlots);
    }

    private static boolean sameGrid(List<Stack> first, List<Stack> second) {
        if (first.size() != second.size()) return false;
        for (int slot = 0; slot < first.size(); slot++)
            if (!first.get(slot).getKey().equals(second.get(slot).getKey())) return false;
        return true;
    }

    /**
     * Re-encoding a concrete legacy/manual grid preserves physical slots and full actual output.
     */
    public static QIOVerifiedWorkbenchPattern concrete(UUID id, ResourceLocation recipe, String signature,
                                                       String generation, QIOWorkbenchDeclarations.Layout layout, QIOWorkbenchValidationService.Result result) {
        if (!result.isValid() || !recipe.equals(result.getRecipeId())) {
            throw new IllegalArgumentException("Concrete grid has not passed validation");
        }
        List<PortableResourceDescriptor> resources = new ArrayList<>(9);
        long[] amounts = new long[9];
        for (int slot = 0; slot < 9; slot++) {
            Stack input = result.getInputs().get(slot);
            if (result.getVirtualSlots()[slot])
                throw new IllegalArgumentException("Virtual inputs require explicit choices");
            resources.add(input.isEmpty() ? null : input.getResource());
            amounts[slot] = input.getAmount();
        }
        return new QIOVerifiedWorkbenchPattern(id, recipe, signature, generation, layout, result.getInputs(),
                result.getOutput(), result.getRemainders(), resources, amounts, new boolean[9]);
    }

    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA);
        data.setString("id", id.toString());
        data.setString("recipe", recipeId.toString());
        data.setString("declaration", declarationSignature);
        data.setString("generation", catalogGeneration);
        data.setString("layout", layout.name());
        data.setTag("output", output.write());
        data.setString("signature", contentSignature);
        NBTTagList slots = new NBTTagList();
        for (int slot = 0; slot < 9; slot++) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("input", grid.get(slot).write());
            entry.setTag("remainder", remainders.get(slot).write());
            entry.setBoolean("virtual", virtualSlots[slot]);
            if (consumedResources.get(slot) != null) entry.setTag("resource", consumedResources.get(slot).write());
            entry.setLong("amount", consumedAmounts[slot]);
            slots.appendTag(entry);
        }
        data.setTag("slots", slots);
        return data;
    }

    public static QIOVerifiedWorkbenchPattern read(NBTTagCompound data) {
        if (data.getInteger("schema") != SCHEMA)
            throw new IllegalArgumentException("Unsupported verified pattern schema");
        NBTTagList slots = data.getTagList("slots", 10);
        if (slots.tagCount() != 9) throw new IllegalArgumentException("Invalid verified pattern slots");
        List<Stack> grid = new ArrayList<>(9);
        List<Stack> remainders = new ArrayList<>(9);
        List<PortableResourceDescriptor> resources = new ArrayList<>(9);
        long[] amounts = new long[9];
        boolean[] virtual = new boolean[9];
        for (int slot = 0; slot < 9; slot++) {
            NBTTagCompound entry = slots.getCompoundTagAt(slot);
            grid.add(Stack.read(entry.getCompoundTag("input")));
            remainders.add(Stack.read(entry.getCompoundTag("remainder")));
            resources.add(entry.hasKey("resource", 10) ? PortableResourceDescriptor.read(entry.getCompoundTag("resource")) : null);
            amounts[slot] = entry.getLong("amount");
            virtual[slot] = entry.getBoolean("virtual");
        }
        QIOVerifiedWorkbenchPattern pattern = new QIOVerifiedWorkbenchPattern(UUID.fromString(data.getString("id")),
                new ResourceLocation(data.getString("recipe")), data.getString("declaration"), data.getString("generation"),
                QIOWorkbenchDeclarations.Layout.valueOf(data.getString("layout")), grid,
                Stack.read(data.getCompoundTag("output")), remainders, resources, amounts, virtual);
        if (!pattern.contentSignature.equals(data.getString("signature"))) {
            throw new IllegalArgumentException("Verified pattern content signature mismatch");
        }
        return pattern;
    }

    private static List<Stack> immutableStacks(List<Stack> source) {
        List<Stack> copy = new ArrayList<>(source.size());
        for (Stack stack : source) copy.add(Objects.requireNonNull(stack, "stack"));
        return Collections.unmodifiableList(copy);
    }

    private static String hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid pattern hash");
        return value;
    }
}
