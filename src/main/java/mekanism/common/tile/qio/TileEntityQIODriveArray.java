package mekanism.common.tile.qio;

import io.netty.buffer.ByteBuf;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.QIODriveData;
import mekanism.common.content.qio.QIODriveMount;
import mekanism.common.content.qio.QIODriveSlotState;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.slot.QIODriveSlot;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The physical twelve-slot QIO drive holder. */
public class TileEntityQIODriveArray extends TileEntityQIOComponent implements IQIODriveHolder {

    public static final int DRIVE_SLOTS = 12;
    private static final int BITS_PER_DRIVE_STATUS = 4;
    private static final int DRIVE_STATUS_MASK = 0xF;

    private final List<QIODriveSlot> driveSlots = new ArrayList<>(DRIVE_SLOTS);
    private long driveStatus;
    private long previousDriveStatus = Long.MIN_VALUE;

    public TileEntityQIODriveArray() {
        super("QIODriveArray");
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        int xSize = 176;
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 6; x++) {
                int slot = y * 6 + x;
                driveSlots.add(new QIODriveSlot(this, slot, listener,
                      xSize / 2 - (6 * 18 / 2) + x * 18, 70 + y * 18));
                builder.addSlot(driveSlots.get(slot));
            }
        }
        return builder.build();
    }

    @Nonnull
    @Override
    public List<ItemStack> getQIODriveStacks() {
        List<ItemStack> stacks = new ArrayList<>(driveSlots.size());
        for (QIODriveSlot slot : driveSlots) {
            stacks.add(slot.getStack());
        }
        return stacks;
    }

    @Override
    public void updateQIODriveStack(int slot, @Nonnull ItemStack stack) {
        if (slot >= 0 && slot < driveSlots.size()) {
            driveSlots.get(slot).setStackFromHolder(stack);
            markNoUpdateSync();
        }
    }

    @Nonnull
    public List<QIODriveSlot> getDriveSlots() {
        return Collections.unmodifiableList(driveSlots);
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (world == null || world.getTotalWorldTime() % 10 != 0) {
            return;
        }
        QIOFrequency frequency = getQIOFrequency();
        for (int i = 0; i < DRIVE_SLOTS; i++) {
            ItemStack stack = driveSlots.get(i).getStack();
            DriveStatus status = stack.isEmpty() ? DriveStatus.NONE : DriveStatus.OFFLINE;
            if (frequency != null) {
                QIODriveMount mount = new QIODriveMount(this, i);
                QIODriveSlotState state = frequency.getSlotState(mount);
                QIODriveData data = frequency.getDriveData(mount);
                if (state == QIODriveSlotState.OVER_CAPACITY && data != null) {
                    status = DriveStatus.OVER_CAPACITY;
                } else if (state == QIODriveSlotState.ACTIVE && data != null) {
                    boolean countFull = data.getRecord().getExactTotalStorageUnits().compareTo(
                          data.getRecord().getExactStorageCapacity()) >= 0;
                    boolean typesFull = data.getRecord().getTotalTypes() >= data.getRecord().getTypeCapacity();
                    if (countFull || typesFull) {
                        status = DriveStatus.FULL;
                    } else if (isNearFull(data.getRecord().getExactTotalStorageUnits(),
                          data.getRecord().getExactStorageCapacity())) {
                        status = DriveStatus.NEAR_FULL;
                    } else {
                        status = DriveStatus.READY;
                    }
                } else if (state == QIODriveSlotState.DUPLICATE_UUID) {
                    status = DriveStatus.DUPLICATE;
                } else if (state == QIODriveSlotState.MISSING_STORAGE) {
                    status = DriveStatus.MISSING;
                } else if (state == QIODriveSlotState.INVALID_DRIVE) {
                    status = DriveStatus.INVALID;
                } else if (state == QIODriveSlotState.UNLOADED) {
                    status = DriveStatus.OFFLINE;
                }
            }
            driveStatus = updateStatus(i, status, driveStatus);
        }
        if (driveStatus != previousDriveStatus) {
            previousDriveStatus = driveStatus;
            markNoUpdateSync();
            if (world != null && !world.isRemote) {
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
        }
    }

    public long getDriveStatusData() {
        return driveStatus;
    }

    public static long updateStatus(int slot, DriveStatus status, long currentStatus) {
        int shift = slot * BITS_PER_DRIVE_STATUS;
        long mask = ((long) DRIVE_STATUS_MASK) << shift;
        return (currentStatus & ~mask) | ((long) status.ordinal() << shift);
    }

    public static boolean isNearFull(long stored, long capacity) {
        return capacity > 0 && stored >= capacity - capacity / 4;
    }

    public static boolean isNearFull(QIOAmount stored, QIOAmount capacity) {
        return stored != null && capacity != null && !capacity.isZero() &&
              stored.multiply(4).compareTo(capacity.multiply(3)) >= 0;
    }

    public static int getStatusOrdinal(int slot, long status) {
        return (int) ((status >> (slot * BITS_PER_DRIVE_STATUS)) & DRIVE_STATUS_MASK);
    }

    public static DriveStatus getStatus(int slot, long status) {
        int ordinal = getStatusOrdinal(slot, status);
        return ordinal >= 0 && ordinal < DriveStatus.values().length ? DriveStatus.values()[ordinal] : DriveStatus.ERROR;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (world != null && world.isRemote && dataStream.readableBytes() >= Long.BYTES) {
            long previous = driveStatus;
            driveStatus = dataStream.readLong();
            previousDriveStatus = driveStatus;
            if (previous != driveStatus) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(driveStatus);
        return data;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setLong(NBTConstants.DRIVES, driveStatus);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        driveStatus = nbtTags.getLong(NBTConstants.DRIVES);
        previousDriveStatus = driveStatus;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        driveStatus = tag.getLong(NBTConstants.DRIVES);
        previousDriveStatus = driveStatus;
        MekanismUtils.updateBlock(world, getPos());
    }

    public enum DriveStatus {
        NONE(null),
        OFFLINE(Mekanism.rl("block/qio_drive/qio_drive_offline")),
        READY(Mekanism.rl("block/qio_drive/qio_drive_empty")),
        NEAR_FULL(Mekanism.rl("block/qio_drive/qio_drive_partial")),
        FULL(Mekanism.rl("block/qio_drive/qio_drive_full")),
        ERROR(Mekanism.rl("block/qio_drive/qio_drive_full")),
        DUPLICATE(Mekanism.rl("block/qio_drive/qio_drive_full")),
        MISSING(Mekanism.rl("block/qio_drive/qio_drive_full")),
        INVALID(Mekanism.rl("block/qio_drive/qio_drive_full")),
        OVER_CAPACITY(Mekanism.rl("block/qio_drive/qio_drive_full"));

        private final ResourceLocation model;

        DriveStatus(ResourceLocation model) {
            this.model = model;
        }

        public int ledIndex() {
            return Math.max(0, ordinal() - READY.ordinal());
        }

        public ResourceLocation getModel() {
            return model;
        }

        public String getDisplayName() {
            String key = switch (this) {
                case NONE -> "empty";
                case OFFLINE -> "offline";
                case READY -> "active";
                case NEAR_FULL -> "near_full";
                case FULL -> "full";
                case DUPLICATE -> "duplicate";
                case MISSING -> "missing";
                case INVALID -> "invalid";
                case OVER_CAPACITY -> "over_capacity";
                case ERROR -> "error";
            };
            return LangUtils.localize("qio.drive.status." + key);
        }
    }
}
