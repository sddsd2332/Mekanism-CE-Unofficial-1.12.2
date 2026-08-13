package mekanism.qioprocessing.common.inventory.container;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.entity.player.InventoryPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Server-authoritative inventory and tracker container shared by all processor tiers. */
public final class ContainerQIOCraftingProcessor extends MekanismTileContainer<QIOCraftingProcessor> {

    public ContainerQIOCraftingProcessor(InventoryPlayer inventory,
          QIOCraftingProcessor processor) {
        super(processor, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return tile != null && tile.isFactoryProcessor() ? 101 : 111;
    }

    @Override
    protected int getInventoryXOffset() {
        return tile != null && tile.getVisibleLaneCount() >= 9 ? 26 : 8;
    }

    @Nullable
    public QIOFrequency getProcessorFrequency() {
        QIOFrequency frequency = tile.getQIOFrequency();
        QIOFrequencyReference reference = tile.getFrequencyReference();
        if (frequency != null || !isRemote() || reference == null) {
            return frequency;
        }
        return findFrequency(reference.getFrequencyUUID());
    }

    @Nonnull
    public List<QIOFrequency> getPublicProcessorFrequencies() {
        return tile.getPublicCache(FrequencyType.QIO);
    }

    @Nonnull
    public List<QIOFrequency> getPrivateProcessorFrequencies() {
        return tile.getPrivateCache(FrequencyType.QIO);
    }

    @Nonnull
    public List<QIOFrequency> getTrustedProcessorFrequencies() {
        return tile.getTrustedCache(FrequencyType.QIO);
    }

    @Nullable
    public UUID getProcessorOwnerUUID() {
        return tile.getSecurity().getOwnerUUID();
    }

    @Nonnull
    public String getProcessorOwnerName() {
        String owner = tile.getSecurity().getClientOwner();
        return owner == null ? "" : owner;
    }

    @Nullable
    private QIOFrequency findFrequency(UUID frequencyUUID) {
        for (QIOFrequency frequency : getPublicProcessorFrequencies()) {
            if (frequencyUUID.equals(frequency.getFrequencyUUID())) {
                return frequency;
            }
        }
        for (QIOFrequency frequency : getPrivateProcessorFrequencies()) {
            if (frequencyUUID.equals(frequency.getFrequencyUUID())) {
                return frequency;
            }
        }
        for (QIOFrequency frequency : getTrustedProcessorFrequencies()) {
            if (frequencyUUID.equals(frequency.getFrequencyUUID())) {
                return frequency;
            }
        }
        return null;
    }
}
