package mekanism.common.integration.groovyscript.machinerecipe;

import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.api.IIngredient;
import com.cleanroommc.groovyscript.compat.mods.mekanism.Mekanism;
import com.cleanroommc.groovyscript.compat.mods.mekanism.recipe.GasRecipeBuilder;
import com.cleanroommc.groovyscript.compat.mods.mekanism.recipe.VirtualizedMekanismRegistry;
import com.cleanroommc.groovyscript.helper.ingredient.IngredientHelper;
import mekanism.api.gas.GasStack;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.integration.groovyscript.GrSMekanismAdd;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class OrganicFarm extends VirtualizedMekanismRegistry<FarmRecipe> {

    public OrganicFarm() {
        super(RecipeHandler.Recipe.ORGANIC_FARM);
    }

    public RecipeBuilder recipeBuilder() {
        return new RecipeBuilder();
    }

    public FarmRecipe add(IIngredient ingredient, GasStack gasInput, ItemStack output) {
        return add(ingredient, gasInput, output, null, 0.0);

    }

    public FarmRecipe add(IIngredient ingredient, GasStack gasInput, ItemStack output, ItemStack secondary) {
        return add(ingredient, gasInput, output, secondary, 1.0);
    }

    public FarmRecipe add(IIngredient ingredient, GasStack gasInput, ItemStack output, ItemStack secondary, double chance) {
        GroovyLog.Msg msg = GroovyLog.msg("Error adding Mekanism Organic Farm recipe").error();
        boolean withSecondary = !IngredientHelper.isEmpty(secondary);
        msg.add(IngredientHelper.isEmpty(ingredient), () -> "input must not be empty");
        msg.add(Mekanism.isEmpty(gasInput), () -> "input must not be empty");
        msg.add(IngredientHelper.isEmpty(output), () -> "output must not be empty");
        msg.add(withSecondary && isInvalidChance(chance), () -> "chance must be between 0 and 1.0");
        if (msg.postIfNotEmpty()) return null;
        if (withSecondary) {
            secondary = secondary.copy();
        }
        output = output.copy();
        FarmOutput farmOutput = withSecondary ? new FarmOutput(output, secondary, chance) : new FarmOutput(output);
        return addRecipes(ingredient, farmOutput, (itemStack) -> new FarmInput(itemStack, gasInput));
    }

    public FarmRecipe add(IIngredient ingredient, FluidStack fluidInput, ItemStack output) {
        return add(ingredient, fluidInput, output, null, 0.0);
    }

    public FarmRecipe add(IIngredient ingredient, FluidStack fluidInput, ItemStack output, ItemStack secondary) {
        return add(ingredient, fluidInput, output, secondary, 1.0);
    }

    public FarmRecipe add(IIngredient ingredient, FluidStack fluidInput, ItemStack output, ItemStack secondary, double chance) {
        GroovyLog.Msg msg = GroovyLog.msg("Error adding Mekanism Organic Farm recipe").error();
        boolean withSecondary = !IngredientHelper.isEmpty(secondary);
        msg.add(IngredientHelper.isEmpty(ingredient), () -> "input must not be empty");
        msg.add(IngredientHelper.isEmpty(fluidInput), () -> "fluid input must not be empty");
        msg.add(IngredientHelper.isEmpty(output), () -> "output must not be empty");
        msg.add(withSecondary && isInvalidChance(chance), () -> "chance must be between 0 and 1.0");
        if (msg.postIfNotEmpty()) return null;
        if (withSecondary) {
            secondary = secondary.copy();
        }
        output = output.copy();
        FarmOutput farmOutput = withSecondary ? new FarmOutput(output, secondary, chance) : new FarmOutput(output);
        return addRecipes(ingredient, farmOutput, (itemStack) -> new FarmInput(itemStack, fluidInput));
    }

    private FarmRecipe addRecipes(IIngredient ingredient, FarmOutput output, FarmInputFactory inputFactory) {
        FarmRecipe recipe1 = null;
        for (ItemStack itemStack : ingredient.getMatchingStacks()) {
            FarmRecipe recipe = new FarmRecipe(inputFactory.create(itemStack.copy()), output);
            if (recipe1 == null) recipe1 = recipe;
            recipeRegistry.put(recipe);
            addScripted(recipe);
        }
        return recipe1;
    }

    public boolean removeByInput(IIngredient inputSolid, GasStack inputGas) {
        GroovyLog.Msg msg = GroovyLog.msg("Error removing Mekanism Organic Farm recipe").error();
        msg.add(IngredientHelper.isEmpty(inputSolid), () -> "input must not be empty");
        msg.add(Mekanism.isEmpty(inputGas), () -> "gas input must not be empty");
        if (msg.postIfNotEmpty()) return false;

        boolean found = false;
        for (ItemStack itemStack : inputSolid.getMatchingStacks()) {
            FarmRecipe recipe = recipeRegistry.get().remove(new FarmInput(itemStack, inputGas));
            if (recipe != null) {
                addBackup(recipe);
                found = true;
            }
        }
        if (!found) {
            removeError("could not find recipe for {} and {}", inputSolid, inputGas);
        }
        return found;
    }

    public boolean removeByInput(IIngredient inputSolid, FluidStack inputFluid) {
        GroovyLog.Msg msg = GroovyLog.msg("Error removing Mekanism Organic Farm recipe").error();
        msg.add(IngredientHelper.isEmpty(inputSolid), () -> "input must not be empty");
        msg.add(IngredientHelper.isEmpty(inputFluid), () -> "fluid input must not be empty");
        if (msg.postIfNotEmpty()) return false;

        boolean found = false;
        for (ItemStack itemStack : inputSolid.getMatchingStacks()) {
            FarmRecipe recipe = recipeRegistry.get().remove(new FarmInput(itemStack, inputFluid));
            if (recipe != null) {
                addBackup(recipe);
                found = true;
            }
        }
        if (!found) {
            removeError("could not find recipe for {} and {}", inputSolid, inputFluid);
        }
        return found;
    }

    private static boolean isInvalidChance(double chance) {
        return Double.isNaN(chance) || Double.isInfinite(chance) || chance < 0 || chance > 1;
    }

    @FunctionalInterface
    private interface FarmInputFactory {

        FarmInput create(ItemStack itemStack);
    }

    public static class RecipeBuilder extends GasRecipeBuilder<FarmRecipe> {

        private ItemStack extra = ItemStack.EMPTY;
        private double chance = 1.0;
        private final List<ItemStack> chanceItems = new ArrayList<>();
        private final List<Double> chances = new ArrayList<>();


        public RecipeBuilder extra(ItemStack extra) {
            this.extra = extra;
            return this;
        }

        public RecipeBuilder chance(double chance) {
            this.chance = chance;
            return this;
        }

        public RecipeBuilder chanceOutput(ItemStack output, double chance) {
            chanceItems.add(output);
            chances.add(chance);
            return this;
        }

        @Override
        public String getErrorMsg() {
            return "Error adding Mekanism Organic Farm recipe";
        }

        @Override
        public void validate(GroovyLog.Msg msg) {
            validateItems(msg, 1, 1, 1, 1);
            validateFluids(msg, 0, 1, 0, 0);
            validateGases(msg, 0, 1, 0, 0);
            msg.add(gasInput.isEmpty() == fluidInput.isEmpty(), "exactly one gas or fluid input must be specified");
            msg.add(isInvalidChance(chance), "chance must be between 0 and 1.0, yet it was {}", chance);
            int chanceOutputCount = chanceItems.size() + (extra.isEmpty() ? 0 : 1);
            msg.add(chanceOutputCount > FarmOutput.MAX_CHANCE_OUTPUTS, "at most {} chance outputs are supported, yet {} were specified",
                  FarmOutput.MAX_CHANCE_OUTPUTS, chanceOutputCount);
            for (int i = 0; i < chanceItems.size(); i++) {
                msg.add(IngredientHelper.isEmpty(chanceItems.get(i)), "chance output {} must not be empty", i);
                double outputChance = chances.get(i);
                msg.add(isInvalidChance(outputChance),
                      "chance output {} must have a chance between 0 and 1.0, yet it was {}", i, outputChance);
            }
        }

        @Override
        public @Nullable FarmRecipe register() {
            if (!validate()) return null;
            List<FarmChanceOutput> chanceOutputs = new ArrayList<>(chanceItems.size() + (extra.isEmpty() ? 0 : 1));
            if (!extra.isEmpty()) {
                chanceOutputs.add(new FarmChanceOutput(extra, chance));
            }
            for (int i = 0; i < chanceItems.size(); i++) {
                chanceOutputs.add(new FarmChanceOutput(chanceItems.get(i), chances.get(i)));
            }
            FarmOutput farmOutput = new FarmOutput(output.get(0), chanceOutputs);
            FarmRecipe recipe = null;
            for (ItemStack itemStack : input.get(0).getMatchingStacks()) {
                FarmInput farmInput = gasInput.isEmpty() ? new FarmInput(itemStack.copy(), fluidInput.get(0)) :
                      new FarmInput(itemStack.copy(), gasInput.get(0));
                FarmRecipe r = new FarmRecipe(farmInput, farmOutput);
                if (recipe == null) recipe = r;
                GrSMekanismAdd.get().organicFarm.add(r);
            }
            return recipe;
        }
    }

}
