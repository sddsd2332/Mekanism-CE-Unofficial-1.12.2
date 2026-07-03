package mekanism.api.gas;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

public final class ExtendedGasHandlerUtils {

    private ExtendedGasHandlerUtils() {
    }

    @Nullable
    public static GasStack insert(@Nullable GasStack stack, Action action, IntSupplier tankCount, GasInTankGetter inTankGetter, InsertGas insertGas) {
        if (isEmpty(stack)) {
            return null;
        }
        int tanks = tankCount.getAsInt();
        if (tanks == 1) {
            return emptyToNull(insertGas.insert(0, stack, action));
        }
        IntList matchingTanks = new IntArrayList();
        IntList emptyTanks = new IntArrayList();
        for (int tank = 0; tank < tanks; tank++) {
            GasStack inTank = inTankGetter.getGasInTank(tank);
            if (isEmpty(inTank)) {
                emptyTanks.add(tank);
            } else if (inTank.isGasEqual(stack)) {
                matchingTanks.add(tank);
            }
        }
        GasStack toInsert = stack;
        for (int tank : matchingTanks) {
            GasStack remainder = insertGas.insert(tank, toInsert, action);
            if (isEmpty(remainder)) {
                return null;
            }
            toInsert = remainder;
        }
        for (int tank : emptyTanks) {
            GasStack remainder = insertGas.insert(tank, toInsert, action);
            if (isEmpty(remainder)) {
                return null;
            }
            toInsert = remainder;
        }
        return toInsert;
    }

    @Nullable
    public static GasStack insert(@Nullable GasStack stack, @Nullable EnumFacing side, Function<EnumFacing, List<IExtendedGasTank>> gasTankSupplier,
          Action action, AutomationType automationType) {
        if (isEmpty(stack)) {
            return null;
        }
        List<IExtendedGasTank> gasTanks = gasTankSupplier.apply(side);
        return insert(stack, action, automationType, gasTanks.size(), gasTanks);
    }

    @Nullable
    public static GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType, int size, List<IExtendedGasTank> gasTanks) {
        if (isEmpty(stack)) {
            return null;
        } else if (size == 0) {
            return stack;
        } else if (size == 1) {
            return emptyToNull(gasTanks.get(0).insert(stack, action, automationType));
        }
        GasStack toInsert = stack;
        List<IExtendedGasTank> emptyTanks = new ArrayList<>();
        for (IExtendedGasTank tank : gasTanks) {
            if (tank.isEmpty()) {
                emptyTanks.add(tank);
            } else if (tank.isTypeEqual(stack)) {
                GasStack remainder = tank.insert(toInsert, action, automationType);
                if (isEmpty(remainder)) {
                    return null;
                }
                toInsert = remainder;
            }
        }
        for (IExtendedGasTank tank : emptyTanks) {
            GasStack remainder = tank.insert(toInsert, action, automationType);
            if (isEmpty(remainder)) {
                return null;
            }
            toInsert = remainder;
        }
        return toInsert;
    }

    @Nullable
    public static GasStack extract(int amount, Action action, IntSupplier tankCount, GasInTankGetter inTankGetter, ExtractGas extractGas) {
        if (amount <= 0) {
            return null;
        }
        int tanks = tankCount.getAsInt();
        if (tanks == 1) {
            return emptyToNull(extractGas.extract(0, amount, action));
        }
        GasStack extracted = null;
        int toDrain = amount;
        for (int tank = 0; tank < tanks; tank++) {
            GasStack inTank = inTankGetter.getGasInTank(tank);
            if (isEmpty(extracted) || !isEmpty(inTank) && extracted.isGasEqual(inTank)) {
                GasStack drained = extractGas.extract(tank, toDrain, action);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    @Nullable
    public static GasStack extract(int amount, @Nullable EnumFacing side, Function<EnumFacing, List<IExtendedGasTank>> gasTankSupplier,
          Action action, AutomationType automationType) {
        if (amount <= 0) {
            return null;
        }
        List<IExtendedGasTank> gasTanks = gasTankSupplier.apply(side);
        return extract(amount, action, automationType, gasTanks.size(), gasTanks);
    }

    @Nullable
    public static GasStack extract(int amount, Action action, AutomationType automationType, int size, List<IExtendedGasTank> gasTanks) {
        if (amount <= 0 || size == 0) {
            return null;
        } else if (size == 1) {
            return emptyToNull(gasTanks.get(0).extract(amount, action, automationType));
        }
        GasStack extracted = null;
        int toDrain = amount;
        for (IExtendedGasTank gasTank : gasTanks) {
            if (isEmpty(extracted) || gasTank.isTypeEqual(extracted)) {
                GasStack drained = gasTank.extract(toDrain, action, automationType);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    @Nullable
    public static GasStack extract(@Nullable GasStack stack, Action action, IntSupplier tankCount, GasInTankGetter inTankGetter,
          ExtractGas extractGas) {
        if (isEmpty(stack)) {
            return null;
        }
        int tanks = tankCount.getAsInt();
        if (tanks == 1) {
            GasStack inTank = inTankGetter.getGasInTank(0);
            if (isEmpty(inTank) || !inTank.isGasEqual(stack)) {
                return null;
            }
            return emptyToNull(extractGas.extract(0, stack.amount, action));
        }
        GasStack extracted = null;
        int toDrain = stack.amount;
        for (int tank = 0; tank < tanks; tank++) {
            GasStack inTank = inTankGetter.getGasInTank(tank);
            if (!isEmpty(inTank) && stack.isGasEqual(inTank)) {
                GasStack drained = extractGas.extract(tank, toDrain, action);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    @Nullable
    public static GasStack extract(@Nullable GasStack stack, @Nullable EnumFacing side, Function<EnumFacing, List<IExtendedGasTank>> gasTankSupplier,
          Action action, AutomationType automationType) {
        if (isEmpty(stack)) {
            return null;
        }
        List<IExtendedGasTank> gasTanks = gasTankSupplier.apply(side);
        return extract(stack, action, automationType, gasTanks.size(), gasTanks);
    }

    @Nullable
    public static GasStack extract(@Nullable GasStack stack, Action action, AutomationType automationType, int size, List<IExtendedGasTank> gasTanks) {
        if (isEmpty(stack) || size == 0) {
            return null;
        } else if (size == 1) {
            IExtendedGasTank tank = gasTanks.get(0);
            if (tank.isEmpty() || !tank.isTypeEqual(stack)) {
                return null;
            }
            return emptyToNull(tank.extract(stack.amount, action, automationType));
        }
        GasStack extracted = null;
        int toDrain = stack.amount;
        for (IExtendedGasTank gasTank : gasTanks) {
            if (gasTank.isTypeEqual(stack)) {
                GasStack drained = gasTank.extract(toDrain, action, automationType);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    public static boolean isEmpty(@Nullable GasStack stack) {
        return stack == null || stack.amount <= 0;
    }

    @Nullable
    public static GasStack emptyToNull(@Nullable GasStack stack) {
        return isEmpty(stack) ? null : stack;
    }

    @FunctionalInterface
    public interface GasInTankGetter {

        @Nullable
        GasStack getGasInTank(int tank);
    }

    @FunctionalInterface
    public interface InsertGas {

        @Nullable
        GasStack insert(int tank, GasStack stack, Action action);
    }

    @FunctionalInterface
    public interface ExtractGas {

        @Nullable
        GasStack extract(int tank, int amount, Action action);
    }
}
