package mekanism.common.tile.qio;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.List;

/** Outputs redstone when one selected QIO resource reaches the configured amount. */
public class TileEntityQIORedstoneAdapter extends TileEntityQIOFilterHandler {

    private long threshold = 1;
    private long clientStoredCount;
    private boolean fuzzy;
    private boolean inverted;
    private boolean powering;

    public TileEntityQIORedstoneAdapter() {
        super("QIORedstoneAdapter");
        setFilterless(false);
    }

    public long getThreshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        long value = Math.max(0, threshold);
        if (this.threshold != value) {
            this.threshold = value;
            markDirty();
        }
    }

    public boolean getFuzzyMode() {
        return fuzzy;
    }

    public void toggleFuzzyMode() {
        fuzzy = !fuzzy;
        markDirty();
    }

    public boolean isInverted() {
        return inverted;
    }

    public void invertSignal() {
        inverted = !inverted;
        markDirty();
    }

    @Nullable
    public QIOFilter getTargetFilter() {
        List<QIOFilter> filters = getFilters();
        if (filters.isEmpty()) {
            return null;
        }
        QIOFilter target = filters.get(0);
        target.setEnabled(true);
        return target;
    }

    public long getStoredCount() {
        return isRemote() ? clientStoredCount : getFrequencyStored();
    }

    private long getFrequencyStored() {
        QIOFrequency frequency = getQIOFrequency();
        QIOFilter target = getTargetFilter();
        if (frequency == null || target == null) {
            return 0;
        }
        long stored = 0;
        for (QIOResourceEntry entry : frequency.getResourceEntries()) {
            boolean matches;
            if (fuzzy && target instanceof QIOItemStackFilter && entry.getKind() == QIOResourceKind.ITEM) {
                ItemStack targetStack = ((QIOItemStackFilter) target).getItemStack();
                ItemStack storedStack = entry.getItem();
                matches = !targetStack.isEmpty() && !storedStack.isEmpty() && targetStack.getItem() == storedStack.getItem();
            } else {
                matches = target.test(entry);
            }
            if (matches) {
                long amount = entry.getAmount();
                stored = amount > Long.MAX_VALUE - stored ? Long.MAX_VALUE : stored + amount;
            }
        }
        return stored;
    }

    private boolean calculatePowering() {
        long stored = getFrequencyStored();
        boolean reachedThreshold = stored > 0 && stored >= threshold;
        return reachedThreshold != inverted;
    }

    public boolean isPowering() {
        return isRemote() ? powering : calculatePowering();
    }

    public int getRedstoneLevel() {
        return isPowering() ? 15 : 0;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        boolean next = calculatePowering();
        if (next != powering) {
            powering = next;
            if (world != null) {
                world.notifyNeighborsOfStateChange(getPos(), getBlockType(), true);
            }
            markNoUpdateSync();
            if (world != null && !world.isRemote) {
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (world != null && world.isRemote && dataStream.readableBytes() >= 1) {
            powering = dataStream.readBoolean();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(powering);
        return data;
    }

    @Override
    public void writeSustainedQIOData(NBTTagCompound nbtTags) {
        super.writeSustainedQIOData(nbtTags);
        nbtTags.setLong("qioThreshold", threshold);
        nbtTags.setBoolean("qioFuzzy", fuzzy);
        nbtTags.setBoolean("qioInverted", inverted);
        nbtTags.setBoolean("qioPowering", powering);
    }

    @Override
    public void readSustainedQIOData(NBTTagCompound nbtTags) {
        super.readSustainedQIOData(nbtTags);
        if (!nbtTags.hasKey("qioFilterless")) {
            setFilterless(false);
        }
        threshold = nbtTags.hasKey("qioThreshold") ? Math.max(0, nbtTags.getLong("qioThreshold")) : 1;
        fuzzy = nbtTags.getBoolean("qioFuzzy");
        inverted = nbtTags.getBoolean("qioInverted");
        powering = nbtTags.getBoolean("qioPowering");
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        powering = tag.getBoolean("qioPowering");
        MekanismUtils.updateBlock(world, getPos());
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableLong.create(this::getThreshold, value -> threshold = Math.max(0, value)));
        container.track(SyncableBoolean.create(this::getFuzzyMode, value -> fuzzy = value));
        container.track(SyncableBoolean.create(this::isInverted, value -> inverted = value));
        container.track(SyncableBoolean.create(this::isPowering, value -> powering = value));
        container.track(SyncableLong.create(this::getFrequencyStored, value -> clientStoredCount = Math.max(0, value)));
    }
}
