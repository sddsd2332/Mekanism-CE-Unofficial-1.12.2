package mekanism.common.integration.groovyscript;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.api.documentation.annotations.Comp;
import com.cleanroommc.groovyscript.api.documentation.annotations.Example;
import com.cleanroommc.groovyscript.api.documentation.annotations.MethodDescription;
import com.cleanroommc.groovyscript.api.documentation.annotations.Property;
import com.cleanroommc.groovyscript.api.documentation.annotations.RecipeBuilderDescription;
import com.cleanroommc.groovyscript.api.documentation.annotations.RecipeBuilderMethodDescription;
import com.cleanroommc.groovyscript.api.documentation.annotations.RecipeBuilderRegistrationMethod;
import com.cleanroommc.groovyscript.api.documentation.annotations.RegistryDescription;
import com.cleanroommc.groovyscript.helper.Alias;
import com.cleanroommc.groovyscript.registry.NamedRegistry;
import com.cleanroommc.groovyscript.sandbox.LoadStage;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@RegistryDescription(
      location = LoadStage.PRE_INIT,
      reloadability = RegistryDescription.Reloadability.DISABLED,
      category = RegistryDescription.Category.ENTRIES,
      priority = 50
)
public class GasRegistration extends NamedRegistry {

    private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("mekanism", "blocks/liquid/liquid");

    public GasRegistration() {
        super(Alias.generateOf("Gas", "Gases", "GasRegistry"));
    }

    @RecipeBuilderDescription(example = @Example("('groovy_gas').tint(0x4FC3F7).fluid('liquid_groovy_gas')"))
    public Builder create(String name) {
        return new Builder(this, name);
    }

    @MethodDescription(type = MethodDescription.Type.ADDITION, example = @Example("'groovy_gas', 0x4FC3F7"))
    public @Nullable Gas register(String name, int tint) {
        return create(name).tint(tint).register();
    }

    @MethodDescription(type = MethodDescription.Type.ADDITION, example = @Example("'groovy_gas', resource('example:blocks/gas/groovy_gas')"))
    public @Nullable Gas register(String name, ResourceLocation texture) {
        return create(name).texture(texture).register();
    }

    @MethodDescription(type = MethodDescription.Type.QUERY, example = @Example("'hydrogen'"))
    public @Nullable Gas get(String name) {
        return name == null ? null : GasRegistry.getGas(name);
    }

    @MethodDescription(type = MethodDescription.Type.QUERY, example = @Example("'hydrogen'"))
    public boolean contains(String name) {
        return name != null && GasRegistry.containsGas(name);
    }

    @MethodDescription(type = MethodDescription.Type.QUERY)
    public List<Gas> getAll() {
        return GasRegistry.getRegisteredGasses();
    }

    @MethodDescription(type = MethodDescription.Type.VALUE, example = @Example("'oxygen', fluid('other_mod_liquid_oxygen')"))
    public @Nullable Gas setFluid(String gasName, FluidStack fluidStack) {
        Gas gas = gasName == null ? null : GasRegistry.getGas(gasName);
        return setFluid(gas, fluidStack, gasName);
    }

    @MethodDescription(type = MethodDescription.Type.VALUE, example = @Example("gas('oxygen'), fluid('other_mod_liquid_oxygen')"))
    public @Nullable Gas setFluid(GasStack gasStack, FluidStack fluidStack) {
        Gas gas = gasStack == null ? null : gasStack.getGas();
        return setFluid(gas, fluidStack, gas == null ? null : gas.getName());
    }

    @MethodDescription(type = MethodDescription.Type.VALUE, example = @Example("'oxygen', 'other_mod_liquid_oxygen'"))
    public @Nullable Gas setFluid(String gasName, String fluidName) {
        Gas gas = gasName == null ? null : GasRegistry.getGas(gasName);
        return deferFluid(gas, fluidName, gasName);
    }

    @MethodDescription(type = MethodDescription.Type.VALUE, example = @Example("gas('oxygen'), 'other_mod_liquid_oxygen'"))
    public @Nullable Gas setFluid(GasStack gasStack, String fluidName) {
        Gas gas = gasStack == null ? null : gasStack.getGas();
        return deferFluid(gas, fluidName, gas == null ? null : gas.getName());
    }

    private @Nullable Gas setFluid(@Nullable Gas gas, @Nullable FluidStack fluidStack, @Nullable String gasName) {
        GroovyLog.Msg msg = GroovyLog.msg("Error changing Mekanism gas fluid").error();
        msg.add(!isPreInit(), () -> "gas fluids must be changed from a preInit script");
        msg.add(gas == null, () -> "could not find gas '" + gasName + "'");
        msg.add(fluidStack == null || fluidStack.getFluid() == null, () -> "fluid must not be empty");
        if (msg.postIfNotEmpty()) {
            return null;
        }
        DeferredGasFluidMappings.cancel(gas.getName());
        gas.setFluid(fluidStack.getFluid());
        return gas;
    }

