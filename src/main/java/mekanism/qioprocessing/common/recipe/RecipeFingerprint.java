package mekanism.qioprocessing.common.recipe;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Length-prefixed, sorted NBT hashing; independent of compound insertion order and numeric item IDs.
 */
final class RecipeFingerprint {
    private final MessageDigest digest;

    RecipeFingerprint() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    RecipeFingerprint add(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update(new byte[]{(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length});
        digest.update(bytes);
        return this;
    }

    RecipeFingerprint add(NBTBase value) {
        add(Byte.toString(value.getId()));
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) value;
            List<String> keys = new ArrayList<>(compound.getKeySet());
            Collections.sort(keys);
            add(Integer.toString(keys.size()));
            for (String key : keys) {
                add(key);
                add(compound.getTag(key));
            }
        } else if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;
            add(Integer.toString(list.getTagType()));
            add(Integer.toString(list.tagCount()));
            for (int i = 0; i < list.tagCount(); i++) add(list.get(i));
        } else {
            add(value.toString());
        }
        return this;
    }

    String finish() {
        char[] hex = "0123456789abcdef".toCharArray();
        byte[] bytes = digest.digest();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = hex[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = hex[bytes[i] & 15];
        }
        return new String(result);
    }
}

