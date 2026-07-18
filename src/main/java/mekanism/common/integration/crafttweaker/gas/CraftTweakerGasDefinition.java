package mekanism.common.integration.crafttweaker.gas;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.api.liquid.ILiquidDefinition;
import crafttweaker.api.minecraft.CraftTweakerMC;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.crafttweaker.CrafttweakerIntegration;
import net.minecraftforge.fluids.Fluid;

public class CraftTweakerGasDefinition implements IGasDefinition {

    private final Gas gas;

    public CraftTweakerGasDefinition(Gas gas) {
        this.gas = gas;
    }

    @Override
    public IGasStack asStack(int mb) {
        return new CraftTweakerGasStack(new GasStack(gas, mb));
    }

    @Override
    public String getName() {
        return gas.getName();
    }

    @Override
    public String getDisplayName() {
        return gas.getLocalizedName();
    }

    @Override
    public ILiquidDefinition getLiquid() {
        return gas.hasFluid() ? CraftTweakerMC.getILiquidDefinition(gas.getFluid()) : null;
    }

    @Override
    public void setLiquid(ILiquidDefinition liquid) {
        CraftTweakerAPI.apply(new SetGasLiquidFormAction(gas, liquid == null ? null : CraftTweakerMC.getFluid(liquid)));
    }

    @Override
    public int getTint() {
        return gas.getTint();
    }

    @Override
    public void setTint(int tint) {
        CraftTweakerAPI.apply(new SetGasTintAction(gas, tint));
    }

    public static class SetGasLiquidFormAction implements IAction {
        private final Gas gas;
        private final Fluid fluid;
        private String invalidReason;

        public SetGasLiquidFormAction(Gas gas, Fluid fluid) {
            this.gas = gas;
            this.fluid = fluid;
        }

        @Override
        public void apply() {
            CrafttweakerIntegration.cancelGasFluidMapping(gas.getName());
            gas.setFluid(fluid);
        }

        @Override
        public String describe() {
            return "Setting the liquid form of Mekanism gas '" + gas.getName() + "' to '" + fluid.getName() + "'";
        }

        @Override
        public boolean validate() {
            if (!CrafttweakerIntegration.isRegistryRegistrationOpen()) {
                invalidReason = "Mekanism gas fluids must be changed from a '#loader mekanism' script";
            } else if (gas == null) {
                invalidReason = "Mekanism gas must not be null";
            } else if (fluid == null) {
                invalidReason = "Mekanism gas fluid must not be null";
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

    public static class SetGasTintAction implements IAction {
        private final Gas gas;
        private final int tint;
        private String invalidReason;

        public SetGasTintAction(Gas gas, int tint) {
            this.gas = gas;
            this.tint = tint;
        }

        @Override
        public void apply() {
            gas.setTint(tint);
        }

        @Override
        public String describe() {
            return "Setting tint of Mekanism gas '" + gas.getName() + "' to #" + Integer.toHexString(tint);
        }

        @Override
        public boolean validate() {
            if (gas == null) {
                invalidReason = "Mekanism gas must not be null";
            } else if (tint < 0 || tint > 0xFFFFFF) {
                invalidReason = "Mekanism gas tint must be between 0x000000 and 0xFFFFFF";
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
