package mekanism.common.recipe.lookup.cache;

import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.ChemicalGasInput;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.recipe.inputs.IntegerInput;
import mekanism.common.recipe.inputs.IWildInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.inputs.RotaryInput;
import mekanism.common.recipe.machines.MachineRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main-thread input candidate cache, modeled after Mekanism's high-version
 * InputRecipeCache. The 1.12 input classes already expose the hash projection
 * used by their RecipeMap keys, so the cache uses that projection only to find
 * candidates and always performs the original input equality test afterwards.
 */
public final class InputRecipeCache<INPUT extends MachineInput<INPUT>, RECIPE extends MachineRecipe<INPUT, ?, RECIPE>> {

    private volatile CacheState<RECIPE> state;

    public void clear() {
        // Publish an empty state instead of mutating a state that another
        // worker may still be reading. The old state remains valid for that
        // reader until its lookup returns.
        state = null;
    }

    public boolean isInitialized() {
        return state != null;
    }

    @Nonnull
    public List<RECIPE> getRecipes(@Nonnull Collection<RECIPE> source) {
        initIfNeeded(source);
        return state.recipes;
    }

    @Nullable
    public RECIPE findFirstRecipe(@Nonnull INPUT input, @Nonnull Collection<RECIPE> source) {
        if (!input.isValid()) {
            return null;
        }
        initIfNeeded(source);
        CacheState<RECIPE> current = state;
        RECIPE recipe = findInBucket(input, current.indexed.get(IndexKey.of(input)));
        if (recipe == null && input instanceof IWildInput<?>) {
            @SuppressWarnings("unchecked")
            IWildInput<INPUT> wild = (IWildInput<INPUT>) input;
            INPUT wildInput = wild.wildCopy();
            if (wildInput != input) {
                recipe = findInBucket(wildInput, current.indexed.get(IndexKey.of(wildInput)));
            }
        }
        if (recipe == null) {
            recipe = findInCollection(input, current.complex);
        }
        return recipe == null ? null : recipe.copy();
    }

    public boolean containsInput(@Nonnull INPUT input, @Nonnull Collection<RECIPE> source) {
        if (!input.isValid()) {
            return false;
        }
        initIfNeeded(source);
        CacheState<RECIPE> current = state;
        if (findInBucket(input, current.indexed.get(IndexKey.of(input))) != null) {
            return true;
        }
        if (input instanceof IWildInput<?>) {
            @SuppressWarnings("unchecked")
            IWildInput<INPUT> wild = (IWildInput<INPUT>) input;
            INPUT wildInput = wild.wildCopy();
            if (findInBucket(wildInput, current.indexed.get(IndexKey.of(wildInput))) != null) {
                return true;
            }
        }
        return findInCollection(input, current.complex) != null;
    }

    private void initIfNeeded(Collection<RECIPE> source) {
        if (state != null) {
            return;
        }
        synchronized (this) {
            if (state != null) {
                return;
            }
            List<RECIPE> ordered = new ArrayList<>(source);
            Map<IndexKey, List<RECIPE>> buildingIndex = new HashMap<>();
            List<RECIPE> buildingComplex = new ArrayList<>();
            for (RECIPE recipe : ordered) {
                INPUT input = recipe.getInput();
                if (isIndexable(input)) {
                    IndexKey key = IndexKey.of(input);
                    buildingIndex.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
                } else {
                    buildingComplex.add(recipe);
                }
            }
            Map<IndexKey, List<RECIPE>> publishedIndex = new HashMap<>();
            for (Map.Entry<IndexKey, List<RECIPE>> entry : buildingIndex.entrySet()) {
                publishedIndex.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
            }
            state = new CacheState<>(Collections.unmodifiableList(ordered),
                  Collections.unmodifiableMap(publishedIndex), Collections.unmodifiableList(buildingComplex));
        }
    }

    @Nullable
    private RECIPE findInBucket(INPUT input, @Nullable List<RECIPE> candidates) {
        if (candidates == null) {
            return null;
        }
        return findInCollection(input, candidates);
    }

    @Nullable
    private RECIPE findInCollection(INPUT input, Iterable<RECIPE> candidates) {
        for (RECIPE recipe : candidates) {
            // This direction mirrors HashMap.get: the stored recipe key is the
            // object whose equality method decides whether it matches input.
            if (recipe.getInput().isInstance(input) && recipe.getInput().testEquality(input)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean isIndexable(MachineInput<?> input) {
        if (input instanceof ItemStackInput) {
            return !MachineInput.hasCustomItemMatcher(((ItemStackInput) input).ingredient);
        }
        if (input instanceof AdvancedMachineInput) {
            return !MachineInput.hasCustomItemMatcher(((AdvancedMachineInput) input).itemStack);
        }
        if (input instanceof DoubleMachineInput) {
            DoubleMachineInput value = (DoubleMachineInput) input;
            return !MachineInput.hasCustomItemMatcher(value.itemStack) && !MachineInput.hasCustomItemMatcher(value.extraStack);
        }
        if (input instanceof InfusionInput) {
            return !MachineInput.hasCustomItemMatcher(((InfusionInput) input).inputStack);
        }
        if (input instanceof PressurizedInput) {
            return !MachineInput.hasCustomItemMatcher(((PressurizedInput) input).getSolid());
        }
        if (input instanceof NucleosynthesizerInput) {
            return !MachineInput.hasCustomItemMatcher(((NucleosynthesizerInput) input).getSolid());
        }
        if (input instanceof FarmInput) {
            return !MachineInput.hasCustomItemMatcher(((FarmInput) input).itemStack);
        }
        return input instanceof GasInput || input instanceof FluidInput || input instanceof GasAndFluidInput ||
              input instanceof ChemicalPairInput || input instanceof ChemicalGasInput || input instanceof RotaryInput ||
              input instanceof IntegerInput;
    }

    private static final class CacheState<RECIPE> {
        final List<RECIPE> recipes;
        final Map<IndexKey, List<RECIPE>> indexed;
        final List<RECIPE> complex;

        CacheState(List<RECIPE> recipes, Map<IndexKey, List<RECIPE>> indexed, List<RECIPE> complex) {
            this.recipes = recipes;
            this.indexed = indexed;
            this.complex = complex;
        }
    }

    private static final class IndexKey {
        private final Class<?> inputType;
        private final int hash;

        private IndexKey(Class<?> inputType, int hash) {
            this.inputType = inputType;
            this.hash = hash;
        }

        static IndexKey of(MachineInput<?> input) {
            return new IndexKey(input.getClass(), input.hashIngredients());
        }

        @Override
        public int hashCode() {
            return 31 * inputType.hashCode() + hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IndexKey)) return false;
            IndexKey key = (IndexKey) other;
            return inputType == key.inputType && hash == key.hash;
        }
    }
}
