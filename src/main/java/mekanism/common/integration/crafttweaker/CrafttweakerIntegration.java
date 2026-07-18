package mekanism.common.integration.crafttweaker;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.mc1120.commands.CTChatCommand;
import crafttweaker.mc1120.brackets.BracketHandlerLiquid;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.common.Mekanism;
import mekanism.common.integration.crafttweaker.commands.GasesCommand;
import mekanism.common.integration.crafttweaker.commands.InfuseTypesCommand;
import mekanism.common.integration.crafttweaker.commands.MekRecipesCommand;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CrafttweakerIntegration {

    public static final List<IAction> LATE_REMOVALS = new LinkedList<>();
    public static final List<IAction> LATE_ADDITIONS = new LinkedList<>();

    private static final Map<String, String> PENDING_GAS_FLUID_MAPPINGS = new LinkedHashMap<>();
    private static boolean registryRegistrationOpen = true;
    private static boolean registryScriptsLoaded;

    public static void loadRegistryScripts() {
        if (!registryScriptsLoaded) {
            registryScriptsLoaded = true;
            BracketHandlerLiquid.rebuildLiquidRegistry();
            CraftTweakerAPI.tweaker.loadScript(false, "mekanism");
        }
    }

    public static boolean isRegistryRegistrationOpen() {
        return registryRegistrationOpen;
    }

    public static void deferGasFluidMapping(String gasName, String fluidName) {
        PENDING_GAS_FLUID_MAPPINGS.put(gasName, fluidName);
    }

    public static void cancelGasFluidMapping(String gasName) {
        PENDING_GAS_FLUID_MAPPINGS.remove(gasName);
    }

    public static void finishRegistryRegistration() {
        PENDING_GAS_FLUID_MAPPINGS.forEach((gasName, fluidName) -> {
            Gas gas = GasRegistry.getGas(gasName);
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (gas == null) {
                CraftTweakerAPI.logError("Could not apply deferred Mekanism gas fluid mapping: gas '" + gasName + "' is not registered");
            } else if (fluid == null) {
                CraftTweakerAPI.logError("Could not apply deferred Mekanism gas fluid mapping: fluid '" + fluidName + "' is not registered");
            } else {
                gas.setFluid(fluid);
            }
        });
        PENDING_GAS_FLUID_MAPPINGS.clear();
        registryRegistrationOpen = false;
    }

    /**
     * Apply after (machine)recipes have been applied, but before the FMLLoadCompleteEvent is fired. Preferably in (post)init.
     * <p>
     * Applying to early causes remove to malfunction as no recipes have been registered. Applying to late causes JEI to not pickup the changes.
     */
    public static void applyRecipeChanges() {
        //Remove before addition, so recipes can be overwritten
        applyChanges(LATE_REMOVALS);
        applyChanges(LATE_ADDITIONS);
    }

    private static void applyChanges(List<IAction> actions) {
        actions.forEach(action -> {
            try {
                CraftTweakerAPI.apply(action);
            } catch (Exception e) {
                Mekanism.logger.error("CT action failed", e);
                CraftTweakerAPI.logError(Mekanism.MOD_NAME + " CT action failed", e);
            }
        });
    }

    public static void registerCommands() {
        CTChatCommand.registerCommand(new GasesCommand());
        CTChatCommand.registerCommand(new InfuseTypesCommand());
        CTChatCommand.registerCommand(new MekRecipesCommand());
    }
}
