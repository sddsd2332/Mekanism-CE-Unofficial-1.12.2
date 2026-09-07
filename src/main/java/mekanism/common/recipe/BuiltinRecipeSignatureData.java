package mekanism.common.recipe;

import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.recipe.cache.ImmutableResourceSnapshot;
import mekanism.common.recipe.cache.RecipeResourceFlow;
import mekanism.common.recipe.cache.RecipeSemanticsSnapshot;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.machines.*;
import mekanism.common.recipe.outputs.*;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Exact built-in types only: subclasses must explicitly describe any additional semantics. */
final class BuiltinRecipeSignatureData {

    private static final Set<Class<?>> RECIPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
          AlloyRecipe.class, AmbientGasRecipe.class, BrushedRecipe.class, CellExtractorRecipe.class,
          CellSeparatorRecipe.class, ChemicalInfuserRecipe.class, CombinerRecipe.class, CrusherRecipe.class,
          CrystallizerRecipe.class, DissolutionRecipe.class, EnrichmentRecipe.class, FarmRecipe.class,
          FusionCoolingRecipe.class, GasCentrifugeRecipe.class, GasStackFuelToEnergyRecipe.class,
          InjectionRecipe.class, IsotopicRecipe.class, MetallurgicInfuserRecipe.class,
          NucleosynthesizerRecipe.class, NutritionalRecipe.class, OsmiumCompressorRecipe.class,
          OxidationRecipe.class, PressurizedRecipe.class, PurificationRecipe.class, RecyclerRecipe.class,
          ReplicatorFluidStackRecipe.class, ReplicatorGasStackRecipe.class, ReplicatorItemStackRecipe.class,
          RollingRecipe.class, RotaryRecipe.class, SawmillRecipe.class, SeparatorRecipe.class,
          SmeltingRecipe.class, SolarNeutronRecipe.class, StampingRecipe.class,
          ThermalEvaporationRecipe.class, TurningRecipe.class, WasherRecipe.class)));

    private BuiltinRecipeSignatureData() {
    }

    @Nullable
    static Map<String, Object> capture(Object value) {
        Class<?> type = value.getClass();
        if (RECIPES.contains(type)) {
            MachineRecipe<?, ?, ?> recipe = (MachineRecipe<?, ?, ?>) value;
            Map<String, Object> result = fields("input", recipe.getInput(), "output", recipe.getOutput());
            if (type == NucleosynthesizerRecipe.class) {
                NucleosynthesizerRecipe r = (NucleosynthesizerRecipe) value;
                result.putAll(fields("extraEnergy", r.extraEnergy, "ticks", r.ticks));
            } else if (type == PressurizedRecipe.class) {
                PressurizedRecipe r = (PressurizedRecipe) value;
                result.putAll(fields("extraEnergy", r.extraEnergy, "ticks", r.ticks));
            } else if (type == ReplicatorItemStackRecipe.class) {
                ReplicatorItemStackRecipe r = (ReplicatorItemStackRecipe) value;
                result.putAll(fields("extraEnergy", r.extraEnergy, "ticks", r.ticks));
            } else if (type == ReplicatorGasStackRecipe.class) {
                ReplicatorGasStackRecipe r = (ReplicatorGasStackRecipe) value;
                result.putAll(fields("extraEnergy", r.extraEnergy, "ticks", r.ticks));
            } else if (type == ReplicatorFluidStackRecipe.class) {
                ReplicatorFluidStackRecipe r = (ReplicatorFluidStackRecipe) value;
                result.putAll(fields("extraEnergy", r.extraEnergy, "ticks", r.ticks));
            } else if (type == SeparatorRecipe.class) {
                result.put("energyUsage", ((SeparatorRecipe) value).energyUsage);
            } else if (type == FusionCoolingRecipe.class) {
                result.put("extraEnergy", ((FusionCoolingRecipe) value).extraEnergy);
            }
            return result;
        }
        if (type == ItemStackInput.class) return fields("item", ((ItemStackInput) value).ingredient);
        if (type == GasInput.class) return fields("gas", ((GasInput) value).ingredient);
        if (type == FluidInput.class) return fields("fluid", ((FluidInput) value).ingredient);
        if (type == IntegerInput.class) return fields("integer", ((IntegerInput) value).ingredient);
        if (type == AdvancedMachineInput.class) {
            AdvancedMachineInput input = (AdvancedMachineInput) value;
            return fields("item", input.itemStack, "gas", input.gasType);
        }
        if (type == DoubleMachineInput.class) {
            DoubleMachineInput input = (DoubleMachineInput) value;
            return fields("item", input.itemStack, "extra", input.extraStack);
        }
        if (type == InfusionInput.class) {
            InfusionInput input = (InfusionInput) value;
            return fields("item", input.inputStack, "infuse", input.infuse);
        }
        if (type == GasAndFluidInput.class) {
            GasAndFluidInput input = (GasAndFluidInput) value;
            return fields("gas", input.ingredientGas, "fluid", input.ingredientFluid);
        }
        if (type == ChemicalPairInput.class) {
            ChemicalPairInput input = (ChemicalPairInput) value;
            return fields("left", input.leftGas, "right", input.rightGas);
        }
        if (type == ChemicalGasInput.class) {
            ChemicalGasInput input = (ChemicalGasInput) value;
            return fields("input", input.input, "uu", input.uu);
        }
        if (type == FarmInput.class) {
            FarmInput input = (FarmInput) value;
            return fields("item", input.itemStack, "gas", input.gasInput, "fluid", input.fluidInput);
        }
        if (type == PressurizedInput.class) {
            PressurizedInput input = (PressurizedInput) value;
            return fields("item", input.getSolid(), "fluid", input.getFluid(), "gas", input.getGas());
        }
        if (type == NucleosynthesizerInput.class) {
            NucleosynthesizerInput input = (NucleosynthesizerInput) value;
            return fields("item", input.getSolid(), "gas", input.getGas());
        }
        if (type == RotaryInput.class) {
            RotaryInput input = (RotaryInput) value;
            return fields("gas", input.gasInput, "fluid", input.fluidInput);
        }
        if (type == ItemStackOutput.class) return fields("item", ((ItemStackOutput) value).output);
        if (type == GasOutput.class) return fields("gas", ((GasOutput) value).output);
        if (type == FluidOutput.class) return fields("fluid", ((FluidOutput) value).output);
        if (type == EnergyOutput.class) return fields("energy", ((EnergyOutput) value).energyOutput);
        if (type == ChemicalPairOutput.class) {
            ChemicalPairOutput output = (ChemicalPairOutput) value;
            return fields("left", output.leftGas, "right", output.rightGas);
        }
        if (type == PressurizedOutput.class) {
            PressurizedOutput output = (PressurizedOutput) value;
            return fields("item", output.getItemOutput(), "gas", output.getGasOutput());
        }
        if (type == RotaryOutput.class) {
            RotaryOutput output = (RotaryOutput) value;
            return fields("gas", output.gasOutput, "fluid", output.fluidOutput);
        }
        if (type == ChanceOutput.class) {
            ChanceOutput output = (ChanceOutput) value;
            return fields("primary", output.primaryOutput, "secondary", output.secondaryOutput, "chance", output.secondaryChance);
        }
        if (type == ChanceOutput2.class) {
            ChanceOutput2 output = (ChanceOutput2) value;
            return fields("output", output.primaryOutput, "chance", output.primaryChance);
        }
        if (type == ChanceGasOutput.class) {
            ChanceGasOutput output = (ChanceGasOutput) value;
            return fields("output", output.output, "chance", output.primaryChance);
        }
        if (type == FarmOutput.class) {
            FarmOutput output = (FarmOutput) value;
            return fields("primary", output.getGuaranteedOutput(), "chances", output.getChanceOutputs());
        }
        if (type == FarmChanceOutput.class) {
            FarmChanceOutput output = (FarmChanceOutput) value;
            return fields("output", output.getOutput(), "chance", output.getChance());
        }
        if (type == ImmutableResourceSnapshot.class) {
            ImmutableResourceSnapshot resource = (ImmutableResourceSnapshot) value;
            return fields("kind", resource.getKind(), "amount", resource.getAmount(), "descriptor", resource.getDescriptor(),
                  "item", resource.getItemCopy(), "fluid", resource.getFluidCopy(), "gas", resource.getGasCopy());
        }
        if (type == RecipeResourceFlow.class) {
            RecipeResourceFlow flow = (RecipeResourceFlow) value;
            return fields("key", flow.getKey(), "resource", flow.getResource(), "phase", flow.getPhase(),
                  "probability", flow.getProbability(), "stochastic", flow.isStochastic());
        }
        if (type == RecipeSemanticsSnapshot.class) {
            RecipeSemanticsSnapshot semantics = (RecipeSemanticsSnapshot) value;
            return fields("inputs", semantics.getInputs(), "outputs", semantics.getOutputs(),
                  "extraEnergy", semantics.getExtraEnergy(), "ticks", semantics.getRequiredTicksOverride(),
                  "supported", semantics.isSupported());
        }
        return null;
    }

    static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]);
        return result;
    }
}
