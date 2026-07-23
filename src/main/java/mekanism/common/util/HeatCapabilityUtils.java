package mekanism.common.util;

import mekanism.api.IHeatTransfer;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.heat.LegacyHeatHandlerAdapter;
import mekanism.common.capabilities.heat.LegacyHeatTransferAdapter;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;

public final class HeatCapabilityUtils {

    private HeatCapabilityUtils() {
    }

    @Nullable
    @SuppressWarnings("removal")
    public static IHeatHandler getHandler(@Nullable ICapabilityProvider provider, @Nullable EnumFacing side) {
        IHeatHandler handler = CapabilityUtils.getCapability(provider, Capabilities.HEAT_HANDLER_CAPABILITY, side);
        if (handler != null) {
            return handler;
        }
        IHeatTransfer legacy = CapabilityUtils.getCapability(provider, Capabilities.HEAT_TRANSFER_CAPABILITY, side);
        if (legacy == null || side != null && !legacy.canConnectHeat(side)) {
            return null;
        }
        if (legacy instanceof LegacyHeatTransferAdapter adapter) {
            return adapter.getHandler();
        }
        return new LegacyHeatHandlerAdapter(legacy);
    }

    public static boolean hasHandler(@Nullable ICapabilityProvider provider, @Nullable EnumFacing side) {
        return getHandler(provider, side) != null;
    }
}
