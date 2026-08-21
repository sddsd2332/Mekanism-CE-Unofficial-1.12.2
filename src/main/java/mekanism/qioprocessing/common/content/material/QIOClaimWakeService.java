package mekanism.qioprocessing.common.content.material;

import mekanism.api.qio.external.IQIOFrequencyStorageAccess;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageChange;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.common.Mekanism;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkLifecycle;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Main-thread, event-driven material-claim wakeups shared by all processing frequencies. */
/**
 * QIO 处理模块中的 QIOClaimWakeService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOClaimWakeService {

    public static final QIOClaimWakeService INSTANCE = new QIOClaimWakeService();

    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> reportedRefreshFailures = new LinkedHashMap<>();
    private int roundRobinIndex;

    private QIOClaimWakeService() {
    }

    public synchronized int tick(@Nonnull Collection<QIOProcessingNetworkData> networks,
          int maximumRefreshes, @Nonnull IQIOFrequencyStorageAccess storageAccess,
          @Nonnull QIOMaterialClaimCoordinator.PersistenceBarrier persistenceBarrier) {
        Objects.requireNonNull(networks, "networks");
        Objects.requireNonNull(storageAccess, "storageAccess");
        Objects.requireNonNull(persistenceBarrier, "persistenceBarrier");
        if (maximumRefreshes <= 0) {
            throw new IllegalArgumentException("maximumRefreshes must be positive");
        }

        Map<UUID, QIOProcessingNetworkData> currentNetworks = new LinkedHashMap<>();
        for (QIOProcessingNetworkData network : networks) {
            if (network != null) {
                currentNetworks.put(network.getFrequencyUUID(), network);
            }
        }
        reportedRefreshFailures.keySet().retainAll(currentNetworks.keySet());
        synchronizeSessions(currentNetworks, storageAccess, persistenceBarrier);
        if (sessions.isEmpty()) {
            roundRobinIndex = 0;
            return 0;
        }

        List<Session> active = new ArrayList<>(sessions.values());
        int index = Math.floorMod(roundRobinIndex, active.size());
        int idleSessions = 0;
        int workUnits = 0;
        int refreshed = 0;
        while (workUnits < maximumRefreshes && idleSessions < active.size()) {
            Session session = active.get(index);
            QIOProcessingNetworkData network = currentNetworks.get(session.frequencyUUID);
            boolean didWork = network != null && prepareOneBatch(session, network);
            UUID jobId = didWork ? session.pollJob() : null;
            if (jobId != null) {
                workUnits++;
                idleSessions = 0;
                if (network.isClaimWakeCandidate(jobId)) {
                    try {
                        QIOMaterialClaimCoordinator.RefreshResult result =
                              QIOMaterialClaimCoordinator.refresh(network, jobId, session.view,
                                    persistenceBarrier);
                        refreshed++;
                        if (result.getOutcome() == QIOMaterialClaimCoordinator.Outcome.RETRY_REQUIRED) {
                            session.defer(jobId);
                        } else if (result.getOutcome() == QIOMaterialClaimCoordinator.Outcome.WAITING_ACCESS) {
                            session.invalidated = true;
                            session.defer(jobId);
                        }
                    } catch (IOException e) {
                        session.defer(jobId);
                        Mekanism.logger.error("Unable to persist a QIO material-claim wakeup for job {}",
                              jobId, e);
                    } catch (RuntimeException e) {
                        session.defer(jobId);
                        session.invalidated = true;
                        if (shouldReportRefreshFailure(session.frequencyUUID, e)) {
                            Mekanism.logger.error("Unable to refresh QIO material claim for job {}", jobId, e);
                        }
                    }
                }
            } else {
                idleSessions++;
            }
            index = (index + 1) % active.size();
        }
        roundRobinIndex = index;
        for (Session session : sessions.values()) {
            session.promoteDeferred();
        }
        removeInvalidatedSessions(currentNetworks, persistenceBarrier);
        return refreshed;
    }

    public synchronized void shutdown() {
        for (Session session : sessions.values()) {
            session.view.close();
        }
        sessions.clear();
        reportedRefreshFailures.clear();
        roundRobinIndex = 0;
    }

    synchronized int getSessionCount() {
        return sessions.size();
    }

    synchronized int getPendingJobCount(UUID frequencyUUID) {
        Session session = sessions.get(frequencyUUID);
        return session == null ? 0 : session.queuedJobs.size();
    }

    synchronized boolean shouldReportRefreshFailure(UUID frequencyUUID, RuntimeException failure) {
        Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        Objects.requireNonNull(failure, "failure");
        Set<String> reported = reportedRefreshFailures.computeIfAbsent(frequencyUUID,
              ignored -> new LinkedHashSet<>());
        return reported.add(failureSignature(failure));
    }

    private static String failureSignature(Throwable failure) {
        StringBuilder signature = new StringBuilder();
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (signature.length() > 0) {
                signature.append('\n');
            }
            signature.append(current.getClass().getName()).append(':')
                  .append(String.valueOf(current.getMessage()));
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return signature.toString();
    }

    private void synchronizeSessions(Map<UUID, QIOProcessingNetworkData> networks,
          IQIOFrequencyStorageAccess storageAccess,
          QIOMaterialClaimCoordinator.PersistenceBarrier persistenceBarrier) {
        Iterator<Map.Entry<UUID, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            QIOProcessingNetworkData network = networks.get(entry.getKey());
            if (network == null || network.getLifecycle() != QIOProcessingNetworkLifecycle.ACTIVE ||
                  entry.getValue().invalidated || !entry.getValue().view.isValid() ||
                  !entry.getValue().hasQueuedSignals() && !network.hasClaimWakeCandidates()) {
                if (network != null && network.getLifecycle() == QIOProcessingNetworkLifecycle.ACTIVE &&
                      (entry.getValue().invalidated || !entry.getValue().view.isValid())) {
                    persistAccessLoss(network, persistenceBarrier);
                }
                entry.getValue().view.close();
                iterator.remove();
            }
        }
        for (QIOProcessingNetworkData network : networks.values()) {
            if (network.getLifecycle() != QIOProcessingNetworkLifecycle.ACTIVE ||
                  sessions.containsKey(network.getFrequencyUUID()) ||
                  !network.hasClaimWakeCandidates()) {
                continue;
            }
            QIOFrequencyIdentitySnapshot identity = network.getLastKnownFrequencyIdentity();
            UUID principal = identity.getOwnerUUID();
            QIOFrequencyReference reference = new QIOFrequencyReference(network.getFrequencyUUID(),
                  identity.getName(), identity.getOwnerUUID(), identity.getSecurityMode(), principal);
            IQIOStorageView view = storageAccess.open(reference, principal);
            if (view == null || !view.isValid()) {
                if (view != null) {
                    view.close();
                }
                persistAccessLoss(network, persistenceBarrier);
                continue;
            }
            Session session = new Session(network.getFrequencyUUID(), view);
            sessions.put(network.getFrequencyUUID(), session);
            if (!view.addListener(changes -> onStorageChanged(session, changes))) {
                sessions.remove(network.getFrequencyUUID());
                view.close();
                persistAccessLoss(network, persistenceBarrier);
                continue;
            }
            session.fullScanRequested = true;
        }
    }

    private boolean prepareOneBatch(Session session, QIOProcessingNetworkData network) {
        if (session.invalidated) {
            return false;
        }
        long generation = network.getClaimWakeGeneration();
        if (session.fullScanRequested || session.observedClaimWakeGeneration != generation) {
            session.fullScanRequested = false;
            session.observedClaimWakeGeneration = generation;
            session.replaceJobs(network.getClaimWakeCandidates());
        }
        if (!session.changedResources.isEmpty()) {
            Iterator<PortableResourceDescriptor> resources = session.changedResources.iterator();
            PortableResourceDescriptor resource = resources.next();
            resources.remove();
            for (QIOCraftingJob job : network.getWaitingJobs(resource)) {
                session.enqueue(job.getJobId());
            }
        }
        return session.hasPendingJobs();
    }

    private synchronized void onStorageChanged(Session expected, QIOStorageChangeBatch changes) {
        Session current = sessions.get(expected.frequencyUUID);
        if (current != expected) {
            return;
        }
        if (changes.isInvalidated()) {
            current.invalidated = true;
            return;
        }
        if (changes.isFullRescanRequired()) {
            current.fullScanRequested = true;
        }
        boolean claimChanged = changes.isClaimChanged();
        if (claimChanged || changes.isCapacityChanged()) {
            current.fullScanRequested = true;
        }
        for (QIOStorageChange change : changes.getChanges()) {
            if (change.getNewAmount().compareTo(change.getOldAmount()) < 0) {
                current.fullScanRequested = true;
            }
            if (claimChanged || change.getNewAmount().compareTo(change.getOldAmount()) <= 0) {
                continue;
            }
            try {
                current.changedResources.add(
                      PortableResourceDescriptor.fromStorageEntry(change.getResource()));
            } catch (RuntimeException e) {
                current.fullScanRequested = true;
                Mekanism.logger.error("Unable to identify a changed QIO resource; requesting a full claim scan", e);
            }
        }
    }

    private void removeInvalidatedSessions(Map<UUID, QIOProcessingNetworkData> networks,
          QIOMaterialClaimCoordinator.PersistenceBarrier persistenceBarrier) {
        Iterator<Session> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.invalidated || !session.view.isValid()) {
                QIOProcessingNetworkData network = networks.get(session.frequencyUUID);
                if (network != null && network.getLifecycle() == QIOProcessingNetworkLifecycle.ACTIVE) {
                    persistAccessLoss(network, persistenceBarrier);
                }
                session.view.close();
                iterator.remove();
            }
        }
    }

    private void persistAccessLoss(QIOProcessingNetworkData network,
          QIOMaterialClaimCoordinator.PersistenceBarrier persistenceBarrier) {
        if (!network.markClaimStorageAccessUnavailable()) {
            return;
        }
        try {
            persistenceBarrier.persist(network);
        } catch (IOException e) {
            Mekanism.logger.error("Unable to persist QIO claim access loss for frequency {}",
                  network.getFrequencyUUID(), e);
        }
    }

    private static final class Session {

        private final UUID frequencyUUID;
        private final IQIOStorageView view;
        private final Deque<UUID> pendingJobs = new ArrayDeque<>();
        private final Deque<UUID> deferredJobs = new ArrayDeque<>();
        private final Set<UUID> queuedJobs = new HashSet<>();
        private final Set<PortableResourceDescriptor> changedResources = new LinkedHashSet<>();
        private boolean fullScanRequested;
        private boolean invalidated;
        private long observedClaimWakeGeneration = -1;

        private Session(UUID frequencyUUID, IQIOStorageView view) {
            this.frequencyUUID = frequencyUUID;
            this.view = view;
        }

        private void enqueue(UUID jobId) {
            if (queuedJobs.add(jobId)) {
                pendingJobs.addLast(jobId);
            }
        }

        private void replaceJobs(List<QIOCraftingJob> jobs) {
            pendingJobs.clear();
            deferredJobs.clear();
            queuedJobs.clear();
            for (QIOCraftingJob job : jobs) {
                enqueue(job.getJobId());
            }
        }

        private void defer(UUID jobId) {
            if (queuedJobs.add(jobId)) {
                deferredJobs.addLast(jobId);
            }
        }

        private UUID pollJob() {
            UUID jobId = pendingJobs.pollFirst();
            if (jobId != null) {
                queuedJobs.remove(jobId);
            }
            return jobId;
        }

        private void promoteDeferred() {
            UUID jobId;
            while ((jobId = deferredJobs.pollFirst()) != null) {
                pendingJobs.addLast(jobId);
            }
        }

        private boolean hasPendingJobs() {
            return !pendingJobs.isEmpty();
        }

        private boolean hasQueuedSignals() {
            return fullScanRequested || !changedResources.isEmpty() || !queuedJobs.isEmpty();
        }
    }
}
