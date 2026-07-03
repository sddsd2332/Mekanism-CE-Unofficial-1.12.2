package mekanism.common.capabilities.gas.item;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.functions.TriPredicate;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.GenericTankSpec;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public class GasTankSpec extends GenericTankSpec<GasStack> {

    final IntSupplier rate;
    final IntSupplier capacity;

    public GasTankSpec(IntSupplier rate, IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
          TriPredicate<GasStack, AutomationType, ItemStack> canInsert, Predicate<GasStack> isValid, Predicate<ItemStack> supportsStack) {
        super(canExtract, canInsert, isValid, supportsStack);
        this.rate = rate;
        this.capacity = capacity;
    }

    public <TANK extends IExtendedGasTank> TANK createTank(TankFromSpecCreator<TANK> tankCreator, ItemStack stack) {
        return createTank(tankCreator, stack, null);
    }

    public <TANK extends IExtendedGasTank> TANK createTank(TankFromSpecCreator<TANK> tankCreator, ItemStack stack, @Nullable IContentsListener listener) {
        return tankCreator.create(rate, capacity, canExtract, (gas, automationType) -> canInsert.test(gas, automationType, stack), isValid, listener);
    }

    public static GasTankSpec create(IntSupplier rate, IntSupplier capacity) {
        return new GasTankSpec(rate, capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrueTri(), ConstantPredicates.alwaysTrue(),
              ConstantPredicates.alwaysTrue());
    }

    public static GasTankSpec createFillOnly(IntSupplier rate, IntSupplier capacity, Predicate<GasStack> isValid) {
        return createFillOnly(rate, capacity, isValid, ConstantPredicates.alwaysTrue());
    }

    public static GasTankSpec createFillOnly(IntSupplier rate, IntSupplier capacity, Predicate<GasStack> isValid, Predicate<ItemStack> supportsStack) {
        return new GasTankSpec(rate, capacity, ConstantPredicates.notExternal(), (gas, automation, stack) -> supportsStack.test(stack), isValid, supportsStack);
    }

    @FunctionalInterface
    public interface TankFromSpecCreator<TANK extends IExtendedGasTank> {

        TANK create(IntSupplier rate, IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
              BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> isValid, @Nullable IContentsListener listener);

        default TANK create(IntSupplier rate, IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
              BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> isValid) {
            return create(rate, capacity, canExtract, canInsert, isValid, null);
        }
    }
}
