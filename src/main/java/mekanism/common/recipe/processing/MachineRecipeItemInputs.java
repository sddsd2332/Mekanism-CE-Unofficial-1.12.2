package mekanism.common.recipe.processing;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Expands wildcard recipe inputs into concrete item stacks suitable for persistent processing recipes.
 */
public final class MachineRecipeItemInputs {

    private MachineRecipeItemInputs() {
    }

    public static List<ItemStack> expand(ItemStack input, Predicate<ItemStack> accepts) {
        if (input == null || input.isEmpty() || input.getCount() <= 0) {
            return Collections.emptyList();
        }
        Predicate<ItemStack> filter = accepts == null ? stack -> true : accepts;
        List<ItemStack> expanded = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ItemStack candidate : rawCandidates(input)) {
            ItemStack normalized = normalizeCandidate(input, candidate);
            if (isConcreteInput(normalized) && filter.test(normalized) && seen.add(identity(normalized))) {
                expanded.add(normalized);
            }
        }
        return expanded;
    }

    public static List<List<ItemStack>> expandCombinations(List<ItemStack> inputs, Predicate<List<ItemStack>> accepts) {
        if (inputs == null || inputs.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<ItemStack>> candidateGroups = new ArrayList<>(inputs.size());
        for (ItemStack input : inputs) {
            List<ItemStack> candidates = expand(input, stack -> true);
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            candidateGroups.add(candidates);
        }
        List<List<ItemStack>> combinations = new ArrayList<>();
        addCombinations(candidateGroups, 0, new ArrayList<>(inputs.size()), accepts == null ? stacks -> true : accepts, combinations);
        return combinations;
    }

    public static boolean isConcreteInput(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0 && stack.getMetadata() != OreDictionary.WILDCARD_VALUE;
    }

    private static void addCombinations(List<List<ItemStack>> groups, int index, List<ItemStack> current,
          Predicate<List<ItemStack>> accepts, List<List<ItemStack>> output) {
        if (index >= groups.size()) {
            List<ItemStack> copy = copyStacks(current);
            if (accepts.test(copy)) {
                output.add(copy);
            }
            return;
        }
        for (ItemStack candidate : groups.get(index)) {
            current.add(candidate);
            addCombinations(groups, index + 1, current, accepts, output);
            current.remove(current.size() - 1);
        }
    }

    private static List<ItemStack> rawCandidates(ItemStack input) {
        if (input.getMetadata() != OreDictionary.WILDCARD_VALUE) {
            return Collections.singletonList(input.copy());
        }
        List<ItemStack> candidates = new ArrayList<>();
        addCreativeSubItems(input, candidates);
        addFallbackMetaZero(input, candidates);
        addOreDictionaryCandidates(input, candidates);
        for (ItemStack candidate : new ArrayList<>(candidates)) {
            addOreDictionaryCandidates(candidate, candidates);
        }
        return candidates;
    }

    private static void addOreDictionaryCandidates(ItemStack input, List<ItemStack> candidates) {
        int[] oreIds;
        try {
            oreIds = OreDictionary.getOreIDs(input);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName == null || oreName.isEmpty()) {
                continue;
            }
            for (ItemStack oreStack : OreDictionary.getOres(oreName, false)) {
                if (oreStack.isEmpty()) {
                    continue;
                }
                if (oreStack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                    addCreativeSubItems(oreStack, candidates);
                    addFallbackMetaZero(oreStack, candidates);
                } else {
                    candidates.add(oreStack.copy());
                }
            }
        }
    }

    private static void addCreativeSubItems(ItemStack input, List<ItemStack> candidates) {
        NonNullList<ItemStack> subItems = NonNullList.create();
        try {
            input.getItem().getSubItems(CreativeTabs.SEARCH, subItems);
        } catch (RuntimeException | LinkageError ignored) {
            return;
        }
        for (ItemStack subItem : subItems) {
            if (!subItem.isEmpty() && subItem.getItem() == input.getItem() && subItem.getMetadata() != OreDictionary.WILDCARD_VALUE) {
                candidates.add(subItem.copy());
            }
        }
    }

    private static void addFallbackMetaZero(ItemStack input, List<ItemStack> candidates) {
        candidates.add(new ItemStack(input.getItem(), 1, 0));
    }

    private static ItemStack normalizeCandidate(ItemStack recipeInput, ItemStack candidate) {
        if (candidate.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack normalized = candidate.copy();
        normalized.setCount(recipeInput.getCount());
        if (recipeInput.hasTagCompound()) {
            NBTTagCompound tag = recipeInput.getTagCompound();
            normalized.setTagCompound(tag == null ? null : tag.copy());
        }
        return normalized;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copied = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copied.add(stack.copy());
        }
        return copied;
    }

    private static String identity(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        return (name == null ? stack.getItem().getClass().getName() : name.toString()) + '@' + stack.getMetadata() + 'x' + stack.getCount() +
              (stack.hasTagCompound() ? '#' + stack.getTagCompound().toString() : "");
    }
}
