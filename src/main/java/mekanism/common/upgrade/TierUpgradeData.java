package mekanism.common.upgrade;

import mekanism.common.tier.BaseTier;

import javax.annotation.Nonnull;

public class TierUpgradeData implements IUpgradeData {

    private final BaseTier upgradeTier;

    public TierUpgradeData(@Nonnull BaseTier upgradeTier) {
        this.upgradeTier = upgradeTier;
    }

    @Nonnull
    public BaseTier getUpgradeTier() {
        return upgradeTier;
    }
}
