package mekanism.common.multiblock.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One bounded, serialized writer for all world sessions. A session permits one outstanding
 * operation; the main thread polls its ticket without waiting. Closing queues a barrier AFTER
 * accepted writes, so a subsequent open of the same path sees them before replay. It never
 * cancels a commit merely because the originating World was unloaded.
 */
public final class ManagedCacheIo implements AutoCloseable {
    private static final int MAX_SESSIONS = 64;
    private static final long MAX_QUEUED_BYTES = 128L * 1024 * 1024;
    private final ThreadPoolExecutor executor;
    private final Map<Path, Session> sessions = new HashMap<>();
    private final Factory factory;
    private boolean closed;
    private final AtomicLong queuedBytes = new AtomicLong();

    public ManagedCacheIo() {
        this(ManagedCacheStore::new);
    }

    ManagedCacheIo(Factory factory) {
        this.factory = factory;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
              new ArrayBlockingQueue<>(MAX_SESSIONS * 2 + 1), runnable -> {
                  Thread thread = new Thread(runnable, "Mekanism-multiblock-store");
                  // Accepted final snapshots must finish even after the last server thread exits.
                  thread.setDaemon(false);
                  return thread;
              }, new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized Session open(Path directory, String namespace) throws IOException {
        if (closed) throw new IOException("Multiblock IO service is closed");
        Path key = directoryKey(directory);
        if (sessions.containsKey(key)) throw new IOException("Multiblock world path is already open or closing: " + key);
        if (sessions.size() >= MAX_SESSIONS) throw new MultiblockBackpressureException("Multiblock world/manager session budget exceeded");
        Session session = new Session(key, namespace);
        sessions.put(key, session);
        try {
            executor.execute(session::load);
        } catch (RejectedExecutionException rejected) {
            sessions.remove(key);
            throw new MultiblockBackpressureException("Multiblock IO queue is full", rejected);
        }
        return session;
    }

    public synchronized boolean isReleased(Path directory) throws IOException {
        return !sessions.containsKey(directoryKey(directory));
    }

    private static Path directoryKey(Path directory) throws IOException {
        Path existing = directory.toAbsolutePath().normalize();
        Deque<Path> missing = new ArrayDeque<>();
        while (existing != null) {
            try {
                Path resolved = existing.toRealPath();
                for (Path component : missing) resolved = resolved.resolve(component);
                return resolved;
            } catch (NoSuchFileException absent) {
                if (existing.getFileName() == null) throw absent;
                missing.addFirst(existing.getFileName());
                existing = existing.getParent();
            }
        }
        throw new IOException("No existing ancestor for multiblock storage path: " + directory);
    }

    private synchronized void released(Session session) {
        sessions.remove(session.directory, session);
    }

    /** Initiates drain only. Tick code must never call awaitTermination or wait on tickets. */
    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            for (Session session : new ArrayList<>(sessions.values())) session.close();
            executor.shutdown();
        }
    }

    public boolean isTerminated() { return executor.isTerminated(); }
    public long queuedBytes() { return queuedBytes.get(); }

    private void reserve(long bytes) throws IOException {
        while (true) {
            long used = queuedBytes.get();
            if (bytes < 0 || bytes > MAX_QUEUED_BYTES) throw new IOException("Invalid multiblock IO snapshot size");
            if (used > MAX_QUEUED_BYTES - bytes) throw new MultiblockBackpressureException("Multiblock IO snapshot memory budget exceeded");
            if (queuedBytes.compareAndSet(used, used + bytes)) return;
        }
    }

    public enum State { LOADING, READY, FAILED, CLOSING, CLOSED }

    public static final class Ticket<T> {
        private volatile boolean done;
        private T result;
        private IOException failure;
        private final long queuedNanos = System.nanoTime();
        private volatile long workerStartedNanos;
        private volatile long workerCompletedNanos;

        public boolean isDone() { return done; }

        /** Time spent executing the operation on the serialized IO worker, excluding queueing. */
        public long workerNanos() {
            long started = workerStartedNanos;
            long completed = workerCompletedNanos;
            return started == 0 || completed == 0 ? -1 : Math.max(0, completed - started);
        }

        public long queuedNanos() {
            long started = workerStartedNanos;
            return started == 0 ? -1 : Math.max(0, started - queuedNanos);
        }

        public T result() throws IOException {
            if (!done) throw new IllegalStateException("Multiblock IO has not completed");
            if (failure != null) throw failure;
            return result;
        }

        private void complete(T result, IOException failure) {
            this.result = result;
            this.failure = failure;
            done = true;
        }

        private void workerStarted() { workerStartedNanos = System.nanoTime(); }
        private void workerCompleted() { workerCompletedNanos = System.nanoTime(); }
    }

    public final class Session implements AutoCloseable {
        public final Path directory;
        public final String namespace;
        private volatile State state = State.LOADING;
        private volatile IOException failure;
        private volatile ManagedCacheStore.View loaded;
        private ManagedCacheStore store; // IO-thread confined, including close.
        private Ticket<ManagedCacheStore.View> outstanding;
        private final Ticket<Void> closedTicket = new Ticket<>();

        private Session(Path directory, String namespace) {
            this.directory = directory;
            this.namespace = namespace;
        }

        public State state() { return state; }
        public IOException failure() { return failure; }
        public Ticket<Void> closedTicket() { return closedTicket; }

        public ManagedCacheStore.View initialView() {
            if (state != State.READY) throw new IllegalStateException("Multiblock store is " + state, failure);
            return loaded;
        }

        private void load() {
            try {
                store = factory.open(directory, namespace);
                MultiblockEmergencySnapshot.restore(directory, namespace, store);
                ManagedCacheStore.View view = store.view();
                synchronized (this) {
                    if (state == State.LOADING) {
                        loaded = view;
                        state = State.READY;
                    }
                }
            } catch (IOException | RuntimeException error) {
                fail(asIo(error));
            }
        }

        public Ticket<ManagedCacheStore.View> transfer(UUID transaction, UUID target,
              Collection<ManagedCacheStore.Source> sources, long[] positions, byte[] snapshot) throws IOException {
            if (sources.size() > ManagedCacheStore.MAX_SOURCES || positions.length > ManagedCacheStore.MAX_BINDING_POSITIONS ||
                  snapshot.length > ManagedCacheStore.MAX_CACHE_BYTES) throw new IOException("Multiblock transfer exceeds queue payload budget");
            Collection<ManagedCacheStore.Source> captured = new ArrayList<>(sources);
            long[] binding = positions.clone();
            byte[] bytes = snapshot.clone();
            return submit(() -> store.transfer(transaction, target, captured, binding, bytes), bytes.length + binding.length * 8L + captured.size() * 64L);
        }

        public Ticket<ManagedCacheStore.View> save(UUID transaction, Collection<ManagedCacheStore.Update> updates) throws IOException {
            if (updates.size() > ManagedCacheStore.MAX_SOURCES) throw new IOException("Multiblock save batch exceeds queue budget");
            Collection<ManagedCacheStore.Update> captured = new ArrayList<>(updates);
            long bytes = 0;
            for (ManagedCacheStore.Update update : captured) bytes += update.payloadBytes() + 64L;
            if (bytes > MultiblockJournal.MAX_PAYLOAD_BYTES) throw new IOException("Multiblock save payload budget exceeded");
            return submit(() -> store.save(transaction, captured), bytes);
        }

        public Ticket<ManagedCacheStore.View> checkpoint() throws IOException {
            return submit(() -> store.checkpoint(), 0);
        }

        private synchronized Ticket<ManagedCacheStore.View> submit(Operation operation, long bytes) throws IOException {
            if (state != State.READY) throw new IOException("Multiblock store is " + state, failure);
            if (outstanding != null && !outstanding.isDone()) throw new IOException("A multiblock write is already pending");
            reserve(bytes);
            Ticket<ManagedCacheStore.View> ticket = new Ticket<>();
            outstanding = ticket;
            try {
                executor.execute(() -> {
                    ticket.workerStarted();
                    try {
                        operation.run();
                        ManagedCacheStore.View view = store.view();
                        ticket.workerCompleted();
                        ticket.complete(view, null);
                    } catch (IOException | RuntimeException error) {
                        IOException io = asIo(error);
                        fail(io);
                        ticket.workerCompleted();
                        ticket.complete(null, io);
                    } finally {
                        queuedBytes.addAndGet(-bytes);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                IOException error = executor.isShutdown() ? new IOException("Multiblock IO service is closing", rejected) :
                      new MultiblockBackpressureException("Multiblock IO queue rejected write", rejected);
                queuedBytes.addAndGet(-bytes);
                ticket.complete(null, error);
                throw error;
            }
            return ticket;
        }

        private synchronized void fail(IOException error) {
            failure = error;
            loaded = null;
            if (state != State.CLOSING && state != State.CLOSED) state = State.FAILED;
        }

        /** Queued behind already accepted writes; no stale World callbacks are captured. */
        @Override
        public synchronized void close() {
            try {
                closeWithFinalSnapshots(java.util.Collections.emptyMap());
            } catch (IOException impossible) {
                throw new IllegalStateException("Could not schedule multiblock close barrier", impossible);
            }
        }

        /**
         * Last authoritative snapshots captured on the main thread after tile/chunk activity stops.
         * They are saved after the outstanding operation, using its resulting revisions. Frozen
         * migration sources must be excluded by the caller; missing IDs fail rather than resurrect.
         */
        public synchronized void closeWithFinalSnapshots(Map<UUID, byte[]> snapshots) throws IOException {
            closeWithFinalSnapshots(snapshots, null);
        }

        synchronized void closeWithFinalSnapshots(Map<UUID, byte[]> snapshots, MultiblockEmergencySnapshot emergency) throws IOException {
            if (state == State.CLOSING || state == State.CLOSED) return;
            if (snapshots.size() > ManagedCacheStore.MAX_SOURCES) throw new IOException("Final snapshot count exceeds budget");
            long bytes = 0;
            for (byte[] value : snapshots.values()) {
                if (value.length > ManagedCacheStore.MAX_CACHE_BYTES) throw new IOException("Final cache snapshot exceeds budget");
                bytes += value.length + 64L;
            }
            if (bytes > MultiblockJournal.MAX_PAYLOAD_BYTES) throw new IOException("Final save batch exceeds budget");
            if (emergency != null) bytes += emergency.payloadBytes();
            reserve(bytes);
            Map<UUID, byte[]> captured = new HashMap<>();
            for (Map.Entry<UUID, byte[]> entry : snapshots.entrySet()) captured.put(entry.getKey(), entry.getValue().clone());
            final long reserved = bytes;
            State previousState = state;
            ManagedCacheStore.View previousView = loaded;
            state = State.CLOSING;
            loaded = null;
            Runnable closeBarrier = () -> {
                IOException closeFailure = failure;
                try {
                    if (emergency != null) {
                        emergency.persist(directory, namespace);
                        if (closeFailure == null && store != null) MultiblockEmergencySnapshot.restore(directory, namespace, store);
                    } else if (closeFailure == null && store != null && !captured.isEmpty()) {
                        Collection<ManagedCacheStore.Update> updates = new ArrayList<>();
                        for (Map.Entry<UUID, byte[]> entry : captured.entrySet()) {
                            ManagedCacheStore.Entry current = store.get(entry.getKey());
                            if (current == null) throw new IOException("Final snapshot has no current authority: " + entry.getKey());
                            updates.add(new ManagedCacheStore.Update(entry.getKey(), current.revision, entry.getValue()));
                        }
                        store.save(UUID.randomUUID(), updates);
                    }
                } catch (IOException | RuntimeException error) {
                    if (closeFailure == null) closeFailure = asIo(error);
                    else closeFailure.addSuppressed(error);
                } finally {
                    try {
                        if (store != null) store.close();
                    } catch (IOException error) {
                        if (closeFailure == null) closeFailure = error;
                        else closeFailure.addSuppressed(error);
                    }
                    queuedBytes.addAndGet(-reserved);
                    failure = closeFailure;
                    if (closeFailure != null) org.apache.logging.log4j.LogManager.getLogger("MekanismMultiblockStorage")
                          .error("Multiblock final save/close failed for {}. Inspect the emergency journal before removing any files.", directory, closeFailure);
                    store = null;
                    state = State.CLOSED;
                    released(this);
                    closedTicket.complete(null, closeFailure);
                }
            };
            try {
                executor.execute(closeBarrier);
            } catch (RejectedExecutionException rejected) {
                queuedBytes.addAndGet(-reserved);
                state = previousState;
                loaded = previousView;
                if (executor.isShutdown()) throw new IOException("Multiblock IO service is closing", rejected);
                throw new MultiblockBackpressureException("Multiblock IO queue rejected close barrier", rejected);
            }
        }
    }

    private static IOException asIo(Exception error) {
        return error instanceof IOException ? (IOException) error : new IOException("Multiblock IO failed", error);
    }

    interface Factory { ManagedCacheStore open(Path directory, String namespace) throws IOException; }
    private interface Operation { void run() throws IOException; }
}
