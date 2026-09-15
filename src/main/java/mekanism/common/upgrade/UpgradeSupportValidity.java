package mekanism.common.upgrade;

import java.util.function.BooleanSupplier;

/** One-shot validity shared by a component and the external support registry; never owns either source. */
public final class UpgradeSupportValidity implements BooleanSupplier {
    private volatile boolean valid = true;

    @Override
    public boolean getAsBoolean() {
        return valid;
    }

    public void invalidate() {
        valid = false;
    }
}
