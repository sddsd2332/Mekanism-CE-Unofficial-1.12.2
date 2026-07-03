package mekanism.client.jei.machine.other;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.machines.RotaryRecipe;
import mekanism.common.util.LangUtils;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

public class RotaryCondensentratorRecipeWrapper implements IRecipeWrapper {

    public static final int GAS_AMOUNT = 1;
    public static final int FLUID_AMOUNT = 1;
    private Fluid fluidType;
    private Gas gasType;
    private boolean condensentrating;

    public RotaryCondensentratorRecipeWrapper(Fluid fluid, Gas gas, boolean b) {
        fluidType = fluid;
        gasType = gas;
        condensentrating = b;
    }

    public RotaryCondensentratorRecipeWrapper(RotaryRecipe recipe, boolean condensentrating) {
        FluidStack fluid = condensentrating ? recipe.getFluidOutput(recipe.getInput().gasInput) : recipe.getFluidInput();
        GasStack gas = condensentrating ? recipe.getGasInput() : recipe.getGasOutput(recipe.getInput().fluidInput);
        fluidType = fluid == null ? null : fluid.getFluid();
        gasType = gas == null ? null : gas.getGas();
        this.condensentrating = condensentrating;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        if (condensentrating) {
            ingredients.setInput(MekanismJEI.TYPE_GAS, new GasStack(gasType, GAS_AMOUNT));
            ingredients.setOutput(VanillaTypes.FLUID, new FluidStack(fluidType, FLUID_AMOUNT));
        } else {
            ingredients.setInput(VanillaTypes.FLUID, new FluidStack(fluidType, FLUID_AMOUNT));
            ingredients.setOutput(MekanismJEI.TYPE_GAS, new GasStack(gasType, GAS_AMOUNT));
        }
    }

    @Override
    public void drawInfo(@Nonnull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        minecraft.fontRenderer.drawString(condensentrating ? LangUtils.localize("gui.condensentrating") : LangUtils.localize("gui.decondensentrating"),
                42, 53, 0x404040, false);
    }

    public Gas getGasType() {
        return gasType;
    }

    public Fluid getFluidType() {
        return fluidType;
    }
}
