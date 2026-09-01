package mekanism.common.content.qio;

import mekanism.api.NBTConstants;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

public interface IQIODriveItem {

    /**
     * Legacy built-in tier hook. New addon drives should override
     * {@link #getDriveDefinition(ItemStack)} instead.
     */
    @Deprecated
    @Nullable
    default QIODriveTier getDriveTier() {
        return null;
    }

    /** Returns the registered capacity definition used by this physical drive. */
    @Nullable
    default QIODriveDefinition getDriveDefinition(ItemStack stack) {
        QIODriveTier tier = getDriveTier();
        return tier == null ? null : tier.getDefinition();
    }

    /** Existing drives remain mixed unless an item explicitly declares a specialization. */
    default QIODriveType getDriveType() {
        return QIODriveType.MIXED;
    }

    default QIODriveType getDriveType(ItemStack stack) {
        return getDriveType();
    }

    /**
     * Returns the registered specialization used by this physical drive.
     *
     * <p>The default bridges old addon items that only override {@link #getDriveType()}.</p>
     */
    @Nullable
    default QIODriveSpecialization getDriveSpecialization() {
        QIODriveType legacyType = getDriveType();
        return legacyType == null ? null : legacyType.getSpecialization();
    }

    @Nullable
    default QIODriveSpecialization getDriveSpecialization(ItemStack stack) {
        QIODriveType legacyType = getDriveType(stack);
        QIODriveType baseLegacyType = getDriveType();
        return legacyType != null && legacyType != baseLegacyType ? legacyType.getSpecialization() :
              getDriveSpecialization();
    }

    default long getCountCapacity() {
        return getCountCapacity(ItemStack.EMPTY);
    }

    default int getTypeCapacity() {
        return getTypeCapacity(ItemStack.EMPTY);
    }

    default long getStorageCapacity() {
        return getStorageCapacity(ItemStack.EMPTY);
    }

    default QIOAmount getExactCountCapacity() {
        return getExactCountCapacity(ItemStack.EMPTY);
    }

    default QIOAmount getExactStorageCapacity() {
        return getExactStorageCapacity(ItemStack.EMPTY);
    }

    default long getCountCapacity(ItemStack stack) {
        QIODriveDefinition definition = getDriveDefinition(stack);
        QIODriveSpecialization specialization = getDriveSpecialization(stack);
        return definition == null || specialization == null ? 0 : specialization.getCountCapacity(definition);
    }

    default int getTypeCapacity(ItemStack stack) {
        QIODriveDefinition definition = getDriveDefinition(stack);
        return definition == null ? 0 : definition.getMaxTypes();
    }

    default long getStorageCapacity(ItemStack stack) {
        QIODriveDefinition definition = getDriveDefinition(stack);
        QIODriveSpecialization specialization = getDriveSpecialization(stack);
        return definition == null || specialization == null ? 0 : specialization.getStorageCapacity(definition);
    }

    default QIOAmount getExactCountCapacity(ItemStack stack) {
        QIODriveDefinition definition = getDriveDefinition(stack);
        QIODriveSpecialization specialization = getDriveSpecialization(stack);
        return definition == null || specialization == null ? QIOAmount.ZERO :
              specialization.getExactCountCapacity(definition);
    }

    default QIOAmount getExactStorageCapacity(ItemStack stack) {
        QIODriveDefinition definition = getDriveDefinition(stack);
        QIODriveSpecialization specialization = getDriveSpecialization(stack);
        return definition == null || specialization == null ? QIOAmount.ZERO :
              specialization.getExactStorageCapacity(definition);
    }

    default boolean hasUnlimitedCountCapacity(ItemStack stack) {
        QIODriveDefinition definition = getDriveDefinition(stack);
        return definition == null ? getCountCapacity(stack) == Long.MAX_VALUE : definition.isCountUnlimited();
    }

    default boolean hasUnlimitedTypeCapacity(ItemStack stack) {
        QIODriveDefinition definition = getDriveDefinition(stack);
        return definition == null ? getTypeCapacity(stack) == Integer.MAX_VALUE : definition.isTypesUnlimited();
    }

    default boolean hasCreativeCapacity(ItemStack stack) {
        return hasUnlimitedCountCapacity(stack) && hasUnlimitedTypeCapacity(stack);
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
