package mekanism.common.content.qio;

import mekanism.api.qio.external.IQIOStorageAccessor;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.util.SecurityUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/** Stable dashboard-owned implementation behind the public QIO storage capability. */
public final class QIOStorageAccessor implements IQIOStorageAccessor {

    private final TileEntityQIODashboard dashboard;
    private final Set<QIOStorageViewImpl> listeningViews =
          Collections.newSetFromMap(new IdentityHashMap<>());

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
        net.minecraft.util.EnumFacing dashboardFacing = dashboard.facing;
        return new QIOStorageViewImpl(frequency,
              () -> isAccessibleFrequency(frequency, requester, dashboardFacing),
              listeningViews::add, listeningViews::remove);
    }

    public void tick() {
        for (QIOStorageViewImpl view : new ArrayList<>(listeningViews)) {
            if (!view.isCurrentlyValid()) {
                view.invalidate(true);
            }
        }
    }

    public void invalidate() {
        for (QIOStorageViewImpl view : new ArrayList<>(listeningViews)) {
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

    private boolean isAccessibleFrequency(QIOFrequency frequency, @Nullable UUID requester,
          net.minecraft.util.EnumFacing dashboardFacing) {
        return isDashboardLoaded() && dashboard.facing == dashboardFacing &&
              dashboard.getQIOFrequency() == frequency &&
              SecurityUtils.canAccess(requester, dashboard) &&
              SecurityUtils.canAccess(frequency.getSecurity(), requester, frequency.getOwner());
    }

    private boolean isDashboardLoaded() {
        if (dashboard.isInvalid() || dashboard.getWorld() == null || dashboard.getWorld().isRemote) {
            return false;
        }
        return dashboard.getWorld().isBlockLoaded(dashboard.getPos()) &&
              dashboard.getWorld().getTileEntity(dashboard.getPos()) == dashboard;
    }

}
