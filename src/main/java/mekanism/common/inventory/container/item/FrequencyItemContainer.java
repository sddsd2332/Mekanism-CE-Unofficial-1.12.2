package mekanism.common.inventory.container.item;

import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyAware;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.inventory.container.sync.SyncableFrequency;
import mekanism.common.inventory.container.sync.SyncableFrequencyList;
import mekanism.common.security.ISecurityTile.SecurityMode;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public abstract class FrequencyItemContainer<FREQ extends Frequency> extends MekanismItemContainer {

    private List<FREQ> publicCache = Collections.emptyList();
    private List<FREQ> privateCache = Collections.emptyList();
    private List<FREQ> trustedCache = Collections.emptyList();
    private FREQ freq;

    protected FrequencyItemContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        super(inv, hand, stack);
    }

    protected abstract FrequencyType<FREQ> getFrequencyType();

    @Nullable
    public FREQ getClientFrequency() {
        return freq;
    }

    @Nullable
    protected FREQ getFrequencyFromStack() {
        if (stack.getItem() instanceof IFrequencyItem frequencyItem) {
            FrequencyAware<?> frequencyAware = frequencyItem.getFrequencyAware(stack);
            if (frequencyAware.identity() != null) {
                return getFrequencyType().getFrequency(frequencyAware.identity(), getPlayerUUID());
            }
        }
        return null;
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

    private void setFrequency(FREQ frequency) {
        freq = frequency;
    }

    @Override
    protected void addContainerTrackers() {
        super.addContainerTrackers();
        FrequencyType<FREQ> frequencyType = getFrequencyType();
        if (isRemote()) {
            track(SyncableFrequency.create(frequencyType, this::getClientFrequency, this::setFrequency));
            track(SyncableFrequencyList.create(frequencyType, this::getPublicCache, value -> publicCache = value));
            track(SyncableFrequencyList.create(frequencyType, this::getPrivateCache, value -> privateCache = value));
            track(SyncableFrequencyList.create(frequencyType, this::getTrustedCache, value -> trustedCache = value));
        } else {
            track(SyncableFrequency.create(frequencyType, this::getFrequencyFromStack, this::setFrequency));
            track(SyncableFrequencyList.create(frequencyType, () -> frequencyType.getManager(null, SecurityMode.PUBLIC).getFrequencies(), value -> publicCache = value));
            track(SyncableFrequencyList.create(frequencyType, () -> frequencyType.getManager(getPlayerUUID(), SecurityMode.PRIVATE).getFrequencies(), value -> privateCache = value));
            track(SyncableFrequencyList.create(frequencyType, () -> frequencyType.getManager(getPlayerUUID(), SecurityMode.TRUSTED).getFrequencies(), value -> trustedCache = value));
        }
    }
}
