package mekanism.client.jei;

import mekanism.api.gas.GasRegistry;
import mekanism.client.gui.*;
import mekanism.client.gui.chemical.*;
import mekanism.client.jei.machine.*;
import mekanism.client.jei.machine.chemical.*;
import mekanism.client.jei.machine.other.*;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.MekanismFluids;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.integration.crafttweaker.handlers.EnergizedSmelter;
import mekanism.common.inventory.container.ContainerFormulaicAssemblicator;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.machines.SmeltingRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tier.FactoryTier;
import mekanism.common.util.MekanismUtils;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.recipe.IRecipeWrapperFactory;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RecipeRegistryHelper {

    public static void registerEnrichmentChamber(IModRegistry registry) {
        if (!MachineType.ENRICHMENT_CHAMBER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.ENRICHMENT_CHAMBER, MachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiEnrichmentChamber.class, 79, 40, 24, 7, Recipe.ENRICHMENT_CHAMBER.getJEICategory());
        registerRecipeItem(registry, MachineType.ENRICHMENT_CHAMBER, Recipe.ENRICHMENT_CHAMBER);
    }

    public static void registerCrusher(IModRegistry registry) {
        if (!MachineType.CRUSHER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CRUSHER, MachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiCrusher.class, 79, 40, 24, 7, Recipe.CRUSHER.getJEICategory());
        registerRecipeItem(registry, MachineType.CRUSHER, Recipe.CRUSHER);
    }

    public static void registerCombiner(IModRegistry registry) {
        if (!MachineType.COMBINER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.COMBINER, DoubleMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiCombiner.class, 79, 40, 24, 7, Recipe.COMBINER.getJEICategory());
        registerRecipeItem(registry, MachineType.COMBINER, Recipe.COMBINER);
    }

    public static void registerPurification(IModRegistry registry) {
        if (!MachineType.PURIFICATION_CHAMBER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.PURIFICATION_CHAMBER, AdvancedMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiPurificationChamber.class, 79, 40, 24, 7, Recipe.PURIFICATION_CHAMBER.getJEICategory());
        registerRecipeItem(registry, MachineType.PURIFICATION_CHAMBER, Recipe.PURIFICATION_CHAMBER);
    }

    public static void registerCompressor(IModRegistry registry) {
        if (!MachineType.OSMIUM_COMPRESSOR.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.OSMIUM_COMPRESSOR, AdvancedMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiOsmiumCompressor.class, 79, 40, 24, 7, Recipe.OSMIUM_COMPRESSOR.getJEICategory());
        registerRecipeItem(registry, MachineType.OSMIUM_COMPRESSOR, Recipe.OSMIUM_COMPRESSOR);
    }

    public static void registerInjection(IModRegistry registry) {
        if (!MachineType.CHEMICAL_INJECTION_CHAMBER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CHEMICAL_INJECTION_CHAMBER, AdvancedMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiChemicalInjectionChamber.class, 79, 40, 24, 7, Recipe.CHEMICAL_INJECTION_CHAMBER.getJEICategory());
        registerRecipeItem(registry, MachineType.CHEMICAL_INJECTION_CHAMBER, Recipe.CHEMICAL_INJECTION_CHAMBER);
    }

    public static void registerSawmill(IModRegistry registry) {
        if (!MachineType.PRECISION_SAWMILL.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.PRECISION_SAWMILL, ChanceMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiPrecisionSawmill.class, 79, 40, 24, 7, Recipe.PRECISION_SAWMILL.getJEICategory());
        registerRecipeItem(registry, MachineType.PRECISION_SAWMILL, Recipe.PRECISION_SAWMILL);
    }

    public static void registerMetallurgicInfuser(IModRegistry registry) {
        if (!MachineType.METALLURGIC_INFUSER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.METALLURGIC_INFUSER, MetallurgicInfuserRecipeWrapper::new);
        registry.addRecipeClickArea(GuiMetallurgicInfuser.class, 72, 47, 32, 8, Recipe.METALLURGIC_INFUSER.getJEICategory());
        registerRecipeItem(registry, MachineType.METALLURGIC_INFUSER, Recipe.METALLURGIC_INFUSER);
    }

    public static void registerCrystallizer(IModRegistry registry) {
        if (!MachineType.CHEMICAL_CRYSTALLIZER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CHEMICAL_CRYSTALLIZER, ChemicalCrystallizerRecipeWrapper::new);
        registry.addRecipeClickArea(GuiChemicalCrystallizer.class, 53, 62, 48, 8, Recipe.CHEMICAL_CRYSTALLIZER.getJEICategory());
        registerRecipeItem(registry, MachineType.CHEMICAL_CRYSTALLIZER, Recipe.CHEMICAL_CRYSTALLIZER);
    }

    public static void registerDissolution(IModRegistry registry) {
        if (!MachineType.CHEMICAL_DISSOLUTION_CHAMBER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CHEMICAL_DISSOLUTION_CHAMBER, ChemicalDissolutionChamberRecipeWrapper::new);
        registry.addRecipeClickArea(GuiChemicalDissolutionChamber.class, 64, 40, 48, 8, Recipe.CHEMICAL_DISSOLUTION_CHAMBER.getJEICategory());
        registerRecipeItem(registry, MachineType.CHEMICAL_DISSOLUTION_CHAMBER, Recipe.CHEMICAL_DISSOLUTION_CHAMBER);
    }

    public static void registerChemicalInfuser(IModRegistry registry) {
        if (!MachineType.CHEMICAL_INFUSER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CHEMICAL_INFUSER, ChemicalInfuserRecipeWrapper::new);
        registry.addRecipeClickArea(GuiChemicalInfuser.class, 47, 39 + 11, 28, 8, Recipe.CHEMICAL_INFUSER.getJEICategory());
        registry.addRecipeClickArea(GuiChemicalInfuser.class, 101, 39 + 11, 28, 8, Recipe.CHEMICAL_INFUSER.getJEICategory());
        registerRecipeItem(registry, MachineType.CHEMICAL_INFUSER, Recipe.CHEMICAL_INFUSER);
    }

    public static void registerOxidizer(IModRegistry registry) {
        if (!MachineType.CHEMICAL_OXIDIZER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CHEMICAL_OXIDIZER, ChemicalOxidizerRecipeWrapper::new);
        registry.addRecipeClickArea(GuiChemicalOxidizer.class, 64, 40, 48, 8, Recipe.CHEMICAL_OXIDIZER.getJEICategory());
        registerRecipeItem(registry, MachineType.CHEMICAL_OXIDIZER, Recipe.CHEMICAL_OXIDIZER);
    }

    public static void registerWasher(IModRegistry registry) {
        if (!MachineType.CHEMICAL_WASHER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CHEMICAL_WASHER, ChemicalWasherRecipeWrapper::new);
        registry.addRecipeClickArea(GuiChemicalWasher.class, 61, 39, 55, 8, Recipe.CHEMICAL_WASHER.getJEICategory());
        registerRecipeItem(registry, MachineType.CHEMICAL_WASHER, Recipe.CHEMICAL_WASHER);
    }

    public static void registerNeutronActivator(IModRegistry registry) {
        if (!MachineType.SOLAR_NEUTRON_ACTIVATOR.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.SOLAR_NEUTRON_ACTIVATOR, SolarNeutronRecipeWrapper::new);
        registry.addRecipeClickArea(GuiSolarNeutronActivator.class, 64, 39, 48, 8, Recipe.SOLAR_NEUTRON_ACTIVATOR.getJEICategory());
        registerRecipeItem(registry, MachineType.SOLAR_NEUTRON_ACTIVATOR, Recipe.SOLAR_NEUTRON_ACTIVATOR);
    }

    public static void registerSeparator(IModRegistry registry) {
        if (!MachineType.ELECTROLYTIC_SEPARATOR.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.ELECTROLYTIC_SEPARATOR, ElectrolyticSeparatorRecipeWrapper::new);
        registry.addRecipeClickArea(GuiElectrolyticSeparator.class, 80, 30, 16, 6, Recipe.ELECTROLYTIC_SEPARATOR.getJEICategory());
        registerRecipeItem(registry, MachineType.ELECTROLYTIC_SEPARATOR, Recipe.ELECTROLYTIC_SEPARATOR);
    }

    public static void registerEvaporationPlant(IModRegistry registry) {
        addRecipes(registry, Recipe.THERMAL_EVAPORATION_PLANT, ThermalEvaporationRecipeWrapper::new);
        registry.addRecipeClickArea(GuiThermalEvaporationController.class, 49, 20, 78, 38, Recipe.THERMAL_EVAPORATION_PLANT.getJEICategory());
        registry.addRecipeCatalyst(BasicBlockType.THERMAL_EVAPORATION_CONTROLLER.getStack(1), Recipe.THERMAL_EVAPORATION_PLANT.getJEICategory());
    }

    public static void registerReactionChamber(IModRegistry registry) {
        if (!MachineType.PRESSURIZED_REACTION_CHAMBER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.PRESSURIZED_REACTION_CHAMBER, PRCRecipeWrapper::new);
        registry.addRecipeClickArea(GuiPRC.class, 75, 37, 36, 10, Recipe.PRESSURIZED_REACTION_CHAMBER.getJEICategory());
        registerRecipeItem(registry, MachineType.PRESSURIZED_REACTION_CHAMBER, Recipe.PRESSURIZED_REACTION_CHAMBER);
    }

    public static void registerCondensentrator(IModRegistry registry) {
        if (!MachineType.ROTARY_CONDENSENTRATOR.isEnabled()) {
            return;
        }
        List<RotaryCondensentratorRecipeWrapper> condensentratorRecipes = new ArrayList<>();
        List<RotaryCondensentratorRecipeWrapper> decondensentratorRecipes = new ArrayList<>();
        GasRegistry.getRegisteredGasses().forEach(gas -> {
            if (gas.hasFluid()) {
                condensentratorRecipes.add(new RotaryCondensentratorRecipeWrapper(gas.getFluid(), gas, true));
                decondensentratorRecipes.add(new RotaryCondensentratorRecipeWrapper(gas.getFluid(), gas, false));
            }
        });
        String condensentrating = "mekanism.rotary_condensentrator_condensentrating";
        String decondensentrating = "mekanism.rotary_condensentrator_decondensentrating";
        registry.addRecipes(condensentratorRecipes, condensentrating);
        registry.addRecipes(decondensentratorRecipes, decondensentrating);
        registry.addRecipeClickArea(GuiRotaryCondensentrator.class, 64, 39, 48, 8, condensentrating, decondensentrating);
        registry.addRecipeCatalyst(MachineType.ROTARY_CONDENSENTRATOR.getStack(), condensentrating, decondensentrating);
    }

    public static void registerSmelter(IModRegistry registry) {
        if (!MachineType.ENERGIZED_SMELTER.isEnabled()) {
            return;
        }
        registry.handleRecipes(SmeltingRecipe.class, MachineRecipeWrapper::new, Recipe.ENERGIZED_SMELTER.getJEICategory());
        if ( /* Mekanism.hooks.GroovyScriptLoaded && Smelter.hasRemovedRecipe() || */ Mekanism.hooks.CraftTweakerLoaded && EnergizedSmelter.hasRemovedRecipe()) {
            // Removed / Removed + Added
            // Add all recipes
            Collection<SmeltingRecipe> recipeList = Recipe.ENERGIZED_SMELTER.get().values();
            registry.addRecipes(recipeList.stream().map(MachineRecipeWrapper::new).collect(Collectors.toList()),
                    Recipe.ENERGIZED_SMELTER.getJEICategory());
            registry.addRecipeClickArea(GuiEnergizedSmelter.class, 79, 40, 24, 7,
                    Recipe.ENERGIZED_SMELTER.getJEICategory());
        } else if ( /* Mekanism.hooks.GroovyScriptLoaded && Smelter.hasAddedRecipe() || */ Mekanism.hooks.CraftTweakerLoaded && EnergizedSmelter.hasAddedRecipe()) {
            // Added but not removed
            // Only add added recipes
            Map<ItemStackInput, SmeltingRecipe> smeltingRecipes = Recipe.ENERGIZED_SMELTER.get();
            List<MachineRecipeWrapper> smeltingWrapper = smeltingRecipes.entrySet().stream().filter(entry ->
                    !FurnaceRecipes.instance().getSmeltingList().containsKey(entry.getKey().ingredient)).map(entry ->
                    new MachineRecipeWrapper<>(entry.getValue())).collect(Collectors.toList());
            registry.addRecipes(smeltingWrapper, Recipe.ENERGIZED_SMELTER.getJEICategory());

            registry.addRecipeClickArea(GuiEnergizedSmelter.class, 79, 40, 24, 7, VanillaRecipeCategoryUid.SMELTING, Recipe.ENERGIZED_SMELTER.getJEICategory());
            registerVanillaSmeltingRecipeCatalyst(registry);
        } else {
            //Only use furnace list, so no extra registration.
            registry.addRecipeClickArea(GuiEnergizedSmelter.class, 79, 40, 24, 7, VanillaRecipeCategoryUid.SMELTING);
            registerVanillaSmeltingRecipeCatalyst(registry);
        }
        registerRecipeItem(registry, MachineType.ENERGIZED_SMELTER, Recipe.ENERGIZED_SMELTER);
    }


    public static void registerFormulaicAssemblicator(IModRegistry registry) {
        if (!MachineType.FORMULAIC_ASSEMBLICATOR.isEnabled()) {
            return;
        }
        registry.addRecipeCatalyst(MachineType.FORMULAIC_ASSEMBLICATOR.getStack(), VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(ContainerFormulaicAssemblicator.class, VanillaRecipeCategoryUid.CRAFTING, 20, 9, 35, 36);
    }

    private static void registerVanillaSmeltingRecipeCatalyst(IModRegistry registry) {
        registry.addRecipeCatalyst(MachineType.ENERGIZED_SMELTER.getStack(), VanillaRecipeCategoryUid.SMELTING);
        FactoryTier.forEnabled(tier -> registry.addRecipeCatalyst(MekanismUtils.getFactory(tier, RecipeType.SMELTING), VanillaRecipeCategoryUid.SMELTING));
    }


    /**
     * ADD START
     */
    public static void registerIsotopicCentrifuge(IModRegistry registry) {
        if (!MachineType.ISOTOPIC_CENTRIFUGE.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.ISOTOPIC_CENTRIFUGE, IsotopicRecipeWrapper::new);
        registry.addRecipeClickArea(GuiIsotopicCentrifuge.class, 61, 39, 55, 8, Recipe.ISOTOPIC_CENTRIFUGE.getJEICategory());
        registerRecipeItem(registry, MachineType.ISOTOPIC_CENTRIFUGE, Recipe.ISOTOPIC_CENTRIFUGE);
    }

    public static void registerNutritional(IModRegistry registry) {
        if (!MachineType.NUTRITIONAL_LIQUIFIER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.NUTRITIONAL_LIQUIFIER, NutritionalLiquifierRecipeWrapper::new);
        registry.addRecipeClickArea(GuiNutritionalLiquifier.class, 64, 40, 48, 8, Recipe.NUTRITIONAL_LIQUIFIER.getJEICategory());
        registerRecipeItem(registry, MachineType.NUTRITIONAL_LIQUIFIER, Recipe.NUTRITIONAL_LIQUIFIER);
    }


    public static void registerFarm(IModRegistry registry) {
        if (!MachineType.ORGANIC_FARM.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.ORGANIC_FARM, FarmMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiOrganicFarm.class, 79, 40, 24, 7, Recipe.ORGANIC_FARM.getJEICategory());
        registerRecipeItem(registry, MachineType.ORGANIC_FARM, Recipe.ORGANIC_FARM);
    }

    public static void registerAntiprotonicNucleosynthesizer(IModRegistry registry) {
        if (!MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER, AntiprotonicNucleosynthesizerRecipeWrapper::new);
        registry.addRecipeClickArea(GuiAntiprotonicNucleosynthesizer.class, 75, 37, 36, 10, Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.getJEICategory());
        registerRecipeItem(registry, MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER);
    }

    public static void registerStamping(IModRegistry registry) {
        if (!MachineType.STAMPING.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.STAMPING, MachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiStamping.class, 79, 40, 24, 7, Recipe.STAMPING.getJEICategory());
        registerRecipeItem(registry, MachineType.STAMPING, Recipe.STAMPING);
    }

    public static void registerRolling(IModRegistry registry) {
        if (!MachineType.ROLLING.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.ROLLING, MachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiRolling.class, 79, 40, 24, 7, Recipe.ROLLING.getJEICategory());
        registerRecipeItem(registry, MachineType.ROLLING, Recipe.ROLLING);
    }

    public static void registerBrushed(IModRegistry registry) {
        if (!MachineType.BRUSHED.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.BRUSHED, MachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiBrushed.class, 79, 40, 24, 7, Recipe.BRUSHED.getJEICategory());
        registerRecipeItem(registry, MachineType.BRUSHED, Recipe.BRUSHED);
    }

    public static void registerTurning(IModRegistry registry) {
        if (!MachineType.TURNING.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.TURNING, MachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiTurning.class, 79, 40, 24, 7, Recipe.TURNING.getJEICategory());
        registerRecipeItem(registry, MachineType.TURNING, Recipe.TURNING);
    }

    public static void registerAlloy(IModRegistry registry) {
        if (!MachineType.ALLOY.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.ALLOY, DoubleMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiAlloy.class, 79, 40, 24, 7, Recipe.ALLOY.getJEICategory());
        registerRecipeItem(registry, MachineType.ALLOY, Recipe.ALLOY);
    }

    public static void registerCellExtractor(IModRegistry registry) {
        if (!MachineType.CELL_EXTRACTOR.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CELL_EXTRACTOR, ChanceMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiCellExtractor.class, 79, 40, 24, 7, Recipe.CELL_EXTRACTOR.getJEICategory());
        registerRecipeItem(registry, MachineType.CELL_EXTRACTOR, Recipe.CELL_EXTRACTOR);
    }

    public static void registerCellSeparator(IModRegistry registry) {
        if (!MachineType.CELL_SEPARATOR.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.CELL_SEPARATOR, ChanceMachineRecipeWrapper::new);
        registry.addRecipeClickArea(GuiCellSeparator.class, 79, 40, 24, 7, Recipe.CELL_SEPARATOR.getJEICategory());
        registerRecipeItem(registry, MachineType.CELL_SEPARATOR, Recipe.CELL_SEPARATOR);
    }

    public static void registerRecycler(IModRegistry registry) {
        if (!MachineType.RECYCLER.isEnabled()) {
            return;
        }

        Map<String, List<ItemStack>> groupedInputs = new java.util.HashMap<>();
        Map<String, mekanism.common.recipe.machines.RecyclerRecipe> sampleRecipes = new java.util.HashMap<>();

        for (mekanism.common.recipe.machines.RecyclerRecipe recipe : Recipe.RECYCLER.get().values()) {
            mekanism.common.recipe.outputs.ChanceOutput2 out = recipe.getOutput();
            if (!out.hasPrimary()) continue;

            String key = out.primaryOutput.getItem().getRegistryName() + "_" + out.primaryOutput.getMetadata() + "_" + out.primaryChance;

            groupedInputs.computeIfAbsent(key, k -> new ArrayList<>()).add(recipe.getInput().ingredient);
            sampleRecipes.putIfAbsent(key, recipe);
        }

        List<RecyclerRecipeWrapper> wrappers = new ArrayList<>();
        for (Map.Entry<String, List<ItemStack>> entry : groupedInputs.entrySet()) {
            wrappers.add(new RecyclerRecipeWrapper(sampleRecipes.get(entry.getKey()), entry.getValue()));
        }

        registry.addRecipes(wrappers, Recipe.RECYCLER.getJEICategory());

        registry.addRecipeClickArea(GuiRecycler.class, 79, 40, 24, 7, Recipe.RECYCLER.getJEICategory());
        registerRecipeItem(registry, MachineType.RECYCLER, Recipe.RECYCLER);
    }

    public static void registerAmbientAccumulator(IModRegistry registry) {
        if (!MachineType.AMBIENT_ACCUMULATOR.isEnabled() || !MachineType.AMBIENT_ACCUMULATOR_ENERGY.isEnabled()) {
            return;
        }
        addRecipes(registry, Recipe.AMBIENT_ACCUMULATOR, AmbientGasRecipeWrapper::new);
        registry.addRecipeClickArea(GuiAmbientAccumulator.class, 7, 13, 80, 65, Recipe.AMBIENT_ACCUMULATOR.getJEICategory());
        registerRecipeItem(registry, MachineType.AMBIENT_ACCUMULATOR, Recipe.AMBIENT_ACCUMULATOR);
        registry.addRecipeClickArea(GuiAmbientAccumulatorEnergy.class, 7, 18, 80, 65, Recipe.AMBIENT_ACCUMULATOR.getJEICategory());
        registerRecipeItem(registry, MachineType.AMBIENT_ACCUMULATOR_ENERGY, Recipe.AMBIENT_ACCUMULATOR);

    }

    public static void registerSPS(IModRegistry registry) {
        List<SPSRecipeWrapper> wrappers = new ArrayList<>();
        wrappers.add(new SPSRecipeWrapper(MekanismFluids.Polonium, MekanismFluids.Antimatter));
        String name = "mekanism.sps";
        registry.addRecipes(wrappers, name);
        if (MachineType.SPS.isEnabled()) {
            registry.addRecipeClickArea(GuiSPS.class, 27, 17, 122, 60, name);
            registry.addRecipeCatalyst(MachineType.SPS.getStack(), name);
        }
        registry.addRecipeClickArea(GuiSPSMultiblock.class, 27, 17, 122, 60, name);
        registry.addRecipeCatalyst(BasicBlockType.SPS_CASING.getStack(1), name);
        registry.addRecipeCatalyst(BasicBlockType.SPS_PORT.getStack(1), name);
        registry.addRecipeCatalyst(MachineType.SUPERCHARGED_COIL.getStack(), name);
    }

    /**
     * ADD END
     */

    public static void registerItemStackToEnergyRecipe(IModRegistry registry) {
        addRecipes(registry, Recipe.ENERGY_RECIPE, ItemStackToEnergyRecipeWrapper::new);
        for (EnergyCubeTier tier : EnergyCubeTier.values()) {
            if (tier == EnergyCubeTier.CREATIVE) {
                continue;
            }
            ItemStack stack = new ItemStack(MekanismBlocks.EnergyCube);
            if (!stack.hasTagCompound()) {
                stack.setTagCompound(new NBTTagCompound());
            }
            stack.getTagCompound().setInteger("tier", tier.ordinal());
            registry.addRecipeCatalyst(stack, Recipe.ENERGY_RECIPE.getJEICategory());
        }
    }

    private static <INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>>
    void addRecipes(IModRegistry registry, Recipe<INPUT, OUTPUT, RECIPE> type, IRecipeWrapperFactory<RECIPE> factory) {
        String recipeCategoryUid = type.getJEICategory();
        registry.handleRecipes(type.getRecipeClass(), factory, recipeCategoryUid);
        registry.addRecipes(type.get().values().stream().map(factory::getRecipeWrapper).collect(Collectors.toList()), recipeCategoryUid);
    }

    private static void registerRecipeItem(IModRegistry registry, MachineType type, Recipe recipe) {
        registry.addRecipeCatalyst(type.getStack(), recipe.getJEICategory());
        RecipeType factoryType = null;
        for (RecipeType t : RecipeType.values()) {
            if (t.getType() == type) {
                factoryType = t;
                break;
            }
        }
        if (factoryType != null) {
            RecipeType finalFactoryType = factoryType;
            FactoryTier.forEnabled(tier -> registry.addRecipeCatalyst(MekanismUtils.getFactory(tier, finalFactoryType), recipe.getJEICategory()));
        }
    }


}
