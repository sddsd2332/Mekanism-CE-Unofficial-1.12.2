package mekanism.qioprocessing.common.network;

import net.minecraft.nbt.NBTTagCompound;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client view binding. Correlation IDs prevent delayed replies from reaching a reopened window.
 */
public final class WorkbenchClientSession implements AutoCloseable {
    private static long sequence;
    private static WorkbenchClientSession active;
    private final int window;
    private final Thread owner = Thread.currentThread();
    private final Consumer<WorkbenchPacket.Request> sender;
    private final Consumer<WorkbenchPacket.Reply> receiver;
    private final Set<Long> pending = new HashSet<>();
    private UUID token;
    private long opening = -1;
    private boolean closed;

    public WorkbenchClientSession(int window, Consumer<WorkbenchPacket.Request> sender, Consumer<WorkbenchPacket.Reply> receiver) {
        this.window = window;
        this.sender = sender;
        this.receiver = receiver;
    }

    public static WorkbenchClientSession bind(int window, Consumer<WorkbenchPacket.Reply> receiver) {
        if (active != null) active.close();
        active = new WorkbenchClientSession(window, WorkbenchNetwork::request, receiver);
        return active;
    }

    public long send(NBTTagCompound payload) {
        requireOwner();
        boolean open = "OPEN".equals(payload.getString("action"));
        if (closed || pending.size() >= 128 || open && (token != null || opening >= 0) || !open && token == null) {
            throw new IllegalStateException("Workbench client session is not ready");
        }
        long request = Math.incrementExact(sequence);
        sequence = request;
        pending.add(request);
        if (open) opening = request;
        sender.accept(new WorkbenchPacket.Request(window, request, open ? null : token, payload));
        return request;
    }

    public boolean receive(WorkbenchPacket.Reply reply, int currentWindow) {
        requireOwner();
        if (!reply.valid || closed || currentWindow != window || reply.window != window || !pending.contains(reply.request))
            return false;
        String status = reply.data().getString("status");
        if (reply.request == opening) {
            if ("OPENED".equals(status) && reply.token != null) token = reply.token;
            else if (reply.token != null) return false;
            opening = -1;
        } else if (token == null || !token.equals(reply.token)) return false;
        pending.remove(reply.request);
        receiver.accept(reply);
        return true;
    }

    static void receiveActive(WorkbenchPacket.Reply reply, int currentWindow) {
        if (active != null) active.receive(reply, currentWindow);
    }

    public static void disconnected() {
        if (active != null) {
            active.closed = true;
            active.pending.clear();
            active = null;
        }
    }

    public UUID token() {
        return token;
    }

    public boolean isClosed() {
        return closed;
    }

    public void forget(long request) {
        requireOwner();
        pending.remove(request);
        if (opening == request) opening = -1;
    }

    @Override
    public void close() {
        requireOwner();
        if (closed) return;
        closed = true;
        pending.clear();
        if (token != null) {
            NBTTagCompound close = new NBTTagCompound();
            close.setString("action", "CLOSE");
            long request = Math.incrementExact(sequence);
            sequence = request;
            sender.accept(new WorkbenchPacket.Request(window, request, token, close));
        }
        if (active == this) active = null;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Workbench client state requires the GUI thread");
    }
}

