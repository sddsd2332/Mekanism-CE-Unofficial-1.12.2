package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.machines.*;
import mekanism.common.recipe.outputs.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Main-thread whitelist compiler for every built-in machine recipe value shape. */
public final class RecipeSemanticsCompiler {

    private RecipeSemanticsCompiler() {
    }

    public static RecipeSemanticsSnapshot compile(@Nullable Object source, String mode) {
        if (source == null) return RecipeSemanticsSnapshot.empty();
        List<RecipeResourceFlow> inputs = new ArrayList<>();
        List<RecipeResourceFlow> outputs = new ArrayList<>();
        MutableParameters parameters = new MutableParameters();
        boolean supported = appendSource(source, mode == null ? "" : mode, "", inputs,
              outputs, parameters);
        return new RecipeSemanticsSnapshot(inputs, outputs, parameters.extraEnergy,
              parameters.requiredTicks, supported);
    }

    private static boolean appendSource(Object source, String mode, String prefix,
          List<RecipeResourceFlow> inputs, List<RecipeResourceFlow> outputs,
          MutableParameters parameters) {
        if (source instanceof MachineRecipe<?, ?, ?>) {
            MachineRecipe<?, ?, ?> recipe = ((MachineRecipe<?, ?, ?>) source).copyForAsync();
            boolean inputSupported = appendInput(recipe.getInput(), mode, prefix, inputs);
            if (recipe instanceof DissolutionRecipe) {
                add(inputs, prefix + "gas.1", ((DissolutionRecipe) recipe).getGasInput(), RecipeResourceFlow.Phase.PER_TICK);
            }
            boolean outputSupported = appendOutput(recipe.getOutput(), mode, prefix, outputs);
            appendParameters(recipe, parameters);
            return inputSupported && outputSupported;
        }
        if (source instanceof Iterable<?>) {
            int lane = 0;
            boolean supported = true;
            for (Object value : (Iterable<?>) source) {
                if (value != null) {
                    supported &= appendSource(value, mode, prefix + "lane." + lane + '.', inputs,
                          outputs, parameters);
                }
                lane++;
            }
            return supported;
        }
        if (source instanceof Object[]) {
            List<Object> values = new ArrayList<>();
            Collections.addAll(values, (Object[]) source);
            return appendSource(values, mode, prefix, inputs, outputs, parameters);
        }
        return false;
    }

