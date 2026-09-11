package mekanism.common.recipe;

import com.google.common.collect.ImmutableList;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseType;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.CommonWorldTickHandler;
import mekanism.common.MekanismFluids;
import mekanism.common.MekanismItems;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.lookup.cache.InputRecipeCache;
import mekanism.common.recipe.machines.*;
import mekanism.common.recipe.outputs.*;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Class used to handle machine recipes. This is used for both adding and fetching recipes.
 *
 * @author AidanBrady, unpairedbracket
 */
public final class RecipeHandler {

    private static int globalRecipeVersion;

    public static <INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>>
    void addRecipe(@Nonnull Recipe<INPUT, OUTPUT, RECIPE> recipeMap, @Nonnull RECIPE recipe) {
        recipeMap.put(recipe);
    }

    public static <INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>>
    void removeRecipe(@Nonnull Recipe<INPUT, OUTPUT, RECIPE> recipeMap, @Nonnull RECIPE recipe) {
        List<INPUT> toRemove = new ArrayList<>();
        recipeMap.get().keySet().forEach(iterInput -> {
            if (iterInput.testEquality(recipe.getInput())) {
                toRemove.add(iterInput);
            }
        });
        toRemove.forEach(iterInput -> recipeMap.get().remove(iterInput));
    }

    public static void markRecipeCachesInvalid() {
        globalRecipeVersion++;
        CommonWorldTickHandler.flushTagAndRecipeCaches = true;
    }

    public static int getGlobalRecipeVersion() {
        return globalRecipeVersion;
    }

