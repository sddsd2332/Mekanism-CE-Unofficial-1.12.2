package mekanism.common.item;

import mekanism.common.tier.BaseTier;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemBlockPlacementDataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void transmitterRestoresTierWithoutUsingItemBlockPlacementHook() {
        ItemBlockTransmitter item = new ItemBlockTransmitter(new Block(Material.ROCK));
        ItemStack stack = new ItemStack(item);
        item.setBaseTier(stack, BaseTier.ULTIMATE);
        TileEntityLogisticalTransporter tile = new TileEntityLogisticalTransporter();

        item.restorePlacementData(stack, null, null, BlockPos.ORIGIN, tile);

        assertEquals(BaseTier.ULTIMATE, tile.getBaseTier());
    }
}
