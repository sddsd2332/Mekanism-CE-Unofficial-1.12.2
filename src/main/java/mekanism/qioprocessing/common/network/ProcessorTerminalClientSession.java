package mekanism.qioprocessing.common.network;

import net.minecraft.nbt.NBTTagCompound;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client-thread correlation model for the future Processor terminal screens.
 */
public final class ProcessorTerminalClientSession implements AutoCloseable {
    private static long sequence;
    private static ProcessorTerminalClientSession active;
    private final int window;
    private final Thread owner = Thread.currentThread();
    private final Consumer<ProcessorTerminalPacket> sender;
    private final Consumer<ProcessorTerminalPacket> receiver;
    private final Set<Long> pending = new HashSet<>();
    private UUID token;
    private long opening = -1;
    private boolean closed;

    public ProcessorTerminalClientSession(int window, Consumer<ProcessorTerminalPacket> sender,
                                          Consumer<ProcessorTerminalPacket> receiver) {
        this.window = window;
        this.sender = sender;
        this.receiver = receiver;
    }

    public static ProcessorTerminalClientSession bind(int window,
                                                      Consumer<ProcessorTerminalPacket> receiver) {
        if (active != null) active.close();
        active = new ProcessorTerminalClientSession(window,
                packet -> QIOProcessingPacketHandler.INSTANCE.sendToServer(packet), receiver);
        return active;
    }

    public long send(NBTTagCompound payload) {
        requireOwner();
        boolean bootstrap = "OPEN".equals(payload.getString("action"))
                || "BIND".equals(payload.getString("action"));
        if (closed || pending.size() >= 128 || bootstrap && (token != null || opening >= 0)
                || !bootstrap && token == null) throw new IllegalStateException("Terminal session is not ready");
        long request = ++sequence;
        pending.add(request);
        if (bootstrap) opening = request;
        sender.accept(new ProcessorTerminalPacket(window, request, bootstrap ? null : token, payload));
        return request;
    }

    public boolean receive(ProcessorTerminalPacket reply, int currentWindow) {
        requireOwner();
        if (!reply.valid || closed || currentWindow != window || reply.window != window
                || !pending.contains(reply.request)) return false;
        if (reply.request == opening) {
            if ("OPENED".equals(reply.data().getString("status")) && reply.token != null) token = reply.token;
            else if (reply.token != null) return false;
            opening = -1;
        } else if (token == null || !token.equals(reply.token)) return false;
        pending.remove(reply.request);
        receiver.accept(reply);
        return true;
    }

    static void receiveActive(ProcessorTerminalPacket reply, int currentWindow) {
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

    @Override
    public void close() {
        requireOwner();
        if (closed) return;
        closed = true;
        pending.clear();
        if (token != null) {
            NBTTagCompound close = new NBTTagCompound();
            close.setString("channel", "qio_processor_terminal");
            close.setString("action", "CLOSE");
            sender.accept(new ProcessorTerminalPacket(window, ++sequence, token, close));
        }
        if (active == this) active = null;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Terminal client session requires its owner thread");
    }
}
