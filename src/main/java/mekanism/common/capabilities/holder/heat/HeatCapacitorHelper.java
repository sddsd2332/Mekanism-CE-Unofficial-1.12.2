package mekanism.common.capabilities.holder.heat;

import mekanism.api.RelativeSide;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class HeatCapacitorHelper {

    private final IHeatCapacitorHolder slotHolder;
    private boolean built;

    private HeatCapacitorHelper(IHeatCapacitorHolder slotHolder) {
        this.slotHolder = slotHolder;
    }

    public static HeatCapacitorHelper forSide(Supplier<EnumFacing> facingSupplier) {
        return new HeatCapacitorHelper(new HeatCapacitorHolder(facingSupplier));
    }

    public static HeatCapacitorHelper forSideWithConfig(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        return new HeatCapacitorHelper(new ConfigHeatCapacitorHolder(facingSupplier, configSupplier));
    }

    public static HeatCapacitorHelper forSideWithConfig(ISideConfiguration sideConfiguration) {
        return new HeatCapacitorHelper(new ConfigHeatCapacitorHolder(sideConfiguration));
    }

    public <CAPACITOR extends IHeatCapacitor> CAPACITOR addCapacitor(@Nonnull CAPACITOR capacitor) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (slotHolder instanceof HeatCapacitorHolder) {
            ((HeatCapacitorHolder) slotHolder).addCapacitor(capacitor);
        } else if (slotHolder instanceof ConfigHeatCapacitorHolder) {
            ((ConfigHeatCapacitorHolder) slotHolder).addCapacitor(capacitor);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add heat capacitors");
        }
        return capacitor;
    }

    public <CAPACITOR extends IHeatCapacitor> CAPACITOR addCapacitor(@Nonnull CAPACITOR capacitor, RelativeSide... sides) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (slotHolder instanceof HeatCapacitorHolder) {
            ((HeatCapacitorHolder) slotHolder).addCapacitor(capacitor, sides);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add heat capacitors on specific sides");
        }
        return capacitor;
    }

    public IHeatCapacitorHolder build() {
        built = true;
        return slotHolder;
    }
}
