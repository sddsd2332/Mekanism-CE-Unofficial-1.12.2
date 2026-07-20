package mekanism.common.content.qio;

import mekanism.api.NBTConstants;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

public interface IQIODriveItem {

    QIODriveTier getDriveTier();

    /** Existing drives remain mixed unless an item explicitly declares a specialization. */
    default QIODriveType getDriveType() {
        return QIODriveType.MIXED;
    }

    default long getCountCapacity() {
        return getDriveType().getCountCapacity(getDriveTier());
    }

    default int getTypeCapacity() {
        return getDriveTier().getMaxTypes();
    }

    default long getStorageCapacity() {
        return getDriveType().getStorageCapacity(getDriveTier());
    }

    default long getCountCapacity(ItemStack stack) {
        return getCountCapacity();
    }

    default int getTypeCapacity(ItemStack stack) {
        return getTypeCapacity();
    }

    default long getStorageCapacity(ItemStack stack) {
        return getStorageCapacity();
    }

    @Nullable
    default UUID getDriveId(ItemStack stack) {
        String value = ItemDataUtils.getString(stack, NBTConstants.QIO_DRIVE_ID);
        if (value.isEmpty()) {
            return null;
        }
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value) ? parsed : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    default UUID getOrCreateDriveId(ItemStack stack) {
        UUID driveId = getDriveId(stack);
        if (driveId == null) {
            driveId = UUID.randomUUID();
            ItemDataUtils.setString(stack, NBTConstants.QIO_DRIVE_ID, driveId.toString());
        }
        return driveId;
    }

    default void setDriveId(ItemStack stack, @Nullable UUID driveId) {
        if (driveId == null) {
            ItemDataUtils.removeData(stack, NBTConstants.QIO_DRIVE_ID);
        } else {
            ItemDataUtils.setString(stack, NBTConstants.QIO_DRIVE_ID, driveId.toString());
        }
    }

    default DriveMetadata getDriveMetadata(ItemStack stack) {
        long count = ItemDataUtils.getLong(stack, NBTConstants.QIO_META_COUNT);
        long storageUnits = ItemDataUtils.hasData(stack, NBTConstants.QIO_META_STORAGE_UNITS) ?
              ItemDataUtils.getLong(stack, NBTConstants.QIO_META_STORAGE_UNITS) :
              QIOStorageUnits.toStorageUnits(QIOResourceKind.ITEM, count);
        return new DriveMetadata(count, ItemDataUtils.getInt(stack, NBTConstants.QIO_META_TYPES), storageUnits);
    }

    default void setDriveMetadata(ItemStack stack, long count, int types) {
        setDriveMetadata(stack, count, types, QIOStorageUnits.toStorageUnits(QIOResourceKind.ITEM, count));
    }

    default void setDriveMetadata(ItemStack stack, long count, int types, long storageUnits) {
        ItemDataUtils.setLongOrRemove(stack, NBTConstants.QIO_META_COUNT, Math.max(0, count));
        ItemDataUtils.setLongOrRemove(stack, NBTConstants.QIO_META_STORAGE_UNITS, Math.max(0, storageUnits));
        if (types <= 0) {
            ItemDataUtils.removeData(stack, NBTConstants.QIO_META_TYPES);
        } else {
            ItemDataUtils.setInt(stack, NBTConstants.QIO_META_TYPES, types);
        }
    }

    final class DriveMetadata {

        private final long count;
        private final int types;
        private final long storageUnits;

        public DriveMetadata(long count, int types) {
            this(count, types, QIOStorageUnits.toStorageUnits(QIOResourceKind.ITEM, count));
        }

        public DriveMetadata(long count, int types, long storageUnits) {
            this.count = Math.max(0, count);
            this.types = Math.max(0, types);
            this.storageUnits = Math.max(0, storageUnits);
        }

        public long getCount() {
            return count;
        }

        public int getTypes() {
            return types;
        }

        public long getStorageUnits() {
            return storageUnits;
        }
    }
}
