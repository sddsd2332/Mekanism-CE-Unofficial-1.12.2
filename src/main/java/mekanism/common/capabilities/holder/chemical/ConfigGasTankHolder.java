package mekanism.common.capabilities.holder.chemical;

import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.util.EnumFacing;

import java.util.function.Supplier;

/**
 * Compatibility wrapper for older gas holder chemical naming.
 *
 * @deprecated Use {@link mekanism.common.capabilities.holder.gas.ConfigGasTankHolder}.
 */
@Deprecated
public class ConfigGasTankHolder extends mekanism.common.capabilities.holder.gas.ConfigGasTankHolder implements IGasTankHolder {

    public ConfigGasTankHolder(ISideConfiguration sideConfiguration) {
        super(sideConfiguration);
    }

    public ConfigGasTankHolder(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        super(facingSupplier, configSupplier);
    }
}
