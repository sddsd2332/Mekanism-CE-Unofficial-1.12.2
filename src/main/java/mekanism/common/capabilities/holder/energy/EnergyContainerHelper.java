package mekanism.common.capabilities.holder.energy;

import mekanism.api.RelativeSide;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class EnergyContainerHelper {

    private final IEnergyContainerHolder containerHolder;
    private boolean built;

    private EnergyContainerHelper(IEnergyContainerHolder containerHolder) {
        this.containerHolder = containerHolder;
    }

    public static EnergyContainerHelper forSide(Supplier<EnumFacing> facingSupplier) {
        return new EnergyContainerHelper(new EnergyContainerHolder(facingSupplier));
    }

    public static EnergyContainerHelper forSideWithConfig(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        return new EnergyContainerHelper(new ConfigEnergyContainerHolder(facingSupplier, configSupplier));
    }

    public static EnergyContainerHelper forSideWithConfig(ISideConfiguration sideConfiguration) {
        return new EnergyContainerHelper(new ConfigEnergyContainerHolder(sideConfiguration));
    }

    public <CONTAINER extends IEnergyContainer> CONTAINER addContainer(@Nonnull CONTAINER container) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (containerHolder instanceof EnergyContainerHolder) {
            ((EnergyContainerHolder) containerHolder).addContainer(container);
        } else if (containerHolder instanceof ConfigEnergyContainerHolder) {
            ((ConfigEnergyContainerHolder) containerHolder).addContainer(container);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add containers");
        }
        return container;
    }

    public <CONTAINER extends IEnergyContainer> CONTAINER addContainer(@Nonnull CONTAINER container, RelativeSide... sides) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (containerHolder instanceof EnergyContainerHolder) {
            ((EnergyContainerHolder) containerHolder).addContainer(container, sides);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add containers on specific sides");
        }
        return container;
    }

    public IEnergyContainerHolder build() {
        built = true;
        return containerHolder;
    }
}
