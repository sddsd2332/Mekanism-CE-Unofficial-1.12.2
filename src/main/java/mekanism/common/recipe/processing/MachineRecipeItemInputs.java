package mekanism.common.recipe.processing;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Expands wildcard recipe inputs into concrete item stacks suitable for persistent processing recipes.
 */
public final class MachineRecipeItemInputs {

    private static final int BOUNDED_SCAN_MULTIPLIER = 8;
    private static final int MIN_BOUNDED_SCAN_LIMIT = 64;

    private MachineRecipeItemInputs() {
    }

    public static List<ItemStack> expand(ItemStack input, Predicate<ItemStack> accepts) {
        return expand(input, accepts, Integer.MAX_VALUE);
    }

    /**
     * Expands at most {@code maxResults} concrete candidates without first materializing every
     * creative-tab and ore-dictionary variant.
     */
    public static List<ItemStack> expand(ItemStack input, Predicate<ItemStack> accepts,
          int maxResults) {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("Maximum expanded item candidates must be positive");
        }
        if (input == null || input.isEmpty() || input.getCount() <= 0) {
            return Collections.emptyList();
        }
        Predicate<ItemStack> filter = accepts == null ? stack -> true : accepts;
        ExpansionCollector collector = new ExpansionCollector(input, filter, maxResults,
              boundedScanLimit(maxResults));
        collectRawCandidates(input, collector);
        return collector.results;
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

    private static void collectRawCandidates(ItemStack input, ExpansionCollector collector) {
        if (input.getMetadata() != OreDictionary.WILDCARD_VALUE) {
            collector.offer(input.copy());
            return;
        }
        List<ItemStack> firstPass = new ArrayList<>();
        if (!addCreativeSubItems(input, collector, firstPass) ||
            !addFallbackMetaZero(input, collector, firstPass) ||
            !addOreDictionaryCandidates(input, collector, firstPass)) {
            return;
        }
        int firstPassSize = firstPass.size();
        for (int index = 0; index < firstPassSize && collector.canContinue(); index++) {
            if (!addOreDictionaryCandidates(firstPass.get(index), collector, null)) {
                return;
            }
        }
    }

    private static boolean addOreDictionaryCandidates(ItemStack input,
          ExpansionCollector collector, List<ItemStack> firstPass) {
        if (!collector.canContinue()) {
            return false;
        }
        int[] oreIds;
        try {
            oreIds = OreDictionary.getOreIDs(input);
        } catch (IllegalArgumentException ignored) {
            return true;
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
                    if (!addCreativeSubItems(oreStack, collector, firstPass) ||
                        !addFallbackMetaZero(oreStack, collector, firstPass)) {
                        return false;
                    }
                } else {
                    if (!offerRawCandidate(oreStack.copy(), collector, firstPass)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean addCreativeSubItems(ItemStack input,
          ExpansionCollector collector, List<ItemStack> firstPass) {
        if (!collector.canContinue()) {
            return false;
        }
        NonNullList<ItemStack> subItems = NonNullList.create();
        try {
            input.getItem().getSubItems(CreativeTabs.SEARCH, subItems);
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
        for (ItemStack subItem : subItems) {
            if (!subItem.isEmpty() && subItem.getItem() == input.getItem() && subItem.getMetadata() != OreDictionary.WILDCARD_VALUE) {
                if (!offerRawCandidate(subItem.copy(), collector, firstPass)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean addFallbackMetaZero(ItemStack input,
          ExpansionCollector collector, List<ItemStack> firstPass) {
        return collector.canContinue() && offerRawCandidate(
              new ItemStack(input.getItem(), 1, 0), collector, firstPass);
    }

    private static boolean offerRawCandidate(ItemStack candidate,
          ExpansionCollector collector, List<ItemStack> firstPass) {
        if (firstPass != null) {
            firstPass.add(candidate);
        }
        return collector.offer(candidate);
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

    private static int boundedScanLimit(int maxResults) {
        if (maxResults == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        long scaled = (long) maxResults * BOUNDED_SCAN_MULTIPLIER;
        return (int) Math.min(Integer.MAX_VALUE,
              Math.max(MIN_BOUNDED_SCAN_LIMIT, scaled));
    }

    private static final class ExpansionCollector {

        private final ItemStack recipeInput;
        private final Predicate<ItemStack> filter;
        private final int maxResults;
        private final int maxScanned;
        private final List<ItemStack> results = new ArrayList<>();
        private final Set<StackIdentity> seen = new LinkedHashSet<>();
        private int scanned;

        private ExpansionCollector(ItemStack recipeInput, Predicate<ItemStack> filter,
              int maxResults, int maxScanned) {
            this.recipeInput = recipeInput;
            this.filter = filter;
            this.maxResults = maxResults;
            this.maxScanned = maxScanned;
        }

        private boolean offer(ItemStack candidate) {
            if (!canContinue()) {
                return false;
            }
            scanned++;
            ItemStack normalized = normalizeCandidate(recipeInput, candidate);
            if (isConcreteInput(normalized) && filter.test(normalized) &&
                seen.add(new StackIdentity(normalized))) {
                results.add(normalized);
            }
            return canContinue();
        }

        private boolean canContinue() {
            return results.size() < maxResults && scanned < maxScanned;
        }
    }

    private static final class StackIdentity {

        private final Item item;
        private final int metadata;
        private final int count;
        private final NBTTagCompound tag;
        private final int hashCode;

        private StackIdentity(ItemStack stack) {
            item = stack.getItem();
            metadata = stack.getMetadata();
            count = stack.getCount();
            tag = stack.getTagCompound();
            int hash = System.identityHashCode(item);
            hash = 31 * hash + metadata;
            hash = 31 * hash + count;
            hashCode = 31 * hash + (tag == null ? 0 : tag.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StackIdentity other)) {
                return false;
            }
            return item == other.item && metadata == other.metadata && count == other.count &&
                  Objects.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
