package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.IQIOStorageAccessor;
import mekanism.api.qio.external.IQIOStorageListener;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.util.SecurityUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/** Stable dashboard-owned implementation behind the public QIO storage capability. */
public final class QIOStorageAccessor implements IQIOStorageAccessor {

    private final TileEntityQIODashboard dashboard;
    private final Set<StorageView> listeningViews = Collections.newSetFromMap(new IdentityHashMap<>());

    public QIOStorageAccessor(TileEntityQIODashboard dashboard) {
        this.dashboard = dashboard;
    }

    @Nullable
    @Override
    public IQIOStorageView open(@Nullable UUID requester) {
        QIOFrequency frequency = getAccessibleFrequency(requester);
        if (frequency == null) {
            return null;
        }
        return new StorageView(frequency, requester, dashboard.facing);
    }

    public void tick() {
        for (StorageView view : new ArrayList<>(listeningViews)) {
            if (!view.isCurrentlyValid()) {
                view.invalidate(true);
            }
        }
    }

    public void invalidate() {
        for (StorageView view : new ArrayList<>(listeningViews)) {
            view.invalidate(true);
        }
        listeningViews.clear();
    }

    @Nullable
    private QIOFrequency getAccessibleFrequency(@Nullable UUID requester) {
        if (!isDashboardLoaded()) {
            return null;
        }
        QIOFrequency frequency = dashboard.getQIOFrequency();
        if (frequency == null || !frequency.isValid() || frequency.isRemoved() ||
              !QIOStorageManager.isLoaded()) {
            return null;
        }
        if (!SecurityUtils.canAccess(requester, dashboard) ||
              !SecurityUtils.canAccess(frequency.getSecurity(), requester, frequency.getOwner())) {
            return null;
        }
        return frequency;
    }

    private boolean isDashboardLoaded() {
        if (dashboard.isInvalid() || dashboard.getWorld() == null || dashboard.getWorld().isRemote) {
            return false;
        }
        return dashboard.getWorld().isBlockLoaded(dashboard.getPos()) &&
              dashboard.getWorld().getTileEntity(dashboard.getPos()) == dashboard;
    }

    private final class StorageView implements IQIOStorageView {

        private final QIOFrequency frequency;
        @Nullable
        private final UUID requester;
        private final net.minecraft.util.EnumFacing dashboardFacing;
        private final Set<IQIOStorageListener> listeners = Collections.newSetFromMap(new IdentityHashMap<>());
        private final IQIOStorageListener frequencyListener = this::onFrequencyChanged;
        private boolean listening;
        private boolean closed;

        private StorageView(QIOFrequency frequency, @Nullable UUID requester,
              net.minecraft.util.EnumFacing dashboardFacing) {
            this.frequency = frequency;
            this.requester = requester;
            this.dashboardFacing = dashboardFacing;
        }

        @Nonnull
        @Override
        public UUID getFrequencyUUID() {
            return frequency.getFrequencyUUID();
        }

        @Nonnull
        @Override
        public String getFrequencyName() {
            return frequency.getName();
        }

        @Override
        public long getContentsRevision() {
            return frequency.getContentsRevision();
        }

        @Override
        public long getCapacityRevision() {
            return frequency.getCapacityRevision();
        }

        @Override
        public long getAccessRevision() {
            return frequency.getAccessRevision();
        }

        @Nonnull
        @Override
        public QIOStorageSnapshot getSnapshot() {
            if (!isCurrentlyValid()) {
                throw new IllegalStateException("QIO storage view is no longer valid");
            }
            return frequency.getExternalStorageSnapshot();
        }

        @Nullable
        @Override
        public QIOStorageEntry getResource(UUID resourceUUID) {
            return isCurrentlyValid() ? frequency.getExternalStorageEntry(resourceUUID) : null;
        }

        @Override
        public long insert(ItemStack stack, long amount, Action action) {
            return isCurrentlyValid() ? frequency.massInsert(stack == null ? ItemStack.EMPTY : stack.copy(), amount, action) : 0;
        }

