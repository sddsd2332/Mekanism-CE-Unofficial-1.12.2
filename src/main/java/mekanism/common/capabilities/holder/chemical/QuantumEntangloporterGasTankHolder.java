package mekanism.common.capabilities.holder.chemical;

import mekanism.common.tile.TileEntityQuantumEntangloporter;

/**
 * Compatibility wrapper for older gas holder chemical naming.
 *
 * @deprecated Use {@link mekanism.common.capabilities.holder.gas.QuantumEntangloporterGasTankHolder}.
 */
@Deprecated
public class QuantumEntangloporterGasTankHolder extends mekanism.common.capabilities.holder.gas.QuantumEntangloporterGasTankHolder implements IGasTankHolder {

    public QuantumEntangloporterGasTankHolder(TileEntityQuantumEntangloporter entangloporter) {
        super(entangloporter);
    }
}
