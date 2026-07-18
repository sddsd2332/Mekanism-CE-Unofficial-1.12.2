package mekanism.common.integration.crafttweaker.helpers;

import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IngredientAny;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.crafttweaker.gas.IGasStack;

public class GasHelper {

    private GasHelper() {
    }

    public static boolean matches(IIngredient ingredient, IGasStack gasStack) {
        if (ingredient == null) {
            return false;
        }
        if (ingredient == IngredientAny.INSTANCE) {
            return true;
        }
        if (ingredient instanceof IGasStack stack) {
            GasStack expected = toGas(stack);
            GasStack actual = toGas(gasStack);
            return expected != null && expected.isGasEqual(actual);
        }
        Object internal = ingredient.getInternal();
        if (internal instanceof IIngredient[] ingredients) {
            for (IIngredient alternative : ingredients) {
                if (matches(alternative, gasStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static GasStack toGas(IGasStack iStack) {
        if (iStack == null) {
            return null;
        }
        Object internal = iStack.getInternal();
        if (internal instanceof GasStack stack) {
            return stack.copy();
        }
        String name = iStack.getName();
        return name == null || GasRegistry.getGas(name) == null ? null : new GasStack(GasRegistry.getGas(name), iStack.getAmount());
    }

    public static GasStack[] toGases(IGasStack[] iStack) {
        GasStack[] stack = new GasStack[iStack.length];
        for (int i = 0; i < stack.length; i++) {
            stack[i] = toGas(iStack[i]);
        }
        return stack;
    }
}
