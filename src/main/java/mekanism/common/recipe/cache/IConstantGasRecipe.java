package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;

public interface IConstantGasRecipe<OUTPUT> {

    ItemStack getItemInput();

    GasStack getGasInput();

    boolean test(ItemStack itemInput, GasStack gasInput);

    OUTPUT getOutput(ItemStack itemInput, GasStack gasInput);

    boolean isOutputEmpty(OUTPUT output);
}
