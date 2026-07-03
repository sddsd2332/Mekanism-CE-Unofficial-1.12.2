package mekanism.common.integration.groovyscript.machinerecipe;

import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.api.documentation.annotations.*;
import com.cleanroommc.groovyscript.compat.mods.mekanism.Mekanism;
import com.cleanroommc.groovyscript.compat.mods.mekanism.recipe.GasRecipeBuilder;
import com.cleanroommc.groovyscript.compat.mods.mekanism.recipe.VirtualizedMekanismRegistry;
import com.cleanroommc.groovyscript.helper.Alias;
import com.cleanroommc.groovyscript.helper.ingredient.IngredientHelper;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.groovyscript.GrSMekanismAdd;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.RotaryInput;
import mekanism.common.recipe.machines.RotaryRecipe;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@RegistryDescription
public class RotaryCondensentrator extends VirtualizedMekanismRegistry<RotaryRecipe> {

    public RotaryCondensentrator() {
        super(RecipeHandler.Recipe.ROTARY_CONDENSENTRATOR, Alias.generateOfClassAnd(RotaryCondensentrator.class, "RotaryCondensentrator"));
    }

    @RecipeBuilderDescription(example = @Example(".fluidInput(fluid('water')).gasInput(gas('water')).gasOutput(gas('water')).fluidOutput(fluid('water'))"))
    public RecipeBuilder recipeBuilder() {
        return new RecipeBuilder();
    }

    @MethodDescription(type = MethodDescription.Type.ADDITION, example = @Example("fluid('water'), gas('water'), gas('water'), fluid('water')"))
    public RotaryRecipe add(FluidStack fluidInput, GasStack gasInput, GasStack gasOutput, FluidStack fluidOutput) {
        GroovyLog.Msg msg = GroovyLog.msg("Error adding Mekanism Rotary Condensentrator recipe").error();
        msg.add(IngredientHelper.isEmpty(fluidInput), () -> "fluid input must not be empty");
        msg.add(Mekanism.isEmpty(gasInput), () -> "gas input must not be empty");
        msg.add(Mekanism.isEmpty(gasOutput), () -> "gas output must not be empty");
        msg.add(IngredientHelper.isEmpty(fluidOutput), () -> "fluid output must not be empty");
        if (msg.postIfNotEmpty()) return null;
        return addUnchecked(fluidInput.copy(), gasInput.copy(), gasOutput.copy(), fluidOutput.copy());
    }

    @MethodDescription(type = MethodDescription.Type.ADDITION, example = @Example("fluid('water'), gas('water')"))
    public RotaryRecipe addFluidToGas(FluidStack fluidInput, GasStack gasOutput) {
        GroovyLog.Msg msg = GroovyLog.msg("Error adding Mekanism Rotary Condensentrator fluid to gas recipe").error();
        msg.add(IngredientHelper.isEmpty(fluidInput), () -> "fluid input must not be empty");
        msg.add(Mekanism.isEmpty(gasOutput), () -> "gas output must not be empty");
        if (msg.postIfNotEmpty()) return null;
        return addUnchecked(fluidInput.copy(), null, gasOutput.copy(), null);
    }

    @MethodDescription(type = MethodDescription.Type.ADDITION, example = @Example("gas('water'), fluid('water')"))
    public RotaryRecipe addGasToFluid(GasStack gasInput, FluidStack fluidOutput) {
        GroovyLog.Msg msg = GroovyLog.msg("Error adding Mekanism Rotary Condensentrator gas to fluid recipe").error();
        msg.add(Mekanism.isEmpty(gasInput), () -> "gas input must not be empty");
        msg.add(IngredientHelper.isEmpty(fluidOutput), () -> "fluid output must not be empty");
        if (msg.postIfNotEmpty()) return null;
        return addUnchecked(null, gasInput.copy(), null, fluidOutput.copy());
    }

    private RotaryRecipe addUnchecked(FluidStack fluidInput, GasStack gasInput, GasStack gasOutput, FluidStack fluidOutput) {
        RotaryRecipe recipe = new RotaryRecipe(fluidInput, gasInput, gasOutput, fluidOutput);
        add(recipe);
        return recipe;
    }

