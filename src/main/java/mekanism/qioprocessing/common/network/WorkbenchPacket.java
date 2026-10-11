package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.io.*;
import java.util.UUID;

/**
 * Bounded uncompressed NBT frames. Transport bounds are independent of directory/import size.
 */
public abstract class WorkbenchPacket implements IMessage {
    public static final int MAX_FRAME = 512 * 1024;
    public static final int VERSION = 2;
    public int window;
    public long request;
    public UUID token;
    private NBTTagCompound data = new NBTTagCompound();
    public boolean valid;

    public WorkbenchPacket() {
    }

    protected WorkbenchPacket(int window, long request, UUID token, NBTTagCompound data) {
        this.window = window;
        this.request = request;
        this.token = token;
        this.data = data.copy();
        valid = true;
    }

    public NBTTagCompound data() {
        return data.copy();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = false;
        try {
            if (buf.readableBytes() > MAX_FRAME + 40 || buf.readInt() != VERSION) return;
            window = buf.readInt();
            request = buf.readLong();
            token = buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
            int length = buf.readInt();
            if (window < 0 || request < 0 || length < 0 || length > MAX_FRAME || length != buf.readableBytes()) return;
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                data = CompressedStreamTools.read(input, new NBTSizeTracker(4L * MAX_FRAME));
                if (input.read() != -1) return;
            }
            valid = true;
        } catch (IOException | RuntimeException ignored) {
            data = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                CompressedStreamTools.write(data, output);
            }
            if (bytes.size() > MAX_FRAME)
                throw new IllegalArgumentException("Workbench page exceeds transport frame size");
            buf.writeInt(VERSION);
            buf.writeInt(window);
            buf.writeLong(request);
            buf.writeBoolean(token != null);
            if (token != null) {
                buf.writeLong(token.getMostSignificantBits());
                buf.writeLong(token.getLeastSignificantBits());
            }
            buf.writeInt(bytes.size());
            buf.writeBytes(bytes.toByteArray());
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot encode workbench packet", error);
        }
    }

    public static final class Request extends WorkbenchPacket {
        public Request() {
        }

        public Request(int window, long request, UUID token, NBTTagCompound data) {
            super(window, request, token, data);
        }
    }

    public static final class Reply extends WorkbenchPacket {
        public Reply() {
        }

        public Reply(int window, long request, UUID token, NBTTagCompound data) {
            super(window, request, token, data);
        }
    }
}
