package mekanism.api.processing;

import net.minecraft.tileentity.TileEntity;

import java.util.Collections;
import java.util.List;

/**
 * Describes the processing routes and stable ports exposed by one machine type.
 */
public interface MachineRecipeProvider<TILE extends TileEntity> {

    default boolean isAvailable(TILE tile) {
        return tile != null && !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote;
    }

    default Object getRecipeSourceKey(TILE tile) {
        return null;
    }

    default int getConfigurationRevision(TILE tile) {
        return 0;
    }

    default List<MachineRecipeRoute> getRecipeRoutes(TILE tile) {
        return Collections.emptyList();
    }

    default List<MachinePort> getPorts(TILE tile) {
        return Collections.emptyList();
    }

    /**
     * Returns the small, persistent item identity used by management UIs. This must
     * never contain complete Tile NBT; the descriptor validates its own bounds.
     */
    default MachinePresentationDescriptor getPresentation(TILE tile) {
        return MachinePresentationDescriptor.fallback(tile);
    }

    /**
     * Optional stable suffix for recipe-profile scope when two instances registered
     * through one provider expose materially different routes, ports, or lanes.
     * Cosmetic presentation differences must return the default empty value.
     */
    default String getRecipeProfileScopeDiscriminator(TILE tile) {
        return "";
    }

    /**
     * Explicitly declares which QIO automation contracts this provider implements. The default is intentionally closed.
     */
    default ProviderConformanceDescriptor getQIOConformance(TILE tile) {
        return ProviderConformanceDescriptor.unregistered();
    }
}
