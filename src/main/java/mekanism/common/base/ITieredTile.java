package mekanism.common.base;

import mekanism.common.tier.BaseTier;

public interface ITieredTile extends IBaseTierProvider {

    BaseTier getTier();

    @Override
    default BaseTier getBaseTier() {
        return getTier();
    }
}
