package mekanism.common.capabilities.gas;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.gas.GasStack;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.TileEntityGasTank;

import javax.annotation.Nullable;
import java.util.Objects;

public class GasTankGasTank extends BasicGasTank {

    public static GasTankGasTank create(TileEntityGasTank tile, @Nullable IContentsListener listener) {
        Objects.requireNonNull(tile, "Gas tank tile entity cannot be null");
        return new GasTankGasTank(tile, listener);
    }

    private final TileEntityGasTank tile;

    private GasTankGasTank(TileEntityGasTank tile, @Nullable IContentsListener listener) {
        super(tile.tier.getStorage(), alwaysTrueBi, alwaysTrueBi, tile::isValidGas, listener);
        this.tile = tile;
    }

    @Override
    protected int getInsertRate(@Nullable AutomationType automationType) {
        return automationType == AutomationType.INTERNAL ? tile.tier.getOutput() : super.getInsertRate(automationType);
    }

    @Override
    protected int getExtractRate(@Nullable AutomationType automationType) {
        return automationType == AutomationType.INTERNAL ? tile.tier.getOutput() : super.getExtractRate(automationType);
    }

    @Override
    public int getCapacity() {
        return tile.tier.getStorage();
    }

    @Override
    @Nullable
    public GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
        boolean isCreative = isCreative();
        if (stack != null && stack.amount > 0 && isCreative && isEmpty() && action.execute() && automationType != AutomationType.EXTERNAL) {
            GasStack simulatedRemainder = super.insert(stack, Action.SIMULATE, automationType);
            if (simulatedRemainder == null) {
                setStackUnchecked(stack.copy().withAmount(getCapacity()));
            }
            return simulatedRemainder;
        }
        return super.insert(stack, action.combine(!isCreative), automationType);
    }

    @Override
    @Nullable
    public GasStack extract(int amount, Action action, AutomationType automationType) {
        return super.extract(amount, action.combine(!isCreative()), automationType);
    }

    @Override
    public int setStackSize(int amount, Action action) {
        return super.setStackSize(amount, action.combine(!isCreative()));
    }

    private boolean isCreative() {
        return tile.tier == GasTankTier.CREATIVE;
    }
}
