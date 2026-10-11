package mekanism.qioprocessing.common.processor;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.common.terminal.QIOProcessingSmartTerminalAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;
import java.util.function.Supplier;

/**
 * Server-owned session contract for a future Processor monitor/maintenance terminal.
 * It is transport-neutral: a packet handler supplies the authenticated player UUID and window.
 */
public final class QIOCraftingProcessorTerminalSession implements AutoCloseable {
    public static final int MAX_PAGE = 32;
    public static final int MAX_REQUESTS = 128;
    public static final long EXPIRY_TICKS = 600;

    public enum Status {
        OK, ACCESS_DENIED, INVALID_SESSION, INVALID_WINDOW, DUPLICATE_REQUEST,
        INVALID_CURSOR, INVALID_EDIT, NOT_FOUND, CONFLICT, REQUIRES_RECONCILIATION
    }

    public static final class Response {
        private final Status status;
        private final long nextCursor;
        private final List<QIOCraftingProcessorSnapshot> snapshots;
        private final UUID orderId;
        private final String detail;
        private final NBTTagCompound payload;

        private Response(Status status, long nextCursor, List<QIOCraftingProcessorSnapshot> snapshots) {
            this(status, nextCursor, snapshots, null, "");
        }

        private Response(Status status, long nextCursor, List<QIOCraftingProcessorSnapshot> snapshots,
                         UUID orderId, String detail) {
            this(status, nextCursor, snapshots, orderId, detail, new NBTTagCompound());
        }

        private Response(Status status, long nextCursor, List<QIOCraftingProcessorSnapshot> snapshots,
                         UUID orderId, String detail, NBTTagCompound payload) {
            this.status = status;
            this.nextCursor = nextCursor;
            this.snapshots = Collections.unmodifiableList(new ArrayList<>(snapshots));
            this.orderId = orderId;
            this.detail = detail == null ? "" : detail;
            this.payload = payload == null ? new NBTTagCompound() : payload.copy();
        }

        public Status status() {
            return status;
        }

        public long nextCursor() {
            return nextCursor;
        }

        public List<QIOCraftingProcessorSnapshot> snapshots() {
            return snapshots;
        }

        public UUID orderId() {
            return orderId;
        }

        public String detail() {
            return detail;
        }

        public NBTTagCompound payload() {
            return payload.copy();
        }
    }

    private final UUID sessionId = UUID.randomUUID();
    private final UUID playerId;
    private final int window;
    private final QIOFrequencyReference reference;
    private final Map<UUID, Response> requests = new LinkedHashMap<>();
    private long lastActivity;
    private boolean closed;

