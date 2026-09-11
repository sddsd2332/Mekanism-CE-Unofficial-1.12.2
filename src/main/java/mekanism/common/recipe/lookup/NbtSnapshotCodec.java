package mekanism.common.recipe.lookup;

import net.minecraft.nbt.*;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Main-thread conversion boundary. Workers receive FrozenNbt and never invoke this codec. */
public final class NbtSnapshotCodec {
    private NbtSnapshotCodec() { }

    @Nullable
    public static FrozenNbt freeze(@Nullable NBTBase tag) {
        return tag == null ? null : freeze(tag, new IdentityHashMap<>(), 0);
    }

    private static FrozenNbt freeze(NBTBase tag, IdentityHashMap<NBTBase, Boolean> active, int depth) {
        if (depth > 512 || active.put(tag, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic or excessively nested NBT");
        }
        try {
            Class<?> type = tag.getClass();
            if (type == NBTTagEnd.class) return FrozenNbt.integer(FrozenNbt.Type.END, 0);
            if (type == NBTTagByte.class) return FrozenNbt.integer(FrozenNbt.Type.BYTE, ((NBTTagByte) tag).getByte());
            if (type == NBTTagShort.class) return FrozenNbt.integer(FrozenNbt.Type.SHORT, ((NBTTagShort) tag).getShort());
            if (type == NBTTagInt.class) return FrozenNbt.integer(FrozenNbt.Type.INT, ((NBTTagInt) tag).getInt());
            if (type == NBTTagLong.class) return FrozenNbt.integer(FrozenNbt.Type.LONG, ((NBTTagLong) tag).getLong());
            if (type == NBTTagFloat.class) return FrozenNbt.floatValue(((NBTTagFloat) tag).getFloat());
            if (type == NBTTagDouble.class) return FrozenNbt.doubleValue(((NBTTagDouble) tag).getDouble());
            if (type == NBTTagString.class) return FrozenNbt.string(((NBTTagString) tag).getString());
            if (type == NBTTagByteArray.class) return FrozenNbt.byteArray(((NBTTagByteArray) tag).getByteArray());
            if (type == NBTTagIntArray.class) return FrozenNbt.intArray(((NBTTagIntArray) tag).getIntArray());
            if (type == NBTTagLongArray.class) return freezeLongArray((NBTTagLongArray) tag);
            if (type == NBTTagCompound.class) {
                NBTTagCompound compound = (NBTTagCompound) tag;
                Map<String, FrozenNbt> values = new LinkedHashMap<>();
                for (String key : compound.getKeySet()) values.put(key, freeze(compound.getTag(key), active, depth + 1));
                return FrozenNbt.compound(values);
            }
            if (type == NBTTagList.class) {
                NBTTagList list = (NBTTagList) tag;
                List<FrozenNbt> values = new ArrayList<>(list.tagCount());
                for (int i = 0; i < list.tagCount(); i++) values.add(freeze(list.get(i), active, depth + 1));
                return FrozenNbt.list(FrozenNbt.Type.fromId(list.getTagType()), values);
            }
            throw new IllegalArgumentException("Unsupported NBT implementation " + type.getName());
        } finally { active.remove(tag); }
    }

    private static FrozenNbt freezeLongArray(NBTTagLongArray tag) {
        // 1.12 has no public long-array accessor. Use its defined binary format,
        // rather than a private-field reflection dependency or textual encoding.
        try {
            NBTTagCompound wrapper = new NBTTagCompound();
            wrapper.setTag("v", tag);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressedStreamTools.write(wrapper, new DataOutputStream(bytes));
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            if (input.readUnsignedByte() != 10) throw new IOException("Invalid compound encoding");
            input.readUTF();
            if (input.readUnsignedByte() != 12 || !"v".equals(input.readUTF())) throw new IOException("Invalid long-array encoding");
            int length = input.readInt();
            if (length < 0 || length > input.available() / Long.BYTES) throw new IOException("Invalid long-array length");
            long[] values = new long[length];
            for (int i = 0; i < length; i++) values[i] = input.readLong();
            if (input.readUnsignedByte() != 0 || input.available() != 0) throw new IOException("Unexpected long-array payload");
            return FrozenNbt.longArray(values);
        } catch (IOException failure) { throw new IllegalArgumentException("Cannot freeze long-array NBT", failure); }
    }

    @Nullable
    public static NBTBase thaw(@Nullable FrozenNbt tag) {
        if (tag == null) return null;
        switch (tag.getType()) {
            case END: return new NBTTagEnd();
            case BYTE: return new NBTTagByte((byte) tag.getNumberBits());
            case SHORT: return new NBTTagShort((short) tag.getNumberBits());
            case INT: return new NBTTagInt((int) tag.getNumberBits());
            case LONG: return new NBTTagLong(tag.getNumberBits());
            case FLOAT: return new NBTTagFloat(Float.intBitsToFloat((int) tag.getNumberBits()));
            case DOUBLE: return new NBTTagDouble(Double.longBitsToDouble(tag.getNumberBits()));
            case STRING: return new NBTTagString(tag.getText());
            case BYTE_ARRAY: return new NBTTagByteArray(tag.getBytes());
            case INT_ARRAY: return new NBTTagIntArray(tag.getInts());
            case LONG_ARRAY: return new NBTTagLongArray(tag.getLongs());
            case COMPOUND:
                NBTTagCompound compound = new NBTTagCompound();
                tag.getMembers().forEach((key, value) -> compound.setTag(key, thaw(value)));
                return compound;
            case LIST:
                if (tag.getElements().isEmpty() && tag.getElementType() != FrozenNbt.Type.END) return thawTypedEmptyList(tag.getElementType());
                NBTTagList list = new NBTTagList();
                for (FrozenNbt value : tag.getElements()) list.appendTag(thaw(value));
                return list;
            default: throw new IllegalArgumentException("Unsupported NBT type");
        }
    }

    private static NBTBase thawTypedEmptyList(FrozenNbt.Type type) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(10); output.writeUTF("");
            output.writeByte(9); output.writeUTF("v");
            output.writeByte(type.ordinal()); output.writeInt(0); output.writeByte(0);
            return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                  NBTSizeTracker.INFINITE).getTag("v");
        } catch (IOException failure) { throw new IllegalStateException("Cannot restore typed empty NBT list", failure); }
    }
}
