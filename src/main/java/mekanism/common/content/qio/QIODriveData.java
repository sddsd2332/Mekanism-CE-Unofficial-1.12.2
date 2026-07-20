package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Live, mounted view of one externally persisted QIO drive record.
 */
public final class QIODriveData {

    private final ItemStack driveStack;
    private final UUID driveId;
    private final QIODriveMount mount;
    private final QIODriveRecord record;
    private final QIODriveType driveType;
    private final Runnable updateListener;

    private QIODriveData(ItemStack driveStack, UUID driveId, QIODriveMount mount, QIODriveRecord record,
          QIODriveType driveType, @Nullable Runnable updateListener) {
        this.driveStack = driveStack;
        this.driveId = driveId;
        this.mount = mount;
        this.record = record;
        this.driveType = driveType;
        this.updateListener = updateListener;
    }

    @Nonnull
    public static MountAttempt tryMount(ItemStack stack, QIODriveMount mount) {
        if (stack == null || stack.isEmpty()) {
            return MountAttempt.invalid(QIODriveSlotState.EMPTY);
        }
        if (!(stack.getItem() instanceof IQIODriveItem)) {
            return MountAttempt.invalid(QIODriveSlotState.INVALID_DRIVE);
        }
        if (mount == null || !QIOResourceTypeRegistry.INSTANCE.isLoaded() || !QIODriveStorage.INSTANCE.isLoaded()) {
            return MountAttempt.invalid(QIODriveSlotState.UNLOADED);
        }
        IQIODriveItem driveItem = (IQIODriveItem) stack.getItem();
        UUID driveId = driveItem.getOrCreateDriveId(stack);
        syncDriveStack(mount, stack);
        QIODriveRecord existingRecord = QIODriveStorage.INSTANCE.get(driveId);
        if (existingRecord != null && (existingRecord.getDriveType() != driveItem.getDriveType() ||
              !existingRecord.hasValidResourceKinds())) {
            return MountAttempt.invalidDrive(driveId);
        }
        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(driveId, driveItem.getDriveTier(), driveItem.getDriveType());
        if (record == null) {
            return MountAttempt.missing(driveId);
        }
        QIODriveStorage.MountResult result = QIODriveStorage.INSTANCE.tryMount(driveId, mount);
        if (result == QIODriveStorage.MountResult.DUPLICATE_UUID) {
            updateMetadata(stack, record);
            syncDriveStack(mount, stack);
            return new MountAttempt(QIODriveSlotState.DUPLICATE_UUID, result, driveId, null);
        } else if (result == QIODriveStorage.MountResult.MISSING_STORAGE) {
            return MountAttempt.missing(driveId);
        }
        QIODriveStorage.INSTANCE.upgradeTier(driveId, driveItem.getDriveTier());
        updateMetadata(stack, record);
        syncDriveStack(mount, stack);
        return new MountAttempt(QIODriveSlotState.ACTIVE, result, driveId,
              new QIODriveData(stack, driveId, mount, record, driveItem.getDriveType(), null));
    }

