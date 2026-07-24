package mekanism.generators.common.tile;

import mekanism.api.Coord4D;
import mekanism.api.IEvaporationSolar;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntityAdvancedSolarGenerator extends TileEntitySolarGenerator implements IBoundingBlock, IEvaporationSolar {

    public TileEntityAdvancedSolarGenerator() {
        super("AdvancedSolarGenerator", MekanismConfig.current().generators.advancedSolarGeneratorStorage.val(), MekanismConfig.current().generators.advancedSolarGeneration.val() * 2);
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return side == facing;
    }

    @Override
    protected float getConfiguredMax() {
        return (float) MekanismConfig.current().generators.advancedSolarGeneration.val();
    }

    @Override
    public void collectBoundingBlocks(java.util.function.BiConsumer<BlockPos, Boolean> consumer) {
        consumer.accept(getPos().up(), false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                consumer.accept(getPos().add(x, 2, z), false);
            }
        }
    }

    @Override
    public void onPlace() {
        tryPlaceBoundingBlocks(world, Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        removeBoundingBlocks(world, getPos());
        invalidate();
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.EVAPORATION_SOLAR_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.EVAPORATION_SOLAR_CAPABILITY) {
            return Capabilities.EVAPORATION_SOLAR_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected boolean canSeeSky() {
        return world.canSeeSky(getPos().up(3));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.generators.client.model.ModelAdvancedSolarGenerator.class;
    }

    @Override
    public boolean shouldApplyDefaultSelectionWireframeFacingRotation(IBlockState state, IBlockAccess world, BlockPos pos) {
        return true;
    }


}