    private QIOCraftingProcessorTerminalSession(UUID playerId, int window,
                                                QIOFrequencyReference reference, long currentTick) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        if (window < 0) throw new IllegalArgumentException("Invalid terminal window");
        this.window = window;
        this.reference = Objects.requireNonNull(reference, "reference");
        if (!playerId.equals(reference.getBindingPlayerUUID())) {
            throw new IllegalArgumentException("Terminal player does not match frequency reference");
        }
        if (currentTick < 0) throw new IllegalArgumentException("Invalid terminal tick");
        lastActivity = currentTick;
    }

    public static QIOCraftingProcessorTerminalSession open(UUID playerId, int window,
                                                           QIOFrequencyReference reference, long currentTick, boolean authorized) {
        if (!authorized) throw new SecurityException("Processor terminal authorization denied");
        return new QIOCraftingProcessorTerminalSession(playerId, window, reference, currentTick);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public int window() {
        return window;
    }

    public QIOFrequencyReference reference() {
        return reference;
    }

    public boolean matchesReference(QIOFrequencyReference current) {
        return reference.equals(current);
    }

    public Response list(UUID requestId, UUID requester, int requestWindow, long cursor,
                         int limit, long currentTick) {
        Response invalid = validate(requestId, requester, requestWindow, currentTick);
        if (invalid != null) return invalid;
        if (cursor < 0 || limit <= 0) return remember(requestId,
                new Response(Status.INVALID_CURSOR, 0, Collections.emptyList()));
        int pageSize = Math.min(MAX_PAGE, limit);
        List<QIOCraftingProcessorSnapshot> all = QIOCraftingProcessorMonitorService.list(reference);
        if (cursor > all.size()) return remember(requestId,
                new Response(Status.INVALID_CURSOR, 0, Collections.emptyList()));
        int start = (int) cursor;
        int end = Math.min(all.size(), start + pageSize);
        long next = end < all.size() ? end : -1;
        return remember(requestId, new Response(Status.OK, next, all.subList(start, end)));
    }

    public Response cancel(UUID requestId, UUID requester, int requestWindow, UUID processorId,
                           long endpointRevision, UUID operationId, String reason, long currentTick) {
        Response invalid = validate(requestId, requester, requestWindow, currentTick);
        if (invalid != null) return invalid;
        QIOCraftingProcessorControlService.Result result = QIOCraftingProcessorMonitorService.cancel(
                reference, processorId, endpointRevision, operationId, reason);
        return remember(requestId, mutation(result));
    }

    public Response reconcile(UUID requestId, UUID requester, int requestWindow, UUID processorId,
                              long endpointRevision, UUID operationId, String reason, long currentTick) {
        Response invalid = validate(requestId, requester, requestWindow, currentTick);
        if (invalid != null) return invalid;
        QIOCraftingProcessorControlService.Result result = QIOCraftingProcessorMonitorService.reconcile(
                reference, processorId, endpointRevision, operationId, reason);
        return remember(requestId, mutation(result));
    }

    public Response setSmartSlot(UUID requestId, UUID requester, int requestWindow,
                                 QIOProcessingSmartTerminalAccess terminal, long expectedConfigurationRevision,
                                 int craftingWindow, int slot, ItemStack value, long currentTick) {
        Response invalid = validate(requestId, requester, requestWindow, currentTick);
        if (invalid != null) return invalid;
        if (terminal == null || !reference.equals(terminal.endpointReference())
                || terminal.configurationRevision() != expectedConfigurationRevision) {
            return remember(requestId, new Response(Status.CONFLICT, -1, Collections.emptyList()));
        }
        try {
            terminal.setCraftingSlot(craftingWindow, slot, value);
            return remember(requestId, new Response(Status.OK, -1, Collections.emptyList()));
        } catch (RuntimeException error) {
            return remember(requestId, new Response(Status.INVALID_EDIT, -1, Collections.emptyList()));
        }
    }

    public Response runExternal(UUID requestId, UUID requester, int requestWindow, long currentTick,
                                Supplier<Status> action) {
        Response invalid = validate(requestId, requester, requestWindow, currentTick);
        if (invalid != null) return invalid;
        try {
            return remember(requestId, new Response(action.get(), -1, Collections.emptyList()));
        } catch (RuntimeException error) {
            return remember(requestId, new Response(Status.REQUIRES_RECONCILIATION, -1,
                    Collections.emptyList()));
        }
    }

    public Response runExternalResponse(UUID requestId, UUID requester, int requestWindow,
                                        long currentTick, Supplier<Response> action) {
        Response invalid = validate(requestId, requester, requestWindow, currentTick);
        if (invalid != null) return invalid;
        try {
            return remember(requestId, action.get());
        } catch (RuntimeException error) {
            return remember(requestId, new Response(Status.REQUIRES_RECONCILIATION, -1,
                    Collections.emptyList()));
        }
    }

    public static Response external(Status status, UUID orderId, String detail) {
        return new Response(status, -1, Collections.emptyList(), orderId, detail);
    }

    public static Response external(Status status, UUID orderId, String detail, NBTTagCompound payload) {
        return new Response(status, -1, Collections.emptyList(), orderId, detail, payload);
    }

    public boolean expired(long currentTick) {
        return closed || currentTick < lastActivity || currentTick - lastActivity > EXPIRY_TICKS;
    }

    @Override
    public void close() {
        closed = true;
        requests.clear();
    }

    private Response validate(UUID requestId, UUID requester, int requestWindow, long currentTick) {
        if (requestId == null || requester == null || !playerId.equals(requester)) {
            return new Response(Status.ACCESS_DENIED, 0, Collections.emptyList());
        }
        if (requestWindow != window) return new Response(Status.INVALID_WINDOW, 0, Collections.emptyList());
        if (expired(currentTick)) return new Response(Status.INVALID_SESSION, 0, Collections.emptyList());
        if (requests.containsKey(requestId)) return new Response(Status.DUPLICATE_REQUEST, 0, Collections.emptyList());
        lastActivity = currentTick;
        return null;
    }

    private Response remember(UUID requestId, Response response) {
        if (requests.size() >= MAX_REQUESTS) {
            UUID first = requests.keySet().iterator().next();
            requests.remove(first);
        }
        requests.put(requestId, response);
        return response;
    }

    private static Response mutation(QIOCraftingProcessorControlService.Result result) {
        switch (result) {
            case APPLIED:
                return new Response(Status.OK, -1, Collections.emptyList());
            case CONFLICT:
                return new Response(Status.CONFLICT, -1, Collections.emptyList());
            case REQUIRES_RECONCILIATION:
                return new Response(Status.REQUIRES_RECONCILIATION, -1, Collections.emptyList());
            default:
                return new Response(Status.NOT_FOUND, -1, Collections.emptyList());
        }
    }
}
