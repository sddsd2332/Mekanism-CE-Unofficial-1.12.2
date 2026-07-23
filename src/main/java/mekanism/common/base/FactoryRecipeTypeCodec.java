package mekanism.common.base;

import mekanism.common.base.IFactory.RecipeType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stable v10 persistence for factory recipe types. Legacy v10 data only contains the ordinal.
 */
public final class FactoryRecipeTypeCodec {

    public static final String ORDINAL_KEY = "recipeType";
    public static final String NAME_KEY = "recipeTypeName";
    public static final String VERSION_KEY = "recipeTypeVersion";
    public static final int FORMAT_VERSION = 1;

    private FactoryRecipeTypeCodec() {
    }

    public static boolean hasRecipeType(@Nullable NBTTagCompound nbt) {
        return nbt != null && (nbt.hasKey(NAME_KEY, NBT.TAG_STRING) || nbt.hasKey(ORDINAL_KEY, NBT.TAG_ANY_NUMERIC));
    }

    @Nullable
    public static RecipeType read(@Nullable NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }
        if (nbt.hasKey(NAME_KEY, NBT.TAG_STRING)) {
            String name = nbt.getString(NAME_KEY);
            for (RecipeType type : RecipeType.values()) {
                if (type.getName().equals(name)) {
                    return type;
                }
            }
            // A named value is authoritative. Falling back to an ordinal could silently turn a
            // future or corrupt factory type into a different machine.
            return null;
        }
        if (nbt.hasKey(ORDINAL_KEY, NBT.TAG_ANY_NUMERIC)) {
            int ordinal = nbt.getInteger(ORDINAL_KEY);
            RecipeType[] values = RecipeType.values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
        }
        return null;
    }

    @Nonnull
    public static RecipeType readOrDefault(@Nullable NBTTagCompound nbt, @Nonnull RecipeType fallback) {
        RecipeType type = read(nbt);
        return type == null ? fallback : type;
    }

    public static void write(@Nonnull NBTTagCompound nbt, @Nonnull RecipeType type) {
        nbt.setString(NAME_KEY, type.getName());
        nbt.setInteger(ORDINAL_KEY, type.ordinal());
        nbt.setInteger(VERSION_KEY, FORMAT_VERSION);
    }

    public static void writeLegacyOrdinal(@Nonnull NBTTagCompound nbt, int ordinal) {
        nbt.removeTag(NAME_KEY);
        nbt.removeTag(VERSION_KEY);
        nbt.setInteger(ORDINAL_KEY, ordinal);
    }
}
