package mekanism.common.capabilities.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.tier.EnergyCubeTier;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Energy cube storage bridge with high-version creative cube semantics.
 */
public class EnergyCubeEnergyContainer extends MachineEnergyContainer {

    public static EnergyCubeEnergyContainer create(Supplier<EnergyCubeTier> tier, DoubleSupplier stored, DoubleConsumer setter,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(tier, "Energy cube tier supplier cannot be null");
        Objects.requireNonNull(stored, "Stored energy supplier cannot be null");
        Objects.requireNonNull(setter, "Stored energy setter cannot be null");
        return new EnergyCubeEnergyContainer(tier, stored, setter, listener);
    }

    private final Supplier<EnergyCubeTier> tier;

    private EnergyCubeEnergyContainer(Supplier<EnergyCubeTier> tier, DoubleSupplier stored, DoubleConsumer setter, @Nullable IContentsListener listener) {
        super(stored, setter, () -> tier.get().getMaxEnergy(), () -> 0, automationType -> true, automationType -> true, listener);
        this.tier = tier;
    }

    private boolean isCreative() {
        return tier.get() == EnergyCubeTier.CREATIVE;
    }

    @Override
    public double getEnergy() {
        double stored = super.getEnergy();
        return isCreative() && stored > 0 ? Double.MAX_VALUE : stored;
    }

    @Override
    public double getMaxEnergy() {
        return isCreative() ? Double.MAX_VALUE : super.getMaxEnergy();
    }

    @Override
    protected double getInsertRate(@Nullable AutomationType automationType) {
        return automationType == AutomationType.INTERNAL ? Math.max(0, tier.get().getOutput()) : super.getInsertRate(automationType);
    }

    @Override
    protected double getExtractRate(@Nullable AutomationType automationType) {
        return automationType == AutomationType.INTERNAL ? Math.max(0, tier.get().getOutput()) : super.getExtractRate(automationType);
    }

    @Override
    public double insert(double amount, Action action, AutomationType automationType) {
        return super.insert(amount, action.combine(!isCreative()), automationType);
    }

    @Override
    public double insert(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
        return insert(amount, action, automationType);
    }

    @Override
    public double extract(double amount, Action action, AutomationType automationType) {
        return super.extract(amount, action.combine(!isCreative()), automationType);
    }

    @Override
    public double extract(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
        return extract(amount, action, automationType);
    }
}
