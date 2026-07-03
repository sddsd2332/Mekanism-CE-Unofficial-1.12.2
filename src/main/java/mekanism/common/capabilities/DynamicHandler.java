package mekanism.common.capabilities;

import mekanism.api.IContentsListener;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class DynamicHandler<TANK> extends SimpleDynamicHandler<TANK> {

    protected final InteractPredicate canExtract;
    protected final InteractPredicate canInsert;

    protected DynamicHandler(Function<EnumFacing, List<TANK>> containerSupplier, Predicate<EnumFacing> canExtract, Predicate<EnumFacing> canInsert,
          @Nullable IContentsListener listener) {
        this(containerSupplier, (tank, side) -> canExtract.test(side), (tank, side) -> canInsert.test(side), listener);
    }

    protected DynamicHandler(Function<EnumFacing, List<TANK>> containerSupplier, InteractPredicate canExtract, InteractPredicate canInsert,
          @Nullable IContentsListener listener) {
        super(containerSupplier, listener);
        this.canExtract = canExtract;
        this.canInsert = canInsert;
    }

    @FunctionalInterface
    public interface InteractPredicate {

        InteractPredicate ALWAYS_TRUE = (tank, side) -> true;

        boolean test(int tank, @Nullable EnumFacing side);
    }
}
