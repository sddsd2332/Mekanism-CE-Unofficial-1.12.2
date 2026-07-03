package mekanism.common.capabilities.proxy;

import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public class ProxyHandler {

    @Nullable
    protected final EnumFacing side;
    @Nullable
    private final IHolder holder;
    protected final boolean readOnly;

    protected ProxyHandler(@Nullable EnumFacing side, @Nullable IHolder holder) {
        this.side = side;
        this.holder = holder;
        readOnly = side == null;
    }

    @Nullable
    protected IHolder getHolder() {
        return holder;
    }

    protected boolean readOnlyInsert() {
        return readOnly || holder != null && !holder.canInsert(side);
    }

    protected boolean readOnlyExtract() {
        return readOnly || holder != null && !holder.canExtract(side);
    }
}
