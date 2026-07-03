package mekanism.common.capabilities.holder;

import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public abstract class ProxiedHolder implements IHolder {

    private final Predicate<EnumFacing> insertPredicate;
    private final Predicate<EnumFacing> extractPredicate;

    protected ProxiedHolder(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate) {
        this.insertPredicate = insertPredicate;
        this.extractPredicate = extractPredicate;
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing side) {
        return insertPredicate.test(side);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing side) {
        return extractPredicate.test(side);
    }
}
