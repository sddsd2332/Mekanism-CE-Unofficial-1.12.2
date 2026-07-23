package mekanism.common.tile.machine;

import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.util.InventoryUtils;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalMinerEjectionTest {

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
    void ejectRequestUsesInternalHandlerWhileMainCapabilityStaysHidden() {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        EnumFacing outputSide = miner.facing.getOpposite();
        assertNull(InventoryUtils.getItemHandler(miner, outputSide));

        miner.setInventorySlotContents(0, new ItemStack(Blocks.STONE, 12));
        TransitRequest request = miner.getEjectItemMap();
        assertFalse(request.isEmpty());

        TransitResponse response = request.createSimpleResponse();
        assertEquals(12, response.getSendingAmount());
        response.useAll();
        assertTrue(miner.getStackInSlot(0).isEmpty());
    }
}
