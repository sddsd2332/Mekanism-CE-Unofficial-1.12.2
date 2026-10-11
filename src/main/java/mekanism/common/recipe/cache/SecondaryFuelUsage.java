package mekanism.common.recipe.cache;

import mekanism.api.math.MathUtils;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;

/**
 * Shared policy and per-operation budget for continuous auxiliary resources.
 */
public final class SecondaryFuelUsage {
    private SecondaryFuelUsage() {
    }

    public static boolean isRandomized(IUpgradeTile tile) {
        return MekanismConfig.current().usage.randomizedConsumption.val()
                && !tile.getComponent().requiresDeterministicSecondaryUsage();
    }

    public static int fixedTotal(IUpgradeTile tile, int baseTicks) {
        return Math.max(1, MathUtils.clampToInt(MekanismUtils.getBaseUsage(tile, baseTicks)));
    }

    /**
     * usedSoFar counts per-operation multiplier units, not the sum across a parallel batch.
     */
    public static long constantUse(long total, long usedSoFar, int requiredTicks, int operatingTicks) {
        long remaining = Math.max(0, total - usedSoFar);
        int ticks = Math.max(1, requiredTicks) - operatingTicks;
        if (ticks <= 1) {
            return remaining;
        }
        return remaining / ticks;
    }
}
