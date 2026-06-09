package mekanism.common.base;

import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;

import java.util.Map;

/**
 * Internal interface containing methods that are shared by many core Mekanism machines.  TODO: remove next minor MC version.
 *
 * @author AidanBrady
 */
public interface IElectricMachine<INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>> {

    /**
     * Update call for machines. Use instead of updateEntity() - it's called every tick.
     */
    void onAsyncUpdateServer();

    /**
     * Whether or not this machine can operate.
     *
     * @return can operate
     */
    boolean canOperate(RECIPE recipe);

    /**
     * Runs this machine's operation -- or smelts the item.
     */
    void operate(RECIPE recipe);


    /**
     * Gets this machine's recipes.
     */
    Map<INPUT, RECIPE> getRecipes();

    default int MultipleActions(RECIPE recipe, int ticksRequired) {
        if (recipe != null) {
            if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val() && ticksRequired <= 0) {
                return operate(recipe, 1 - ticksRequired);
            }
            operate(recipe);
            return 1;
        }
        return 0;
    }

    default int operate(RECIPE recipe, int operations) {
        int performed = 0;
        for (int i = 0; i < operations; i++) {
            if (!canOperate(recipe)) {
                break;
            }
            operate(recipe);
            performed++;
        }
        return performed;
    }


    RECIPE getRecipe();

    INPUT getInput();
}
