package mekanism.generators.common.tile.fission;

import mekanism.api.gas.GasStack;
import mekanism.common.MekanismFluids;
import mekanism.common.content.network.distribution.GasHandlerTarget;
import mekanism.common.util.GasUtils;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FissionReactorPortEmissionTest {

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        Loader loader = Loader.instance();
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(loader) == null) {
            namedMods.set(loader, Collections.emptyMap());
        }
        Bootstrap.register();
    }

    @Test
    void outputTemplateIsIsolatedAndReusedForStableGasType() {
        TileEntityFissionReactorPort port = new TileEntityFissionReactorPort();
        GasStack stored = new GasStack(MekanismFluids.NuclearWaste, 40);

        GasStack first = port.prepareGasForEmission(stored);
        assertEquals(40, first.amount);
        stored.amount = 25;
        GasStack second = port.prepareGasForEmission(stored);
        GasStack changedType = port.prepareGasForEmission(new GasStack(MekanismFluids.SuperheatedSodium, 10));

        assertNotSame(stored, first);
        assertSame(first, second);
        assertEquals(25, second.amount);
        assertNotSame(second, changedType);
        assertEquals(10, changedType.amount);
        assertNull(port.prepareGasForEmission(null));
    }

    @Test
    void callerOwnedTargetIsResetEvenWhenNothingCanBeEmitted() {
        TileEntityFissionReactorPort port = new TileEntityFissionReactorPort();
        GasHandlerTarget target = port.getGasEmitTarget();
        target.addHandler(EnumFacing.NORTH, null);

        assertEquals(0, GasUtils.emit(new GasStack(MekanismFluids.NuclearWaste, 1), null, Collections.emptySet(), target));
        assertEquals(0, target.getHandlerCount());
        assertSame(target, port.getGasEmitTarget());
    }
}
