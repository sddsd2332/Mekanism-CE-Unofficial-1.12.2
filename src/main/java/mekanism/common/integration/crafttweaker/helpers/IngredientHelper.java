package mekanism.common.integration.crafttweaker.helpers;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.item.IngredientAny;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.api.oredict.IOreDictEntry;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.crafttweaker.gas.CraftTweakerGasStack;
import mekanism.common.integration.crafttweaker.gas.IGasStack;
import mekanism.common.integration.crafttweaker.util.IngredientWrapper;
import mekanism.common.recipe.ingredients.IMekanismIngredient;
import mekanism.common.recipe.ingredients.IngredientMekIngredientWrapper;
import mekanism.common.recipe.ingredients.ItemStackMekIngredient;
import mekanism.common.recipe.ingredients.OredictMekIngredient;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.outputs.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public class IngredientHelper {

    private IngredientHelper() {
    }

    public static IIngredient optionalIngredient(IIngredient ingredient) {
        return ingredient != null ? ingredient : IngredientAny.INSTANCE;
    }

    public static boolean checkNotNull(String name, IIngredient... ingredients) {
        for (IIngredient ingredient : ingredients) {
            if (ingredient == null) {
                CraftTweakerAPI.logError(String.format("Required parameters missing for %s Recipe.", name));
                return false;
            }
        }
        return true;
    }

    private static IIngredient getIngredient(Object ingredient) {
        if (ingredient instanceof ItemStack stack) {
            return CraftTweakerMC.getIItemStack(stack);
        } else if (ingredient instanceof GasStack stack) {
            return new CraftTweakerGasStack(stack);
        } else if (ingredient instanceof Gas gas) {
            return new CraftTweakerGasStack(new GasStack(gas, 1));
        } else if (ingredient instanceof FluidStack stack) {
            return CraftTweakerMC.getILiquidStack(stack);
        } else if (ingredient instanceof Fluid fluid) {
            return CraftTweakerMC.getILiquidStack(new FluidStack(fluid, 1));
        }
        //TODO: Support other types of things like ore dict
        return IngredientAny.INSTANCE;
    }

    public static boolean matches(IIngredient input, IIngredient toMatch) {
        if (input instanceof IGasStack stack) {
            return GasHelper.matches(toMatch, stack);
        } else if (input instanceof IItemStack stack) {
            return toMatch != null && toMatch.matches(stack);
        } else if (input instanceof ILiquidStack liquidStack) {
            return toMatch != null && toMatch.matches(liquidStack);
        }
        //TODO: Support other types of things like ore dict
        return false;
    }

    public static boolean matches(Object input, IIngredient toMatch) {
        if (toMatch == IngredientAny.INSTANCE) {
            return true;
        }
        return matches(getIngredient(input), toMatch);
    }

    public static <INPUT extends MachineInput<INPUT>> boolean matches(INPUT in, IngredientWrapper toMatch) {
        if (in instanceof ItemStackInput input) {
            return matches(input.ingredient, toMatch.getIngredient());
        } else if (in instanceof GasInput input) {
            return matches(input.ingredient, toMatch.getIngredient());
        } else if (in instanceof FluidInput input) {
            return matches(input.ingredient, toMatch.getIngredient());
        } else if (in instanceof AdvancedMachineInput input) {
            return matches(input.itemStack, toMatch.getLeft()) && matches(input.gasType, toMatch.getRight());
        } else if (in instanceof ChemicalPairInput input) {
            return matches(input.leftGas, toMatch.getLeft()) && matches(input.rightGas, toMatch.getRight());
        } else if (in instanceof DoubleMachineInput input) {
            return matches(input.itemStack, toMatch.getLeft()) && matches(input.extraStack, toMatch.getRight());
        } else if (in instanceof PressurizedInput input) {
            return matches(input.getSolid(), toMatch.getLeft()) && matches(input.getFluid(), toMatch.getMiddle()) && matches(input.getGas(), toMatch.getRight());
        } else if (in instanceof InfusionInput input) {
            return matches(input.inputStack, toMatch.getIngredient()) && (toMatch.getInfuseType().isEmpty() || toMatch.getInfuseType().equalsIgnoreCase(input.infuse.getType().name));
        } else if (in instanceof GasAndFluidInput input) {
            return matches(input.ingredientGas, toMatch.getLeft()) && matches(input.ingredientFluid, toMatch.getRight());
        } else if (in instanceof RotaryInput input) {
            return matches(input.fluidInput, toMatch.getLeft()) && matches(input.gasInput, toMatch.getRight());
        } else if (in instanceof IntegerInput input) {
            return input.ingredient == toMatch.getAmount();
        }
        return false;
    }

    public static <OUTPUT extends MachineOutput<OUTPUT>> boolean matches(OUTPUT out, IngredientWrapper toMatch) {
        if (out instanceof ItemStackOutput output) {
            return matches(output.output, toMatch.getIngredient());
        } else if (out instanceof GasOutput output) {
            return matches(output.output, toMatch.getIngredient());
        } else if (out instanceof FluidOutput output) {
            return matches(output.output, toMatch.getIngredient());
        } else if (out instanceof ChanceOutput output) {
            return matches(output.primaryOutput, toMatch.getLeft()) && matches(output.secondaryOutput, toMatch.getRight());
        } else if (out instanceof ChemicalPairOutput output) {
            return matches(output.leftGas, toMatch.getLeft()) && matches(output.rightGas, toMatch.getRight());
        } else if (out instanceof PressurizedOutput output) {
            return matches(output.getItemOutput(), toMatch.getLeft()) && matches(output.getGasOutput(), toMatch.getRight());
        } else if (out instanceof RotaryOutput output) {
            return matches(output.gasOutput, toMatch.getLeft()) && matches(output.fluidOutput, toMatch.getRight());
        }
        return false;
    }

    public static IMekanismIngredient<ItemStack> getMekanismIngredient(IIngredient ingredient) {
        if (ingredient instanceof IOreDictEntry oreDictEntry) {
            return new OredictMekIngredient(oreDictEntry.getName());
        } else if (ingredient instanceof IItemStack) {
            return new ItemStackMekIngredient(CraftTweakerMC.getItemStack(ingredient));
        }
        return new IngredientMekIngredientWrapper(CraftTweakerMC.getIngredient(ingredient));
    }

    public static FluidStack toFluid(ILiquidStack fluid) {
        return fluid == null ? null : FluidRegistry.getFluidStack(fluid.getName(), fluid.getAmount());
    }
}
