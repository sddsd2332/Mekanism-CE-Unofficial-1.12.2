package mekanism.common.integration.farmersdelightlegacy;

import com.wdcftgg.farmersdelightlegacy.api.heat.HeatSourceApi;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.util.HeatCapabilityUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class FarmersDelightLegacyIntegration {

    private static final String HEAT_SOURCE_KEY = "mekanism:heat_transfer";

    private FarmersDelightLegacyIntegration() {
    }

    public static void init() {
        HeatSourceApi.registerDirectHeatSourcePredicate(HEAT_SOURCE_KEY, FarmersDelightLegacyIntegration::isMekanismHeatSource);
    }

    private static boolean isMekanismHeatSource(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) {
            return false;
        }
        IHeatHandler heatHandler = HeatCapabilityUtils.getHandler(tile, null);
        return heatHandler != null && heatHandler.getTotalTemperature() > HeatAPI.AMBIENT_TEMP;
    }
}
