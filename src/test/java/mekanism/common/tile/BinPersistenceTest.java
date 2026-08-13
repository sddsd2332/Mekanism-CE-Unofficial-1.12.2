package mekanism.common.tile;

import mekanism.common.TestBootstrap;
import mekanism.common.tier.BinTier;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinPersistenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void creativeBinLoadsInventoryBeforeWorldIsAssigned() {
        TileEntityBin saved = new TileEntityBin();
        saved.tier = BinTier.CREATIVE;
        saved.getBinSlot().setStackUncheckedNoUpdate(new ItemStack(Items.IRON_INGOT, 64));
        NBTTagCompound data = new NBTTagCompound();
        saved.writeCustomNBT(data);

        TileEntityBin loaded = new TileEntityBin();
        assertTrue(loaded.getWorld() == null);
        assertDoesNotThrow(() -> loaded.readFromNBT(data));
        assertEquals(BinTier.CREATIVE, loaded.tier);
        assertEquals(64, loaded.getItemCount());
        assertEquals(Items.IRON_INGOT, loaded.getBinSlot().getStack().getItem());
    }
}
