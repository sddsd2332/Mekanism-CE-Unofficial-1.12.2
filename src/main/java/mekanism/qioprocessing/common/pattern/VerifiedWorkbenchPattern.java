package mekanism.qioprocessing.common.pattern;

import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.*;

/**
 * Concrete, main-thread-verified 3x3 pattern. It contains no live recipe/world references.
 */
public final class VerifiedWorkbenchPattern {
    public static final int SCHEMA = 1;
    private final UUID id;
    private final String recipeId, declarationSignature, catalogGeneration, contentSignature;
    private final List<RecipeItemKey> grid, remainders;
    private final RecipeItemKey output;
    private final int outputCount;
    private final Map<RecipeItemKey, Integer> consumedInputs;
    private final long retainedBytes;

    VerifiedWorkbenchPattern(UUID id, String recipeId, String declarationSignature, String catalogGeneration,
                             List<RecipeItemKey> grid, RecipeItemKey output, int outputCount, List<RecipeItemKey> remainders,
                             Map<RecipeItemKey, Integer> consumedInputs) {
        this.id = Objects.requireNonNull(id);
        this.recipeId = Objects.requireNonNull(recipeId);
        this.declarationSignature = requireHash(declarationSignature);
        this.catalogGeneration = requireHash(catalogGeneration);
        if (grid.size() != 9 || remainders.size() != 9 || outputCount <= 0 || output == null)
            throw new IllegalArgumentException("Invalid verified pattern");
        this.grid = immutable(grid);
        this.remainders = immutable(remainders);
        this.output = output;
        this.outputCount = outputCount;
        Map<RecipeItemKey, Integer> expectedInputs = new LinkedHashMap<>();
        for (RecipeItemKey input : this.grid) if (input != null) expectedInputs.merge(input, 1, Integer::sum);
        if (!expectedInputs.equals(consumedInputs))
            throw new IllegalArgumentException("Pattern consumption does not match grid");
        this.consumedInputs = Collections.unmodifiableMap(expectedInputs);
        PatternSignature signature = new PatternSignature().add(recipeId).add(this.declarationSignature).add(this.catalogGeneration)
                .add(output.fingerprint()).add(Integer.toString(outputCount));
        for (int i = 0; i < 9; i++) signature.add(key(this.grid.get(i))).add(key(this.remainders.get(i)));
        contentSignature = signature.finish();
        long bytes = 1024L;
        for (RecipeItemKey key : this.grid) if (key != null) bytes += key.displayNBT().toString().length() * 2L + 96;
        for (RecipeItemKey key : this.remainders)
            if (key != null) bytes += key.displayNBT().toString().length() * 2L + 96;
        bytes += output.displayNBT().toString().length() * 2L + consumedInputs.size() * 96L;
        retainedBytes = bytes;
    }

    private static List<RecipeItemKey> immutable(List<RecipeItemKey> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String key(RecipeItemKey value) {
        return value == null ? "empty" : value.fingerprint();
    }

    private static String requireHash(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid pattern hash");
        return value;
    }

    public UUID id() {
        return id;
    }

    public String recipeId() {
        return recipeId;
    }

    public String declarationSignature() {
        return declarationSignature;
    }

    public String catalogGeneration() {
        return catalogGeneration;
    }

    public String contentSignature() {
        return contentSignature;
    }

    public VerifiedWorkbenchPattern withId(UUID nextId) {
        return new VerifiedWorkbenchPattern(nextId, recipeId, declarationSignature, catalogGeneration,
                grid, output, outputCount, remainders, consumedInputs);
    }

    public List<RecipeItemKey> grid() {
        return grid;
    }

    public List<RecipeItemKey> remainders() {
        return remainders;
    }

    public RecipeItemKey output() {
        return output;
    }

    public int outputCount() {
        return outputCount;
    }

    public Map<RecipeItemKey, Integer> consumedInputs() {
        return consumedInputs;
    }

    public long retainedBytes() {
        return retainedBytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VerifiedWorkbenchPattern)) return false;
        VerifiedWorkbenchPattern that = (VerifiedWorkbenchPattern) other;
        return outputCount == that.outputCount && id.equals(that.id) && recipeId.equals(that.recipeId)
                && declarationSignature.equals(that.declarationSignature) && catalogGeneration.equals(that.catalogGeneration)
                && contentSignature.equals(that.contentSignature) && grid.equals(that.grid) && remainders.equals(that.remainders)
                && output.equals(that.output) && consumedInputs.equals(that.consumedInputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, recipeId, declarationSignature, catalogGeneration, contentSignature, grid, remainders, output, outputCount, consumedInputs);
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", SCHEMA);
        tag.setUniqueId("id", id);
        tag.setString("recipe", recipeId);
        tag.setString("declaration", declarationSignature);
        tag.setString("generation", catalogGeneration);
        tag.setString("signature", contentSignature);
        tag.setInteger("outputCount", outputCount);
        tag.setTag("output", output.displayNBT());
        NBTTagList grids = new NBTTagList();
        for (int i = 0; i < 9; i++) {
            NBTTagCompound slot = new NBTTagCompound();
            if (grid.get(i) != null) slot.setTag("input", grid.get(i).displayNBT());
            if (remainders.get(i) != null) slot.setTag("remainder", remainders.get(i).displayNBT());
            grids.appendTag(slot);
        }
        tag.setTag("slots", grids);
        NBTTagList inputs = new NBTTagList();
        for (Map.Entry<RecipeItemKey, Integer> entry : consumedInputs.entrySet()) {
            NBTTagCompound input = new NBTTagCompound();
            input.setTag("stack", entry.getKey().displayNBT());
            input.setInteger("amount", entry.getValue());
            inputs.appendTag(input);
        }
        tag.setTag("inputs", inputs);
        return tag;
    }

    public static VerifiedWorkbenchPattern read(NBTTagCompound tag) {
        if (tag.getInteger("schema") != SCHEMA || !tag.hasUniqueId("id") || !tag.hasKey("slots", 9))
            throw new IllegalArgumentException("Unsupported verified pattern");
        NBTTagList slots = tag.getTagList("slots", 10);
        if (slots.tagCount() != 9) throw new IllegalArgumentException("Invalid pattern slots");
        List<RecipeItemKey> grid = new ArrayList<>(), remainders = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            NBTTagCompound slot = slots.getCompoundTagAt(i);
            grid.add(slot.hasKey("input", 10) ? RecipeItemKey.capture(new ItemStack(slot.getCompoundTag("input"))) : null);
            remainders.add(slot.hasKey("remainder", 10) ? RecipeItemKey.capture(new ItemStack(slot.getCompoundTag("remainder"))) : null);
        }
        RecipeItemKey output = RecipeItemKey.capture(new ItemStack(tag.getCompoundTag("output")));
        Map<RecipeItemKey, Integer> inputs = new LinkedHashMap<>();
        NBTTagList list = tag.getTagList("inputs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound input = list.getCompoundTagAt(i);
            int amount = input.getInteger("amount");
            if (amount <= 0 || inputs.putIfAbsent(RecipeItemKey.capture(new ItemStack(input.getCompoundTag("stack"))), amount) != null)
                throw new IllegalArgumentException("Invalid input amount or duplicate");
        }
        VerifiedWorkbenchPattern result = new VerifiedWorkbenchPattern(tag.getUniqueId("id"), tag.getString("recipe"), tag.getString("declaration"), tag.getString("generation"), grid, output, tag.getInteger("outputCount"), remainders, inputs);
        if (!result.contentSignature.equals(tag.getString("signature")))
            throw new IllegalArgumentException("Verified pattern signature mismatch");
        return result;
    }
}
