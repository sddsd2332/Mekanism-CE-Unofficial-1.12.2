package mekanism.common.multiblock.persistence;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/** Uncompressed, bounded NBT snapshots. No registry lookups and no optional-module dependencies. */
public final class ManagedCacheNbt {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 100_000;

    private ManagedCacheNbt() {}

    /** Must capture live machine state on its owning thread before calling this method. */
    public static byte[] encode(NBTTagCompound root) throws IOException {
        inspectTree(root, 0, new Budget());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new OutputStream() {
            @Override public void write(int value) throws IOException {
                require(bytes.size() < ManagedCacheStore.MAX_CACHE_BYTES, "encoded size");
                bytes.write(value);
            }

            @Override public void write(byte[] value, int offset, int length) throws IOException {
                require(length <= ManagedCacheStore.MAX_CACHE_BYTES - bytes.size(), "encoded size");
                bytes.write(value, offset, length);
            }
        })) {
            CompressedStreamTools.write(root, output);
        } catch (RuntimeException error) {
            throw new IOException("Unable to serialize managed cache NBT", error);
        }
        byte[] result = bytes.toByteArray();
        validate(result);
        return result;
    }

    public static NBTTagCompound decode(byte[] snapshot) throws IOException {
        validate(snapshot);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(snapshot))) {
            // The independent scanner has already bounded array/list allocation and depth. Do
            // not depend on vanilla's tracker, which some server coremods patch into a warning.
            return CompressedStreamTools.read(input, new NBTSizeTracker(64L * 1024 * 1024));
        } catch (RuntimeException error) {
            throw new IOException("Unable to deserialize managed cache NBT", error);
        }
    }

    public static void validate(byte[] snapshot) throws IOException {
        require(snapshot.length <= ManagedCacheStore.MAX_CACHE_BYTES, "snapshot size");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(snapshot))) {
            require(input.readUnsignedByte() == 10, "root compound");
            input.readUTF();
            scan(input, 10, 0, new Budget());
            require(input.read() == -1, "trailing bytes");
        }
    }

    private static void inspectTree(NBTBase tag, int depth, Budget budget) throws IOException {
        require(tag != null && tag.getId() >= 1 && tag.getId() <= 12, "tag type");
        budget.node(depth);
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            for (String key : compound.getKeySet()) inspectTree(compound.getTag(key), depth + 1, budget);
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            require(list.tagCount() <= MAX_NODES - budget.nodes, "list length");
            int type = list.tagCount() == 0 ? 0 : list.get(0).getId();
            for (NBTBase child : list) {
                require(child != null && child.getId() == type, "mixed list types");
                inspectTree(child, depth + 1, budget);
            }
        }
    }

    private static void scan(DataInputStream input, int type, int depth, Budget budget) throws IOException {
        budget.node(depth);
        switch (type) {
            case 1: skip(input, 1); return;
            case 2: skip(input, 2); return;
            case 3: case 5: skip(input, 4); return;
            case 4: case 6: skip(input, 8); return;
            case 7: case 11: case 12: {
                int count = input.readInt();
                require(count >= 0, "negative array length");
                long size = (long) count * (type == 7 ? 1 : type == 11 ? 4 : 8);
                require(size <= input.available(), "array length exceeds snapshot");
                skip(input, (int) size);
                return;
            }
            case 8: input.readUTF(); return;
            case 9: {
                int childType = input.readUnsignedByte();
                int count = input.readInt();
                require(count >= 0 && count <= MAX_NODES - budget.nodes && count <= input.available(), "list length");
                require(childType <= 12 && (childType != 0 || count == 0), "list type");
                for (int i = 0; i < count; i++) scan(input, childType, depth + 1, budget);
                return;
            }
            case 10: {
                Set<String> keys = new HashSet<>();
                while (true) {
                    int childType = input.readUnsignedByte();
                    if (childType == 0) return;
                    require(keys.add(input.readUTF()), "duplicate compound key");
                    scan(input, childType, depth + 1, budget);
                }
            }
            default: throw new IOException("Invalid managed cache NBT tag type: " + type);
        }
    }

    private static void skip(DataInputStream input, int count) throws IOException {
        require(count <= input.available() && input.skipBytes(count) == count, "truncated payload");
    }

    private static void require(boolean valid, String reason) throws IOException {
        if (!valid) throw new IOException("Invalid managed cache NBT: " + reason);
    }

    private static final class Budget {
        int nodes;
        void node(int depth) throws IOException {
            require(depth <= MAX_DEPTH && ++nodes <= MAX_NODES, "depth or node budget");
        }
    }
}
