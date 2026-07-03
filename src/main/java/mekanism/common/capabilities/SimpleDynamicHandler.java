package mekanism.common.capabilities;

import mekanism.api.IContentsListener;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

public abstract class SimpleDynamicHandler<TANK> implements IContentsListener {

    protected final Function<EnumFacing, List<TANK>> containerSupplier;
    @Nullable
    private final IContentsListener listener;

    protected SimpleDynamicHandler(Function<EnumFacing, List<TANK>> containerSupplier, @Nullable IContentsListener listener) {
        this.containerSupplier = containerSupplier;
        this.listener = listener;
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }
}
