package mekanism.common.integration.crafttweaker;

import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.crafttweaker.gas.CraftTweakerGasStack;
import mekanism.common.integration.crafttweaker.helpers.GasHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftTweakerGasStackTest {

    private final Gas firstGas = new Gas("ct_ingredient_first", 0x112233);
    private final Gas secondGas = new Gas("ct_ingredient_second", 0x445566);

    @Test
    void exposesEmptyItemAndLiquidCollections() {
        CraftTweakerGasStack stack = stack(firstGas, 1);

        assertNotNull(stack.getItems());
        assertTrue(stack.getItems().isEmpty());
        assertNotNull(stack.getLiquids());
        assertTrue(stack.getLiquids().isEmpty());
    }

    @Test
    void supportsOrIngredientsForGasMatching() {
        IIngredient alternatives = stack(firstGas, 1).or(stack(secondGas, 1));

        assertTrue(alternatives.getItems().isEmpty());
        assertTrue(alternatives.getLiquids().isEmpty());
        assertTrue(GasHelper.matches(alternatives, stack(secondGas, 1)));
        assertFalse(GasHelper.matches(alternatives, stack(new Gas("ct_ingredient_other", 0x778899), 1)));
    }

    @Test
    void containsRequiresMatchingGasAndEnoughAmount() {
        CraftTweakerGasStack required = stack(firstGas, 5);

        assertTrue(required.contains(stack(firstGas, 5)));
        assertTrue(required.contains(stack(firstGas, 10)));
        assertFalse(required.contains(stack(firstGas, 4)));
        assertFalse(required.contains(stack(secondGas, 10)));
        assertFalse(required.contains(null));
    }

    @Test
    void rejectsItemOnlyIngredientDecorators() {
        CraftTweakerGasStack stack = stack(firstGas, 1);

        assertThrows(UnsupportedOperationException.class, () -> stack.transformNew(null));
        assertThrows(UnsupportedOperationException.class, () -> stack.transform(null));
        assertThrows(UnsupportedOperationException.class, () -> stack.only(null));
        assertThrows(UnsupportedOperationException.class, () -> stack.marked("input"));
    }

    @Test
    void itemTransformsLeaveTheInputUntouched() {
        CraftTweakerGasStack stack = stack(firstGas, 1);
        IItemStack item = (IItemStack) Proxy.newProxyInstance(
              IItemStack.class.getClassLoader(),
              new Class<?>[]{IItemStack.class},
              (proxy, method, args) -> null);

        assertSame(item, stack.applyTransform(item, null));
        assertSame(item, stack.applyNewTransform(item));
    }

    private static CraftTweakerGasStack stack(Gas gas, int amount) {
        return new CraftTweakerGasStack(new GasStack(gas, amount));
    }
}