        @Override
        public long insert(FluidStack stack, long amount, Action action) {
            return isCurrentlyValid() && stack != null ? frequency.massInsert(stack.copy(), amount, action) : 0;
        }

        @Override
        public long insert(GasStack stack, long amount, Action action) {
            return isCurrentlyValid() && stack != null ? frequency.massInsert(stack.copy(), amount, action) : 0;
        }

        @Override
        public long extract(ItemStack stack, long amount, Action action) {
            return isCurrentlyValid() ? frequency.massExtract(stack == null ? ItemStack.EMPTY : stack.copy(), amount, action) : 0;
        }

        @Override
        public long extract(FluidStack stack, long amount, Action action) {
            return isCurrentlyValid() && stack != null ? frequency.massExtract(stack.copy(), amount, action) : 0;
        }

        @Override
        public long extract(GasStack stack, long amount, Action action) {
            return isCurrentlyValid() && stack != null ? frequency.massExtract(stack.copy(), amount, action) : 0;
        }

        @Override
        public boolean addListener(IQIOStorageListener listener) {
            if (listener == null || closed || !isCurrentlyValid() || !listeners.add(listener)) {
                return false;
            }
            if (!listening) {
                listening = frequency.addExternalStorageListener(frequencyListener);
                if (!listening) {
                    listeners.remove(listener);
                    return false;
                }
                listeningViews.add(this);
            }
            return true;
        }

        @Override
        public boolean removeListener(IQIOStorageListener listener) {
            if (listener == null || !listeners.remove(listener)) {
                return false;
            }
            stopListeningIfUnused();
            return true;
        }

        @Override
        public boolean isValid() {
            return isCurrentlyValid();
        }

        @Override
        public void close() {
            invalidate(false);
        }

        private boolean isCurrentlyValid() {
            if (closed || !isDashboardLoaded() ||
                  dashboard.facing != dashboardFacing || dashboard.getQIOFrequency() != frequency ||
                  !frequency.isValid() || frequency.isRemoved() || !QIOStorageManager.isLoaded()) {
                return false;
            }
            return SecurityUtils.canAccess(requester, dashboard) &&
                  SecurityUtils.canAccess(frequency.getSecurity(), requester, frequency.getOwner());
        }

        private void onFrequencyChanged(QIOStorageChangeBatch changes) {
            if (changes.isInvalidated() || !isCurrentlyValid()) {
                invalidate(true);
                return;
            }
            for (IQIOStorageListener listener : new ArrayList<>(listeners)) {
                if (!listeners.contains(listener)) {
                    continue;
                }
                try {
                    listener.onQIOStorageChanged(changes);
                } catch (RuntimeException e) {
                    listeners.remove(listener);
                    QIOLog.LOGGER.error("Removing a failing listener from a QIO storage view", e);
                }
            }
            stopListeningIfUnused();
        }

        private void invalidate(boolean notify) {
            if (closed) {
                return;
            }
            closed = true;
            if (listening) {
                frequency.removeExternalStorageListener(frequencyListener);
                listening = false;
            }
            listeningViews.remove(this);
            if (notify && !listeners.isEmpty()) {
                QIOStorageChangeBatch invalidation = QIOStorageChangeBatch.invalidated(
                      frequency.getContentsRevision(), frequency.getCapacityRevision(), frequency.getAccessRevision());
                for (IQIOStorageListener listener : new ArrayList<>(listeners)) {
                    try {
                        listener.onQIOStorageChanged(invalidation);
                    } catch (RuntimeException e) {
                        QIOLog.LOGGER.error("A QIO storage view listener failed during invalidation", e);
                    }
                }
            }
            listeners.clear();
        }

        private void stopListeningIfUnused() {
            if (listeners.isEmpty() && listening) {
                frequency.removeExternalStorageListener(frequencyListener);
                listening = false;
                listeningViews.remove(this);
            }
        }
    }
}
