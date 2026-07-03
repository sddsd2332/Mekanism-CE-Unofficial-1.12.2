package mekanism.common.capabilities.holder;

import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public interface IHolder {

    default boolean canInsert(@Nullable EnumFacing direction) {
        return true;
    }

    default boolean canExtract(@Nullable EnumFacing direction) {
        return true;
    }
}
