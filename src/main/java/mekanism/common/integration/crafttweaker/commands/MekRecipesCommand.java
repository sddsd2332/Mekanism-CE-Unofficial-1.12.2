package mekanism.common.integration.crafttweaker.commands;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.mc1120.commands.CraftTweakerCommand;
import crafttweaker.mc1120.commands.SpecialMessagesChat;
import mekanism.common.integration.crafttweaker.helpers.RecipeInfoHelper;
import mekanism.common.recipe.GasStackFuelToEnergyRecipe;
import mekanism.common.recipe.ItemStackToEnergyRecipe;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.*;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MekRecipesCommand extends CraftTweakerCommand {

    private final List<String> subCommands;

    public MekRecipesCommand() {
        super("mekrecipes");
        subCommands = Stream.of("crystallizer",
                "dissolution",
                "chemicalInfuser",
                "injection",
                "oxidizer",
                "washer",
                "combiner",
                "crusher",
                "separator",
                "smelter",
                "enrichment",
                "metallurgicInfuser",
                "compressor",
                "sawmill",
                "prc",
                "purification",
                "rotarycondensentrator",
                "solarneutronactivator",
                "thermalevaporation",
                "isotopiccentrifuge",
                "nutritionalliquifier",
                "organicfarm",
                "antiprotonicnucleosynthesizer",
                "stamping",
                "rolling",
                "brushed",
                "turning",
                "alloy",
                "cellextractor",
                "cellseparator",
                "recycler",
                "fusioncooling",
                "ambientaccumulator",
                "itemstacktoenergy",
                "gastoenergy",
                "replicatoritemstack",
                "replicatorgases",
                "replicatorfluidstack"
        ).collect(Collectors.toList());
    }

    @Override
    protected void init() {
        setDescription(SpecialMessagesChat.getClickableCommandText(TextFormatting.DARK_GREEN + "/ct " + subCommandName + "<type>", "/ct " + subCommandName, true),
                SpecialMessagesChat.getNormalMessage(TextFormatting.DARK_AQUA + "Outputs a list of all registered Mekanism Machine recipes to the crafttweaker.log for the given machine type."));
    }

    @Override
    public void executeCommand(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0 || !subCommands.contains(args[0])) {
            sender.sendMessage(SpecialMessagesChat.getNormalMessage("Recipe Type required, pick one of the following: " + Arrays.toString(subCommands.toArray())));
            return;
        }
        String subCommand = args[0];
        CraftTweakerAPI.logCommand(subCommand + ":");
        Recipe<?, ?, ?> type;
        switch (subCommand) {
            case "crystallizer" -> {
                type = Recipe.CHEMICAL_CRYSTALLIZER;
                for (CrystallizerRecipe recipe : Recipe.CHEMICAL_CRYSTALLIZER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.chemical.crystallizer.addRecipe(%s, %s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "dissolution" -> {
                type = Recipe.CHEMICAL_DISSOLUTION_CHAMBER;
                for (DissolutionRecipe recipe : Recipe.CHEMICAL_DISSOLUTION_CHAMBER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.chemical.dissolution.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getGasName(recipe.getOutput().output)
                    ));
                }
            }
            case "chemicalInfuser" -> {
                type = Recipe.CHEMICAL_INFUSER;
                for (ChemicalInfuserRecipe recipe : Recipe.CHEMICAL_INFUSER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.chemical.infuser.addRecipe(%s, %s, %s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().leftGas),
                            RecipeInfoHelper.getGasName(recipe.getInput().rightGas),
                            RecipeInfoHelper.getGasName(recipe.getOutput().output)
                    ));
                }
            }
            case "injection" -> {
                type = Recipe.CHEMICAL_INJECTION_CHAMBER;
                for (InjectionRecipe recipe : Recipe.CHEMICAL_INJECTION_CHAMBER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.chemical.injection.addRecipe(%s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().itemStack),
                            RecipeInfoHelper.getGasName(recipe.getInput().gasType),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "oxidizer" -> {
                type = Recipe.CHEMICAL_OXIDIZER;
                for (OxidationRecipe recipe : Recipe.CHEMICAL_OXIDIZER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.chemical.oxidizer.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getGasName(recipe.getOutput().output)
                    ));
                }
            }
            case "washer" -> {
                type = Recipe.CHEMICAL_WASHER;
                for (WasherRecipe recipe : Recipe.CHEMICAL_WASHER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.chemical.washer.addRecipe(%s,%s,%s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().ingredientGas),
                            RecipeInfoHelper.getFluidName(recipe.getInput().ingredientFluid),
                            RecipeInfoHelper.getGasName(recipe.getOutput().output)
                    ));
                }
            }
            case "combiner" -> {
                type = Recipe.COMBINER;
                for (CombinerRecipe recipe : Recipe.COMBINER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.combiner.addRecipe(%s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().itemStack),
                            RecipeInfoHelper.getItemName(recipe.getInput().extraStack),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "crusher" -> {
                type = Recipe.CRUSHER;
                for (CrusherRecipe recipe : Recipe.CRUSHER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.crusher.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "separator" -> {
                type = Recipe.ELECTROLYTIC_SEPARATOR;
                for (SeparatorRecipe recipe : Recipe.ELECTROLYTIC_SEPARATOR.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.separator.addRecipe(%s, %s, %s, %s)",
                            RecipeInfoHelper.getFluidName(recipe.getInput().ingredient),
                            recipe.energyUsage,
                            RecipeInfoHelper.getGasName(recipe.getOutput().leftGas),
                            RecipeInfoHelper.getGasName(recipe.getOutput().rightGas)
                    ));
                }
            }
            case "smelter" -> {
                type = Recipe.ENERGIZED_SMELTER;
                for (SmeltingRecipe recipe : Recipe.ENERGIZED_SMELTER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.smelter.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "enrichment" -> {
                type = Recipe.ENRICHMENT_CHAMBER;
                for (EnrichmentRecipe recipe : Recipe.ENRICHMENT_CHAMBER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.enrichment.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "metallurgicInfuser" -> {
                type = Recipe.METALLURGIC_INFUSER;
                for (MetallurgicInfuserRecipe recipe : Recipe.METALLURGIC_INFUSER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.infuser.addRecipe(%s, %s, %s, %s)",
                            recipe.getInput().infuse.getType(),
                            recipe.getInput().infuse.getAmount(),
                            RecipeInfoHelper.getItemName(recipe.getInput().inputStack),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "compressor" -> {
                type = Recipe.OSMIUM_COMPRESSOR;
                for (OsmiumCompressorRecipe recipe : Recipe.OSMIUM_COMPRESSOR.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.compressor.addRecipe(%s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().itemStack),
                            RecipeInfoHelper.getGasName(recipe.getInput().gasType),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "sawmill" -> {
                type = Recipe.PRECISION_SAWMILL;
                for (SawmillRecipe recipe : Recipe.PRECISION_SAWMILL.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.sawmill.addRecipe(%s, %s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().primaryOutput),
                            RecipeInfoHelper.getItemName(recipe.getOutput().secondaryOutput),
                            recipe.getOutput().secondaryChance
                    ));
                }
            }
            case "prc" -> {
                type = Recipe.PRESSURIZED_REACTION_CHAMBER;
                for (PressurizedRecipe recipe : Recipe.PRESSURIZED_REACTION_CHAMBER.get().values()) {
                    CraftTweakerAPI
                            .logCommand(String.format("mods.mekanism.reaction.addRecipe(%s, %s, %s, %s, %s, %s, %s)",
                                    RecipeInfoHelper.getItemName(recipe.getInput().getSolid()),
                                    RecipeInfoHelper.getFluidName(recipe.getInput().getFluid()),
                                    RecipeInfoHelper.getGasName(recipe.getInput().getGas()),
                                    RecipeInfoHelper.getItemName(recipe.getOutput().getItemOutput()),
                                    RecipeInfoHelper.getGasName(recipe.getOutput().getGasOutput()),
                                    recipe.extraEnergy,
                                    recipe.ticks
                            ));
                }
            }
            case "purification" -> {
                type = Recipe.PURIFICATION_CHAMBER;
                for (PurificationRecipe recipe : Recipe.PURIFICATION_CHAMBER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.purification.addRecipe(%s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().itemStack),
                            RecipeInfoHelper.getGasName(recipe.getInput().gasType),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "rotarycondensentrator" -> {
                type = Recipe.ROTARY_CONDENSENTRATOR;
                for (RotaryRecipe recipe : Recipe.ROTARY_CONDENSENTRATOR.get().values()) {
                    if (recipe.hasFluidToGas() && recipe.hasGasToFluid()) {
                        CraftTweakerAPI.logCommand(String.format("mods.mekanism.rotarycondensentrator.addRecipe(%s, %s, %s, %s)",
                                RecipeInfoHelper.getFluidName(recipe.getInput().fluidInput),
                                RecipeInfoHelper.getGasName(recipe.getInput().gasInput),
                                RecipeInfoHelper.getGasName(recipe.getOutput().gasOutput),
                                RecipeInfoHelper.getFluidName(recipe.getOutput().fluidOutput)
                        ));
                    } else if (recipe.hasFluidToGas()) {
                        CraftTweakerAPI.logCommand(String.format("mods.mekanism.rotarycondensentrator.addFluidToGasRecipe(%s, %s)",
                                RecipeInfoHelper.getFluidName(recipe.getInput().fluidInput),
                                RecipeInfoHelper.getGasName(recipe.getOutput().gasOutput)
                        ));
                    } else if (recipe.hasGasToFluid()) {
                        CraftTweakerAPI.logCommand(String.format("mods.mekanism.rotarycondensentrator.addGasToFluidRecipe(%s, %s)",
                                RecipeInfoHelper.getGasName(recipe.getInput().gasInput),
                                RecipeInfoHelper.getFluidName(recipe.getOutput().fluidOutput)
                        ));
                    }
                }
            }
            case "solarneutronactivator" -> {
                type = Recipe.SOLAR_NEUTRON_ACTIVATOR;
                for (SolarNeutronRecipe recipe : Recipe.SOLAR_NEUTRON_ACTIVATOR.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.solarneutronactivator.addRecipe(%s, %s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getGasName(recipe.getOutput().output)
                    ));
                }
            }
            case "thermalevaporation" -> {
                type = Recipe.THERMAL_EVAPORATION_PLANT;
                for (ThermalEvaporationRecipe recipe : Recipe.THERMAL_EVAPORATION_PLANT.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.thermalevaporation.addRecipe(%s, %s)",
                            RecipeInfoHelper.getFluidName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getFluidName(recipe.getOutput().output)
                    ));
                }
            }
            /**
             * ADD START
             */
            case "isotopiccentrifuge" -> {
                type = Recipe.ISOTOPIC_CENTRIFUGE;
                for (IsotopicRecipe recipe : Recipe.ISOTOPIC_CENTRIFUGE.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.isotopiccentrifuge.addRecipe(%s, %s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getGasName(recipe.getOutput().output)
                    ));
                }
            }
            case "nutritionalliquifier" -> {
                type = Recipe.NUTRITIONAL_LIQUIFIER;
                for (NutritionalRecipe recipe : Recipe.NUTRITIONAL_LIQUIFIER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.nutritionalliquifier.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getGasName(recipe.getOutput().output)
                    ));
                }
            }
            case "organicfarm" -> {
                type = Recipe.ORGANIC_FARM;
                for (FarmRecipe recipe : Recipe.ORGANIC_FARM.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.organicfarm.addRecipe(%s, %s, %s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().itemStack),
                            RecipeInfoHelper.getGasName(recipe.getInput().gasType),
                            RecipeInfoHelper.getItemName(recipe.getOutput().primaryOutput),
                            RecipeInfoHelper.getItemName(recipe.getOutput().secondaryOutput),
                            recipe.getOutput().secondaryChance
                    ));
                }
            }
            case "antiprotonicnucleosynthesizer" -> {
                type = Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER;
                for (NucleosynthesizerRecipe recipe : Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.nucleosynthesizer.addRecipe(%s, %s, %s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().getSolid()),
                            RecipeInfoHelper.getGasName(recipe.getInput().getGas()),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output),
                            recipe.extraEnergy,
                            recipe.ticks
                    ));
                }
            }
            case "stamping" -> {
                type = Recipe.STAMPING;
                for (StampingRecipe recipe : Recipe.STAMPING.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.stamping.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "rolling" -> {
                type = Recipe.ROLLING;
                for (RollingRecipe recipe : Recipe.ROLLING.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.rolling.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "brushed" -> {
                type = Recipe.BRUSHED;
                for (BrushedRecipe recipe : Recipe.BRUSHED.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.brushed.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "turning" -> {
                type = Recipe.TURNING;
                for (TurningRecipe recipe : Recipe.TURNING.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.turning.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "alloy" -> {
                type = Recipe.ALLOY;
                for (AlloyRecipe recipe : Recipe.ALLOY.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.alloy.addRecipe(%s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().itemStack),
                            RecipeInfoHelper.getItemName(recipe.getInput().extraStack),
                            RecipeInfoHelper.getItemName(recipe.getOutput().output)
                    ));
                }
            }
            case "cellextractor" -> {
                type = Recipe.CELL_EXTRACTOR;
                for (CellExtractorRecipe recipe : Recipe.CELL_EXTRACTOR.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.cellextractor.addRecipe(%s, %s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().primaryOutput),
                            RecipeInfoHelper.getItemName(recipe.getOutput().secondaryOutput),
                            recipe.getOutput().secondaryChance
                    ));
                }
            }
            case "cellseparator" -> {
                type = Recipe.CELL_SEPARATOR;
                for (CellSeparatorRecipe recipe : Recipe.CELL_SEPARATOR.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.cellseparator.addRecipe(%s, %s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().primaryOutput),
                            RecipeInfoHelper.getItemName(recipe.getOutput().secondaryOutput),
                            recipe.getOutput().secondaryChance
                    ));
                }
            }
            case "recycler" -> {
                type = Recipe.RECYCLER;
                for (RecyclerRecipe recipe : Recipe.RECYCLER.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.recycler.addRecipe(%s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getItemName(recipe.getOutput().primaryOutput),
                            recipe.getOutput().primaryChance
                    ));
                }
            }
            case "fusioncooling" -> {
                type = Recipe.FUSION_COOLING;
                for (FusionCoolingRecipe recipe : Recipe.FUSION_COOLING.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.fusioncooling.addRecipe(%s, %s)",
                            RecipeInfoHelper.getFluidName(recipe.getInput().ingredient),
                            RecipeInfoHelper.getFluidName(recipe.getOutput().output)
                    ));
                }
            }
            case "ambientaccumulator" -> {
                type = Recipe.AMBIENT_ACCUMULATOR;
                for (AmbientGasRecipe recipe : Recipe.AMBIENT_ACCUMULATOR.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.ambientaccumulator.addRecipe(%s, %s, %s)",
                            recipe.recipeInput.ingredient,
                            RecipeInfoHelper.getGasName(recipe.getOutput().output),
                            recipe.getOutput().primaryChance
                    ));
                }
            }
            case "itemstacktoenergy" -> {
                type = Recipe.ENERGY_RECIPE;
                for (ItemStackToEnergyRecipe recipe : Recipe.ENERGY_RECIPE.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.ItemStackToEnergy.addRecipe(%s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().ingredient),
                            recipe.getOutput().energyOutput
                    ));
                }
            }
            case "gastoenergy" -> {
                type = Recipe.GAS_FUEL_TO_ENERGY_RECIPE;
                for (GasStackFuelToEnergyRecipe recipe : Recipe.GAS_FUEL_TO_ENERGY_RECIPE.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.GasToEnergy.addRecipe(%s, %s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().ingredient),
                            recipe.getOutput().energyOutput
                    ));
                }
            }
            case "replicatoritemstack" -> {
                type = Recipe.REPLICATOR_ITEMSTACK_RECIPE;
                for (ReplicatorItemStackRecipe recipe : Recipe.REPLICATOR_ITEMSTACK_RECIPE.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.replicator.itemstack.addRecipe(%s, %s, %s, %s)",
                            RecipeInfoHelper.getItemName(recipe.getInput().getSolid()),
                            RecipeInfoHelper.getGasName(recipe.getInput().getGas()),
                            recipe.extraEnergy,
                            recipe.ticks
                    ));
                }
            }
            case "replicatorgases" -> {
                type = Recipe.REPLICATOR_GASES_RECIPE;
                for (ReplicatorGasStackRecipe recipe : Recipe.REPLICATOR_GASES_RECIPE.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.replicator.gases.addRecipe(%s, %s, %s, %s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().input),
                            RecipeInfoHelper.getGasName(recipe.getInput().uu),
                            recipe.extraEnergy,
                            recipe.ticks
                    ));
                }
            }
            case "replicatorfluidstack" -> {
                type = Recipe.REPLICATOR_FLUIDSTACK_RECIPE;
                for (ReplicatorFluidStackRecipe recipe : Recipe.REPLICATOR_FLUIDSTACK_RECIPE.get().values()) {
                    CraftTweakerAPI.logCommand(String.format("mods.mekanism.replicator.fluidstack.addRecipe(%s, %s, %s, %s)",
                            RecipeInfoHelper.getGasName(recipe.getInput().ingredientGas),
                            RecipeInfoHelper.getFluidName(recipe.getInput().ingredientFluid),
                            recipe.extraEnergy,
                            recipe.ticks
                    ));
                }
            }
            /**
             * ADD END
             */

            default -> {
                sender.sendMessage(SpecialMessagesChat.getNormalMessage("Recipe Type required, pick one of the following: " + Arrays.toString(subCommands.toArray())));
                return;
            }
        }

        sender.sendMessage(SpecialMessagesChat.getLinkToCraftTweakerLog("List of " + type.get().size() + " " + subCommand + " recipes generated;", sender));
    }

    @Override
    public List<String> getSubSubCommand(MinecraftServer server, ICommandSender sender, String[] args,
                                         @Nullable BlockPos targetPos) {
        if (args.length > 1) {
            return Collections.emptyList();
        }
        String start = args[0].toLowerCase();
        return subCommands.stream().filter(command -> command.toLowerCase().startsWith(start)).collect(Collectors.toList());
    }
}