    private @Nullable Gas deferFluid(@Nullable Gas gas, @Nullable String fluidName, @Nullable String gasName) {
        String normalizedFluidName = fluidName == null ? null : fluidName.trim();
        GroovyLog.Msg msg = GroovyLog.msg("Error changing Mekanism gas fluid").error();
        msg.add(!isPreInit(), () -> "gas fluids must be changed from a preInit script");
        msg.add(gas == null, () -> "could not find gas '" + gasName + "'");
        msg.add(normalizedFluidName == null || normalizedFluidName.isEmpty(), () -> "fluid name must not be empty");
        if (msg.postIfNotEmpty()) {
            return null;
        }
        DeferredGasFluidMappings.defer(gas.getName(), normalizedFluidName);
        return gas;
    }

    private @Nullable Gas register(Builder builder) {
        boolean invalidName = builder.name == null || builder.name.isEmpty();
        boolean invalidTranslationKey = builder.translationKey == null || builder.translationKey.isEmpty();

        GroovyLog.Msg msg = GroovyLog.msg("Error registering Mekanism gas").error();
        msg.add(!isPreInit(), () -> "gases must be registered from a preInit script");
        msg.add(invalidName, () -> "name must not be empty");
        msg.add(!invalidName && GasRegistry.containsGas(builder.name), () -> "a gas named '" + builder.name + "' is already registered");
        msg.add(builder.texture == null, () -> "texture must not be null");
        msg.add(builder.tint < 0 || builder.tint > 0xFFFFFF, () -> "tint must be between 0x000000 and 0xFFFFFF");
        msg.add(invalidTranslationKey, () -> "translation key must not be empty");
        msg.add(builder.radioactivity < 0 || Double.isNaN(builder.radioactivity) || Double.isInfinite(builder.radioactivity),
              () -> "radioactivity must be a finite value greater than or equal to zero");
        msg.add(builder.fluidName != null && builder.fluidName.isEmpty(), () -> "fluid name must not be empty");
        if (msg.postIfNotEmpty()) {
            return null;
        }

        Gas gas = new Gas(builder.name, builder.texture);
        gas.setTint(builder.tint);
        gas.setTranslationKey(builder.translationKey);
        gas.setVisible(builder.visible);
        if (builder.radioactivity > 0) {
            gas.setRadiation(builder.radioactivity);
        }
        Gas registered = GasRegistry.register(gas);
        if (builder.fluidName != null) {
            registered.registerFluid(builder.fluidName);
            FluidRegistry.addBucketForFluid(registered.getFluid());
        }
        return registered;
    }

    private boolean isPreInit() {
        return GroovyScript.isSandboxLoaded() && GroovyScript.getSandbox().getCurrentLoader() == LoadStage.PRE_INIT;
    }

    @Property(property = "name", comp = @Comp(not = "null"))
    @Property(property = "texture", defaultValue = "resource('mekanism:blocks/liquid/liquid')", comp = @Comp(not = "null"))
    @Property(property = "tint", defaultValue = "0xFFFFFF", comp = @Comp(gte = 0, lte = 0xFFFFFF))
    @Property(property = "translationKey", comp = @Comp(not = "null"))
    @Property(property = "visible", defaultValue = "true")
    @Property(property = "radioactivity", defaultValue = "0")
    @Property(property = "fluidName", defaultValue = "null")
    public static class Builder {

        private final GasRegistration owner;
        private final String name;
        private ResourceLocation texture = DEFAULT_TEXTURE;
        private int tint = 0xFFFFFF;
        private String translationKey;
        private boolean visible = true;
        private double radioactivity;
        private String fluidName;

        private Builder(GasRegistration owner, String name) {
            this.owner = owner;
            this.name = name == null ? null : name.trim();
            this.translationKey = this.name;
        }

        @RecipeBuilderMethodDescription(field = "texture")
        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        @RecipeBuilderMethodDescription(field = "texture")
        public Builder texture(String texture) {
            return texture(new ResourceLocation(texture));
        }

        @RecipeBuilderMethodDescription(field = "tint")
        public Builder tint(int tint) {
            this.tint = tint;
            return this;
        }

        @RecipeBuilderMethodDescription(field = "translationKey")
        public Builder translationKey(String translationKey) {
            this.translationKey = translationKey == null ? null : translationKey.trim();
            return this;
        }

        @RecipeBuilderMethodDescription(field = "visible")
        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        @RecipeBuilderMethodDescription(field = "visible")
        public Builder hidden() {
            return visible(false);
        }

        @RecipeBuilderMethodDescription(field = "radioactivity")
        public Builder radioactivity(double radioactivity) {
            this.radioactivity = radioactivity;
            return this;
        }

        @RecipeBuilderMethodDescription(field = "fluidName")
        public Builder fluid() {
            this.fluidName = name;
            return this;
        }

        @RecipeBuilderMethodDescription(field = "fluidName")
        public Builder fluid(String fluidName) {
            this.fluidName = fluidName == null ? null : fluidName.trim();
            return this;
        }

        @RecipeBuilderRegistrationMethod
        public @Nullable Gas register() {
            return owner.register(this);
        }
    }
}