    /**
     * Add an Enrichment Chamber recipe.
     *
     * @param input  - input ItemStack
     * @param output - output ItemStack
     */
    public static void addEnrichmentChamberRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.ENRICHMENT_CHAMBER, new EnrichmentRecipe(input, output));
    }

    /**
     * Add an Osmium Compressor recipe.
     *
     * @param input  - input ItemStack
     * @param output - output ItemStack
     */
    public static void addOsmiumCompressorRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.OSMIUM_COMPRESSOR, new OsmiumCompressorRecipe(input, output));
    }

    /**
     * Add a Combiner recipe.
     *
     * @param input  - input ItemStack
     * @param output - output ItemStack
     * @deprecated Replaced by {@link #addCombinerRecipe(ItemStack, ItemStack, ItemStack)}. May be removed with Minecraft 1.13.
     */
    @Deprecated
    public static void addCombinerRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.COMBINER, new CombinerRecipe(input, output));
    }

    /**
     * Add a Combiner recipe.
     *
     * @param input  - input ItemStack
     * @param extra  - extra ItemStack
     * @param output - output ItemStack
     */
    public static void addCombinerRecipe(ItemStack input, ItemStack extra, ItemStack output) {
        addRecipe(Recipe.COMBINER, new CombinerRecipe(input, extra, output));
    }


    /**
     * Add a Crusher recipe.
     *
     * @param input  - input ItemStack
     * @param output - output ItemStack
     */
    public static void addCrusherRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.CRUSHER, new CrusherRecipe(input, output));
    }

    /**
     * Add a Purification Chamber recipe.
     *
     * @param input  - input ItemStack
     * @param output - output ItemStack
     */
    public static void addPurificationChamberRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.PURIFICATION_CHAMBER, new PurificationRecipe(input, output));
    }

    /**
     * Add a Metallurgic Infuser recipe.
     *
     * @param infuse - which Infuse to use
     * @param amount - how much of the Infuse to use
     * @param input  - input ItemStack
     * @param output - output ItemStack
     */
    public static void addMetallurgicInfuserRecipe(InfuseType infuse, int amount, ItemStack input, ItemStack output) {
        addRecipe(Recipe.METALLURGIC_INFUSER, new MetallurgicInfuserRecipe(new InfusionInput(infuse, amount, input), output));
        addRecipe(Recipe.INFUSER_RECIPE, new MetallurgicInfuserRecipe(new InfusionInput(infuse, amount, input), output));
    }

    /**
     * Add a Chemical Infuser recipe.
     *
     * @param leftInput  - left GasStack to input
     * @param rightInput - right GasStack to input
     * @param output     - output GasStack
     */
    public static void addChemicalInfuserRecipe(GasStack leftInput, GasStack rightInput, GasStack output) {
        addRecipe(Recipe.CHEMICAL_INFUSER, new ChemicalInfuserRecipe(leftInput, rightInput, output));
    }

    /**
     * Add a Chemical Oxidizer recipe.
     *
     * @param input  - input ItemStack
     * @param output - output GasStack
     */
    public static void addChemicalOxidizerRecipe(ItemStack input, GasStack output) {
        addRecipe(Recipe.CHEMICAL_OXIDIZER, new OxidationRecipe(input, output));
    }

    /**
     * Add a Chemical Injection Chamber recipe.
     *
     * @param input  - input ItemStack
     * @param output - output ItemStack
     */
    public static void addChemicalInjectionChamberRecipe(ItemStack input, Gas gas, ItemStack output) {
        addRecipe(Recipe.CHEMICAL_INJECTION_CHAMBER, new InjectionRecipe(input, gas, output));
    }

    /**
     * Add an Electrolytic Separator recipe.
     *
     * @param fluid       - FluidStack to electrolyze
     * @param leftOutput  - left gas to produce when the fluid is electrolyzed
     * @param rightOutput - right gas to produce when the fluid is electrolyzed
     */
    public static void addElectrolyticSeparatorRecipe(FluidStack fluid, double energy, GasStack leftOutput, GasStack rightOutput) {
        addRecipe(Recipe.ELECTROLYTIC_SEPARATOR, new SeparatorRecipe(fluid, energy, leftOutput, rightOutput));
    }

    public static void addRotaryRecipe(FluidStack fluidInput, GasStack gasInput, GasStack gasOutput, FluidStack fluidOutput) {
        addRecipe(Recipe.ROTARY_CONDENSENTRATOR, new RotaryRecipe(fluidInput, gasInput, gasOutput, fluidOutput));
    }

    public static void addDefaultRotaryRecipes() {
        GasRegistry.getRegisteredGasses().forEach(gas -> {
            if (gas.hasFluid()) {
                addRotaryRecipe(new FluidStack(gas.getFluid(), 1), new GasStack(gas, 1), new GasStack(gas, 1), new FluidStack(gas.getFluid(), 1));
            }
        });
    }

    /**
     * Add a Precision Sawmill recipe.
     *
     * @param input           - input ItemStack
     * @param primaryOutput   - guaranteed output
     * @param secondaryOutput - possible extra output
     * @param chance          - probability of obtaining extra output
     */
    public static void addPrecisionSawmillRecipe(ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        addRecipe(Recipe.PRECISION_SAWMILL, new SawmillRecipe(input, primaryOutput, secondaryOutput, chance));
    }

    /**
     * Add a Precision Sawmill recipe with no chance output
     *
     * @param input         - input ItemStack
     * @param primaryOutput - guaranteed output
     */
    public static void addPrecisionSawmillRecipe(ItemStack input, ItemStack primaryOutput) {
        addRecipe(Recipe.PRECISION_SAWMILL, new SawmillRecipe(input, primaryOutput));
    }

    /**
     * Add a Chemical Dissolution Chamber recipe.
     *
     * @param input  - input ItemStack
     * @param output - output GasStack
     */
    public static void addChemicalDissolutionChamberRecipe(ItemStack input, GasStack output) {
        addRecipe(Recipe.CHEMICAL_DISSOLUTION_CHAMBER, new DissolutionRecipe(input, output));
    }

    /**
     * Add a Chemical Washer recipe.
     *
     * @param input  - input GasStack
     * @param output - output GasStack
     */
    public static void addChemicalWasherRecipe(GasStack input, GasStack output) {
        addRecipe(Recipe.CHEMICAL_WASHER, new WasherRecipe(input, output));
    }

    public static void addChemicalWasherRecipe(GasStack input, FluidStack stack, GasStack output) {
        addRecipe(Recipe.CHEMICAL_WASHER, new WasherRecipe(input, stack, output));
    }


    /**
     * Add a Chemical Crystallizer recipe.
     *
     * @param input  - input GasStack
     * @param output - output ItemStack
     */
    public static void addChemicalCrystallizerRecipe(GasStack input, ItemStack output) {
        addRecipe(Recipe.CHEMICAL_CRYSTALLIZER, new CrystallizerRecipe(input, output));
    }

    /**
     * Add a Pressurized Reaction Chamber recipe.
     *
     * @param inputSolid  - input ItemStack
     * @param inputFluid  - input FluidStack
     * @param inputGas    - input GasStack
     * @param outputSolid - output ItemStack
     * @param outputGas   - output GasStack
     * @param extraEnergy - extra energy needed by the recipe
     * @param ticks       - amount of ticks it takes for this recipe to complete
     */
    public static void addPRCRecipe(ItemStack inputSolid, FluidStack inputFluid, GasStack inputGas, ItemStack outputSolid, GasStack outputGas, double extraEnergy, int ticks) {
        addRecipe(Recipe.PRESSURIZED_REACTION_CHAMBER, new PressurizedRecipe(inputSolid, inputFluid, inputGas, outputSolid, outputGas, extraEnergy, ticks));
    }

    public static void addThermalEvaporationRecipe(FluidStack inputFluid, FluidStack outputFluid) {
        addRecipe(Recipe.THERMAL_EVAPORATION_PLANT, new ThermalEvaporationRecipe(inputFluid, outputFluid));
    }

    public static void addSolarNeutronRecipe(GasStack inputGas, GasStack outputGas) {
        addRecipe(Recipe.SOLAR_NEUTRON_ACTIVATOR, new SolarNeutronRecipe(inputGas, outputGas));
    }

    public static void addAmbientGas(int dimensionID) {
        addAmbientGas(dimensionID, new GasStack(MekanismFluids.UnstableDimensional, 1), 1F / 5F);
    }

    public static void addAmbientGas(int dimensionID, GasStack outputGas, double chance) {
        addRecipe(Recipe.AMBIENT_ACCUMULATOR, new AmbientGasRecipe(dimensionID, outputGas, chance));
        addRecipe(Recipe.AMBIENT_ACCUMULATOR_ENERGY, new AmbientGasRecipe(dimensionID, outputGas, chance));
    }


    /**
     * Add Start
     */

    public static void addIsotopicRecipe(GasStack inputGas, GasStack outputGas) {
        addRecipe(Recipe.ISOTOPIC_CENTRIFUGE, new IsotopicRecipe(inputGas, outputGas));
    }

    /**
     * Add a Nutritional Liquifier recipe.
     *
     * @param input  - input ItemStack
     * @param output - output GasStack
     */
    public static void addNutritionalLiquifierRecipe(ItemStack input, GasStack output) {
        addRecipe(Recipe.NUTRITIONAL_LIQUIFIER, new NutritionalRecipe(input, output));
    }

    public static void addOrganicFarmRecipe(ItemStack input, Gas gas, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        addRecipe(Recipe.ORGANIC_FARM, new FarmRecipe(input, gas, primaryOutput, secondaryOutput, chance));
    }


    public static void addOrganicFarmRecipe(ItemStack input, Gas gas, ItemStack primaryOutput) {
        addRecipe(Recipe.ORGANIC_FARM, new FarmRecipe(input, gas, primaryOutput));
    }

    public static void addOrganicFarmRecipe(ItemStack input, Gas gas, ItemStack primaryOutput, List<FarmChanceOutput> chanceOutputs) {
        addRecipe(Recipe.ORGANIC_FARM, new FarmRecipe(input, new GasStack(gas, 1), primaryOutput, chanceOutputs));
    }

    public static void addOrganicFarmRecipe(ItemStack input, FluidStack fluid, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        addRecipe(Recipe.ORGANIC_FARM, new FarmRecipe(new FarmInput(input, fluid), new FarmOutput(primaryOutput, secondaryOutput, chance)));
    }

    public static void addOrganicFarmRecipe(ItemStack input, FluidStack fluid, ItemStack primaryOutput) {
        addRecipe(Recipe.ORGANIC_FARM, new FarmRecipe(new FarmInput(input, fluid), new FarmOutput(primaryOutput)));
    }

    public static void addOrganicFarmRecipe(ItemStack input, FluidStack fluid, ItemStack primaryOutput, List<FarmChanceOutput> chanceOutputs) {
        addRecipe(Recipe.ORGANIC_FARM, new FarmRecipe(input, fluid, primaryOutput, chanceOutputs));
    }


    public static void addNucleosynthesizerRecipe(ItemStack inputSolid, GasStack inputGas, ItemStack outputSolid, double extraEnergy, int ticks) {
        addRecipe(Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER, new NucleosynthesizerRecipe(inputSolid, inputGas, outputSolid, extraEnergy, ticks));
    }

    public static void addStampingRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.STAMPING, new StampingRecipe(input, output));
    }

    public static void addRollingRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.ROLLING, new RollingRecipe(input, output));
    }

    public static void addBrushedRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.BRUSHED, new BrushedRecipe(input, output));
    }

    public static void addTurningRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.TURNING, new TurningRecipe(input, output));
    }

    public static void addAlloyRecipe(ItemStack input, ItemStack extra, ItemStack output) {
        addRecipe(Recipe.ALLOY, new AlloyRecipe(input, extra, output));
    }

    public static void addCellExtractorRecipe(ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        addRecipe(Recipe.CELL_EXTRACTOR, new CellExtractorRecipe(input, primaryOutput, secondaryOutput, chance));
    }

    public static void addCellExtractorRecipe(ItemStack input, ItemStack primaryOutput) {
        addRecipe(Recipe.CELL_EXTRACTOR, new CellExtractorRecipe(input, primaryOutput));
    }

    public static void addCellSeparatorRecipe(ItemStack input, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        addRecipe(Recipe.CELL_SEPARATOR, new CellSeparatorRecipe(input, primaryOutput, secondaryOutput, chance));
    }

    public static void addCellSeparatorRecipe(ItemStack input, ItemStack primaryOutput) {
        addRecipe(Recipe.CELL_SEPARATOR, new CellSeparatorRecipe(input, primaryOutput));
    }

    public static void addRecyclerRecipe(ItemStack input) {
        addRecyclerRecipe(input, new ItemStack(MekanismItems.Scrap, 1), 1F / 6F);
    }

    public static void addRecyclerRecipe(ItemStack input, double chance) {
        addRecyclerRecipe(input, new ItemStack(MekanismItems.Scrap, 1), chance);
    }

    public static void addRecyclerRecipe(ItemStack input, ItemStack primaryOutput, double chance) {
        addRecipe(Recipe.RECYCLER, new RecyclerRecipe(input, primaryOutput, chance));
    }

    public static void addSmeltingRecipe(ItemStack input, ItemStack output) {
        addRecipe(Recipe.ENERGIZED_SMELTER, new SmeltingRecipe(input, output));
    }

    public static void addFusionCoolingRecipe(FluidStack inputFluid, FluidStack outputFluid) {
        addRecipe(Recipe.FUSION_COOLING, new FusionCoolingRecipe(inputFluid, outputFluid));
    }

    public static void addFusionCoolingRecipe(FluidStack inputFluid, FluidStack outputFluid, double energy) {
        addRecipe(Recipe.FUSION_COOLING, new FusionCoolingRecipe(inputFluid, outputFluid, energy));
    }


    public static void addItemStackToEnergyRecipe(ItemStack input, double outputEnergy) {
        addRecipe(Recipe.ENERGY_RECIPE, new ItemStackToEnergyRecipe(input, outputEnergy));
    }

    public static void addGasStackFuelToEnergyRecipe(GasStack input, double outputEnergy) {
        if (outputEnergy <= 0) {
            outputEnergy = MekanismConfig.current().general.FROM_H2.val();
        }
        double energyDensity = outputEnergy / input.amount;
        addRecipe(Recipe.GAS_FUEL_TO_ENERGY_RECIPE, new GasStackFuelToEnergyRecipe(input, energyDensity));
    }


    public static void addItemReplicatorRecipe(ItemStack input, GasStack uu, double extraEnergy, int ticks) {
        addRecipe(Recipe.REPLICATOR_ITEMSTACK_RECIPE, new ReplicatorItemStackRecipe(input, uu, input, extraEnergy, ticks));
    }

    public static void addGasReplicatorRecipe(GasStack input, GasStack uu, double extraEnergy, int ticks) {
        if (input.isGasEqual(uu)) {
            return;
        }
        addRecipe(Recipe.REPLICATOR_GASES_RECIPE, new ReplicatorGasStackRecipe(input, uu, input, extraEnergy, ticks));
    }

    public static void addFluidReplicatorRecipe(FluidStack input, GasStack uu, double extraEnergy, int ticks) {
        addRecipe(Recipe.REPLICATOR_FLUIDSTACK_RECIPE, new ReplicatorFluidStackRecipe(input, uu, input, extraEnergy, ticks));
    }

    /**
     * Add End
     */


    /**
     * Gets the Metallurgic Infuser Recipe for the InfusionInput in the parameters.
     *
     * @param input - input Infusion
     * @return MetallurgicInfuserRecipe
     */
    @Nullable
    public static MetallurgicInfuserRecipe getMetallurgicInfuserRecipe(@Nonnull InfusionInput input) {
        return getRecipe(input, Recipe.METALLURGIC_INFUSER);
    }

    /**
     * Gets the Chemical Infuser Recipe of the ChemicalPairInput in the parameters.
     *
     * @param input - the pair of gases to infuse
     * @return ChemicalInfuserRecipe
     */
    @Nullable
    public static ChemicalInfuserRecipe getChemicalInfuserRecipe(@Nonnull ChemicalPairInput input) {
        return getRecipe(input, Recipe.CHEMICAL_INFUSER);
    }

    /**
     * Gets the Chemical Crystallizer Recipe for the defined Gas input.
     *
     * @param input - GasInput
     * @return CrystallizerRecipe
     */
    @Nullable
    public static CrystallizerRecipe getChemicalCrystallizerRecipe(@Nonnull GasInput input) {
        return getRecipe(input, Recipe.CHEMICAL_CRYSTALLIZER);
    }

    /**
     * Gets the Chemical Washer Recipe for the defined Gas input.
     *
     * @param input - GasInput
     * @return WasherRecipe
     */
    @Nullable
    public static WasherRecipe getChemicalWasherRecipe(@Nonnull GasAndFluidInput input) {
        return getRecipe(input, Recipe.CHEMICAL_WASHER);
    }

    /**
     * Gets the Chemical Dissolution Chamber of the ItemStackInput in the parameters
     *
     * @param input - ItemStackInput
     * @return DissolutionRecipe
     */
    @Nullable
    public static DissolutionRecipe getDissolutionRecipe(@Nonnull ItemStackInput input) {
        return getRecipe(input, Recipe.CHEMICAL_DISSOLUTION_CHAMBER);
    }

    /**
     * Gets the Chemical Oxidizer Recipe for the ItemStackInput in the parameters.
     *
     * @param input - ItemStackInput
     * @return OxidationRecipe
     */
    @Nullable
    public static OxidationRecipe getOxidizerRecipe(@Nonnull ItemStackInput input) {
        return getRecipe(input, Recipe.CHEMICAL_OXIDIZER);
    }

    /**
     * Gets the ChanceMachineRecipe of the ItemStackInput in the parameters, using the map in the parameters.
     *
     * @param input   - ItemStackInput
     * @param recipes - Map of recipes
     * @return ChanceRecipe
     */
    @Nullable
    public static <RECIPE extends ChanceMachineRecipe<RECIPE>> RECIPE getChanceRecipe(@Nonnull ItemStackInput input, @Nonnull Map<ItemStackInput, RECIPE> recipes) {
        return getRecipe(input, recipes);
    }


    @Nullable
    public static <RECIPE extends FarmMachineRecipe<RECIPE>> RECIPE getFarmRecipe(@Nonnull FarmInput input, @Nonnull Map<FarmInput, RECIPE> recipes) {
        return getRecipe(input, recipes);
    }


    /**
     * Gets the Recipe of the given Input in the parameters, using the map in the parameters.
     *
     * @param input   - Input
     * @param recipes - Map of recipes
     * @return Recipe
     */
    @Nullable
    public static <INPUT extends MachineInput<INPUT>, RECIPE extends MachineRecipe<INPUT, ?, RECIPE>>
    RECIPE getRecipe(@Nonnull INPUT input, @Nonnull Map<INPUT, RECIPE> recipes) {
        if (recipes instanceof Recipe.IndexedRecipeMap) {
            @SuppressWarnings("unchecked")
            RECIPE indexed = (RECIPE) ((Recipe.IndexedRecipeMap) recipes).findIndexed(input);
            return indexed;
        }
        if (input.isValid()) {
            RECIPE recipe = recipes.get(input);
            if (recipe == null && input instanceof IWildInput) {
                //noinspection unchecked
                IWildInput<INPUT> wildInput = (IWildInput<INPUT>) input;
                recipe = recipes.get(wildInput.wildCopy());
            }
            return recipe == null ? null : recipe.copy();
        }
        return null;
    }

    @Nullable
    public static <INPUT extends MachineInput<INPUT>, RECIPE extends MachineRecipe<INPUT, ?, RECIPE>>
    RECIPE getRecipe(@Nonnull INPUT input, @Nonnull Recipe<INPUT, ?, RECIPE> type) {
        return type.findRecipe(input);
    }

    /**
     * Get the Electrolytic Separator Recipe corresponding to electrolysing a given fluid.
     *
     * @param input - the FluidInput to electrolyse fluid from
     * @return SeparatorRecipe
     */
    @Nullable
    public static SeparatorRecipe getElectrolyticSeparatorRecipe(@Nonnull FluidInput input) {
        return getRecipe(input, Recipe.ELECTROLYTIC_SEPARATOR);
    }

    @Nullable
    public static RotaryRecipe getRotaryRecipe(@Nullable GasStack input) {
        if (input == null || input.amount <= 0) {
            return null;
        }
        for (RotaryRecipe recipe : Recipe.ROTARY_CONDENSENTRATOR.get().values()) {
            if (recipe.test(input)) {
                return recipe.copy();
            }
        }
        return null;
    }

    @Nullable
    public static RotaryRecipe getRotaryRecipe(@Nullable FluidStack input) {
        if (input == null || input.amount <= 0) {
            return null;
        }
        for (RotaryRecipe recipe : Recipe.ROTARY_CONDENSENTRATOR.get().values()) {
            if (recipe.test(input)) {
                return recipe.copy();
            }
        }
        return null;
    }

    public static boolean isRotaryGasValid(@Nullable GasStack gas) {
        if (gas == null || gas.amount <= 0) {
            return false;
        }
        for (RotaryRecipe recipe : Recipe.ROTARY_CONDENSENTRATOR.get().values()) {
            RotaryInput input = recipe.getInput();
            RotaryOutput output = recipe.getOutput();
            if (input.containsType(gas) || output.gasOutput != null && output.gasOutput.isGasEqual(gas)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRotaryFluidValid(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) {
            return false;
        }
        for (RotaryRecipe recipe : Recipe.ROTARY_CONDENSENTRATOR.get().values()) {
            RotaryInput input = recipe.getInput();
            RotaryOutput output = recipe.getOutput();
            if (input.containsType(fluid) || output.fluidOutput != null && output.fluidOutput.isFluidEqual(fluid)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static ThermalEvaporationRecipe getThermalEvaporationRecipe(@Nonnull FluidInput input) {
        return getRecipe(input, Recipe.THERMAL_EVAPORATION_PLANT);
    }

    @Nullable
    public static SolarNeutronRecipe getSolarNeutronRecipe(@Nonnull GasInput input) {
        return getRecipe(input, Recipe.SOLAR_NEUTRON_ACTIVATOR);
    }

    @Nullable
    public static PressurizedRecipe getPRCRecipe(@Nonnull PressurizedInput input) {
        return getRecipe(input, Recipe.PRESSURIZED_REACTION_CHAMBER);
    }

    @Nullable
    public static AmbientGasRecipe getDimensionGas(IntegerInput input) {
        return getRecipe(input, Recipe.AMBIENT_ACCUMULATOR);
    }

    /**
     * Add Start
     */

    @Nullable
    public static IsotopicRecipe getIsotopicRecipe(@Nonnull GasInput input) {
        return getRecipe(input, Recipe.ISOTOPIC_CENTRIFUGE);
    }

    @Nullable
    public static NucleosynthesizerRecipe getNucleosynthesizerRecipe(@Nonnull NucleosynthesizerInput input) {
        return getRecipe(input, Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER);
    }


    /**
     * Gets the Nutritional Liquifier Recipe for the ItemStackInput in the parameters.
     *
     * @param input - ItemStackInput
     * @return NutritionalRecipe
     */
    @Nullable
    public static NutritionalRecipe getNutritionalRecipe(@Nonnull ItemStackInput input) {
        return getRecipe(input, Recipe.NUTRITIONAL_LIQUIFIER);
    }

    @Nullable
    public static FusionCoolingRecipe getFusionCoolingRecipe(@Nonnull FluidInput input) {
        return getRecipe(input, Recipe.FUSION_COOLING);
    }


    @Nullable
    public static <RECIPE extends Chance2MachineRecipe<RECIPE>> RECIPE getChance2Recipe(@Nonnull ItemStackInput input, @Nonnull Map<ItemStackInput, RECIPE> recipes) {
        return getRecipe(input, recipes);
    }

    @Nullable
    public static ItemStackToEnergyRecipe getItemStackToEnergyRecipe(@Nonnull ItemStack input) {
        return getItemStackToEnergyRecipe(new ItemStackInput(input));
    }

    @Nullable
    public static ItemStackToEnergyRecipe getItemStackToEnergyRecipe(@Nonnull ItemStackInput input) {
        return getRecipe(input, Recipe.ENERGY_RECIPE);
    }

    @Nullable
    public static MetallurgicInfuserRecipe getInfuserRecipe(@Nonnull InfusionInput input) {
        return getRecipe(input, Recipe.INFUSER_RECIPE);
    }


    @Nullable
    public static GasStackFuelToEnergyRecipe getGasStackFuelToEnergyRecipe(@Nonnull GasStack input) {
        return getGasStackFuelToEnergyRecipe(new GasInput(input));
    }

    @Nullable
    public static GasStackFuelToEnergyRecipe getGasStackFuelToEnergyRecipe(@Nonnull GasInput input) {
        return getRecipe(input, Recipe.GAS_FUEL_TO_ENERGY_RECIPE);
    }

    @Nullable
    public static ReplicatorItemStackRecipe getReplicatorItemStackRecipe(@Nonnull NucleosynthesizerInput input) {
        return getRecipe(input, Recipe.REPLICATOR_ITEMSTACK_RECIPE);
    }

    @Nullable
    public static ReplicatorGasStackRecipe getReplicatorGasStackRecipe(@Nonnull ChemicalGasInput input) {
        return getRecipe(input, Recipe.REPLICATOR_GASES_RECIPE);
    }

    @Nullable
    public static ReplicatorFluidStackRecipe getReplicatorFluidStackRecipe(@Nonnull GasAndFluidInput input) {
        return getRecipe(input, Recipe.REPLICATOR_FLUIDSTACK_RECIPE);
    }


    /**
     * Gets the whether the input ItemStack is in a recipe
     *
     * @param itemstack - input ItemStack
     * @param recipes   - Map of recipes
     * @return whether the item can be used in a recipe
     */
    public static <RECIPE extends MachineRecipe<ItemStackInput, ?, RECIPE>> boolean isInRecipe(@Nonnull ItemStack itemstack, @Nonnull Map<ItemStackInput, RECIPE> recipes) {
        if (!itemstack.isEmpty()) {
            for (RECIPE recipe : recipes.values()) {
                ItemStackInput required = recipe.getInput();
                if (MachineInput.inputContains(itemstack, required.ingredient)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInPressurizedRecipe(@Nonnull ItemStack stack) {
        if (!stack.isEmpty()) {
            for (PressurizedInput key : Recipe.PRESSURIZED_REACTION_CHAMBER.get().keySet()) {
                if (key.containsType(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInNucleosynthesizerRecipe(@Nonnull ItemStack stack) {
        if (!stack.isEmpty()) {
            for (NucleosynthesizerInput key : Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.get().keySet()) {
                if (key.containsType(stack)) {
                    return true;
                }
            }
        }
        return false;
    }


    public static class Recipe<INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>> {

        private static List<Recipe<?, ?, ?>> values = new ArrayList<>();

        public static final Recipe<ItemStackInput, ItemStackOutput, SmeltingRecipe> ENERGIZED_SMELTER = new Recipe<>(
                MachineType.ENERGIZED_SMELTER, ItemStackInput.class, ItemStackOutput.class, SmeltingRecipe.class);

        public static final Recipe<ItemStackInput, ItemStackOutput, EnrichmentRecipe> ENRICHMENT_CHAMBER = new Recipe<>(
                MachineType.ENRICHMENT_CHAMBER, ItemStackInput.class, ItemStackOutput.class, EnrichmentRecipe.class);

        public static final Recipe<AdvancedMachineInput, ItemStackOutput, OsmiumCompressorRecipe> OSMIUM_COMPRESSOR = new Recipe<>(
                MachineType.OSMIUM_COMPRESSOR, AdvancedMachineInput.class, ItemStackOutput.class, OsmiumCompressorRecipe.class);

        public static final Recipe<DoubleMachineInput, ItemStackOutput, CombinerRecipe> COMBINER = new Recipe<>(
                MachineType.COMBINER, DoubleMachineInput.class, ItemStackOutput.class, CombinerRecipe.class);

        public static final Recipe<ItemStackInput, ItemStackOutput, CrusherRecipe> CRUSHER = new Recipe<>(
                MachineType.CRUSHER, ItemStackInput.class, ItemStackOutput.class, CrusherRecipe.class);

        public static final Recipe<AdvancedMachineInput, ItemStackOutput, PurificationRecipe> PURIFICATION_CHAMBER = new Recipe<>(
                MachineType.PURIFICATION_CHAMBER, AdvancedMachineInput.class, ItemStackOutput.class, PurificationRecipe.class);

        public static final Recipe<InfusionInput, ItemStackOutput, MetallurgicInfuserRecipe> METALLURGIC_INFUSER = new Recipe<>(
                MachineType.METALLURGIC_INFUSER, InfusionInput.class, ItemStackOutput.class, MetallurgicInfuserRecipe.class);

        public static final Recipe<ChemicalPairInput, GasOutput, ChemicalInfuserRecipe> CHEMICAL_INFUSER = new Recipe<>(
                MachineType.CHEMICAL_INFUSER, ChemicalPairInput.class, GasOutput.class, ChemicalInfuserRecipe.class);

        public static final Recipe<ItemStackInput, GasOutput, OxidationRecipe> CHEMICAL_OXIDIZER = new Recipe<>(
                MachineType.CHEMICAL_OXIDIZER, ItemStackInput.class, GasOutput.class, OxidationRecipe.class);

        public static final Recipe<AdvancedMachineInput, ItemStackOutput, InjectionRecipe> CHEMICAL_INJECTION_CHAMBER = new Recipe<>(
                MachineType.CHEMICAL_INJECTION_CHAMBER, AdvancedMachineInput.class, ItemStackOutput.class, InjectionRecipe.class);

        public static final Recipe<FluidInput, ChemicalPairOutput, SeparatorRecipe> ELECTROLYTIC_SEPARATOR = new Recipe<>(
                MachineType.ELECTROLYTIC_SEPARATOR, FluidInput.class, ChemicalPairOutput.class, SeparatorRecipe.class);

        public static final Recipe<RotaryInput, RotaryOutput, RotaryRecipe> ROTARY_CONDENSENTRATOR = new Recipe<>(
                MachineType.ROTARY_CONDENSENTRATOR, RotaryInput.class, RotaryOutput.class, RotaryRecipe.class);

        public static final Recipe<ItemStackInput, ChanceOutput, SawmillRecipe> PRECISION_SAWMILL = new Recipe<>(
                MachineType.PRECISION_SAWMILL, ItemStackInput.class, ChanceOutput.class, SawmillRecipe.class);

        public static final Recipe<ItemStackInput, GasOutput, DissolutionRecipe> CHEMICAL_DISSOLUTION_CHAMBER = new Recipe<>(
                MachineType.CHEMICAL_DISSOLUTION_CHAMBER, ItemStackInput.class, GasOutput.class, DissolutionRecipe.class);

        public static final Recipe<GasAndFluidInput, GasOutput, WasherRecipe> CHEMICAL_WASHER = new Recipe<>(
                MachineType.CHEMICAL_WASHER, GasAndFluidInput.class, GasOutput.class, WasherRecipe.class);

        public static final Recipe<GasInput, ItemStackOutput, CrystallizerRecipe> CHEMICAL_CRYSTALLIZER = new Recipe<>(
                MachineType.CHEMICAL_CRYSTALLIZER, GasInput.class, ItemStackOutput.class, CrystallizerRecipe.class);

        public static final Recipe<PressurizedInput, PressurizedOutput, PressurizedRecipe> PRESSURIZED_REACTION_CHAMBER = new Recipe<>(
                MachineType.PRESSURIZED_REACTION_CHAMBER, PressurizedInput.class, PressurizedOutput.class, PressurizedRecipe.class);

        public static final Recipe<IntegerInput, ChanceGasOutput, AmbientGasRecipe> AMBIENT_ACCUMULATOR = new Recipe<>(
                MachineType.AMBIENT_ACCUMULATOR, IntegerInput.class, ChanceGasOutput.class, AmbientGasRecipe.class);


        public static final Recipe<IntegerInput, ChanceGasOutput, AmbientGasRecipe> AMBIENT_ACCUMULATOR_ENERGY = new Recipe<>(
                MachineType.AMBIENT_ACCUMULATOR_ENERGY, IntegerInput.class, ChanceGasOutput.class, AmbientGasRecipe.class);


        public static final Recipe<FluidInput, FluidOutput, ThermalEvaporationRecipe> THERMAL_EVAPORATION_PLANT = new Recipe<>(
                "ThermalEvaporationPlant", FluidInput.class, FluidOutput.class, ThermalEvaporationRecipe.class);

        public static final Recipe<GasInput, GasOutput, SolarNeutronRecipe> SOLAR_NEUTRON_ACTIVATOR = new Recipe<>(
                MachineType.SOLAR_NEUTRON_ACTIVATOR, GasInput.class, GasOutput.class, SolarNeutronRecipe.class);

        /**
         * Add Start
         */

        public static final Recipe<GasInput, GasOutput, IsotopicRecipe> ISOTOPIC_CENTRIFUGE = new Recipe<>(
                MachineType.ISOTOPIC_CENTRIFUGE, GasInput.class, GasOutput.class, IsotopicRecipe.class);

        public static final Recipe<ItemStackInput, GasOutput, NutritionalRecipe> NUTRITIONAL_LIQUIFIER = new Recipe<>(
                MachineType.NUTRITIONAL_LIQUIFIER, ItemStackInput.class, GasOutput.class, NutritionalRecipe.class);

        public static final Recipe<FarmInput, FarmOutput, FarmRecipe> ORGANIC_FARM = new Recipe<>(
                MachineType.ORGANIC_FARM, FarmInput.class, FarmOutput.class, FarmRecipe.class);

        public static final Recipe<NucleosynthesizerInput, ItemStackOutput, NucleosynthesizerRecipe> ANTIPROTONIC_NUCLEOSYNTHESIZER = new Recipe<>(
                MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, NucleosynthesizerInput.class, ItemStackOutput.class, NucleosynthesizerRecipe.class);

        public static final Recipe<ItemStackInput, ItemStackOutput, StampingRecipe> STAMPING = new Recipe<>(
                MachineType.STAMPING, ItemStackInput.class, ItemStackOutput.class, StampingRecipe.class);

        public static final Recipe<ItemStackInput, ItemStackOutput, RollingRecipe> ROLLING = new Recipe<>(
                MachineType.ROLLING, ItemStackInput.class, ItemStackOutput.class, RollingRecipe.class);

        public static final Recipe<ItemStackInput, ItemStackOutput, BrushedRecipe> BRUSHED = new Recipe<>(
                MachineType.BRUSHED, ItemStackInput.class, ItemStackOutput.class, BrushedRecipe.class);

        public static final Recipe<ItemStackInput, ItemStackOutput, TurningRecipe> TURNING = new Recipe<>(
                MachineType.TURNING, ItemStackInput.class, ItemStackOutput.class, TurningRecipe.class);

        public static final Recipe<DoubleMachineInput, ItemStackOutput, AlloyRecipe> ALLOY = new Recipe<>(
                MachineType.ALLOY, DoubleMachineInput.class, ItemStackOutput.class, AlloyRecipe.class);

        public static final Recipe<ItemStackInput, ChanceOutput, CellExtractorRecipe> CELL_EXTRACTOR = new Recipe<>(
                MachineType.CELL_EXTRACTOR, ItemStackInput.class, ChanceOutput.class, CellExtractorRecipe.class);

        public static final Recipe<ItemStackInput, ChanceOutput, CellSeparatorRecipe> CELL_SEPARATOR = new Recipe<>(
                MachineType.CELL_SEPARATOR, ItemStackInput.class, ChanceOutput.class, CellSeparatorRecipe.class);

        public static final Recipe<ItemStackInput, ChanceOutput2, RecyclerRecipe> RECYCLER = new Recipe<>(
                MachineType.RECYCLER, ItemStackInput.class, ChanceOutput2.class, RecyclerRecipe.class);

        public static final Recipe<FluidInput, FluidOutput, FusionCoolingRecipe> FUSION_COOLING = new Recipe<>(
                "FusionCooling", FluidInput.class, FluidOutput.class, FusionCoolingRecipe.class);


        public static final Recipe<ItemStackInput, EnergyOutput, ItemStackToEnergyRecipe> ENERGY_RECIPE = new Recipe<>("ItemStackToEnergy", ItemStackInput.class, EnergyOutput.class, ItemStackToEnergyRecipe.class);
        //TODO
        public static final Recipe<InfusionInput, ItemStackOutput, MetallurgicInfuserRecipe> INFUSER_RECIPE = new Recipe<>("ItemStackToInfuseType", InfusionInput.class, ItemStackOutput.class, MetallurgicInfuserRecipe.class);
        public static final Recipe<GasInput, EnergyOutput, GasStackFuelToEnergyRecipe> GAS_FUEL_TO_ENERGY_RECIPE = new Recipe<>("GasFlueStackToEnergy", GasInput.class, EnergyOutput.class, GasStackFuelToEnergyRecipe.class);


        public static final Recipe<NucleosynthesizerInput, ItemStackOutput, ReplicatorItemStackRecipe> REPLICATOR_ITEMSTACK_RECIPE = new Recipe<>("ReplicatorItemStackRecipe", NucleosynthesizerInput.class, ItemStackOutput.class, ReplicatorItemStackRecipe.class);

        public static final Recipe<ChemicalGasInput, GasOutput, ReplicatorGasStackRecipe> REPLICATOR_GASES_RECIPE = new Recipe<>("ReplicatorGasesRecipe", ChemicalGasInput.class, GasOutput.class, ReplicatorGasStackRecipe.class);

        public static final Recipe<GasAndFluidInput, FluidOutput, ReplicatorFluidStackRecipe> REPLICATOR_FLUIDSTACK_RECIPE = new Recipe<>("ReplicatorFluidStackRecipe", GasAndFluidInput.class, FluidOutput.class, ReplicatorFluidStackRecipe.class);

        /**
         * ADD END
         */
        static {
            values = ImmutableList.copyOf(values);
        }

        public static Iterable<Recipe<?, ?, ?>> values() {
            return values;
        }

        private final HashMap<INPUT, RECIPE> recipes = new RecipeMap();
        private final InputRecipeCache<INPUT, RECIPE> inputCache = new InputRecipeCache<>();
        private final String recipeName;
        @Nonnull
        private final String jeiCategory;

        private Class<INPUT> inputClass;
        private Class<OUTPUT> outputClass;
        private Class<RECIPE> recipeClass;
        private int recipeVersion;

        private Recipe(MachineType type, Class<INPUT> input, Class<OUTPUT> output, Class<RECIPE> recipe) {
            this(type.getBlockName(), input, output, recipe);
        }

        private Recipe(String name, Class<INPUT> input, Class<OUTPUT> output, Class<RECIPE> recipe) {
            recipeName = name;
            jeiCategory = "mekanism." + recipeName.toLowerCase(Locale.ROOT);

            inputClass = input;
            outputClass = output;
            recipeClass = recipe;

            values.add(this);
        }

        public void put(@Nonnull RECIPE recipe) {
            recipes.put(recipe.getInput(), recipe);
        }

        /** High-version-style lazy input cache for this recipe category. */
        @Nullable
        public RECIPE findRecipe(@Nonnull INPUT input) {
            return inputCache.findFirstRecipe(input, recipes.values());
        }

        @Nonnull
        public List<RECIPE> getCachedRecipes() {
            return inputCache.getRecipes(recipes.values());
        }

        @Nonnull
        public InputRecipeCache<INPUT, RECIPE> getInputCache() {
            return inputCache;
        }

        public void remove(@Nonnull RECIPE recipe) {
            recipes.remove(recipe.getInput());
        }

        public int getRecipeVersion() {
            return recipeVersion;
        }

        private void onRecipesChanged() {
            recipeVersion++;
            inputCache.clear();
            markRecipeCachesInvalid();
        }

        public String getRecipeName() {
            return recipeName;
        }

        @Nonnull
        public String getJEICategory() {
            return jeiCategory;
        }

        @Nullable
        public INPUT createInput(NBTTagCompound nbtTags) {
            try {
                INPUT input = inputClass.newInstance();
                input.load(nbtTags);
                return input;
            } catch (Exception e) {
                return null;
            }
        }

        @Nullable
        public RECIPE createRecipe(INPUT input, NBTTagCompound nbtTags) {
            try {
                OUTPUT output = outputClass.newInstance();
                output.load(nbtTags);
                try {
                    Constructor<RECIPE> construct = recipeClass.getDeclaredConstructor(inputClass, outputClass);
                    return construct.newInstance(input, output);
                } catch (Exception e) {
                    Constructor<RECIPE> construct = recipeClass.getDeclaredConstructor(inputClass, outputClass, NBTTagCompound.class);
                    return construct.newInstance(input, output, nbtTags);
                }
            } catch (Exception e) {
                return null;
            }
        }

        public boolean containsRecipe(ItemStack input) {
            //TODO: Support other input types
            for (Entry<INPUT, RECIPE> entry : recipes.entrySet()) {
                if (entry.getKey() instanceof ItemStackInput itemStackInput) {
                    ItemStack stack = itemStackInput.ingredient;
                    if (StackUtils.equalsWildcard(stack, input)) {
                        return true;
                    }
                } else if (entry.getKey() instanceof FarmInput farmInput) {
                    if (StackUtils.equalsWildcard(farmInput.itemStack, input)) {
                        return true;
                    }
                } else if (entry.getKey() instanceof FluidInput fluidInput) {
                    if (fluidInput.ingredient.isFluidEqual(input)) {
                        return true;
                    }
                } else if (entry.getKey() instanceof AdvancedMachineInput advancedMachineInput) {
                    ItemStack stack = advancedMachineInput.itemStack;
                    if (StackUtils.equalsWildcard(stack, input)) {
                        return true;
                    }
                } else if (entry.getKey() instanceof GasAndFluidInput gasAndFluidInput) {
                    if (gasAndFluidInput.ingredientFluid.isFluidEqual(input)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean containsRecipe(Fluid input) {
            //TODO: Support other input types
            for (Entry<INPUT, RECIPE> entry : recipes.entrySet()) {
                if (entry.getKey() instanceof FluidInput fluidInput) {
                    if (fluidInput.ingredient.getFluid() == input) {
                        return true;
                    }
                } else if (entry.getKey() instanceof FarmInput farmInput) {
                    if (farmInput.isFluidInput() && farmInput.fluidInput.getFluid() == input) {
                        return true;
                    }
                } else if (entry.getKey() instanceof GasAndFluidInput gasAndFluidInput) {
                    if (gasAndFluidInput.ingredientFluid.getFluid() == input) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean containsRecipe(Gas input) {
            //TODO: Support other input types
            for (Entry<INPUT, RECIPE> entry : recipes.entrySet()) {
                Gas toCheck = null;
                if (entry.getKey() instanceof GasInput gasInput) {
                    toCheck = gasInput.ingredient.getGas();
                } else if (entry.getKey() instanceof FarmInput farmInput) {
                    if (farmInput.isGasInput()) {
                        toCheck = farmInput.gasInput.getGas();
                    }
                } else if (entry.getKey() instanceof AdvancedMachineInput advancedMachineInput) {
                    toCheck = advancedMachineInput.gasType;
                } else if (entry.getKey() instanceof PressurizedInput pressurizedInput) {
                    toCheck = pressurizedInput.getGas().getGas();
                } else if (entry.getKey() instanceof NucleosynthesizerInput nucleosynthesizerInput) {
                    toCheck = nucleosynthesizerInput.getGas().getGas();
                } else if (entry.getKey() instanceof GasAndFluidInput gasAndFluidInput) {
                    toCheck = gasAndFluidInput.ingredientGas.getGas();
                } else if (entry.getKey() instanceof RotaryInput rotaryInput) {
                    if (rotaryInput.containsType(new GasStack(input, 1))) {
                        return true;
                    }
                }
                if (toCheck == input) {
                    return true;
                }
            }
            return false;
        }


        public Class<RECIPE> getRecipeClass() {
            return recipeClass;
        }

        // N.B. Must return a HashMap, not Map as Unidict expects the stronger type
        @Nonnull
        public HashMap<INPUT, RECIPE> get() {
            return recipes;
        }

        private interface IndexedRecipeMap {
            @Nullable
            MachineRecipe<?, ?, ?> findIndexed(MachineInput<?> input);

            boolean containsIndexed(MachineInput<?> input);
        }

        private class RecipeMap extends HashMap<INPUT, RECIPE> implements IndexedRecipeMap {

            @Override
            public MachineRecipe<?, ?, ?> findIndexed(MachineInput<?> input) {
                @SuppressWarnings("unchecked")
                INPUT typed = (INPUT) input;
                return findRecipe(typed);
            }

            @Override
            public boolean containsIndexed(MachineInput<?> input) {
                @SuppressWarnings("unchecked")
                INPUT typed = (INPUT) input;
                return inputCache.containsInput(typed, recipes.values());
            }

            @Override
            public RECIPE put(INPUT key, RECIPE value) {
                removeConflictingRotaryRecipes(key);
                RECIPE previous = super.put(key, value);
                onRecipesChanged();
                return previous;
            }

            private void removeConflictingRotaryRecipes(INPUT key) {
                if (!(key instanceof RotaryInput rotaryInput)) {
                    return;
                }
                Iterator<Entry<INPUT, RECIPE>> iterator = super.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<INPUT, RECIPE> entry = iterator.next();
                    INPUT existingKey = entry.getKey();
                    if (existingKey != key && !existingKey.equals(key) && conflictsRotaryInput(rotaryInput, existingKey)) {
                        iterator.remove();
                    }
                }
            }

            private boolean conflictsRotaryInput(RotaryInput input, INPUT existingKey) {
                if (existingKey instanceof RotaryInput existingInput) {
                    return input.fluidInput != null && existingInput.fluidInput != null && input.fluidInput.isFluidEqual(existingInput.fluidInput) ||
                           input.gasInput != null && existingInput.gasInput != null && input.gasInput.isGasEqual(existingInput.gasInput);
                }
                return false;
            }

            @Override
            public RECIPE computeIfAbsent(INPUT key, Function<? super INPUT, ? extends RECIPE> mappingFunction) {
                boolean hadValue = containsKey(key) && get(key) != null;
                RECIPE value = super.computeIfAbsent(key, mappingFunction);
                if (!hadValue && value != null) {
                    onRecipesChanged();
                }
                return value;
            }

            @Override
            public RECIPE computeIfPresent(INPUT key, BiFunction<? super INPUT, ? super RECIPE, ? extends RECIPE> remappingFunction) {
                if (!containsKey(key) || get(key) == null) {
                    return super.computeIfPresent(key, remappingFunction);
                }
                RECIPE value = super.computeIfPresent(key, remappingFunction);
                onRecipesChanged();
                return value;
            }

            @Override
            public RECIPE compute(INPUT key, BiFunction<? super INPUT, ? super RECIPE, ? extends RECIPE> remappingFunction) {
                boolean hadKey = containsKey(key);
                RECIPE oldValue = get(key);
                RECIPE value = super.compute(key, remappingFunction);
                if (hadKey != containsKey(key) || !Objects.equals(oldValue, value)) {
                    onRecipesChanged();
                }
                return value;
            }

            @Override
            public RECIPE merge(INPUT key, RECIPE value, BiFunction<? super RECIPE, ? super RECIPE, ? extends RECIPE> remappingFunction) {
                boolean hadKey = containsKey(key);
                RECIPE oldValue = get(key);
                RECIPE result = super.merge(key, value, remappingFunction);
                if (hadKey != containsKey(key) || !Objects.equals(oldValue, result)) {
                    onRecipesChanged();
                }
                return result;
            }

            @Override
            public void replaceAll(BiFunction<? super INPUT, ? super RECIPE, ? extends RECIPE> function) {
                if (!isEmpty()) {
                    super.replaceAll(function);
                    onRecipesChanged();
                }
            }

            @Override
            public void putAll(Map<? extends INPUT, ? extends RECIPE> map) {
                if (!map.isEmpty()) {
                    for (Entry<? extends INPUT, ? extends RECIPE> entry : map.entrySet()) {
                        removeConflictingRotaryRecipes(entry.getKey());
                        super.put(entry.getKey(), entry.getValue());
                    }
                    onRecipesChanged();
                }
            }

            @Override
            public RECIPE remove(Object key) {
                if (containsKey(key)) {
                    RECIPE previous = super.remove(key);
                    onRecipesChanged();
                    return previous;
                }
                return null;
            }

            @Override
            public boolean remove(Object key, Object value) {
                boolean removed = super.remove(key, value);
                if (removed) {
                    onRecipesChanged();
                }
                return removed;
            }

            @Override
            public void clear() {
                if (!isEmpty()) {
                    super.clear();
                    onRecipesChanged();
                }
            }

            @Override
            public RECIPE putIfAbsent(INPUT key, RECIPE value) {
                boolean hadKey = containsKey(key);
                RECIPE previous = super.putIfAbsent(key, value);
                if (!hadKey) {
                    onRecipesChanged();
                }
                return previous;
            }

            @Override
            public RECIPE replace(INPUT key, RECIPE value) {
                if (containsKey(key)) {
                    RECIPE previous = super.replace(key, value);
                    onRecipesChanged();
                    return previous;
                }
                return null;
            }

            @Override
            public boolean replace(INPUT key, RECIPE oldValue, RECIPE newValue) {
                boolean replaced = super.replace(key, oldValue, newValue);
                if (replaced) {
                    onRecipesChanged();
                }
                return replaced;
            }

            @Override
            public Set<INPUT> keySet() {
                Set<INPUT> delegate = super.keySet();
                return new AbstractSet<>() {
                    @Override
                    public Iterator<INPUT> iterator() {
                        Iterator<INPUT> iterator = delegate.iterator();
                        return new Iterator<>() {
                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public INPUT next() {
                                return iterator.next();
                            }

                            @Override
                            public void remove() {
                                iterator.remove();
                                onRecipesChanged();
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return delegate.size();
                    }

                    @Override
                    public boolean contains(Object object) {
                        return delegate.contains(object);
                    }

                    @Override
                    public boolean remove(Object object) {
                        return RecipeMap.this.remove(object) != null;
                    }

                    @Override
                    public void clear() {
                        RecipeMap.this.clear();
                    }
                };
            }

            @Override
            public Collection<RECIPE> values() {
                Collection<RECIPE> delegate = super.values();
                return new AbstractCollection<>() {
                    @Override
                    public Iterator<RECIPE> iterator() {
                        Iterator<RECIPE> iterator = delegate.iterator();
                        return new Iterator<>() {
                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public RECIPE next() {
                                return iterator.next();
                            }

                            @Override
                            public void remove() {
                                iterator.remove();
                                onRecipesChanged();
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return delegate.size();
                    }

                    @Override
                    public boolean contains(Object object) {
                        return delegate.contains(object);
                    }

                    @Override
                    public void clear() {
                        RecipeMap.this.clear();
                    }
                };
            }

            @Override
            public Set<Entry<INPUT, RECIPE>> entrySet() {
                Set<Entry<INPUT, RECIPE>> delegate = super.entrySet();
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<INPUT, RECIPE>> iterator() {
                        Iterator<Entry<INPUT, RECIPE>> iterator = delegate.iterator();
                        return new Iterator<>() {
                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public Entry<INPUT, RECIPE> next() {
                                Entry<INPUT, RECIPE> entry = iterator.next();
                                return new Entry<>() {
                                    @Override
                                    public INPUT getKey() {
                                        return entry.getKey();
                                    }

                                    @Override
                                    public RECIPE getValue() {
                                        return entry.getValue();
                                    }

                                    @Override
                                    public RECIPE setValue(RECIPE value) {
                                        RECIPE oldValue = entry.setValue(value);
                                        onRecipesChanged();
                                        return oldValue;
                                    }
                                };
                            }

                            @Override
                            public void remove() {
                                iterator.remove();
                                onRecipesChanged();
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return delegate.size();
                    }

                    @Override
                    public boolean contains(Object object) {
                        return delegate.contains(object);
                    }

                    @Override
                    public boolean remove(Object object) {
                        if (object instanceof Entry<?, ?> entry) {
                            return RecipeMap.this.remove(entry.getKey(), entry.getValue());
                        }
                        return false;
                    }

                    @Override
                    public void clear() {
                        RecipeMap.this.clear();
                    }
                };
            }
        }
    }
}
