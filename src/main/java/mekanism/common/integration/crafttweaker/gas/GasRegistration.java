package mekanism.common.integration.crafttweaker.gas;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.liquid.ILiquidDefinition;
import crafttweaker.api.liquid.ILiquidStack;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.common.integration.crafttweaker.CrafttweakerIntegration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenClass("mods.mekanism.gas")
@ZenRegister
public class GasRegistration {

    private GasRegistration() {
    }

    @ZenMethod
    public static CraftTweakerGasBuilder create(String name) {
        return new CraftTweakerGasBuilder(name);
    }

    @ZenMethod
    public static IGasDefinition register(String name, int tint) {
        return create(name).tint(tint).register();
    }

    @ZenMethod
    public static IGasDefinition register(String name, String texture, int tint) {
        return create(name).texture(texture).tint(tint).register();
    }

    @ZenMethod
    public static IGasDefinition get(String name) {
        Gas gas = name == null ? null : GasRegistry.getGas(name);
        return gas == null ? null : new CraftTweakerGasDefinition(gas);
    }

    @ZenMethod
    public static boolean contains(String name) {
        return name != null && GasRegistry.containsGas(name);
    }

    @ZenMethod
    public static void setFluid(String gasName, ILiquidDefinition liquid) {
        Gas gas = gasName == null ? null : GasRegistry.getGas(gasName);
        Fluid fluid = liquid == null ? null : (Fluid) liquid.getInternal();
        CraftTweakerAPI.apply(new CraftTweakerGasDefinition.SetGasLiquidFormAction(gas, fluid));
    }

    @ZenMethod
    public static void setFluid(String gasName, ILiquidStack liquid) {
        Gas gas = gasName == null ? null : GasRegistry.getGas(gasName);
        Object internal = liquid == null ? null : liquid.getInternal();
        Fluid fluid = internal instanceof FluidStack stack ? stack.getFluid() : null;
        CraftTweakerAPI.apply(new CraftTweakerGasDefinition.SetGasLiquidFormAction(gas, fluid));
    }

    @ZenMethod
    public static void setFluid(String gasName, String fluidName) {
        CraftTweakerAPI.apply(new SetGasFluidByNameAction(gasName, fluidName));
    }

    private static class SetGasFluidByNameAction implements IAction {

        private final String gasName;
        private final String fluidName;
        private Gas gas;
        private String invalidReason;

        private SetGasFluidByNameAction(String gasName, String fluidName) {
            this.gasName = gasName == null ? null : gasName.trim();
            this.fluidName = fluidName == null ? null : fluidName.trim();
        }

        @Override
        public void apply() {
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid == null) {
                CrafttweakerIntegration.deferGasFluidMapping(gas.getName(), fluidName);
            } else {
                CrafttweakerIntegration.cancelGasFluidMapping(gas.getName());
                gas.setFluid(fluid);
            }
        }

        @Override
        public String describe() {
            return "Setting the liquid form of Mekanism gas '" + gasName + "' to '" + fluidName + "'";
        }

        @Override
        public boolean validate() {
            if (!CrafttweakerIntegration.isRegistryRegistrationOpen()) {
                invalidReason = "Mekanism gas fluids must be changed from a '#loader mekanism' script";
            } else if (gasName == null || gasName.isEmpty()) {
                invalidReason = "Mekanism gas name must not be empty";
            } else if ((gas = GasRegistry.getGas(gasName)) == null) {
                invalidReason = "Could not find Mekanism gas '" + gasName + "'";
            } else if (fluidName == null || fluidName.isEmpty()) {
                invalidReason = "Mekanism gas fluid name must not be empty";
            } else {
                invalidReason = null;
            }
            return invalidReason == null;
        }

        @Override
        public String describeInvalid() {
            return invalidReason;
        }
    }
}