    /**
     * Ensures that a newly encountered drive has an external record without mounting it.
     */
    public static boolean initialize(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IQIODriveItem) ||
              !QIOResourceTypeRegistry.INSTANCE.isLoaded() || !QIODriveStorage.INSTANCE.isLoaded()) {
            return false;
        }
        IQIODriveItem item = (IQIODriveItem) stack.getItem();
        UUID driveId = item.getOrCreateDriveId(stack);
        QIODriveRecord existingRecord = QIODriveStorage.INSTANCE.get(driveId);
        if (existingRecord != null && (existingRecord.getDriveType() != item.getDriveType() ||
              !existingRecord.hasValidResourceKinds())) {
            return false;
        }
        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(driveId, item.getDriveTier(), item.getDriveType());
        if (record == null) {
            return false;
        }
        updateMetadata(stack, record);
        return true;
    }

    public QIODriveData withUpdateListener(@Nullable Runnable listener) {
        return new QIODriveData(driveStack, driveId, mount, record, driveType, listener);
    }

    public boolean isActive() {
        QIODriveMount active = QIODriveStorage.INSTANCE.getActiveMount(driveId);
        return active != null && mount.equals(active) && mount.getHolder() == active.getHolder();
    }

    public long insert(ItemStack stack, long amount, Action action) {
        if (!accepts(QIOResourceKind.ITEM) || !isActive() || stack == null || stack.isEmpty() || amount <= 0 || action == null) {
            return 0;
        }
        HashedItem type = HashedItem.create(stack);
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(type);
        if (resource == null) {
            if (record.getInsertable(QIOResourceKind.ITEM, amount, true) <= 0) {
                return 0;
            }
            if (action.simulate()) {
                return record.getInsertable(QIOResourceKind.ITEM, amount, true);
            }
            resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(type);
        }
        long inserted = record.insert(resource, amount, action);
        changed(action, inserted);
        return inserted;
    }

    public long insert(FluidStack stack, long amount, Action action) {
        if (!accepts(QIOResourceKind.FLUID) || !isActive() || stack == null || stack.getFluid() == null || amount <= 0 || action == null) {
            return 0;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(stack);
        if (resource == null) {
            if (record.getInsertable(QIOResourceKind.FLUID, amount, true) <= 0) {
                return 0;
            }
            if (action.simulate()) {
                return record.getInsertable(QIOResourceKind.FLUID, amount, true);
            }
            resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(stack);
        }
        long inserted = record.insert(resource, amount, action);
        changed(action, inserted);
        return inserted;
    }

    public long insert(GasStack stack, long amount, Action action) {
        if (!accepts(QIOResourceKind.GAS) || !isActive() || stack == null || stack.getGas() == null || amount <= 0 || action == null) {
            return 0;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(stack);
        if (resource == null) {
            if (record.getInsertable(QIOResourceKind.GAS, amount, true) <= 0) {
                return 0;
            }
            if (action.simulate()) {
                return record.getInsertable(QIOResourceKind.GAS, amount, true);
            }
            resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackGas(stack);
        }
        long inserted = record.insert(resource, amount, action);
        changed(action, inserted);
        return inserted;
    }

    public long extract(ItemStack stack, long amount, Action action) {
        if (!isActive() || stack == null || stack.isEmpty() || amount <= 0) {
            return 0;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(stack));
        return extract(resource, amount, action);
    }

    public long extract(FluidStack stack, long amount, Action action) {
        if (!isActive() || stack == null || stack.getFluid() == null || amount <= 0) {
            return 0;
        }
        return extract(QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(stack), amount, action);
    }

    public long extract(GasStack stack, long amount, Action action) {
        if (!isActive() || stack == null || stack.getGas() == null || amount <= 0) {
            return 0;
        }
        return extract(QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(stack), amount, action);
    }

    public long getStored(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : getStored(QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(stack)));
    }

    public long getStored(FluidStack stack) {
        return stack == null ? 0 : getStored(QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(stack));
    }

    public long getStored(GasStack stack) {
        return stack == null ? 0 : getStored(QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(stack));
    }

    public long getStored(@Nullable UUID resource) {
        return !isActive() || resource == null ? 0 : record.getStored(resource);
    }

    @Nonnull
    public ItemStack extractItemStack(ItemStack requested, long amount, Action action) {
        if (requested == null || requested.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        long extracted = extract(requested, Math.min(Integer.MAX_VALUE, amount), action);
        return QIOResourceTypeRegistry.INSTANCE.createItemStack(
              QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(requested)), (int) Math.min(Integer.MAX_VALUE, extracted));
    }

    public UUID getDriveId() {
        return driveId;
    }

    public QIODriveMount getMount() {
        return mount;
    }

    public QIODriveRecord getRecord() {
        return record;
    }

    public QIODriveType getDriveType() {
        return driveType;
    }

    public boolean accepts(QIOResourceKind kind) {
        return driveType.accepts(kind);
    }

    public long extract(@Nullable UUID resource, long amount, Action action) {
        if (!isActive() || resource == null || amount <= 0 || action == null) {
            return 0;
        }
        long extracted = record.extract(resource, amount, action);
        changed(action, extracted);
        return extracted;
    }

    private void changed(Action action, long amount) {
        if (action.execute() && amount > 0) {
            QIODriveStorage.INSTANCE.markDriveDirty(driveId);
            updateMetadata(driveStack, record);
            syncDriveStack(mount, driveStack);
            if (updateListener != null) {
                updateListener.run();
            }
        }
    }

    private static void updateMetadata(ItemStack stack, QIODriveRecord record) {
        if (stack.getItem() instanceof IQIODriveItem) {
            ((IQIODriveItem) stack.getItem()).setDriveMetadata(stack, record.getTotalCount(), record.getTotalTypes(),
                  record.getTotalStorageUnits());
        }
    }

    private static void syncDriveStack(@Nullable QIODriveMount mount, ItemStack stack) {
        if (mount != null && stack != null && !stack.isEmpty()) {
            // Pass a copy so holders can safely replace their slot without
            // retaining a mutable stack owned by the frequency scan.
            mount.getHolder().updateQIODriveStack(mount.getSlot(), stack.copy());
        }
    }

    public static final class MountAttempt {

        private final QIODriveSlotState state;
        private final QIODriveStorage.MountResult result;
        @Nullable
        private final UUID driveId;
        @Nullable
        private final QIODriveData data;

        private MountAttempt(QIODriveSlotState state, QIODriveStorage.MountResult result, @Nullable UUID driveId,
              @Nullable QIODriveData data) {
            this.state = state;
            this.result = result;
            this.driveId = driveId;
            this.data = data;
        }

        private static MountAttempt invalid(QIODriveSlotState state) {
            return new MountAttempt(state, QIODriveStorage.MountResult.MISSING_STORAGE, null, null);
        }

        private static MountAttempt missing(UUID driveId) {
            return new MountAttempt(QIODriveSlotState.MISSING_STORAGE, QIODriveStorage.MountResult.MISSING_STORAGE, driveId, null);
        }

        private static MountAttempt invalidDrive(UUID driveId) {
            return new MountAttempt(QIODriveSlotState.INVALID_DRIVE, QIODriveStorage.MountResult.INVALID_DRIVE, driveId, null);
        }

        public QIODriveSlotState getState() {
            return state;
        }

        public QIODriveStorage.MountResult getResult() {
            return result;
        }

        @Nullable
        public UUID getDriveId() {
            return driveId;
        }

        @Nullable
        public QIODriveData getData() {
            return data;
        }
    }
}
