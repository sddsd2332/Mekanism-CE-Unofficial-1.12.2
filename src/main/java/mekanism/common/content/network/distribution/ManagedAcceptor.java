package mekanism.common.content.network.distribution;

import mekanism.common.capabilities.Capabilities;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.tile.transmitter.TileEntityThermodynamicConductor;
import mekanism.common.util.CapabilityUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

/**
 * Positive cache only. Identity, availability and native resolver generation are checked on every use.
 */
public final class ManagedAcceptor<T> {
    private TileEntity owner;
    private T handler;
    private Object token;
    private Capability<?> cachedCapability;
    private EnumFacing cachedSide;

    @Nullable
    public T resolve(@Nullable TileEntity tile, Capability<T> capability, EnumFacing side) {
        if (tile == null || tile.isInvalid() || capability == null) {
            clear();
            return null;
        }
        Object current = token(tile, capability, side);
        if (current != null && tile == owner && current == token && capability == cachedCapability && side == cachedSide)
            return handler;
        clear();
        T resolved = CapabilityUtils.getCapability(tile, capability, side);
        // Resolve may run callbacks; only admit if the credential survived the entire lookup.
        if (resolved != null && current != null && current == token(tile, capability, side)) {
            owner = tile;
            handler = resolved;
            token = current;
            cachedCapability = capability;
            cachedSide = side;
        }
        return resolved;
    }

    @Nullable
    private static Object token(TileEntity tile, Capability<?> capability, EnumFacing side) {
        if (tile instanceof TileEntityContainerBlock container)
            return container.getManagedResourceAcceptorToken(capability, side);
        if (capability == Capabilities.HEAT_HANDLER_CAPABILITY && tile.getClass() == TileEntityThermodynamicConductor.class) {
            return ((TileEntityThermodynamicConductor) tile).getManagedHeatAcceptorToken(side);
        }
        return null;
    }

    public void clear() {
        owner = null;
        handler = null;
        token = null;
        cachedCapability = null;
        cachedSide = null;
    }
}
