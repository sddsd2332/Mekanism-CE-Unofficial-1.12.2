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
}
