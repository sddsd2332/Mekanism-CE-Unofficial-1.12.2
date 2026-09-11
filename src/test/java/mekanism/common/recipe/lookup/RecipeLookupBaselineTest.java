package mekanism.common.recipe.lookup;

import mekanism.api.gas.Gas;
import mekanism.api.Action;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.machines.EnrichmentRecipe;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.machine.TileEntityEnrichmentChamber;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Behavioral baseline captured before changing the authoritative lookup or machine path. */
class RecipeLookupBaselineTest {
    private static Gas left;
    private static Gas right;
    private static InfuseType infusion;

    @BeforeAll
    static void bootstrap() throws Exception {
        Field mods = Loader.class.getDeclaredField("namedMods");
        mods.setAccessible(true);
        if (mods.get(Loader.instance()) == null) mods.set(Loader.instance(), Collections.emptyMap());
        Bootstrap.register();
        left = GasRegistry.register(new Gas("lookup_baseline_left", 0));
        right = GasRegistry.register(new Gas("lookup_baseline_right", 0));
        infusion = new InfuseType("lookup_baseline", new ResourceLocation("test", "infuse"))
              .setTranslationKey("lookup_baseline");
    }

    @TestFactory
    List<DynamicTest> supportedInputShapesIgnoreRequiredQuantitiesAtLookup() {
        return Arrays.asList(
              quantityCase("item", new ItemStackInput(item(8, 2)), new ItemStackInput(item(1, 2))),
              quantityCase("double item", new DoubleMachineInput(item(8, 2), item(9, 4)),
                    new DoubleMachineInput(item(1, 2), item(1, 4))),
              quantityCase("item and gas type", new AdvancedMachineInput(item(8, 2), left),
                    new AdvancedMachineInput(item(1, 2), left)),
              quantityCase("infusion", new InfusionInput(infusion, 20, item(8, 2)),
                    new InfusionInput(infusion, 1, item(1, 2))),
              quantityCase("gas", new GasInput(gas(left, 30)), new GasInput(gas(left, 1))),
              quantityCase("fluid", new FluidInput(water(30)), new FluidInput(water(1))),
              quantityCase("gas and fluid", new GasAndFluidInput(gas(left, 30), water(40)),
                    new GasAndFluidInput(gas(left, 1), water(1))),
              quantityCase("chemical pair swapped", new ChemicalPairInput(gas(left, 20), gas(right, 30)),
                    new ChemicalPairInput(gas(right, 1), gas(left, 1))),
              quantityCase("PRC", new PressurizedInput(item(8, 2), water(30), gas(left, 40)),
                    new PressurizedInput(item(1, 2), water(1), gas(left, 1))),
              quantityCase("nucleosynthesizer", new NucleosynthesizerInput(item(8, 2), gas(left, 20)),
                    new NucleosynthesizerInput(item(1, 2), gas(left, 1))),
              quantityCase("farm gas", new FarmInput(item(8, 2), gas(left, 20)),
                    new FarmInput(item(1, 2), gas(left, 1))),
              quantityCase("farm fluid", new FarmInput(item(8, 2), water(20)),
                    new FarmInput(item(1, 2), water(1))),
              quantityCase("chemical template", new ChemicalGasInput(gas(left, 20), gas(right, 30)),
                    new ChemicalGasInput(gas(left, 1), gas(right, 1))),
              quantityCase("rotary complete key", new RotaryInput(water(20), gas(left, 30)),
                    new RotaryInput(water(1), gas(left, 1))),
              quantityCase("dimension", new IntegerInput(-1), new IntegerInput(-1)));
    }

    private static <I extends MachineInput<I>> DynamicTest quantityCase(String name, I required, I actual) {
        return DynamicTest.dynamicTest(name, () -> {
            CountingMap<I, ProbeRecipe<I>> recipes = new CountingMap<>();
            ProbeRecipe<I> definition = new ProbeRecipe<>(required);
            recipes.put(required, definition);
            ProbeRecipe<I> found = RecipeHandler.getRecipe(actual, recipes);
            assertNotNull(found, name);
            assertNotSame(definition, found);
            assertNotSame(definition.getInput(), found.getInput());
            assertNotSame(definition.getOutput(), found.getOutput());
            assertEquals(1, recipes.reads, "exact lookup must not scan or retry");
            assertEquals(0, recipes.scans);
        });
    }

    @Test
    void exactPrecedesWildcardRegardlessOfInsertionOrder() {
        for (boolean reverse : new boolean[]{false, true}) {
            CountingMap<ItemStackInput, EnrichmentRecipe> recipes = new CountingMap<>();
            EnrichmentRecipe exact = enrichment(item(8, 2), Items.DIAMOND);
            EnrichmentRecipe wild = enrichment(item(8, OreDictionary.WILDCARD_VALUE), Items.GOLD_INGOT);
            if (reverse) { recipes.put(wild.getInput(), wild); recipes.put(exact.getInput(), exact); }
            else { recipes.put(exact.getInput(), exact); recipes.put(wild.getInput(), wild); }
            assertSame(Items.DIAMOND, RecipeHandler.getRecipe(new ItemStackInput(item(1, 2)), recipes).getOutput().output.getItem());
            assertEquals(1, recipes.reads);
            assertSame(Items.GOLD_INGOT, RecipeHandler.getRecipe(new ItemStackInput(item(1, 3)), recipes).getOutput().output.getItem());
            assertEquals(3, recipes.reads);
            assertEquals(0, recipes.scans);
        }
    }

