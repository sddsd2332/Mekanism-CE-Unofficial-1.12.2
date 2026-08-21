package mekanism.common.recipe.processing;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.machines.*;
import mekanism.common.recipe.outputs.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects network-independent, typed routes from Mekanism recipe maps.
 *
 * <p>These collectors never synthesize carrier items for gases or fluids. Network integrations may add their own
 * presentation layer after consuming the typed route.</p>
 */
public final class MachineRecipeRouteCollectors {

    private MachineRecipeRouteCollectors() {
    }

    public static List<MachineRecipeRoute> collectBasicItem(
          Map<ItemStackInput, ? extends BasicMachineRecipe<?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        recipes.values().forEach(recipe -> addExpandedItemRoute(routes, recipes, recipe.getInput().ingredient,
              recipe.getOutput().output));
        return routes;
    }

    public static List<MachineRecipeRoute> collectChanceItem(
          Map<ItemStackInput, ? extends ChanceMachineRecipe<?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (ChanceMachineRecipe<?> recipe : recipes.values()) {
            ChanceOutput output = recipe.getOutput();
            if (!isPositiveItem(output.getMainOutput())) {
                continue;
            }
            for (ItemStack input : MachineRecipeItemInputs.expand(recipe.getInput().ingredient,
                  candidate -> chanceRecipeMatches(recipes, candidate, output.getMainOutput()))) {
                MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder("route:item_to_item_chance")
                      .inputItem("item_input", input)
                      .outputItem("item_output", output.getMainOutput());
                ItemStack secondary = output.getMaxSecondaryOutput();
                if (isPositiveItem(secondary)) {
                    builder.optionalOutputItem("secondary_item_output", secondary);
                }
                routes.add(builder.build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectGuaranteedChanceItem(
          Map<ItemStackInput, ? extends Chance2MachineRecipe<?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (Chance2MachineRecipe<?> recipe : recipes.values()) {
            ChanceOutput2 output = recipe.getOutput();
            if (output.primaryChance < 1 || !isPositiveItem(output.getMaxPrimaryOutput())) {
                continue;
            }
            ItemStack guaranteed = output.getMaxPrimaryOutput();
            for (ItemStack input : MachineRecipeItemInputs.expand(recipe.getInput().ingredient,
                  candidate -> chance2RecipeMatches(recipes, candidate, guaranteed))) {
                routes.add(MachineRecipeRoute.builder("route:item_to_item")
                      .inputItem("item_input", input)
                      .outputItem("item_output", guaranteed)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectDoubleItem(
          Map<?, ? extends DoubleMachineRecipe<?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (DoubleMachineRecipe<?> recipe : recipes.values()) {
            DoubleMachineInput input = recipe.getInput();
            ItemStack output = recipe.getOutput().output;
            if (!isPositiveItem(output)) {
                continue;
            }
            List<ItemStack> rawInputs = java.util.Arrays.asList(input.itemStack, input.extraStack);
            for (List<ItemStack> expanded : MachineRecipeItemInputs.expandCombinations(rawInputs,
                  candidates -> doubleRecipeMatches(recipes, candidates.get(0), candidates.get(1), output))) {
                routes.add(MachineRecipeRoute.builder("route:item_item_to_item")
                      .inputItem("item_input", expanded.get(0))
                      .inputItem("extra_item_input", expanded.get(1))
                      .outputItem("item_output", output)
                      .build());
            }
        }
        return routes;
    }

    /**
     * Exposes metallurgic infuser recipes through the real item that supplies the required infuse type.
     * The recipe is scaled to the smallest whole number of operations and source items.
     */
    public static List<MachineRecipeRoute> collectInfusionItem(
          Map<InfusionInput, ? extends MachineRecipe<InfusionInput, ItemStackOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<InfusionInput, ItemStackOutput, ?> recipe : recipes.values()) {
            InfusionInput input = recipe.getInput();
            ItemStack output = recipe.getOutput().output;
            if (input == null || input.infuse == null || input.infuse.getType() == null || input.infuse.getAmount() <= 0 ||
                !isPositiveItem(output)) {
                continue;
            }
            for (Map.Entry<ItemStack, InfuseObject> source : InfuseRegistry.getObjectMap().entrySet()) {
                InfuseObject object = source.getValue();
                if (object == null || object.type != input.infuse.getType() || object.stored <= 0) {
                    continue;
                }
                int divisor = greatestCommonDivisor(input.infuse.getAmount(), object.stored);
                int operations = object.stored / divisor;
                int sourceCount = input.infuse.getAmount() / divisor;
                ItemStack scaledInput = scaleItem(input.inputStack, operations);
                ItemStack sourceUnit = source.getKey().copy();
                sourceUnit.setCount(1);
                ItemStack scaledSource = scaleItem(sourceUnit, sourceCount);
                ItemStack scaledOutput = scaleItem(output, operations);
                if (!isPositiveItem(scaledInput) || !isPositiveItem(scaledSource) || !isPositiveItem(scaledOutput)) {
                    continue;
                }
                for (List<ItemStack> expanded : MachineRecipeItemInputs.expandCombinations(
                      Arrays.asList(scaledInput, scaledSource), candidates -> infusionRecipeMatches(recipes,
                            candidates.get(0), candidates.get(1), input.infuse.getAmount(), operations, scaledOutput))) {
                    routes.add(MachineRecipeRoute.builder("route:item_item_to_item_infusing")
                          .inputItem("item_input", expanded.get(0))
                          .inputItem("extra_item_input", expanded.get(1))
                          .outputItem("item_output", scaledOutput)
                          .build());
                }
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectItemToGas(
          Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ItemStackInput, GasOutput, ?> recipe : recipes.values()) {
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(output)) {
                continue;
            }
            for (ItemStack input : MachineRecipeItemInputs.expand(recipe.getInput().ingredient,
                  candidate -> itemToGasRecipeMatches(recipes, candidate, output))) {
                routes.add(MachineRecipeRoute.builder("route:item_to_gas")
                      .inputItem("item_input", input)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectGasToItem(Map<GasInput, ? extends CrystallizerRecipe> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (CrystallizerRecipe recipe : recipes.values()) {
            GasStack input = recipe.getInput().ingredient;
            ItemStack output = recipe.getOutput().output;
            if (isPositiveGas(input) && isPositiveItem(output)) {
                routes.add(MachineRecipeRoute.builder("route:gas_to_item")
                      .inputGas("gas_input", input)
                      .outputItem("item_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectGasToGas(
          Map<GasInput, ? extends MachineRecipe<GasInput, GasOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasInput, GasOutput, ?> recipe : recipes.values()) {
            GasStack input = recipe.getInput().ingredient;
            GasStack output = recipe.getOutput().output;
            if (isPositiveGas(input) && isPositiveGas(output)) {
                routes.add(MachineRecipeRoute.builder("route:gas_to_gas")
                      .inputGas("gas_input", input)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectItemGasToGas(
          Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipes, @Nullable Gas gasType,
          int gasPerOperation) {
        if (gasType == null || gasPerOperation <= 0) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        GasStack gasInput = new GasStack(gasType, gasPerOperation);
        for (MachineRecipe<ItemStackInput, GasOutput, ?> recipe : recipes.values()) {
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(output)) {
                continue;
            }
            for (ItemStack itemInput : MachineRecipeItemInputs.expand(recipe.getInput().ingredient,
                  candidate -> itemToGasRecipeMatches(recipes, candidate, output))) {
                routes.add(MachineRecipeRoute.builder("route:item_gas_to_gas")
                      .inputItem("item_input", itemInput)
                      .inputGas("gas_input", gasInput)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectAdvancedGasToItem(
          Map<AdvancedMachineInput, ? extends AdvancedMachineRecipe<?>> recipes, int gasPerOperation) {
        if (gasPerOperation <= 0) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (AdvancedMachineRecipe<?> recipe : recipes.values()) {
            AdvancedMachineInput input = recipe.getInput();
            ItemStack output = recipe.getOutput().output;
            if (input.gasType == null || !isPositiveItem(output)) {
                continue;
            }
            for (ItemStack itemInput : MachineRecipeItemInputs.expand(input.itemStack,
                  candidate -> advancedRecipeMatches(recipes, candidate, input.gasType, output))) {
                routes.add(MachineRecipeRoute.builder("route:item_gas_to_item")
                      .inputItem("item_input", itemInput)
                      .inputGas("gas_input", new GasStack(input.gasType, gasPerOperation))
                      .outputItem("item_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectFarmGasToItem(
          Map<FarmInput, ? extends FarmMachineRecipe<?>> recipes, int secondaryPerOperation) {
        if (secondaryPerOperation <= 0) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (FarmMachineRecipe<?> recipe : recipes.values()) {
            FarmInput input = recipe.getInput();
            FarmOutput output = recipe.getOutput();
            if (!input.isValid() || !isPositiveItem(output.getGuaranteedOutput())) {
                continue;
            }
            for (ItemStack itemInput : MachineRecipeItemInputs.expand(input.itemStack,
                  candidate -> farmRecipeMatches(recipes, candidate, input, output.getGuaranteedOutput()))) {
                MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder(input.isGasInput() ?
                            "route:item_gas_to_item_chance" : "route:item_fluid_to_item_chance")
                      .inputItem("item_input", itemInput)
                      .outputItem("item_output", output.getGuaranteedOutput());
                if (input.isGasInput()) {
                    builder.inputGas("gas_input", input.gasInput.copy().withAmount(input.gasInput.amount * secondaryPerOperation));
                } else {
                    builder.inputFluid("fluid_input", new FluidStack(input.fluidInput, input.fluidInput.amount * secondaryPerOperation));
                }
                for (FarmChanceOutput chanceOutput : output.getChanceOutputs()) {
                    ItemStack secondary = chanceOutput.getOutput();
                    double chance = chanceOutput.getChance();
                    if (chance > 0 && isPositiveItem(secondary)) {
                        if (chance == 1) {
                            builder.outputItem("item_output", secondary);
                        } else {
                            builder.optionalOutputItem("item_output", secondary);
                        }
                    }
                }
                routes.add(builder.build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectNucleosynthesizerGasToItem(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?> recipe : recipes.values()) {
            NucleosynthesizerInput input = recipe.getInput();
            GasStack gas = input.getGas();
            ItemStack output = recipe.getOutput().output;
            if (!isPositiveGas(gas) || !isPositiveItem(output)) {
                continue;
            }
            for (ItemStack solid : MachineRecipeItemInputs.expand(input.getSolid(),
                  candidate -> nucleosynthesizerRecipeMatches(recipes, candidate, gas, output))) {
                routes.add(MachineRecipeRoute.builder("route:item_gas_to_item")
                      .inputItem("item_input", solid)
                      .inputGas("gas_input", gas)
                      .outputItem("item_output", output)
                      .build());
            }
        }
        return routes;
    }

    /**
     * Collects configured item-template replication routes. The template is required machine state, but is not
     * consumed by an operation, so only the UU gas is exposed as a route input.
     */
    public static List<MachineRecipeRoute> collectReplicatorItemTemplate(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes,
          @Nullable ItemStack template) {
        return collectReplicatorItemTemplate(recipes, template, "uu_input");
    }

    public static List<MachineRecipeRoute> collectReplicatorItemTemplate(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes,
          @Nullable ItemStack template, String uuPortId) {
        if (!isPositiveItem(template)) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?> recipe : recipes.values()) {
            NucleosynthesizerInput input = recipe.getInput();
            GasStack uu = input == null ? null : input.getGas();
            ItemStack requiredTemplate = input == null ? ItemStack.EMPTY : input.getSolid();
            ItemStack output = recipe.getOutput().output;
            if (MachineInput.inputContains(template, requiredTemplate) && isPositiveGas(uu) && isPositiveItem(output)) {
                routes.add(MachineRecipeRoute.builder("route:replicator_item.template")
                      .inputGas(requirePortId(uuPortId), uu)
                      .outputItem("item_output", output)
                      .build());
            }
        }
        return routes;
    }

    /**
     * Collects configured gas-template replication routes without treating the retained template gas as an input.
     */
    public static List<MachineRecipeRoute> collectReplicatorGasTemplate(
          Map<ChemicalGasInput, ? extends MachineRecipe<ChemicalGasInput, GasOutput, ?>> recipes,
          @Nullable GasStack template) {
        return collectReplicatorGasTemplate(recipes, template, "uu_input");
    }

    public static List<MachineRecipeRoute> collectReplicatorGasTemplate(
          Map<ChemicalGasInput, ? extends MachineRecipe<ChemicalGasInput, GasOutput, ?>> recipes,
          @Nullable GasStack template, String uuPortId) {
        if (!isPositiveGas(template)) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ChemicalGasInput, GasOutput, ?> recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (input != null && containsGas(template, input.input) && isPositiveGas(input.uu) && isPositiveGas(output)) {
                routes.add(MachineRecipeRoute.builder("route:replicator_gas.template")
                      .inputGas(requirePortId(uuPortId), input.uu)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    /**
     * Collects configured fluid-template replication routes without treating the retained template fluid as an input.
     */
    public static List<MachineRecipeRoute> collectReplicatorFluidTemplate(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, FluidOutput, ?>> recipes,
          @Nullable FluidStack template) {
        return collectReplicatorFluidTemplate(recipes, template, "uu_input");
    }

    public static List<MachineRecipeRoute> collectReplicatorFluidTemplate(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, FluidOutput, ?>> recipes,
          @Nullable FluidStack template, String uuPortId) {
        if (!isPositiveFluid(template)) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasAndFluidInput, FluidOutput, ?> recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            FluidStack output = recipe.getOutput().output;
            if (input != null && containsFluid(template, input.ingredientFluid) && isPositiveGas(input.ingredientGas) &&
                isPositiveFluid(output)) {
                routes.add(MachineRecipeRoute.builder("route:replicator_fluid.template")
                      .inputGas(requirePortId(uuPortId), input.ingredientGas)
                      .outputFluid("fluid_output", output)
                      .build());
            }
        }
        return routes;
    }

    /**
     * Publishes every item replicator recipe with a retained, replaceable template. Unlike the
     * legacy template-filtered collector, this method does not depend on current machine state.
     */
    public static List<MachineRecipeRoute> collectConfigurableReplicatorItems(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes) {
        return collectConfigurableReplicatorItems(recipes, "template", "uu_input", "item_output");
    }

    public static List<MachineRecipeRoute> collectConfigurableReplicatorItems(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes,
          String templatePortId, String uuPortId, String outputPortId) {
        String templatePort = requirePortId(templatePortId);
        String uuPort = requirePortId(uuPortId);
        String outputPort = requirePortId(outputPortId);
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?> recipe : recipes.values()) {
            NucleosynthesizerInput input = recipe.getInput();
            GasStack uu = input == null ? null : input.getGas();
            ItemStack template = input == null ? ItemStack.EMPTY : input.getSolid();
            ItemStack output = recipe.getOutput().output;
            if (!isPositiveItem(template) || !isPositiveGas(uu) || !isPositiveItem(output)) {
                continue;
            }
            for (ItemStack expanded : MachineRecipeItemInputs.expand(template,
                  candidate -> nucleosynthesizerRecipeMatches(recipes, candidate, uu, output))) {
                routes.add(MachineRecipeRoute.builder("route:replicator_item.configurable")
                      .configurationInput(MachineResourceStack.item(templatePort, expanded, 1))
                      .inputGas(uuPort, uu)
                      .outputItem(outputPort, output)
                      .build());
            }
        }
        return routes;
    }

    /** Publishes every gas replicator recipe with a one-unit retained template. */
    public static List<MachineRecipeRoute> collectConfigurableReplicatorGases(
          Map<ChemicalGasInput, ? extends MachineRecipe<ChemicalGasInput, GasOutput, ?>> recipes) {
        return collectConfigurableReplicatorGases(recipes, "template", "uu_input", "gas_output");
    }

    public static List<MachineRecipeRoute> collectConfigurableReplicatorGases(
          Map<ChemicalGasInput, ? extends MachineRecipe<ChemicalGasInput, GasOutput, ?>> recipes,
          String templatePortId, String uuPortId, String outputPortId) {
        String templatePort = requirePortId(templatePortId);
        String uuPort = requirePortId(uuPortId);
        String outputPort = requirePortId(outputPortId);
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ChemicalGasInput, GasOutput, ?> recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (input == null || !isPositiveGas(input.input) || !isPositiveGas(input.uu) ||
                !isPositiveGas(output)) {
                continue;
            }
            routes.add(MachineRecipeRoute.builder("route:replicator_gas.configurable")
                  .configurationInput(MachineResourceStack.gas(templatePort, input.input, 1))
                  .inputGas(uuPort, input.uu)
                  .outputGas(outputPort, output)
                  .build());
        }
        return routes;
    }

    /** Publishes every fluid replicator recipe with a one-mB retained template. */
    public static List<MachineRecipeRoute> collectConfigurableReplicatorFluids(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, FluidOutput, ?>> recipes) {
        return collectConfigurableReplicatorFluids(recipes, "template", "uu_input", "fluid_output");
    }

    public static List<MachineRecipeRoute> collectConfigurableReplicatorFluids(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, FluidOutput, ?>> recipes,
          String templatePortId, String uuPortId, String outputPortId) {
        String templatePort = requirePortId(templatePortId);
        String uuPort = requirePortId(uuPortId);
        String outputPort = requirePortId(outputPortId);
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasAndFluidInput, FluidOutput, ?> recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            FluidStack output = recipe.getOutput().output;
            if (input == null || !isPositiveFluid(input.ingredientFluid) ||
                !isPositiveGas(input.ingredientGas) || !isPositiveFluid(output)) {
                continue;
            }
            routes.add(MachineRecipeRoute.builder("route:replicator_fluid.configurable")
                  .configurationInput(MachineResourceStack.fluid(templatePort,
                        input.ingredientFluid, 1))
                  .inputGas(uuPort, input.ingredientGas)
                  .outputFluid(outputPort, output)
                  .build());
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectChemicalGasToGas(
          Map<ChemicalGasInput, ? extends MachineRecipe<ChemicalGasInput, GasOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ChemicalGasInput, GasOutput, ?> recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (isPositiveGas(input.input) && isPositiveGas(input.uu) && isPositiveGas(output)) {
                routes.add(MachineRecipeRoute.builder("route:gas_gas_to_gas")
                      .inputGas("left_gas", input.input)
                      .inputGas("right_gas", input.uu)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectChemicalPairToGas(
          Map<ChemicalPairInput, ? extends MachineRecipe<ChemicalPairInput, GasOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ChemicalPairInput, GasOutput, ?> recipe : recipes.values()) {
            ChemicalPairInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (isPositiveGas(input.leftGas) && isPositiveGas(input.rightGas) && isPositiveGas(output)) {
                routes.add(MachineRecipeRoute.builder("route:gas_gas_to_gas")
                      .inputGas("left_gas", input.leftGas)
                      .inputGas("right_gas", input.rightGas)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectGasFluidToGas(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, GasOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasAndFluidInput, GasOutput, ?> recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (isPositiveGas(input.ingredientGas) && isPositiveFluid(input.ingredientFluid) && isPositiveGas(output)) {
                routes.add(MachineRecipeRoute.builder("route:gas_fluid_to_gas")
                      .inputGas("gas_input", input.ingredientGas)
                      .inputFluid("fluid_input", input.ingredientFluid)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectGasFluidToFluid(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, FluidOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasAndFluidInput, FluidOutput, ?> recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            FluidStack output = recipe.getOutput().output;
            if (isPositiveGas(input.ingredientGas) && isPositiveFluid(input.ingredientFluid) && isPositiveFluid(output)) {
                routes.add(MachineRecipeRoute.builder("route:gas_fluid_to_fluid")
                      .inputGas("gas_input", input.ingredientGas)
                      .inputFluid("fluid_input", input.ingredientFluid)
                      .outputFluid("fluid_output", output)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectFluidToGasPair(
          Map<FluidInput, ? extends MachineRecipe<FluidInput, ChemicalPairOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<FluidInput, ChemicalPairOutput, ?> recipe : recipes.values()) {
            FluidStack input = recipe.getInput().ingredient;
            ChemicalPairOutput output = recipe.getOutput();
            if (isPositiveFluid(input) && output != null && output.isValid() && isPositiveGas(output.leftGas) &&
                isPositiveGas(output.rightGas)) {
                routes.add(MachineRecipeRoute.builder("route:fluid_to_gas_pair")
                      .inputFluid("fluid_input", input)
                      .outputGas("left_gas_output", output.leftGas)
                      .outputGas("right_gas_output", output.rightGas)
                      .build());
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectPressurized(
          Map<PressurizedInput, ? extends MachineRecipe<PressurizedInput, PressurizedOutput, ?>> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<PressurizedInput, PressurizedOutput, ?> recipe : recipes.values()) {
            PressurizedInput input = recipe.getInput();
            PressurizedOutput output = recipe.getOutput();
            if (input == null || !input.isValid() || output == null || !isPositiveFluid(input.getFluid()) ||
                !isPositiveGas(input.getGas())) {
                continue;
            }
            for (ItemStack solid : MachineRecipeItemInputs.expand(input.getSolid(),
                  candidate -> pressurizedRecipeMatches(recipes, candidate, input.getFluid(), input.getGas(), output))) {
                MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder("route:pressurized")
                      .inputItem("item_input", solid)
                      .inputFluid("fluid_input", input.getFluid())
                      .inputGas("gas_input", input.getGas());
                if (isPositiveItem(output.getItemOutput())) {
                    builder.outputItem("item_output", output.getItemOutput());
                }
                if (isPositiveGas(output.getGasOutput())) {
                    builder.outputGas("gas_output", output.getGasOutput());
                }
                if (isPositiveItem(output.getItemOutput()) || isPositiveGas(output.getGasOutput())) {
                    routes.add(builder.build());
                }
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectRotaryGasToFluid(Map<RotaryInput, ? extends RotaryRecipe> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (RotaryRecipe recipe : recipes.values()) {
            if (recipe.hasGasToFluid()) {
                GasStack input = recipe.getGasInput();
                FluidStack output = recipe.getFluidOutput(input);
                if (isPositiveGas(input) && isPositiveFluid(output)) {
                    routes.add(MachineRecipeRoute.builder("route:gas_to_fluid")
                          .inputGas("gas_input", input)
                          .outputFluid("fluid_output", output)
                          .build());
                }
            }
        }
        return routes;
    }

    public static List<MachineRecipeRoute> collectRotaryFluidToGas(Map<RotaryInput, ? extends RotaryRecipe> recipes) {
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (RotaryRecipe recipe : recipes.values()) {
            if (recipe.hasFluidToGas()) {
                FluidStack input = recipe.getFluidInput();
                GasStack output = recipe.getGasOutput(input);
                if (isPositiveFluid(input) && isPositiveGas(output)) {
                    routes.add(MachineRecipeRoute.builder("route:fluid_to_gas")
                          .inputFluid("fluid_input", input)
                          .outputGas("gas_output", output)
                          .build());
                }
            }
        }
        return routes;
    }

    /**
     * Expands each logical recipe route into stable, independently addressable processing lanes.
     */
    public static List<MachineRecipeRoute> expandLanes(List<MachineRecipeRoute> routes, int lanes,
          String... sharedPortIds) {
        Set<String> sharedPorts = sharedPortIds == null || sharedPortIds.length == 0 ? Collections.emptySet() :
              new HashSet<>(Arrays.asList(sharedPortIds));
        return expandLanes(routes, lanes, sharedPorts);
    }

    public static List<MachineRecipeRoute> expandLanes(List<MachineRecipeRoute> routes, int lanes,
          Set<String> sharedPortIds) {
        if (routes == null || routes.isEmpty() || lanes <= 0) {
            return Collections.emptyList();
        }
        Set<String> sharedPorts = sharedPortIds == null ? Collections.emptySet() : sharedPortIds;
        List<MachineRecipeRoute> expanded = new ArrayList<>(routes.size() * lanes);
        for (int lane = 0; lane < lanes; lane++) {
            for (MachineRecipeRoute route : routes) {
                MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder(route.routeId())
                      .recipeKey(route.recipeKey() + ":lane_" + lane)
                      .logicalRecipeKey(route.logicalRecipeKey());
                for (MachineResourceStack input : route.configurationInputs()) {
                    builder.configurationInput(withLane(input, lane, sharedPorts));
                }
                for (MachineResourceStack input : route.inputs()) {
                    builder.input(withLane(input, lane, sharedPorts));
                }
                for (MachineResourceStack output : route.guaranteedOutputs()) {
                    builder.output(withLane(output, lane, sharedPorts));
                }
                for (MachineResourceStack output : route.optionalOutputs()) {
                    builder.optionalOutput(withLane(output, lane, sharedPorts));
                }
                expanded.add(builder.build());
            }
        }
        return expanded;
    }

    private static void addExpandedItemRoute(List<MachineRecipeRoute> routes,
          Map<ItemStackInput, ? extends BasicMachineRecipe<?>> recipes, ItemStack input, ItemStack output) {
        if (!isPositiveItem(output)) {
            return;
        }
        for (ItemStack expanded : MachineRecipeItemInputs.expand(input,
              candidate -> basicRecipeMatches(recipes, candidate, output))) {
            routes.add(MachineRecipeRoute.builder("route:item_to_item")
                  .inputItem("item_input", expanded)
                  .outputItem("item_output", output)
                  .build());
        }
    }

    private static boolean basicRecipeMatches(Map<ItemStackInput, ? extends BasicMachineRecipe<?>> recipes,
          ItemStack input, ItemStack output) {
        Object matched = getRecipe(recipes, new ItemStackInput(input.copy()));
        return matched instanceof BasicMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().output, output);
    }

    private static boolean chanceRecipeMatches(Map<ItemStackInput, ? extends ChanceMachineRecipe<?>> recipes,
          ItemStack input, ItemStack output) {
        Object matched = getRecipe(recipes, new ItemStackInput(input.copy()));
        return matched instanceof ChanceMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().getMainOutput(), output);
    }

    private static boolean chance2RecipeMatches(Map<ItemStackInput, ? extends Chance2MachineRecipe<?>> recipes,
          ItemStack input, ItemStack output) {
        Object matched = getRecipe(recipes, new ItemStackInput(input.copy()));
        return matched instanceof Chance2MachineRecipe<?> recipe && recipe.getOutput().primaryChance >= 1 &&
              ItemStack.areItemStacksEqual(recipe.getOutput().getMaxPrimaryOutput(), output);
    }

    private static boolean doubleRecipeMatches(Map<?, ? extends DoubleMachineRecipe<?>> recipes, ItemStack first,
          ItemStack second, ItemStack output) {
        Object matched = getRecipe(recipes, new DoubleMachineInput(first.copy(), second.copy()));
        return matched instanceof DoubleMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().output, output);
    }

    private static boolean infusionRecipeMatches(
          Map<InfusionInput, ? extends MachineRecipe<InfusionInput, ItemStackOutput, ?>> recipes, ItemStack itemInput,
          ItemStack sourceInput, int infusePerOperation, int operations, ItemStack expectedOutput) {
        InfuseObject source = InfuseRegistry.getObject(sourceInput);
        if (source == null || source.type == null || source.stored <= 0 || operations <= 0 ||
            (long) source.stored * sourceInput.getCount() != (long) infusePerOperation * operations) {
            return false;
        }
        ItemStack singleOperationInput = itemInput.copy();
        if (singleOperationInput.getCount() % operations != 0) {
            return false;
        }
        singleOperationInput.setCount(singleOperationInput.getCount() / operations);
        Object matched = getRecipe(recipes, new InfusionInput(source.type, infusePerOperation, singleOperationInput));
        if (!(matched instanceof MachineRecipe<?, ?, ?> recipe) || !(recipe.getOutput() instanceof ItemStackOutput itemOutput)) {
            return false;
        }
        ItemStack scaledMatchedOutput = scaleItem(itemOutput.output, operations);
        return ItemStack.areItemStacksEqual(scaledMatchedOutput, expectedOutput);
    }

    private static boolean itemToGasRecipeMatches(
          Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipes, ItemStack input, GasStack output) {
        Object matched = getRecipe(recipes, new ItemStackInput(input.copy()));
        return matched instanceof MachineRecipe<?, ?, ?> recipe && recipe.getOutput() instanceof GasOutput gasOutput &&
              gasStacksEqual(gasOutput.output, output);
    }

    private static boolean advancedRecipeMatches(Map<AdvancedMachineInput, ? extends AdvancedMachineRecipe<?>> recipes,
          ItemStack input, Gas gas, ItemStack output) {
        Object matched = getRecipe(recipes, new AdvancedMachineInput(input.copy(), gas));
        return matched instanceof AdvancedMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().output, output);
    }

    private static boolean farmRecipeMatches(Map<FarmInput, ? extends FarmMachineRecipe<?>> recipes,
          ItemStack itemInput, FarmInput recipeInput, ItemStack output) {
        FarmInput input = recipeInput.isGasInput() ? new FarmInput(itemInput.copy(), recipeInput.gasInput) :
              new FarmInput(itemInput.copy(), recipeInput.fluidInput);
        Object matched = getRecipe(recipes, input);
        return matched instanceof FarmMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().getGuaranteedOutput(), output);
    }

    private static boolean nucleosynthesizerRecipeMatches(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes,
          ItemStack solid, GasStack gas, ItemStack output) {
        Object matched = getRecipe(recipes, new NucleosynthesizerInput(solid.copy(), gas.copy()));
        return matched instanceof MachineRecipe<?, ?, ?> recipe && recipe.getOutput() instanceof ItemStackOutput itemOutput &&
              ItemStack.areItemStacksEqual(itemOutput.output, output);
    }

    private static boolean pressurizedRecipeMatches(
          Map<PressurizedInput, ? extends MachineRecipe<PressurizedInput, PressurizedOutput, ?>> recipes,
          ItemStack solid, FluidStack fluid, GasStack gas, PressurizedOutput output) {
        Object matched = getRecipe(recipes, new PressurizedInput(solid.copy(), fluid.copy(), gas.copy()));
        return matched instanceof MachineRecipe<?, ?, ?> recipe && recipe.getOutput() instanceof PressurizedOutput actual &&
              ItemStack.areItemStacksEqual(actual.getItemOutput(), output.getItemOutput()) &&
              gasStacksEqual(actual.getGasOutput(), output.getGasOutput());
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getRecipe(Map recipes, MachineInput input) {
        return RecipeHandler.getRecipe(input, recipes);
    }

    private static boolean isPositiveItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0;
    }

    private static boolean isPositiveGas(@Nullable GasStack stack) {
        return stack != null && stack.getGas() != null && stack.amount > 0;
    }

    private static boolean isPositiveFluid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() != null && stack.amount > 0;
    }

    private static boolean containsGas(GasStack stored, @Nullable GasStack required) {
        return isPositiveGas(required) && stored.isGasEqual(required) && stored.amount >= required.amount;
    }

    private static boolean containsFluid(FluidStack stored, @Nullable FluidStack required) {
        return isPositiveFluid(required) && stored.isFluidEqual(required) && stored.amount >= required.amount;
    }

    private static String requirePortId(String portId) {
        if (portId == null || portId.isEmpty()) {
            throw new IllegalArgumentException("Replicator UU port id cannot be empty");
        }
        return portId;
    }

    private static boolean gasStacksEqual(@Nullable GasStack first, @Nullable GasStack second) {
        if (!isPositiveGas(first)) {
            return !isPositiveGas(second);
        }
        return isPositiveGas(second) && first.amount == second.amount && first.isGasEqual(second);
    }

    private static MachineResourceStack withLane(MachineResourceStack stack, int lane, Set<String> sharedPorts) {
        return sharedPorts.contains(stack.portId()) ? stack : stack.withPort(stack.portId() + '_' + lane);
    }

    private static ItemStack scaleItem(@Nullable ItemStack stack, int multiplier) {
        if (!isPositiveItem(stack) || multiplier <= 0 || stack.getCount() > Integer.MAX_VALUE / multiplier) {
            return ItemStack.EMPTY;
        }
        ItemStack scaled = stack.copy();
        scaled.setCount(stack.getCount() * multiplier);
        return scaled;
    }

    private static int greatestCommonDivisor(int first, int second) {
        first = Math.abs(first);
        second = Math.abs(second);
        while (second != 0) {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first == 0 ? 1 : first;
    }
}
