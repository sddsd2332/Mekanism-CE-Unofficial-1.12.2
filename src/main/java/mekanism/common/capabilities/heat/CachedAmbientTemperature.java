package mekanism.common.capabilities.heat;

import mekanism.api.heat.HeatAPI;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class CachedAmbientTemperature implements DoubleSupplier {

    private final double[] ambientTemperature = new double[EnumFacing.VALUES.length + 1];
    private final Supplier<World> worldSupplier;
    private final Supplier<BlockPos> positionSupplier;

    public CachedAmbientTemperature(Supplier<World> worldSupplier, Supplier<BlockPos> positionSupplier) {
        this.worldSupplier = worldSupplier;
        this.positionSupplier = positionSupplier;
        Arrays.fill(ambientTemperature, -1);
    }

    @Override
    public double getAsDouble() {
        return getTemperature(null);
    }

    public double getTemperature(@Nullable EnumFacing side) {
        int index = side == null ? EnumFacing.VALUES.length : side.ordinal();
        double cached = ambientTemperature[index];
        if (cached == -1) {
            World world = worldSupplier.get();
            if (world == null) {
                return HeatAPI.AMBIENT_TEMP;
            }
            BlockPos pos = positionSupplier.get();
            if (side != null) {
                pos = pos.offset(side);
            }
            cached = HeatAPI.getAmbientTemp(world, pos);
            ambientTemperature[index] = cached;
        }
        return cached;
    }
}