    @Test
    void wildcardLookupDropsQueryNbtAsThe112WildCopyDoes() {
        Map<ItemStackInput, EnrichmentRecipe> recipes = new HashMap<>();
        EnrichmentRecipe wild = enrichment(item(8, OreDictionary.WILDCARD_VALUE), Items.DIAMOND);
        recipes.put(wild.getInput(), wild);
        ItemStack query = item(1, 2);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", "tagged");
        query.setTagCompound(tag);
        assertNotNull(RecipeHandler.getRecipe(new ItemStackInput(query), recipes));
        assertFalse(new ItemStackInput(query).testEquality(wild.getInput()),
              "Cache revalidation uses the real tagged input, unlike wildCopy lookup");
    }

    @Test
    void nbtCollisionUsesFullEqualityAndIgnoresCompoundInsertionOrder() {
        ItemStack first = item(1, 2);
        NBTTagCompound a = new NBTTagCompound(); a.setInteger("a", 1); a.setInteger("b", 2);
        first.setTagCompound(a);
        ItemStack second = item(1, 2);
        NBTTagCompound b = new NBTTagCompound(); b.setInteger("b", 2); b.setInteger("a", 1);
        second.setTagCompound(b);
        Map<ItemStackInput, EnrichmentRecipe> recipes = new HashMap<>();
        EnrichmentRecipe definition = enrichment(first, Items.DIAMOND);
        recipes.put(definition.getInput(), definition);
        assertNotNull(RecipeHandler.getRecipe(new ItemStackInput(second), recipes));
        second.getTagCompound().setInteger("b", 3);
        assertEquals(definition.getInput().hashCode(), new ItemStackInput(second).hashCode());
        assertNull(RecipeHandler.getRecipe(new ItemStackInput(second), recipes));
    }

    @Test
    void invalidInputDoesNotReadTheMap() {
        CountingMap<ItemStackInput, EnrichmentRecipe> recipes = new CountingMap<>();
        assertNull(RecipeHandler.getRecipe(new ItemStackInput(ItemStack.EMPTY), recipes));
        assertEquals(0, recipes.reads);
        assertEquals(0, recipes.scans);
    }

    @Test
    void realMachineRetainsHitThroughQuantityEnergyAndOutputChanges() {
        ProbeMachine machine = new ProbeMachine();
        EnrichmentRecipe definition = enrichment(item(8, 2), Items.DIAMOND);
        machine.recipes.put(definition.getInput(), definition);
        machine.getInventorySlot(0).setStack(item(8, 2));
        machine.getInventorySlot(0).shrinkStack(7, Action.EXECUTE);
        EnrichmentRecipe hit = machine.getRecipe();
        assertNotNull(hit, "insufficient quantity is still a recipe hit");
        machine.getInventorySlot(0).growStack(6, Action.EXECUTE);
        machine.setEnergy(0);
        machine.blockOutput();
        assertSame(hit, machine.getRecipe());
        assertEquals(1, machine.recipes.reads);
        RecipeHandler.markRecipeCachesInvalid();
        assertNotSame(hit, machine.getRecipe());
        assertEquals(2, machine.recipes.reads);
    }

    private static ItemStack item(int count, int metadata) { return new ItemStack(Blocks.WOOL, count, metadata); }
    private static GasStack gas(Gas type, int amount) { return new GasStack(type, amount); }
    private static FluidStack water(int amount) { return new FluidStack(FluidRegistry.WATER, amount); }
    private static EnrichmentRecipe enrichment(ItemStack input, net.minecraft.item.Item output) {
        return new EnrichmentRecipe(input, new ItemStack(output));
    }

    private static final class ProbeRecipe<I extends MachineInput<I>> extends MachineRecipe<I, ItemStackOutput, ProbeRecipe<I>> {
        ProbeRecipe(I input) { super(input, new ItemStackOutput(new ItemStack(Items.DIAMOND))); }
        @Override public ProbeRecipe<I> copy() { return new ProbeRecipe<>(getInput().copy()); }
    }

    private static final class CountingMap<I, R> extends HashMap<I, R> {
        int reads;
        int scans;
        @Override public R get(Object key) { reads++; return super.get(key); }
        @Override public java.util.Set<Map.Entry<I, R>> entrySet() { scans++; return super.entrySet(); }
        @Override public java.util.Collection<R> values() { scans++; return super.values(); }
    }

    private static final class ProbeMachine extends TileEntityEnrichmentChamber {
        final CountingMap<ItemStackInput, EnrichmentRecipe> recipes = new CountingMap<>();
        void blockOutput() { outputSlot.setStack(new ItemStack(Items.GOLD_INGOT, 64)); }
        @Override public Map<ItemStackInput, EnrichmentRecipe> getRecipes() { return recipes; }
        @Override public void markDirty() { }
    }
}
