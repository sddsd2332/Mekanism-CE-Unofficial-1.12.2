package mekanism.common.multiblock.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded IO service. Owners retain one handle per world/manager load generation and close it on
 * unload. Workers capture only a resolved Path, never a World, MapStorage or TileEntity. Observing
 * LOADING/FAILED is not permission to restore a legacy cache. No caller waits on a Future.
 */
public final class LegacyIndexLoader implements AutoCloseable {
    private static final long MAX_INDEX_BYTES = 128L * 1024 * 1024;
    private final ThreadPoolExecutor executor;
    private final long memoryLimit;
    private final AtomicLong reservedBytes = new AtomicLong();
    private final Set<Handle> handles = Collections.synchronizedSet(new HashSet<>());
    private final Reader reader;
    private boolean closed;

    public LegacyIndexLoader() {
        this(2, 64, 512L * 1024 * 1024, LegacyMultiblockIndex::read);
    }

    LegacyIndexLoader(int workers, int queueCapacity, long memoryLimit, Reader reader) {
        this.memoryLimit = memoryLimit;
        this.reader = reader;
        AtomicLong threadId = new AtomicLong();
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
              new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                  Thread thread = new Thread(runnable, "Mekanism-legacy-multiblock-" + threadId.incrementAndGet());
                  thread.setDaemon(true);
                  return thread;
              }, new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized Handle open(Path resolvedFile) {
        if (closed) throw new IllegalStateException("Legacy index loader is closed");
        Handle handle = new Handle(resolvedFile.toAbsolutePath().normalize());
        handles.add(handle);
        try {
            handle.future = executor.submit(() -> load(handle));
        } catch (RejectedExecutionException full) {
            handle.fail(new IOException("Legacy index task budget exceeded; explicit retry required", full));
        }
        return handle;
    }

    private void load(Handle handle) {
        long reservation = 0;
        try {
            synchronized (handle) {
                if (handle.state == State.CLOSED) return;
                handle.queueNanos = Math.max(0, System.nanoTime() - handle.submittedNanos);
            }
            while (true) {
                long used = reservedBytes.get();
                if (used > memoryLimit - MAX_INDEX_BYTES) {
                    throw new IOException("Legacy index memory budget exceeded; unload unused worlds or raise the budget before retrying");
                }
                if (reservedBytes.compareAndSet(used, used + MAX_INDEX_BYTES)) break;
            }
            reservation = MAX_INDEX_BYTES;
            LegacyMultiblockIndex index;
            long readStarted = System.nanoTime();
            try {
                index = reader.read(handle.file);
            } finally {
                synchronized (handle) {
                    handle.readNanos = Math.max(0, System.nanoTime() - readStarted);
                }
            }
            synchronized (handle) {
                if (handle.state != State.CLOSED) {
                    reservedBytes.addAndGet(index.memoryBytes() - reservation);
                    handle.reservation = index.memoryBytes();
                    reservation = 0; // The handle now owns the reservation until unload.
                    handle.index = index;
                    handle.state = State.READY;
                }
            }
        } catch (IOException | RuntimeException error) {
            handle.fail(error instanceof IOException ? (IOException) error : new IOException("Legacy index load failed", error));
        } finally {
            reservedBytes.addAndGet(-reservation);
        }
    }

    public long reservedBytes() {
        return reservedBytes.get();
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            Handle[] current;
            synchronized (handles) {
                current = handles.toArray(new Handle[0]);
            }
            for (Handle handle : current) handle.close();
            executor.shutdownNow();
        }
    }

    public enum State { LOADING, READY, FAILED, CLOSED }

    public final class Handle implements AutoCloseable {
        public final Path file;
        private State state = State.LOADING;
        private LegacyMultiblockIndex index;
        private IOException failure;
        private Future<?> future;
        private long reservation;
        private final long submittedNanos = System.nanoTime();
        private long queueNanos = -1;
        private long readNanos = -1;

        private Handle(Path file) {
            this.file = file;
        }

        public synchronized State state() {
            return state;
        }

        /** Guarded access prevents LOADING/FAILED from being mistaken for an empty index. */
        public synchronized LegacyMultiblockIndex index() {
            if (state != State.READY) throw new IllegalStateException("Legacy index is " + state, failure);
            return index;
        }

        public synchronized IOException failure() {
            return failure;
        }

        /** -1 means not started (queue) or not completed (reader), including pre-read rejection. */
        public synchronized long queueNanos() { return queueNanos; }
        public synchronized long readNanos() { return readNanos; }

        private synchronized void fail(IOException error) {
            if (state != State.CLOSED) {
                failure = error;
                state = State.FAILED;
            }
        }

        @Override
        public void close() {
            synchronized (this) {
                if (state == State.CLOSED) return;
                state = State.CLOSED;
                index = null;
                reservedBytes.addAndGet(-reservation);
                reservation = 0;
                if (future != null) future.cancel(true);
            }
            handles.remove(this);
            executor.purge();
        }
    }

    interface Reader {
        LegacyMultiblockIndex read(Path file) throws IOException;
    }
}