    private static boolean appendInput(Object input, String mode, String prefix,
          List<RecipeResourceFlow> flows) {
        int index = flows.size();
        if (input instanceof ItemStackInput) {
            add(flows, prefix + "item." + index, ((ItemStackInput) input).ingredient,
                  RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof AdvancedMachineInput) {
            AdvancedMachineInput value = (AdvancedMachineInput) input;
            add(flows, prefix + "item." + index, value.itemStack,
                  RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "gas." + (index + 1), new GasStack(value.gasType, 1),
                  RecipeResourceFlow.Phase.PER_TICK);
        } else if (input instanceof DoubleMachineInput) {
            DoubleMachineInput value = (DoubleMachineInput) input;
            add(flows, prefix + "item." + index, value.itemStack,
                  RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "item." + (index + 1), value.extraStack,
                  RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof InfusionInput) {
            InfusionInput value = (InfusionInput) input;
            add(flows, prefix + "item." + index, value.inputStack,
                  RecipeResourceFlow.Phase.COMPLETION);
            if (value.infuse != null && value.infuse.getType() != null) {
                flows.add(new RecipeResourceFlow(prefix + "infuse." + (index + 1),
                      ImmutableResourceSnapshot.descriptor("infuse:" + value.infuse.getType().name,
                            value.infuse.getAmount()), RecipeResourceFlow.Phase.COMPLETION));
            }
        } else if (input instanceof GasInput) {
            add(flows, prefix + "gas." + index, ((GasInput) input).ingredient,
                  RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof FluidInput) {
            add(flows, prefix + "fluid." + index, ((FluidInput) input).ingredient,
                  RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof GasAndFluidInput) {
            GasAndFluidInput value = (GasAndFluidInput) input;
            add(flows, prefix + "gas." + index, value.ingredientGas,
                  RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "fluid." + (index + 1), value.ingredientFluid,
                  RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof ChemicalPairInput) {
            ChemicalPairInput value = (ChemicalPairInput) input;
            add(flows, prefix + "gas." + index, value.leftGas,
                  RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "gas." + (index + 1), value.rightGas,
                  RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof FarmInput) {
            FarmInput value = (FarmInput) input;
            add(flows, prefix + "item." + index, value.itemStack,
                  RecipeResourceFlow.Phase.COMPLETION);
            if (value.gasInput != null) {
                add(flows, prefix + "gas." + (index + 1), value.gasInput,
                      RecipeResourceFlow.Phase.PER_TICK);
            } else if (value.fluidInput != null) {
                add(flows, prefix + "fluid." + (index + 1), value.fluidInput,
                      RecipeResourceFlow.Phase.PER_TICK);
            }
        } else if (input instanceof PressurizedInput) {
            PressurizedInput value = (PressurizedInput) input;
            add(flows, prefix + "item." + index, value.getSolid(), RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "fluid." + (index + 1), value.getFluid(), RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "gas." + (index + 2), value.getGas(), RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof NucleosynthesizerInput) {
            NucleosynthesizerInput value = (NucleosynthesizerInput) input;
            add(flows, prefix + "item." + index, value.getSolid(), RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "gas." + (index + 1), value.getGas(), RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof ChemicalGasInput) {
            ChemicalGasInput value = (ChemicalGasInput) input;
            add(flows, prefix + "gas." + index, value.input, RecipeResourceFlow.Phase.COMPLETION);
            add(flows, prefix + "gas." + (index + 1), value.uu, RecipeResourceFlow.Phase.COMPLETION);
        } else if (input instanceof RotaryInput) {
            RotaryInput value = (RotaryInput) input;
            boolean fluidToGas = mode.contains("fluid_to_gas") || mode.equals("1") || mode.equals("false");
            if (fluidToGas && value.fluidInput != null || value.gasInput == null) {
                add(flows, prefix + "fluid." + index, value.fluidInput, RecipeResourceFlow.Phase.COMPLETION);
            } else {
                add(flows, prefix + "gas." + index, value.gasInput, RecipeResourceFlow.Phase.COMPLETION);
            }
        } else if (!(input instanceof IntegerInput)) {
            return false;
        }
        return true;
    }

    private static boolean appendOutput(Object output, String mode, String prefix,
          List<RecipeResourceFlow> flows) {
        int index = flows.size();
        if (output instanceof ItemStackOutput) {
            addOutput(flows, prefix + "item." + index, ((ItemStackOutput) output).output, 1);
        } else if (output instanceof GasOutput) {
            addOutput(flows, prefix + "gas." + index, ((GasOutput) output).output, 1);
        } else if (output instanceof FluidOutput) {
            addOutput(flows, prefix + "fluid." + index, ((FluidOutput) output).output, 1);
        } else if (output instanceof ChemicalPairOutput) {
            ChemicalPairOutput value = (ChemicalPairOutput) output;
            addOutput(flows, prefix + "gas." + index, value.leftGas, 1);
            addOutput(flows, prefix + "gas." + (index + 1), value.rightGas, 1);
        } else if (output instanceof PressurizedOutput) {
            PressurizedOutput value = (PressurizedOutput) output;
            addOutput(flows, prefix + "item." + index, value.getItemOutput(), 1);
            addOutput(flows, prefix + "gas." + (index + 1), value.getGasOutput(), 1);
        } else if (output instanceof RotaryOutput) {
            RotaryOutput value = (RotaryOutput) output;
            boolean fluidToGas = mode.contains("fluid_to_gas") || mode.equals("1") || mode.equals("false");
            if (fluidToGas && value.gasOutput != null || value.fluidOutput == null) {
                addOutput(flows, prefix + "gas." + index, value.gasOutput, 1);
            } else {
                addOutput(flows, prefix + "fluid." + index, value.fluidOutput, 1);
            }
        } else if (output instanceof ChanceOutput) {
            ChanceOutput value = (ChanceOutput) output;
            addOutput(flows, prefix + "item." + index, value.primaryOutput, 1, false);
            addOutput(flows, prefix + "item." + (index + 1), value.secondaryOutput,
                  value.secondaryChance, true);
        } else if (output instanceof ChanceOutput2) {
            ChanceOutput2 value = (ChanceOutput2) output;
            addOutput(flows, prefix + "item." + index, value.primaryOutput, value.primaryChance, true);
        } else if (output instanceof ChanceGasOutput) {
            ChanceGasOutput value = (ChanceGasOutput) output;
            addOutput(flows, prefix + "gas." + index, value.output, value.primaryChance, true);
        } else if (output instanceof FarmOutput) {
            FarmOutput value = (FarmOutput) output;
            addOutput(flows, prefix + "item." + index, value.getGuaranteedOutput(), 1, false);
            int offset = 1;
            for (FarmChanceOutput chance : value.getChanceOutputs()) {
                addOutput(flows, prefix + "item." + (index + offset++), chance.getOutput(),
                      chance.getChance(), true);
            }
        } else if (output instanceof EnergyOutput) {
            EnergyOutput value = (EnergyOutput) output;
            flows.add(new RecipeResourceFlow(prefix + "energy." + index,
                  ImmutableResourceSnapshot.descriptor("joules", nonNegativeLong(value.energyOutput)),
                  RecipeResourceFlow.Phase.OUTPUT));
        } else {
            return false;
        }
        return true;
    }

    private static void appendParameters(MachineRecipe<?, ?, ?> recipe, MutableParameters parameters) {
        if (recipe instanceof NucleosynthesizerRecipe) {
            NucleosynthesizerRecipe value = (NucleosynthesizerRecipe) recipe;
            parameters.include(value.extraEnergy, value.ticks);
        } else if (recipe instanceof PressurizedRecipe) {
            PressurizedRecipe value = (PressurizedRecipe) recipe;
            parameters.include(value.extraEnergy, value.ticks);
        } else if (recipe instanceof ReplicatorItemStackRecipe) {
            ReplicatorItemStackRecipe value = (ReplicatorItemStackRecipe) recipe;
            parameters.include(value.extraEnergy, value.ticks);
        } else if (recipe instanceof ReplicatorGasStackRecipe) {
            ReplicatorGasStackRecipe value = (ReplicatorGasStackRecipe) recipe;
            parameters.include(value.extraEnergy, value.ticks);
        } else if (recipe instanceof ReplicatorFluidStackRecipe) {
            ReplicatorFluidStackRecipe value = (ReplicatorFluidStackRecipe) recipe;
            parameters.include(value.extraEnergy, value.ticks);
        } else if (recipe instanceof SeparatorRecipe) {
            parameters.include(((SeparatorRecipe) recipe).energyUsage, 0);
        } else if (recipe instanceof FusionCoolingRecipe) {
            parameters.include(((FusionCoolingRecipe) recipe).extraEnergy, 0);
        }
    }

    private static void add(List<RecipeResourceFlow> flows, String key, @Nullable Object value,
          RecipeResourceFlow.Phase phase) {
        ImmutableResourceSnapshot resource = ImmutableResourceSnapshot.of(value);
        if (!resource.isEmpty()) flows.add(new RecipeResourceFlow(key, resource, phase));
    }

    private static void addOutput(List<RecipeResourceFlow> flows, String key, @Nullable Object value,
          double probability) {
        addOutput(flows, key, value, probability, false);
    }

    private static void addOutput(List<RecipeResourceFlow> flows, String key, @Nullable Object value,
          double probability, boolean stochastic) {
        ImmutableResourceSnapshot resource = ImmutableResourceSnapshot.of(value);
        if (!resource.isEmpty() && probability > 0) {
            flows.add(new RecipeResourceFlow(key, resource, RecipeResourceFlow.Phase.OUTPUT,
                  Math.min(1, probability), stochastic));
        }
    }

    private static long nonNegativeLong(double value) {
        if (!Double.isFinite(value) || value <= 0) return 0;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) value;
    }

    private static final class MutableParameters {
        private double extraEnergy;
        private int requiredTicks;
        private void include(double energy, int ticks) {
            if (Double.isFinite(energy) && energy > 0) extraEnergy = Math.max(extraEnergy, energy);
            if (ticks > 0) requiredTicks = Math.max(requiredTicks, ticks);
        }
    }
}
