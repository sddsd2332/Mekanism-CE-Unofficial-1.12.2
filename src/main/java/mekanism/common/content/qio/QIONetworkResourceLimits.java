package mekanism.common.content.qio;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Hard limits for codec-owned data sent to a QIO viewer. */
public final class QIONetworkResourceLimits {

    public static final int MAX_DESCRIPTOR_BYTES = 48 * 1024;
    public static final int MAX_ENTRY_BYTES = 64 * 1024;
    public static final int MAX_PACKET_BYTES = 1024 * 1024;
    public static final int MAX_NBT_DEPTH = 32;
    public static final int MAX_NBT_NODES = 2_048;
    public static final int MAX_NBT_LIST_ENTRIES = 1_024;

    private QIONetworkResourceLimits() {
    }

    public static boolean isSafeDescriptorPayload(@Nullable NBTTagCompound payload) {
        if (payload == null) {
            return false;
        }
        try {
            NbtBudget budget = new NbtBudget();
            validate(payload, 0, budget);
            try (DataOutputStream output = new DataOutputStream(new LimitedCountingOutputStream(
                  MAX_DESCRIPTOR_BYTES))) {
                CompressedStreamTools.write(payload, output);
            }
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static void validate(NBTBase tag, int depth, NbtBudget budget) {
        if (depth > MAX_NBT_DEPTH || ++budget.nodes > MAX_NBT_NODES) {
            throw new IllegalArgumentException("QIO network descriptor NBT is too complex");
        }
        if (tag instanceof NBTTagCompound compound) {
            for (String key : compound.getKeySet()) {
                if (key == null || key.length() > 256) {
                    throw new IllegalArgumentException("QIO network descriptor NBT key is too long");
                }
                validate(compound.getTag(key), depth + 1, budget);
            }
        } else if (tag instanceof NBTTagList list) {
            budget.listEntries += list.tagCount();
            if (budget.listEntries > MAX_NBT_LIST_ENTRIES) {
                throw new IllegalArgumentException("QIO network descriptor NBT list is too large");
            }
            for (NBTBase child : list) {
                validate(child, depth + 1, budget);
            }
        }
    }

    private static final class NbtBudget {
        private int nodes;
        private int listEntries;
    }

    private static final class LimitedCountingOutputStream extends OutputStream {

        private final long limit;
        private long count;

        private LimitedCountingOutputStream(long limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            if (values == null || offset < 0 || length < 0 || offset + length > values.length) {
                throw new IndexOutOfBoundsException();
            }
            add(length);
        }

        private void add(long amount) throws IOException {
            if (amount > limit - count) {
                throw new IOException("QIO network descriptor exceeds " + limit + " bytes");
            }
            count += amount;
        }
    }
}
