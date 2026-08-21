package mekanism.qioprocessing.common.terminal;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks portable terminal instances currently reachable through open server containers.
 * A duplicate persistent UUID quarantines every observed copy until explicit recovery.
 */
/**
 * QIO 处理模块中的 QIOPortableTerminalIdentityRegistry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPortableTerminalIdentityRegistry {

    public static final QIOPortableTerminalIdentityRegistry INSTANCE =
          new QIOPortableTerminalIdentityRegistry();
    private static final int MAX_LOCATION_LENGTH = 256;

    private final Map<UUID, List<Lease>> leasesByUUID = new LinkedHashMap<>();
    private final Set<UUID> quarantinedUUIDs = new LinkedHashSet<>();

    private QIOPortableTerminalIdentityRegistry() {
    }

    @Nonnull
    public synchronized Lease acquire(@Nonnull UUID terminalUUID,
          @Nonnull Object exactStackHandle, @Nonnull String auditLocation) {
        Objects.requireNonNull(terminalUUID, "terminalUUID");
        Objects.requireNonNull(exactStackHandle, "exactStackHandle");
        String location = requireLocation(auditLocation);
        List<Lease> leases = leasesByUUID.computeIfAbsent(terminalUUID,
              ignored -> new ArrayList<>());
        leases.removeIf(Lease::isClosed);
        for (Lease existing : leases) {
            if (existing.exactStackHandle == exactStackHandle) {
                return new Lease(terminalUUID, exactStackHandle, location, true, false);
            }
        }
        Lease acquired = new Lease(terminalUUID, exactStackHandle, location);
        leases.add(acquired);
        if (quarantinedUUIDs.contains(terminalUUID) || distinctHandleCount(leases) > 1) {
            quarantinedUUIDs.add(terminalUUID);
            leases.forEach(Lease::quarantine);
        }
        return acquired;
    }

    public synchronized boolean isQuarantined(@Nonnull UUID terminalUUID) {
        return quarantinedUUIDs.contains(Objects.requireNonNull(terminalUUID,
              "terminalUUID"));
    }

    @Nonnull
    public synchronized List<String> getActiveAuditLocations(@Nonnull UUID terminalUUID) {
        List<Lease> leases = leasesByUUID.get(Objects.requireNonNull(terminalUUID,
              "terminalUUID"));
        if (leases == null) {
            return Collections.emptyList();
        }
        List<String> locations = new ArrayList<>();
        for (Lease lease : leases) {
            if (!lease.isClosed()) {
                locations.add(lease.auditLocation);
            }
        }
        Collections.sort(locations);
        return Collections.unmodifiableList(locations);
    }

    /** Explicit recovery is allowed only after every observed copy has closed. */
    public synchronized boolean acknowledgeRecovery(@Nonnull UUID terminalUUID) {
        Objects.requireNonNull(terminalUUID, "terminalUUID");
        List<Lease> leases = leasesByUUID.get(terminalUUID);
        if (leases != null) {
            leases.removeIf(Lease::isClosed);
            if (!leases.isEmpty()) {
                return false;
            }
            leasesByUUID.remove(terminalUUID);
        }
        return quarantinedUUIDs.remove(terminalUUID);
    }

    public synchronized void shutdown() {
        for (List<Lease> leases : leasesByUUID.values()) {
            leases.forEach(Lease::closeInternal);
        }
        leasesByUUID.clear();
        quarantinedUUIDs.clear();
    }

    private synchronized void release(Lease lease) {
        if (lease.closed) {
            return;
        }
        lease.closeInternal();
        List<Lease> leases = leasesByUUID.get(lease.terminalUUID);
        if (leases != null) {
            leases.remove(lease);
            if (leases.isEmpty()) {
                leasesByUUID.remove(lease.terminalUUID);
            }
        }
    }

    private static int distinctHandleCount(List<Lease> leases) {
        Set<Object> handles = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Lease lease : leases) {
            if (!lease.closed && !lease.alreadyOpenRejection) {
                handles.add(lease.exactStackHandle);
            }
        }
        return handles.size();
    }

    private static String requireLocation(String auditLocation) {
        String checked = Objects.requireNonNull(auditLocation, "auditLocation").trim();
        if (checked.isEmpty() || checked.length() > MAX_LOCATION_LENGTH) {
            throw new IllegalArgumentException("Portable terminal audit location is invalid");
        }
        return checked;
    }

    public final class Lease implements AutoCloseable {

        private final UUID terminalUUID;
        private final Object exactStackHandle;
        private final String auditLocation;
        private final boolean alreadyOpenRejection;
        private volatile boolean quarantined;
        private volatile boolean closed;

        private Lease(UUID terminalUUID, Object exactStackHandle, String auditLocation) {
            this(terminalUUID, exactStackHandle, auditLocation, false, false);
        }

        private Lease(UUID terminalUUID, Object exactStackHandle, String auditLocation,
              boolean alreadyOpenRejection, boolean closed) {
            this.terminalUUID = terminalUUID;
            this.exactStackHandle = exactStackHandle;
            this.auditLocation = auditLocation;
            this.alreadyOpenRejection = alreadyOpenRejection;
            this.closed = closed;
        }

        @Nonnull
        public UUID getTerminalUUID() {
            return terminalUUID;
        }

        @Nonnull
        public String getAuditLocation() {
            return auditLocation;
        }

        public boolean isUsable() {
            return !closed && !quarantined && !alreadyOpenRejection &&
                  !QIOPortableTerminalIdentityRegistry.this.isQuarantined(terminalUUID);
        }

        public boolean isQuarantined() {
            return quarantined ||
                  QIOPortableTerminalIdentityRegistry.this.isQuarantined(terminalUUID);
        }

        public boolean isAlreadyOpenRejection() {
            return alreadyOpenRejection;
        }

        public boolean isClosed() {
            return closed;
        }

        private void quarantine() {
            quarantined = true;
        }

        private void closeInternal() {
            closed = true;
        }

        @Override
        public void close() {
            QIOPortableTerminalIdentityRegistry.this.release(this);
        }
    }
}