    @MethodDescription(example = @Example("fluid('water')"))
    public boolean removeFluidToGasByInput(FluidStack input) {
        GroovyLog.Msg msg = GroovyLog.msg("Error removing Mekanism Rotary Condensentrator fluid to gas recipe").error();
        msg.add(IngredientHelper.isEmpty(input), () -> "input must not be empty");
        if (msg.postIfNotEmpty()) return false;
        return removeMatching(input, null);
    }

    @MethodDescription(example = @Example("gas('water')"))
    public boolean removeGasToFluidByInput(GasStack input) {
        GroovyLog.Msg msg = GroovyLog.msg("Error removing Mekanism Rotary Condensentrator gas to fluid recipe").error();
        msg.add(Mekanism.isEmpty(input), () -> "input must not be empty");
        if (msg.postIfNotEmpty()) return false;
        return removeMatching(null, input);
    }

    private boolean removeMatching(@Nullable FluidStack fluidInput, @Nullable GasStack gasInput) {
        List<RotaryInput> toRemove = new ArrayList<>();
        recipeRegistry.get().forEach((input, recipe) -> {
            if (input instanceof RotaryInput rotaryInput &&
                (fluidInput == null || recipe.hasFluidToGas() && rotaryInput.containsType(fluidInput)) &&
                (gasInput == null || recipe.hasGasToFluid() && rotaryInput.containsType(gasInput))) {
                toRemove.add(rotaryInput);
            }
        });
        if (toRemove.isEmpty()) {
            removeError("could not find recipe for %", fluidInput != null ? fluidInput : gasInput);
            return false;
        }
        toRemove.forEach(input -> addBackup(recipeRegistry.get().remove(input)));
        return true;
    }

    @Property(property = "fluidInput", comp = @Comp(gte = 0, lte = 1))
    @Property(property = "gasInput", comp = @Comp(gte = 0, lte = 1))
    @Property(property = "gasOutput", comp = @Comp(gte = 0, lte = 1))
    @Property(property = "fluidOutput", comp = @Comp(gte = 0, lte = 1))
    public static class RecipeBuilder extends GasRecipeBuilder<RotaryRecipe> {

        @Override
        public String getErrorMsg() {
            return "Error adding Mekanism Rotary Condensentrator recipe";
        }

        @Override
        public void validate(GroovyLog.Msg msg) {
            validateItems(msg);
            validateFluids(msg, 0, 1, 0, 1);
            validateGases(msg, 0, 1, 0, 1);
            boolean hasFluidToGas = !fluidInput.isEmpty() && !gasOutput.isEmpty();
            boolean hasGasToFluid = !gasInput.isEmpty() && !fluidOutput.isEmpty();
            msg.add(!hasFluidToGas && !hasGasToFluid, () -> "recipe must define a complete fluid to gas or gas to fluid conversion");
            msg.add(!fluidInput.isEmpty() && gasOutput.isEmpty(), () -> "fluid input requires gas output");
            msg.add(fluidInput.isEmpty() && !gasOutput.isEmpty(), () -> "gas output requires fluid input");
            msg.add(!gasInput.isEmpty() && fluidOutput.isEmpty(), () -> "gas input requires fluid output");
            msg.add(gasInput.isEmpty() && !fluidOutput.isEmpty(), () -> "fluid output requires gas input");
        }

        @Override
        @RecipeBuilderRegistrationMethod
        public @Nullable RotaryRecipe register() {
            if (!validate()) return null;
            FluidStack fluidIn = fluidInput.isEmpty() ? null : fluidInput.get(0);
            GasStack gasIn = gasInput.isEmpty() ? null : gasInput.get(0);
            GasStack gasOut = gasOutput.isEmpty() ? null : gasOutput.get(0);
            FluidStack fluidOut = fluidOutput.isEmpty() ? null : fluidOutput.get(0);
            return GrSMekanismAdd.get().rotaryCondensentrator.addUnchecked(fluidIn, gasIn, gasOut, fluidOut);
        }
    }
}
