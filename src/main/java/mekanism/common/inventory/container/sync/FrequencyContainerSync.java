package mekanism.common.inventory.container.sync;

import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.security.ISecurityTile.SecurityMode;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Frequency state and list trackers for containers that cannot extend FrequencyItemContainer. */
public final class FrequencyContainerSync<FREQ extends Frequency> {

    private List<FREQ> publicCache = Collections.emptyList();
    private List<FREQ> privateCache = Collections.emptyList();
    private List<FREQ> trustedCache = Collections.emptyList();
    @Nullable
    private FREQ frequency;
    private boolean tracking;

    public void addTrackers(MekanismContainer container, FrequencyType<FREQ> frequencyType,
          Supplier<FREQ> serverFrequency) {
        if (tracking) {
            throw new IllegalStateException("Frequency container sync is already tracking a container");
        }
        tracking = true;
        if (container.isRemote()) {
            container.track(SyncableFrequency.create(frequencyType, this::getFrequency, this::setFrequency));
            container.track(SyncableFrequencyList.create(frequencyType, this::getPublicCache, value -> publicCache = value));
            container.track(SyncableFrequencyList.create(frequencyType, this::getPrivateCache, value -> privateCache = value));
            container.track(SyncableFrequencyList.create(frequencyType, this::getTrustedCache, value -> trustedCache = value));
        } else {
            container.track(SyncableFrequency.create(frequencyType, serverFrequency, this::setFrequency));
            container.track(SyncableFrequencyList.create(frequencyType,
                  () -> frequencyType.getManager(null, SecurityMode.PUBLIC).getFrequencies(), value -> publicCache = value));
            container.track(SyncableFrequencyList.create(frequencyType,
                  () -> frequencyType.getManager(container.getPlayerUUID(), SecurityMode.PRIVATE).getFrequencies(), value -> privateCache = value));
            container.track(SyncableFrequencyList.create(frequencyType,
                  () -> frequencyType.getManager(container.getPlayerUUID(), SecurityMode.TRUSTED).getFrequencies(), value -> trustedCache = value));
        }
    }

    @Nullable
    public FREQ getFrequency() {
        return frequency;
    }

    public List<FREQ> getPublicCache() {
        return publicCache;
    }

    public List<FREQ> getPrivateCache() {
        return privateCache;
    }

    public List<FREQ> getTrustedCache() {
        return trustedCache;
    }

    private void setFrequency(@Nullable FREQ frequency) {
        this.frequency = frequency;
    }
}
