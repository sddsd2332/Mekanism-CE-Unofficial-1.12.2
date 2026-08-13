package mekanism.qioprocessing.common.maintenance;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Pure, bounded debounce state used by one frequency's QIO storage listener. */
final class QIOMaintenanceWakeTracker {

    private final int maximumDirtyResources;
    private final long contentSettleTicks;
    private final long maximumContentDelayTicks;
    private final long structuralSettleTicks;
    private final long maximumStructuralDelayTicks;
    private final Set<PortableResourceDescriptor> dirtyResources = new LinkedHashSet<>();
    private boolean fullRescan;
    private boolean invalidated;
    private long firstSignalTick = Long.MAX_VALUE;
    private long wakeAtTick = Long.MAX_VALUE;

    QIOMaintenanceWakeTracker(int maximumDirtyResources, long contentSettleTicks,
          long maximumContentDelayTicks, long structuralSettleTicks,
          long maximumStructuralDelayTicks) {
        if (maximumDirtyResources <= 0 || contentSettleTicks < 0 ||
              maximumContentDelayTicks < contentSettleTicks || structuralSettleTicks < 0 ||
              maximumStructuralDelayTicks < structuralSettleTicks) {
            throw new IllegalArgumentException("Invalid maintenance wake limits");
        }
        this.maximumDirtyResources = maximumDirtyResources;
        this.contentSettleTicks = contentSettleTicks;
        this.maximumContentDelayTicks = maximumContentDelayTicks;
        this.structuralSettleTicks = structuralSettleTicks;
        this.maximumStructuralDelayTicks = maximumStructuralDelayTicks;
    }

    void requestInitialScan(long currentTick) {
        requestFullScan(currentTick, 0, 0);
    }

    void recordResource(PortableResourceDescriptor resource, long currentTick) {
        Objects.requireNonNull(resource, "resource");
        if (fullRescan) {
            return;
        }
        if (dirtyResources.size() >= maximumDirtyResources &&
              !dirtyResources.contains(resource)) {
            requestFullScan(currentTick, contentSettleTicks, maximumContentDelayTicks);
            return;
        }
        dirtyResources.add(resource);
        schedule(currentTick, contentSettleTicks, maximumContentDelayTicks);
    }

    void requestStructuralScan(long currentTick) {
        requestFullScan(currentTick, structuralSettleTicks, maximumStructuralDelayTicks);
    }

    void invalidate() {
        invalidated = true;
        dirtyResources.clear();
        fullRescan = false;
        firstSignalTick = Long.MAX_VALUE;
        wakeAtTick = Long.MAX_VALUE;
    }

    boolean consumeIfDue(long currentTick,
          Predicate<PortableResourceDescriptor> relevantResource) {
        return !drainIfDue(currentTick, relevantResource).isEmpty();
    }

    WakeBatch drainIfDue(long currentTick,
          Predicate<PortableResourceDescriptor> relevantResource) {
        Objects.requireNonNull(relevantResource, "relevantResource");
        if (invalidated || currentTick < wakeAtTick ||
              !fullRescan && dirtyResources.isEmpty()) {
            return WakeBatch.EMPTY;
        }
        boolean full = fullRescan;
        List<PortableResourceDescriptor> relevant = new ArrayList<>();
        if (!full) {
            for (PortableResourceDescriptor resource : dirtyResources) {
                if (relevantResource.test(resource)) {
                    relevant.add(resource);
                }
            }
        }
        dirtyResources.clear();
        fullRescan = false;
        firstSignalTick = Long.MAX_VALUE;
        wakeAtTick = Long.MAX_VALUE;
        return full ? WakeBatch.FULL : relevant.isEmpty() ? WakeBatch.EMPTY :
              new WakeBatch(false, relevant);
    }

    boolean isInvalidated() {
        return invalidated;
    }

    int getDirtyResourceCount() {
        return dirtyResources.size();
    }

    boolean isFullRescanRequested() {
        return fullRescan;
    }

    long getWakeAtTick() {
        return wakeAtTick;
    }

    private void requestFullScan(long currentTick, long delay, long maximumDelay) {
        fullRescan = true;
        dirtyResources.clear();
        schedule(currentTick, delay, maximumDelay);
    }

    private void schedule(long currentTick, long delay, long maximumDelay) {
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick cannot be negative");
        }
        if (firstSignalTick == Long.MAX_VALUE) {
            firstSignalTick = currentTick;
        }
        long requested = delay > Long.MAX_VALUE - currentTick ? Long.MAX_VALUE :
              currentTick + delay;
        long deadline = maximumDelay > Long.MAX_VALUE - firstSignalTick ? Long.MAX_VALUE :
              firstSignalTick + maximumDelay;
        long settled = wakeAtTick == Long.MAX_VALUE ? requested :
              Math.max(wakeAtTick, requested);
        wakeAtTick = Math.min(settled, deadline);
    }

    static final class WakeBatch {

        private static final WakeBatch EMPTY = new WakeBatch(false, Collections.emptyList());
        private static final WakeBatch FULL = new WakeBatch(true, Collections.emptyList());

        private final boolean fullScan;
        private final List<PortableResourceDescriptor> resources;

        private WakeBatch(boolean fullScan, List<PortableResourceDescriptor> resources) {
            this.fullScan = fullScan;
            this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        }

        boolean isFullScan() {
            return fullScan;
        }

        List<PortableResourceDescriptor> getResources() {
            return resources;
        }

        boolean isEmpty() {
            return !fullScan && resources.isEmpty();
        }
    }
}
