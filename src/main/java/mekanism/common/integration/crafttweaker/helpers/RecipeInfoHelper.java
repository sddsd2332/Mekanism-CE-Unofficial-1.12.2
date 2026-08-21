package mekanism.common.integration.crafttweaker.helpers;

import crafttweaker.mc1120.item.MCItemStack;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.Map.Entry;

public class RecipeInfoHelper {

    private RecipeInfoHelper() {
    }

    public static String getRecipeInfo(Entry<? extends MachineInput<?>, ? extends MachineRecipe<?, ?, ?>> recipe) {
        MachineOutput<?> output = recipe.getValue().recipeOutput;
        if (output instanceof ItemStackOutput stackOutput) {
            return getItemName(stackOutput.output);
        } else if (output instanceof GasOutput gasOutput) {
            return getGasName(gasOutput.output);
        } else if (output instanceof FluidOutput fluidOutput) {
            return getFluidName(fluidOutput.output);
        } else if (output instanceof ChemicalPairOutput out) {
            return "[" + getGasName(out.leftGas) + ", " + getGasName(out.rightGas) + "]";
        } else if (output instanceof ChanceOutput chanceOutput) {
            return getItemName(chanceOutput.primaryOutput);
        } else if (output instanceof FarmOutput farmOutput) {
            return getItemName(farmOutput.getGuaranteedOutput());
        } else if (output instanceof PressurizedOutput out) {
            return "[" + getItemName(out.getItemOutput()) + ", " + getGasName(out.getGasOutput()) + "]";
        }
        return null;
    }

    public static String getGasName(GasStack stack) {
        if (stack == null || stack.getGas() == null) {
            return "nothing";
        }
        return stack.amount > 1 ? String.format("<gas:%s> * %s", stack.getGas().getName(), stack.amount) : getGasName(stack.getGas());
    }

    public static String getGasName(Gas gas) {
        if (gas == null) {
            return "nothing";
        }
        return String.format("<gas:%s>", gas.getName());
    }

    public static String getFluidName(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) {
            return "nothing";
        }
        return stack.amount > 1 ? String.format("<liquid:%s> * %s", stack.getFluid().getName(), stack.amount) : getFluidName(stack.getFluid());
    }

    public static String getFluidName(Fluid fluid) {
        if (fluid == null) {
            return "nothing";
        }
        return String.format("<liquid:%s>", fluid.getName());
    }

    public static String getItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "nothing";
        }
        return new MCItemStack(stack).toString();
    }
}
