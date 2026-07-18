package mekanism.common.integration.crafttweaker;

import mekanism.api.gas.Gas;
import mekanism.common.integration.crafttweaker.gas.CraftTweakerGasBuilder;
import mekanism.common.integration.crafttweaker.gas.CraftTweakerGasDefinition;
import mekanism.common.integration.crafttweaker.gas.GasRegistration;
import crafttweaker.api.liquid.ILiquidStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CraftTweakerRegistrationApiTest {

    @Test
    void gasBuilderExposesFluentRegistrationOptions() {
        CraftTweakerGasBuilder builder = GasRegistration.create("test_gas");

        assertNotNull(builder);
        assertSame(builder, builder.texture("mekanism:blocks/liquid/liquid"));
        assertSame(builder, builder.tint(0x123456));
        assertSame(builder, builder.translationKey("test_gas"));
        assertSame(builder, builder.visible(true));
        assertSame(builder, builder.hidden());
        assertSame(builder, builder.radioactivity(0.5));
        assertSame(builder, builder.fluid("liquid_test_gas"));
    }

    @Test
    void gasDefinitionWithoutFluidReturnsNullLiquid() {
        CraftTweakerGasDefinition definition = new CraftTweakerGasDefinition(new Gas("test_gas", 0x123456));

        assertNull(definition.getLiquid());
    }

    @Test
    void registryQueriesHandleNullNames() {
        assertFalse(GasRegistration.contains(null));
        assertFalse(InfuseRegistration.contains(null));
    }

    @Test
    void exposesLiquidStackFluidMappingOverload() throws NoSuchMethodException {
        assertNotNull(GasRegistration.class.getMethod("setFluid", String.class, ILiquidStack.class));
    }
}
