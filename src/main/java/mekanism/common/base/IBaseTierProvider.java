package mekanism.common.base;

import mekanism.common.tier.BaseTier;

/**
 * Exposes a machine's current base tier without leaking its module-specific tier enum.
 */
public interface IBaseTierProvider {

    BaseTier getBaseTier();
}
