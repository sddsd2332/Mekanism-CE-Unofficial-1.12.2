package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/** Naming alias for integrations which call immutable resource values resource snapshots. */
@Deprecated
public final class RecipeResourceSnapshot {

    private final ImmutableResourceSnapshot delegate;

    private RecipeResourceSnapshot(ImmutableResourceSnapshot delegate) {
        this.delegate = delegate;
    }

    public static RecipeResourceSnapshot of(@Nullable ItemStack stack) {
        return new RecipeResourceSnapshot(ImmutableResourceSnapshot.of(stack));
    }

    public static RecipeResourceSnapshot of(@Nullable FluidStack stack) {
        return new RecipeResourceSnapshot(ImmutableResourceSnapshot.of(stack));
    }

    public static RecipeResourceSnapshot of(@Nullable GasStack stack) {
        return new RecipeResourceSnapshot(ImmutableResourceSnapshot.of(stack));
    }

    public ImmutableResourceSnapshot asImmutable() { return delegate; }
    public ImmutableResourceSnapshot.Kind getKind() { return delegate.getKind(); }
    public long getAmount() { return delegate.getAmount(); }
    public ItemStack getItemCopy() { return delegate.getItemCopy(); }
    public FluidStack getFluidCopy() { return delegate.getFluidCopy(); }
    public GasStack getGasCopy() { return delegate.getGasCopy(); }
    public String semanticKey() { return delegate.semanticKey(); }
}
