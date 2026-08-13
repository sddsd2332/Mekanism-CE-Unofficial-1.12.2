package mekanism.qioprocessing.common.block;

import mekanism.common.TestBootstrap;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.world.DummyWorld;
import mekanism.qioprocessing.common.tile.TileEntityQIOManagementTerminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BlockQIOProcessingTerminalTest {

    private static BlockQIOProcessingTerminal terminal;
    private static DummyWorld world;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        terminal = new BlockQIOProcessingTerminal(TileEntityQIOManagementTerminal::new);
        world = new DummyWorld();
    }

    @Test
    void terminalUsesTheQioDashboardPanelForEveryFacing() {
        assertBox(EnumFacing.UP, 1, 0, 1, 15, 1, 15);
        assertBox(EnumFacing.DOWN, 1, 15, 1, 15, 16, 15);
        assertBox(EnumFacing.NORTH, 1, 1, 15, 15, 15, 16);
        assertBox(EnumFacing.SOUTH, 1, 1, 0, 15, 15, 1);
        assertBox(EnumFacing.WEST, 15, 1, 1, 16, 15, 15);
        assertBox(EnumFacing.EAST, 0, 1, 1, 1, 15, 15);
        assertFalse(terminal.isOpaqueCube(terminal.getDefaultState()));
        assertFalse(terminal.isFullCube(terminal.getDefaultState()));
        assertFalse(terminal.isFullBlock(terminal.getDefaultState()));
    }

    @Test
    void placementFaceBecomesThePanelFacing() {
        for (EnumFacing facing : EnumFacing.VALUES) {
            IBlockState placed = terminal.getStateForPlacement(world, BlockPos.ORIGIN,
                  facing, 0.5F, 0.5F, 0.5F, 0,
                  (EntityLivingBase) null, EnumHand.MAIN_HAND);
            assertEquals(facing, placed.getValue(BlockStateFacing.facingProperty));
        }
    }

    private static void assertBox(EnumFacing facing, double minX, double minY,
          double minZ, double maxX, double maxY, double maxZ) {
        IBlockState state = terminal.getDefaultState().withProperty(
              BlockStateFacing.facingProperty, facing);
        AxisAlignedBB box = terminal.getBoundingBox(state, new StateAccess(state),
              BlockPos.ORIGIN);
        assertEquals(minX / 16D, box.minX, 1.0E-9);
        assertEquals(minY / 16D, box.minY, 1.0E-9);
        assertEquals(minZ / 16D, box.minZ, 1.0E-9);
        assertEquals(maxX / 16D, box.maxX, 1.0E-9);
        assertEquals(maxY / 16D, box.maxY, 1.0E-9);
        assertEquals(maxZ / 16D, box.maxZ, 1.0E-9);
    }

    private static final class StateAccess implements IBlockAccess {

        private final IBlockState state;

        private StateAccess(IBlockState state) {
            this.state = state;
        }

        @Override public TileEntity getTileEntity(BlockPos pos) { return null; }
        @Override public IBlockState getBlockState(BlockPos pos) { return state; }
        @Override public int getCombinedLight(BlockPos pos, int lightValue) {
            return lightValue;
        }
        @Override public boolean isAirBlock(BlockPos pos) { return false; }
        @Override public Biome getBiome(BlockPos pos) { return Biome.getBiome(1); }
        @Override public int getStrongPower(BlockPos pos, EnumFacing direction) { return 0; }
        @Override public WorldType getWorldType() { return WorldType.DEFAULT; }
        @Override public boolean isSideSolid(BlockPos pos, EnumFacing side,
              boolean defaultValue) {
            return defaultValue;
        }
    }
}
