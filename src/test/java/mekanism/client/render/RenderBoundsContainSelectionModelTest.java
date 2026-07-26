package mekanism.client.render;

import mekanism.common.TestBootstrap;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.GeneratorsConfig;
import mekanism.common.config.MultiblockMachineConfig;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.TileEntityResistiveHeater;
import mekanism.common.tile.TileEntitySecurityDesk;
import mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer;
import mekanism.common.tile.machine.TileEntityChemicalCrystallizer;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import mekanism.common.tile.machine.TileEntityIsotopicCentrifuge;
import mekanism.common.tile.machine.TileEntityNutritionalLiquifier;
import mekanism.common.tile.machine.TileEntityRotaryCondensentrator;
import mekanism.common.tile.machine.TileEntitySeismicVibrator;
import mekanism.common.tile.machine.TileEntitySolarNeutronActivator;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.generators.common.tile.TileEntityAdvancedSolarGenerator;
import mekanism.generators.common.tile.TileEntityBioGenerator;
import mekanism.generators.common.tile.TileEntityGasGenerator;
import mekanism.generators.common.tile.TileEntityHeatGenerator;
import mekanism.generators.common.tile.TileEntitySolarGenerator;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeGasGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalWasher;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderBoundsContainSelectionModelTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -4);
    private boolean previousSelectionSetting;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        if (MekanismConfig.current().generators == null) {
            MekanismConfig.current().generators = new GeneratorsConfig();
        }
        if (MekanismConfig.current().multiblock == null) {
            MekanismConfig.current().multiblock = new MultiblockMachineConfig();
        }
    }

    @BeforeEach
    void enableSelectionWireframes() {
        previousSelectionSetting = MekanismConfig.current().client.enableSelectionWireframeRendering.val();
        MekanismConfig.current().client.enableSelectionWireframeRendering.set(true);
    }

    @AfterEach
    void restoreSelectionWireframes() {
        MekanismConfig.current().client.enableSelectionWireframeRendering.set(previousSelectionSetting);
    }

    @Test
    void modelBasedRenderersAreInsideTheirTileBounds() {
        assertAll(
              () -> assertContains(new TileEntitySecurityDesk()),
              () -> assertContains(new TileEntityEnergyCube()),
              () -> assertContains(new TileEntityQuantumEntangloporter()),
              () -> assertContains(new TileEntityResistiveHeater()),
              () -> assertContains(new TileEntityAntiprotonicNucleosynthesizer()),
              () -> assertContains(new TileEntityChemicalCrystallizer()),
              () -> assertContains(new TileEntityChemicalDissolutionChamber()),
              () -> assertContains(new TileEntityNutritionalLiquifier()),
              () -> assertContains(new TileEntityRotaryCondensentrator()),
              () -> assertContains(new TileEntitySeismicVibrator()),
              () -> assertContains(new TileEntitySolarNeutronActivator()),
              () -> assertContains(new TileEntityIsotopicCentrifuge()),
              () -> assertContains(new TileEntityBioGenerator()),
              () -> assertContains(new TileEntityGasGenerator()),
              () -> assertContains(new TileEntityHeatGenerator()),
              () -> assertContains(new TileEntitySolarGenerator()),
              () -> assertContains(new TileEntityAdvancedSolarGenerator()),
              () -> assertContains(new TileEntityWindGenerator()),
              () -> assertContains(new TileEntityDigitalMiner()),
              () -> assertContains(new TileEntityLargeGasGenerator()),
              () -> assertContains(new TileEntityLargeWindGenerator()),
              () -> assertContains(new TileEntityLargeChemicalInfuser()),
              () -> assertContains(new TileEntityLargeChemicalWasher()),
              () -> assertContains(new TileEntityLargeElectrolyticSeparator()),
              () -> assertContains(new TileEntityLargeSolarNeutronActivator())
        );
    }

    private static void assertContains(TileEntity tile) {
        tile.setPos(ORIGIN);
        if (tile instanceof mekanism.common.tile.prefab.TileEntityBasicBlock basic) {
            basic.facing = EnumFacing.NORTH;
        }
        IBlockState state = Blocks.STONE.getDefaultState();
        IBlockAccess world = new SingleTileBlockAccess(tile, state);
        JsonModelSelectionBoxCache.OutlineBox[] outlines = SpecialSelectionWireframeRegistry.getWireframes(state, world, ORIGIN);
        AxisAlignedBB modelBounds = null;
        for (JsonModelSelectionBoxCache.OutlineBox outline : outlines) {
            if (outline == null || outline.getBounds() == null) {
                continue;
            }
            AxisAlignedBB worldBounds = outline.getBounds().offset(ORIGIN);
            modelBounds = modelBounds == null ? worldBounds : modelBounds.union(worldBounds);
        }
        AxisAlignedBB renderBounds = tile.getRenderBoundingBox();
        System.out.println(tile.getClass().getSimpleName() + " outlines=" + outlines.length + " model=" + modelBounds + " render=" + renderBounds);
        final AxisAlignedBB measuredModelBounds = modelBounds;
        assertTrue(measuredModelBounds != null && renderBounds.minX <= measuredModelBounds.minX + 1.0E-6D
                    && renderBounds.minY <= measuredModelBounds.minY + 1.0E-6D
                    && renderBounds.minZ <= measuredModelBounds.minZ + 1.0E-6D
                    && renderBounds.maxX >= measuredModelBounds.maxX - 1.0E-6D
                    && renderBounds.maxY >= measuredModelBounds.maxY - 1.0E-6D
                    && renderBounds.maxZ >= measuredModelBounds.maxZ - 1.0E-6D,
              () -> tile.getClass().getSimpleName() + " model=" + measuredModelBounds + " render=" + renderBounds);
    }

    private static final class SingleTileBlockAccess implements IBlockAccess {

        private final TileEntity tile;
        private final IBlockState state;

        private SingleTileBlockAccess(TileEntity tile, IBlockState state) {
            this.tile = tile;
            this.state = state;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return ORIGIN.equals(pos) ? tile : null;
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
