package mekanism.api.gas;

import javax.annotation.Nullable;

public interface IGasTank {

    @Nullable
    GasStack getGas();

    int getGasAmount();

    int getMaxGas();

    GasTankInfo getInfo();

    int input(GasStack resource, boolean input);

    @Nullable
    GasStack output(int Maxoutput ,boolean output);

    default Gas getGasType() {
        GasStack stack = getGas();
        return stack == null ? null : stack.getGas();
    }

    default int getNeeded() {
        return getMaxGas() - getGasAmount();
    }

    default boolean canReceive(Gas gas) {
        GasStack stored = getGas();
        return getNeeded() > 0 && (stored == null || gas == null || gas == stored.getGas());
    }

    default boolean canReceiveType(Gas gas) {
        GasStack stored = getGas();
        return stored == null || gas == null || gas == stored.getGas();
    }

    default boolean canDraw(Gas gas) {
        GasStack stored = getGas();
        return stored != null && (gas == null || gas == stored.getGas());
    }

    default int receive(GasStack amount, boolean doReceive) {
        return input(amount, doReceive);
    }

    @Nullable
    default GasStack draw(int amount, boolean doDraw) {
        return output(amount, doDraw);
    }
}
