package mekanism.generators.common;

import mekanism.common.TestBootstrap;
import mekanism.common.config.GeneratorsConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.generators.common.tile.reactor.TileEntityReactorPort;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FusionReactorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        if (MekanismConfig.current().generators == null) {
            MekanismConfig.current().generators = new GeneratorsConfig();
        }
    }

    @Test
    void onlyTopCenterIsTheControllerPosition() {
        assertTrue(FusionReactor.isControllerPosition(0, 2, 0));
        assertFalse(FusionReactor.isControllerPosition(1, 2, 0));
        assertFalse(FusionReactor.isControllerPosition(0, -2, 0));
    }

    @Test
    void formedReactorBlocksCannotBeClaimedByAnotherReactor() {
        FusionReactor owner = new FusionReactor(null);
        FusionReactor contender = new FusionReactor(null);
        TileEntityReactorPort port = new TileEntityReactorPort();
        port.setReactor(owner);

        owner.formed = true;
        assertFalse(contender.canClaimReactorBlock(port));
        assertTrue(owner.canClaimReactorBlock(port));

        owner.formed = false;
        assertTrue(contender.canClaimReactorBlock(port));
    }

    @Test
    void controllerRoundTripsThermalAndOperatingState() {
        TileEntityReactorController source = new TileEntityReactorController();
        FusionReactor reactor = new FusionReactor(source);
        source.setReactor(reactor);
        reactor.formed = true;
        reactor.setPlasmaTemp(1_234_567);
        reactor.getHeatCapacitor().updateHeatAndCapacity(25);
        reactor.getHeatCapacitor().setHeat(2_500);
        reactor.setInjectionRate(48);
        reactor.setBurning(true);
        NBTTagCompound saved = new NBTTagCompound();

        source.writeCustomNBT(saved);
        TileEntityReactorController loaded = new TileEntityReactorController();
        loaded.readCustomNBT(saved);

        assertNotNull(loaded.getReactor());
        assertTrue(loaded.getReactor().formed);
        assertEquals(1_234_567, loaded.getReactor().getPlasmaTemp());
        assertEquals(25, loaded.getReactor().getHeatCapacitor().getHeatCapacity());
        assertEquals(2_500, loaded.getReactor().getHeatCapacitor().getHeat());
        assertEquals(48, loaded.getReactor().getInjectionRate());
        assertTrue(loaded.getReactor().isBurning());
    }
}
