package mekanism.common.integration.groovyscript.machinerecipe;

import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.compat.mods.mekanism.Mekanism;
import com.cleanroommc.groovyscript.compat.mods.mekanism.recipe.GasRecipeBuilder;
import com.cleanroommc.groovyscript.compat.mods.mekanism.recipe.VirtualizedMekanismRegistry;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.groovyscript.GrSMekanismAdd;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.IntegerInput;
import mekanism.common.recipe.machines.AmbientGasRecipe;
import mekanism.common.recipe.outputs.ChanceGasOutput;
import org.jetbrains.annotations.Nullable;

public class AmbientAccumulator extends VirtualizedMekanismRegistry<AmbientGasRecipe> {

    public AmbientAccumulator() {
        super(RecipeHandler.Recipe.AMBIENT_ACCUMULATOR);
    }

    public RecipeBuilder recipeBuilder() {
        return new RecipeBuilder();
    }

    public AmbientGasRecipe add(int dim, GasStack output) {
        return add(dim, output, 1);
    }


    public AmbientGasRecipe add(int dim, GasStack output, double chane) {
        GroovyLog.Msg msg = GroovyLog.msg("Error adding Mekanism Ambient Accumulator recipe").error();
        msg.add(Mekanism.isEmpty(output), () -> "output must not be empty");
        if (msg.postIfNotEmpty()) return null;
        AmbientGasRecipe recipe = new AmbientGasRecipe(dim, output.copy(), chane);
        recipeRegistry.put(recipe);
        addScripted(recipe);
        return recipe;
    }

    public boolean removeByInput(int dim) {
        GroovyLog.Msg msg = GroovyLog.msg("Error removing Mekanism Ambient Accumulator recipe").error();
        if (msg.postIfNotEmpty()) return false;

        boolean found = false;
        AmbientGasRecipe recipe = recipeRegistry.get().remove(new IntegerInput(dim));
        if (recipe != null) {
            addBackup(recipe);
            found = true;
        }
        if (!found) {
            removeError("could not find recipe for %", dim);
        }
        return found;
    }

    public static class RecipeBuilder extends GasRecipeBuilder<AmbientGasRecipe> {

        private int dim;
        private double chance;

        public RecipeBuilder dim(int dim) {
            this.dim = dim;
            return this;
        }

        public RecipeBuilder chance(double chance) {
            this.chance = chance;
            return this;
        }

        @Override
        public String getErrorMsg() {
            return "Error adding Mekanism Ambient Accumulator recipe";
        }

        @Override
        public void validate(GroovyLog.Msg msg) {
            validateItems(msg);
            validateGases(msg, 0, 0, 1, 1);
            msg.add(this.chance <= Integer.MIN_VALUE || this.chance >= Integer.MAX_VALUE, "dim must be between -2147483648 and 2147483647, yet it was {}", new Object[]{this.chance});
            msg.add(this.chance <= 0.0 || this.chance >= 1.0, "chance must be between 0 and 1.0, yet it was {}", new Object[]{this.chance});
        }

        @Override
        public @Nullable AmbientGasRecipe register() {
            if (!validate()) return null;
            ChanceGasOutput chanceGasOutput = new ChanceGasOutput(gasOutput.get(0), chance);
            IntegerInput input = new IntegerInput(dim);
            AmbientGasRecipe recipe = new AmbientGasRecipe(input, chanceGasOutput);
            GrSMekanismAdd.get().ambient.add(recipe);
            return recipe;
        }
    }

}
