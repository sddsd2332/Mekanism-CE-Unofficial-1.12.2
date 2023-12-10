package mekanism.api.heat;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class HeatAPI {

    private HeatAPI() {
    }

    public static final double AMBIENT_TEMP = 300;
    /**
     * The heat transfer coefficient for air
     */
    public static final double AIR_INVERSE_COEFFICIENT = 10_000;
    /**
     * Default heat capacity
     */
    public static final double DEFAULT_HEAT_CAPACITY = 1;
    /**
     * Default inverse conduction coefficient
     */
    public static final double DEFAULT_INVERSE_CONDUCTION = 1;
    /**
     * Default inverse insulation coefficient
     */
    public static final double DEFAULT_INVERSE_INSULATION = 0;
    /**
     * Represents the value at which changes to heat below this amount will not be taken into account by Mekanism.
     *
     * @since 10.4.0
     */
    public static final double EPSILON = 1.0E-6F;

    /**
     * Gets the atmospheric temperature at a given spot in the specified world based on the biome's temperature modifier at that position. The baseline temperature
     * modifier is taken to be the plains biome, or a biome with a temperature modifier of 0.8.
     *
     * @param world World; if {@code null} {@link #AMBIENT_TEMP} is returned instead.
     * @param pos   Position in the world.
     *
     * @return Atmospheric temperature at the given position.
     *
     * @implNote This method is a helper to call {@link #getAmbientTemp(double)} using the temperature of the biome at the location specified.
     * @see #AMBIENT_TEMP
     */
    public static double getAmbientTemp(@Nullable World world, BlockPos pos) {
        if (world == null) {
            return AMBIENT_TEMP;
        }
        return getAmbientTemp(world.getBiome(pos).getTemperature(pos));
    }

    /**
     *
     * @param biomeTemp Temperature of the biome.
     *
     * @return Atmospheric temperature at the given position.
     *
     * @implNote Biome temperature is clamped in the range of [-5, 5] as vanilla only uses values in the range [-0.7, 2.0], and we want to normalize it slightly so that
     * we ensure this does not return below absolute zero Kelvin. While there is a larger buffer zone that could return valid values, they are likely super extreme and
     * may indicate an issue in another mod's biome.
     * @see #AMBIENT_TEMP
     */
    public static double getAmbientTemp(double biomeTemp) {
        //See implementation note about this range. If any other mods do have valid more extreme temperatures,
        // we may want to consider expanding this range to [-10, 10]
        biomeTemp = MathHelper.clamp(biomeTemp, -5, 5);
        return AMBIENT_TEMP + 25 * (biomeTemp - 0.8);
    }

    @Desugar
    public record HeatTransfer(double adjacentTransfer, double environmentTransfer) {
    }
}
