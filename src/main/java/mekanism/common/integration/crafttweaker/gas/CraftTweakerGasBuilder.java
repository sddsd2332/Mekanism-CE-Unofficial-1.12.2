package mekanism.common.integration.crafttweaker.gas;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.common.integration.crafttweaker.CrafttweakerIntegration;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenClass("mods.mekanism.GasBuilder")
@ZenRegister
public class CraftTweakerGasBuilder {

    private static final String DEFAULT_TEXTURE = "mekanism:blocks/liquid/liquid";

    private final String name;
    private String texture = DEFAULT_TEXTURE;
    private int tint = 0xFFFFFF;
    private String translationKey;
    private boolean visible = true;
    private double radioactivity;
    private String fluidName;

    CraftTweakerGasBuilder(String name) {
        this.name = normalize(name);
        this.translationKey = this.name;
    }

    @ZenMethod
    public CraftTweakerGasBuilder texture(String texture) {
        this.texture = normalize(texture);
        return this;
    }

    @ZenMethod
    public CraftTweakerGasBuilder tint(int tint) {
        this.tint = tint;
        return this;
    }

    @ZenMethod
    public CraftTweakerGasBuilder translationKey(String translationKey) {
        this.translationKey = normalize(translationKey);
        return this;
    }

    @ZenMethod
    public CraftTweakerGasBuilder visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    @ZenMethod
    public CraftTweakerGasBuilder hidden() {
        return visible(false);
    }

    @ZenMethod
    public CraftTweakerGasBuilder radioactivity(double radioactivity) {
        this.radioactivity = radioactivity;
        return this;
    }

    @ZenMethod
    public CraftTweakerGasBuilder fluid() {
        this.fluidName = name;
        return this;
    }

    @ZenMethod
    public CraftTweakerGasBuilder fluid(String fluidName) {
        this.fluidName = normalize(fluidName);
        return this;
    }

    @ZenMethod
    public IGasDefinition register() {
        RegisterGasAction action = new RegisterGasAction(this);
        CraftTweakerAPI.apply(action);
        return action.registered == null ? null : new CraftTweakerGasDefinition(action.registered);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static class RegisterGasAction implements IAction {

        private final String name;
        private final String texture;
        private final int tint;
        private final String translationKey;
        private final boolean visible;
        private final double radioactivity;
        private final String fluidName;
        private String invalidReason;
        private Gas registered;

        private RegisterGasAction(CraftTweakerGasBuilder builder) {
            name = builder.name;
            texture = builder.texture;
            tint = builder.tint;
            translationKey = builder.translationKey;
            visible = builder.visible;
            radioactivity = builder.radioactivity;
            fluidName = builder.fluidName;
        }

        @Override
        public void apply() {
            Gas gas = new Gas(name, new ResourceLocation(texture));
            gas.setTint(tint);
            gas.setTranslationKey(translationKey);
            gas.setVisible(visible);
            if (radioactivity > 0) {
                gas.setRadiation(radioactivity);
            }
            registered = GasRegistry.register(gas);
            if (fluidName != null) {
                registered.registerFluid(fluidName);
                FluidRegistry.addBucketForFluid(registered.getFluid());
            }
        }

        @Override
        public String describe() {
            return "Registering Mekanism gas '" + name + "'";
        }

        @Override
        public boolean validate() {
            invalidReason = validateParameters();
            return invalidReason == null;
        }

        @Override
        public String describeInvalid() {
            return invalidReason;
        }

        private String validateParameters() {
            if (!CrafttweakerIntegration.isRegistryRegistrationOpen()) {
                return "Mekanism gases must be registered from a '#loader mekanism' script";
            }
            if (name == null || name.isEmpty()) {
                return "Mekanism gas name must not be empty";
            }
            if (GasRegistry.containsGas(name)) {
                return "A Mekanism gas named '" + name + "' is already registered";
            }
            if (texture == null || texture.isEmpty()) {
                return "Mekanism gas texture must not be empty";
            }
            try {
                new ResourceLocation(texture);
            } catch (RuntimeException e) {
                return "Invalid Mekanism gas texture '" + texture + "'";
            }
            if (tint < 0 || tint > 0xFFFFFF) {
                return "Mekanism gas tint must be between 0x000000 and 0xFFFFFF";
            }
            if (translationKey == null || translationKey.isEmpty()) {
                return "Mekanism gas translation key must not be empty";
            }
            if (radioactivity < 0 || Double.isNaN(radioactivity) || Double.isInfinite(radioactivity)) {
                return "Mekanism gas radioactivity must be a finite value greater than or equal to zero";
            }
            if (fluidName != null && fluidName.isEmpty()) {
                return "Mekanism gas fluid name must not be empty";
            }
            return null;
        }
    }
}
