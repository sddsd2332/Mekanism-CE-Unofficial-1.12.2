package mekanism.common.recipe;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.recipes.IRecipeSignatureSource;
import mekanism.common.recipe.cache.AsyncMachinePlanSupport;
import mekanism.common.recipe.machines.EnrichmentRecipe;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.machines.ReplicatorItemStackRecipe;
import mekanism.common.recipe.machines.ReplicatorGasStackRecipe;
import mekanism.common.recipe.machines.ReplicatorFluidStackRecipe;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mekanism.common.recipe.machines.FusionCoolingRecipe;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.outputs.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSignatureContractTest {

    @BeforeAll
    static void bootstrap() { Bootstrap.register(); }

    @Test
    void unknownRecipeSubclassCannotLoseItsExtraSemanticsThroughCopy() {
        UnknownRecipe recipe = new UnknownRecipe();
        assertThrows(UnsupportedRecipeSignatureException.class, () -> RecipeSnapshotCompiler.semanticSignature(recipe));
        assertThrows(UnsupportedRecipeSignatureException.class, recipe::semanticSignature);
        assertThrows(UnsupportedRecipeSignatureException.class, () -> RecipeSnapshotCompiler.compile("unknown", new RecipeGeneration(1, 1), recipe));
        assertThrows(UnsupportedRecipeSignatureException.class, () -> AsyncMachinePlanSupport.recipeSignature(Arrays.asList(recipe, recipe)));
    }

    @Test
    void adaptedRecipeSubclassTracksItsExtraSemanticFields() {
        AdaptedRecipe recipe = new AdaptedRecipe();
        String signature = recipe.semanticSignature();
        assertEquals(signature, AsyncMachinePlanSupport.recipeSignature(recipe));
        recipe.energy++;
        assertNotEquals(signature, recipe.semanticSignature());
    }

    @Test
    void adapterCannotHideUnsupportedNestedValuesOrCycles() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inputs", Collections.singletonList(new Object()));
        UnsupportedRecipeSignatureException error = assertThrows(UnsupportedRecipeSignatureException.class,
              () -> RecipeSnapshotCompiler.semanticSignature("test:bad_v1", data));
        assertEquals(Object.class.getName(), error.getValueType());
        assertTrue(error.getValuePath().contains(".data"));
        data.clear();
        data.put("cycle", data);
        assertThrows(UnsupportedRecipeSignatureException.class, () -> RecipeSnapshotCompiler.semanticSignature(data));
        IRecipeSignatureSource cyclicAdapter = new IRecipeSignatureSource() {
            public String getRecipeSignatureType() { return "test:cycle_v1"; }
            public Map<String, ?> getRecipeSignatureData() { return Collections.singletonMap("self", this); }
        };
        assertThrows(UnsupportedRecipeSignatureException.class, () -> RecipeSnapshotCompiler.semanticSignature(cyclicAdapter));
        assertThrows(UnsupportedRecipeSignatureException.class, () -> RecipeSnapshotCompiler.semanticSignature("", Collections.emptyMap()));
    }

    @Test
    void deepDataIsRejectedBeforeStackOverflow() {
        Object value = "end";
        for (int index = 0; index < 140; index++) value = Collections.singletonList(value);
        Object nested = value;
        assertThrows(UnsupportedRecipeSignatureException.class, () -> RecipeSnapshotCompiler.semanticSignature(nested));
    }

    @Test
    void equivalentCollectionsDoNotDependOnIdentityOrImplementation() {
        List<String> shared = Collections.singletonList("same");
        assertEquals(RecipeSnapshotCompiler.semanticSignature(Arrays.asList(shared, shared)),
              RecipeSnapshotCompiler.semanticSignature(Arrays.asList(new ArrayList<>(shared), new ArrayList<>(shared))));
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("a", 1);
        first.put("b", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("b", 2);
        second.put("a", 1);
        assertEquals(RecipeSnapshotCompiler.semanticSignature(first), RecipeSnapshotCompiler.semanticSignature(second));
        assertEquals(RecipeSnapshotCompiler.semanticSignature(new LinkedHashSet<>(Arrays.asList("a", "b"))),
              RecipeSnapshotCompiler.semanticSignature(new LinkedHashSet<>(Arrays.asList("b", "a"))));
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature(Arrays.asList("a", "b")),
              RecipeSnapshotCompiler.semanticSignature(Arrays.asList("b", "a")));
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature(Arrays.asList("a;java.lang.String:b", "c")),
              RecipeSnapshotCompiler.semanticSignature(Arrays.asList("a", "b;java.lang.String:c")));
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature("test:a", first), RecipeSnapshotCompiler.semanticSignature("test:b", first));
    }

    @Test
    void nbtOrderIsIrrelevantButTypesAndContentsMatter() {
        NBTTagCompound first = new NBTTagCompound();
        first.setInteger("Aa", 1);
        first.setString("BB", "two");
        NBTTagCompound second = new NBTTagCompound();
        second.setString("BB", "two");
        second.setInteger("Aa", 1);
        assertEquals(RecipeSnapshotCompiler.semanticSignature(first), RecipeSnapshotCompiler.semanticSignature(second));
        second.setLong("Aa", 1);
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature(first), RecipeSnapshotCompiler.semanticSignature(second));
        ItemStack input = new ItemStack(Items.IRON_INGOT);
        input.setTagCompound(first);
        String signature = RecipeSnapshotCompiler.semanticSignature(input);
        first.setString("BB", "changed");
        assertNotEquals(signature, RecipeSnapshotCompiler.semanticSignature(input));
    }

    @Test
    void itemLookupCachesAreNotRecipeSemantics() {
        ItemStackInput input = new ItemStackInput(new ItemStack(Items.IRON_INGOT));
        String signature = RecipeSnapshotCompiler.semanticSignature(input);
        input.wildCopy();
        assertEquals(signature, RecipeSnapshotCompiler.semanticSignature(input));
        input.ingredient.setCount(2);
        assertNotEquals(signature, RecipeSnapshotCompiler.semanticSignature(input));
    }

    @Test
    void customInputSubclassRequiresExplicitProjection() {
        ItemStackInput input = new ItemStackInput(new ItemStack(Items.IRON_INGOT)) { private int extraCost = 2; };
        assertThrows(UnsupportedRecipeSignatureException.class, () -> RecipeSnapshotCompiler.semanticSignature(input));
    }

    @Test
    void specialRecipeTimeAndEnergyRemainPartOfSignature() {
        GasStack gas = new GasStack(new Gas("test", 0), 1);
        ItemStack item = new ItemStack(Items.IRON_INGOT);
        ItemStackOutput output = new ItemStackOutput(new ItemStack(Items.DIAMOND));
        NucleosynthesizerRecipe nucleosynthesizer = new NucleosynthesizerRecipe(new NucleosynthesizerInput(item, gas), output, 5, 10);
        String before = nucleosynthesizer.semanticSignature();
        nucleosynthesizer.ticks++;
        assertNotEquals(before, nucleosynthesizer.semanticSignature());
        nucleosynthesizer.ticks--;
        nucleosynthesizer.extraEnergy++;
        assertNotEquals(before, nucleosynthesizer.semanticSignature());
        PressurizedRecipe pressurized = new PressurizedRecipe(new PressurizedInput(item, null, gas), new PressurizedOutput(item, gas), 5, 10);
        before = pressurized.semanticSignature();
        pressurized.extraEnergy++;
        assertNotEquals(before, pressurized.semanticSignature());
        ReplicatorItemStackRecipe replicatorItem = new ReplicatorItemStackRecipe(new NucleosynthesizerInput(item, gas), output, 5, 10);
        before = replicatorItem.semanticSignature();
        replicatorItem.ticks++;
        assertNotEquals(before, replicatorItem.semanticSignature());
        ReplicatorGasStackRecipe replicatorGas = new ReplicatorGasStackRecipe(new ChemicalGasInput(gas, gas), new GasOutput(gas), 5, 10);
        before = replicatorGas.semanticSignature();
        replicatorGas.extraEnergy++;
        assertNotEquals(before, replicatorGas.semanticSignature());
        ReplicatorFluidStackRecipe replicatorFluid = new ReplicatorFluidStackRecipe(new GasAndFluidInput(gas, null), new FluidOutput(null), 5, 10);
        before = replicatorFluid.semanticSignature();
        replicatorFluid.ticks++;
        assertNotEquals(before, replicatorFluid.semanticSignature());
        SeparatorRecipe separator = new SeparatorRecipe(new FluidInput(null), 5, new ChemicalPairOutput(gas, gas));
        before = separator.semanticSignature();
        separator.energyUsage++;
        assertNotEquals(before, separator.semanticSignature());
        FusionCoolingRecipe cooling = new FusionCoolingRecipe(new FluidInput(null), new FluidOutput(null), 5);
        before = cooling.semanticSignature();
        cooling.extraEnergy++;
        assertNotEquals(before, cooling.semanticSignature());
    }

    private static class UnknownRecipe extends EnrichmentRecipe {
        private UnknownRecipe() { super(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND)); }
        @Override
        public EnrichmentRecipe copy() { throw new AssertionError("Signature extraction must not erase a subclass through copy()"); }
    }

    private static final class AdaptedRecipe extends UnknownRecipe implements IRecipeSignatureSource {
        private int energy = 5;
        public String getRecipeSignatureType() { return "test:enrichment_v1"; }
        public Map<String, ?> getRecipeSignatureData() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("input", getInput());
            data.put("output", getOutput());
            data.put("energy", energy);
            return data;
        }
    }
}
