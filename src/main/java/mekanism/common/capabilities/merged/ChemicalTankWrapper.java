package mekanism.common.capabilities.merged;

import mekanism.api.gas.IExtendedGasTank;

import java.util.function.BooleanSupplier;

/**
 * Compatibility wrapper for older merged chemical tank naming.
 *
 * @deprecated Use {@link GasTankWrapper}.
 */
@Deprecated
public class ChemicalTankWrapper extends GasTankWrapper {

    public ChemicalTankWrapper(MergedTank mergedTank, IExtendedGasTank internal, BooleanSupplier insertCheck) {
        super(mergedTank, internal, insertCheck);
    }
}
