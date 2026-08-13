package mekanism.common.block;

import mekanism.common.TestBootstrap;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.inventory.BinMekanismInventory;
import mekanism.common.item.ItemBlockBasic;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.BinTier;
import mekanism.common.tile.TileEntityBin;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.registries.GameData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static mekanism.common.block.states.BlockStateBasic.BasicBlock.BASIC_BLOCK_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BlockBasicPickBlockTest {

    private static final BlockPos POS = BlockPos.ORIGIN;

    private BlockBasic mappedBlock;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @AfterEach
    void removeTemporaryBlockItemMapping() {
        if (mappedBlock != null) {
            GameData.getBlockItemMap().remove(mappedBlock);
        }
    }

    @Test
    void creativeBinPickBlockUsesCreativeCapacityWhenCopyingContents() {
        mappedBlock = BlockBasic.getBlockBasic(BASIC_BLOCK_1);
        ItemBlockBasic item = new ItemBlockBasic(mappedBlock);
        GameData.getBlockItemMap().put(mappedBlock, item);

        TileEntityBin bin = new TestBin();
        bin.tier = BinTier.CREATIVE;
        bin.setItemType(new ItemStack(Blocks.STONE));
        bin.setItemCount(5_000);

        IBlockState state = mappedBlock.getStateFromMeta(BasicBlockType.BIN.meta);
        ItemStack picked = mappedBlock.getDropItem(state, new SingleTileBlockAccess(bin, state), POS);
        BinMekanismInventory pickedInventory = BinMekanismInventory.create(picked);

        assertEquals(BaseTier.CREATIVE, item.getBaseTier(picked));
        assertNotNull(pickedInventory);
        assertEquals(5_000, pickedInventory.getItemCount());
    }

    private static class TestBin extends TileEntityBin {

        @Override
        public void onContentsChanged() {
        }

        @Override
        public void markDirty() {
        }
    }

    private static class SingleTileBlockAccess implements IBlockAccess {

        private final TileEntity tile;
        private final IBlockState state;

        private SingleTileBlockAccess(TileEntity tile, IBlockState state) {
            this.tile = tile;
            this.state = state;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return POS.equals(pos) ? tile : null;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return state;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return lightValue;
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return false;
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return Biome.getBiome(1);
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            return defaultValue;
        }
    }
}
