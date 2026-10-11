package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDiscovery;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Server-owned sessions. Workers see only immutable recipe/configuration snapshots.
 */
public final class WorkbenchSessions implements AutoCloseable {
    public interface Access {
        UUID player();

        UUID frequency();

        default int dimension() {
            return 0;
        }

        boolean valid();

        default mekanism.api.qio.external.QIOFrequencyReference frequencyReference() {
            return null;
        }
    }

    public interface Persistence {
        WorkbenchSnapshot load(UUID frequency) throws IOException;

        void save(WorkbenchSnapshot snapshot) throws IOException;
    }

    public enum Failure {ACCESS_DENIED, BUSY, NO_PREVIEW, CLOSED}

    public static final class Rejected extends IllegalStateException {
        public final Failure reason;

        Rejected(Failure reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    public static final class Opened {
        public final UUID token;
        public final WorkbenchSnapshot snapshot;

        private Opened(UUID token, WorkbenchSnapshot snapshot) {
            this.token = token;
            this.snapshot = snapshot;
        }
    }

    public static final class Prepared {
        public final UUID token;
        public final WorkbenchImport.Preview preview;
        public final Throwable error;
        public final boolean ready;

        private Prepared(UUID token, WorkbenchImport.Preview preview, Throwable error) {
            this.token = token;
            this.preview = preview;
            this.error = error;
            ready = preview != null || error != null;
        }
    }

    private final Thread owner = Thread.currentThread();
    private final Persistence persistence;
    private final ExecutorService worker;
    private final Map<UUID, WorkbenchRecipeBook> books = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private volatile boolean closed;

    public WorkbenchSessions(Persistence persistence, ExecutorService worker) {
        this.persistence = persistence;
        this.worker = worker;
    }

    public Opened open(Access access) throws IOException {
        requireOwner();
        if (!access.valid()) throw new Rejected(Failure.ACCESS_DENIED);
        WorkbenchRecipeBook book = books.get(access.frequency());
        if (book == null) {
            WorkbenchSnapshot loaded = persistence.load(access.frequency());
            if (!access.frequency().equals(loaded.id())) throw new IOException("Workbench identity mismatch");
            book = new WorkbenchRecipeBook(loaded);
            books.put(access.frequency(), book);
        }
        end(access.player());
        books.put(access.frequency(), book);
        Session session = new Session(access);
        sessions.put(access.player(), session);
        return new Opened(session.token, book.snapshot());
    }

    public WorkbenchSnapshot snapshot(UUID player, UUID token) {
        return books.get(require(player, token).access.frequency()).snapshot();
    }

    public Access access(UUID player, UUID token) {
        return require(player, token).access;
    }

    /**
     * Fork, mutate, persist, then publish. IO failure never exposes a successful in-memory revision.
     */
    public WorkbenchSnapshot edit(UUID player, UUID token, Consumer<WorkbenchRecipeBook> mutation) throws IOException {
        Session session = require(player, token);
        WorkbenchRecipeBook current = books.get(session.access.frequency());
        WorkbenchRecipeBook candidate = new WorkbenchRecipeBook(current.snapshot());
        mutation.accept(candidate);
        if (candidate.snapshot() != current.snapshot()) {
            persistence.save(candidate.snapshot());
            books.put(session.access.frequency(), candidate);
        }
        return candidate.snapshot();
    }

    public UUID prepare(UUID player, UUID token, RecipeCatalog catalog, Iterable<RecipeItemKey> targets,
                        RecipeDiscovery.Mode mode) {
        Session session = require(player, token);
        if (session.pending != null && !session.pending.isDone()) throw new Rejected(Failure.BUSY);
        WorkbenchSnapshot base = books.get(session.access.frequency()).snapshot();
        List<RecipeItemKey> roots = new ArrayList<>();
        targets.forEach(roots::add);
        UUID job = UUID.randomUUID();
        session.preview = null;
        session.previewToken = null;
        session.completed = null;
        session.job = job;
        try {
            session.pending = worker.submit(() -> WorkbenchImport.prepare(base, catalog, roots, mode));
            return job;
        } catch (RejectedExecutionException error) {
            session.job = null;
            throw new Rejected(Failure.BUSY);
        }
    }

    /**
     * Non-blocking result lookup, invoked by a client request. No worker-to-tick completion queue.
     */
    public Prepared poll(UUID player, UUID token, UUID job) {
        Session session = require(player, token);
        if (job == null || !job.equals(session.job)) throw new Rejected(Failure.NO_PREVIEW);
        if (session.completed != null) return session.completed;
        if (session.pending == null) throw new Rejected(Failure.NO_PREVIEW);
        if (!session.pending.isDone()) return new Prepared(job, null, null);
        try {
            session.preview = session.pending.get();
            session.previewToken = job;
            session.completed = new Prepared(job, session.preview, null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new Rejected(Failure.BUSY);
        } catch (ExecutionException | java.util.concurrent.CancellationException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            session.completed = new Prepared(job, null, cause);
        }
        session.pending = null;
        return session.completed;
    }

    public WorkbenchSnapshot commit(UUID player, UUID token, UUID previewToken, RecipeCatalog catalog) throws IOException {
        Session session = require(player, token);
        if (session.preview == null && !poll(player, token, previewToken).ready) throw new Rejected(Failure.BUSY);
        if (session.preview == null || !session.previewToken.equals(previewToken))
            throw new Rejected(Failure.NO_PREVIEW);
        WorkbenchSnapshot result = edit(player, token, book -> book.commit(catalog, session.preview));
        session.preview = null;
        session.previewToken = null;
        session.completed = null;
        session.job = null;
        return result;
    }

    public void tick() {
        requireOwner();
        for (UUID player : new ArrayList<>(sessions.keySet())) if (!sessions.get(player).access.valid()) end(player);
    }

    public void cancelPreview(UUID player, UUID token) {
        Session session = require(player, token);
        if (session.pending != null) session.pending.cancel(true);
        session.pending = null;
        session.job = null;
        session.preview = null;
        session.previewToken = null;
        session.completed = null;
    }

    public void end(UUID player, UUID token) {
        require(player, token);
        end(player);
    }

    public void end(UUID player) {
        requireOwner();
        Session session = sessions.remove(player);
        if (session != null && session.pending != null) session.pending.cancel(true);
        if (session != null) {
            boolean stillUsed = false;
            for (Session remaining : sessions.values()) {
                if (remaining.access.frequency().equals(session.access.frequency())) {
                    stillUsed = true;
                    break;
                }
            }
            if (!stillUsed) books.remove(session.access.frequency());
        }
    }

    private Session require(UUID player, UUID token) {
        requireOwner();
        Session session = sessions.get(player);
        if (session == null || !session.token.equals(token)) throw new Rejected(Failure.ACCESS_DENIED);
        if (!session.access.valid()) {
            end(player);
            throw new Rejected(Failure.ACCESS_DENIED);
        }
        return session;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Workbench sessions require the server thread");
        if (closed) throw new Rejected(Failure.CLOSED);
    }

    @Override
    public void close() {
        if (closed) return;
        requireOwner();
        for (UUID player : new ArrayList<>(sessions.keySet())) end(player);
        closed = true;
        worker.shutdownNow();
        books.clear();
    }

    private static final class Session {
        final Access access;
        final UUID token = UUID.randomUUID();
        Future<WorkbenchImport.Preview> pending;
        UUID job, previewToken;
        WorkbenchImport.Preview preview;
        Prepared completed;

        Session(Access access) {
            this.access = access;
        }
    }
}
