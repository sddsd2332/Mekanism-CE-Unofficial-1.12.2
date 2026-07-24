package mekanism.common.item;

import mekanism.api.RelativeSide;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.TestBootstrap;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.factory.TileEntityBasicFactory;
import mekanism.common.tier.BaseTier;
import mekanism.common.util.ItemDataUtils;
import mekanism.generators.common.item.ItemBlockGenerator;
import mekanism.multiblockmachine.common.item.ItemBlockLargeBase;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemBlockPlacementDataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
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

    @Test
    void factoryTypeIsAppliedBeforeStoredEnergy() {
        ItemBlockMachine item = new ItemBlockMachine(new Block(Material.ROCK));
        ItemStack stack = new ItemStack(item);
        RecipeType expected = RecipeType.PRC;
        item.setRecipeType(expected.ordinal(), stack);
        item.setOwnerUUID(stack, UUID.randomUUID());
        TrackingFactory factory = new TrackingFactory();
        factory.recipeWhenEnergyWasRestored = null;

        item.restorePlacementData(stack, null, null, BlockPos.ORIGIN, factory);

        assertEquals(expected, factory.getRecipeType());
        assertEquals(expected, factory.recipeWhenEnergyWasRestored);
    }

    @Test
    void creativeEnergyCubeKeepsStoredSideConfiguration() {
        ItemBlockEnergyCube item = new ItemBlockEnergyCube(new Block(Material.ROCK));
        ItemStack stack = creativeCube(item);
        TileEntityEnergyCube source = new TileEntityEnergyCube();
        source.configComponent.fillConfig(TransmissionType.ENERGY, DataType.NONE);
        source.configComponent.getConfigInfo(TransmissionType.ENERGY).setDataType(DataType.OUTPUT, RelativeSide.LEFT);
        source.configComponent.setEjecting(TransmissionType.ENERGY, false);
        source.configComponent.write(ItemDataUtils.getDataMap(stack));
        byte[] expected = source.configComponent.asByteArray(TransmissionType.ENERGY);

        TileEntityEnergyCube placed = new TileEntityEnergyCube();
        item.restorePlacementData(stack, null, null, BlockPos.ORIGIN, placed);

        assertArrayEquals(expected, placed.configComponent.asByteArray(TransmissionType.ENERGY));
        assertFalse(placed.configComponent.isEjecting(TransmissionType.ENERGY));
    }

    @Test
    void creativeEnergyCubeVariantsMatchModernInputOutputDefaults() {
        ItemBlockEnergyCube item = new ItemBlockEnergyCube(new Block(Material.ROCK));
        ItemStack empty = creativeCube(item);
        item.setCreativeDefaultSideConfig(empty, false);
        TileEntityEnergyCube emptyPlaced = new TileEntityEnergyCube();
        item.restorePlacementData(empty, null, null, BlockPos.ORIGIN, emptyPlaced);
        for (RelativeSide side : RelativeSide.SIDES) {
            assertEquals(DataType.INPUT, emptyPlaced.configComponent.getConfigInfo(TransmissionType.ENERGY).getDataType(side));
        }
        assertFalse(emptyPlaced.configComponent.isEjecting(TransmissionType.ENERGY));

        ItemStack filled = creativeCube(item);
        item.setCreativeDefaultSideConfig(filled, true);
        TileEntityEnergyCube filledPlaced = new TileEntityEnergyCube();
        item.restorePlacementData(filled, null, null, BlockPos.ORIGIN, filledPlaced);
        for (RelativeSide side : RelativeSide.SIDES) {
            assertEquals(DataType.OUTPUT, filledPlaced.configComponent.getConfigInfo(TransmissionType.ENERGY).getDataType(side));
        }
        assertTrue(filledPlaced.configComponent.isEjecting(TransmissionType.ENERGY));
    }

    @Test
    void malformedOwnerUuidCannotAbortMachinePlacement() {
        ItemBlockGenerator generator = new ItemBlockGenerator(new Block(Material.ROCK));
        ItemStack generatorStack = new ItemStack(generator);
        ItemDataUtils.setString(generatorStack, "ownerUUID", "not-a-uuid");
        assertNull(generator.getOwnerUUID(generatorStack));

        ItemBlockLargeBase large = new ItemBlockLargeBase(new Block(Material.ROCK), "test") {
        };
        ItemStack largeStack = new ItemStack(large);
        ItemDataUtils.setString(largeStack, "ownerUUID", "also-not-a-uuid");
        assertNull(large.getOwnerUUID(largeStack));
    }

    private static ItemStack creativeCube(ItemBlockEnergyCube item) {
        ItemStack stack = new ItemStack(item);
        item.setBaseTier(stack, BaseTier.CREATIVE);
        item.setOwnerUUID(stack, UUID.randomUUID());
        return stack;
    }

    private static class TrackingFactory extends TileEntityBasicFactory {

        private RecipeType recipeWhenEnergyWasRestored;

        @Override
        public void setEnergy(double energy) {
            recipeWhenEnergyWasRestored = getRecipeType();
            super.setEnergy(energy);
        }
    }
}
