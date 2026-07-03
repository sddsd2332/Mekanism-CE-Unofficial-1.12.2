package mekanism.common.capabilities.fluid;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.util.WorldUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.IntSupplier;

public class FluidTankFluidTank extends BasicFluidTank {

    public static FluidTankFluidTank create(TileEntityFluidTank tile, @Nullable IContentsListener listener) {
        Objects.requireNonNull(tile, "Fluid tank tile entity cannot be null");
        return new FluidTankFluidTank(tile, listener);
    }

    private final TileEntityFluidTank tile;
    private final IntSupplier rate;

    private FluidTankFluidTank(TileEntityFluidTank tile, @Nullable IContentsListener listener) {
        super(tile.tier.getStorage(), alwaysTrueBi, alwaysTrueBi, alwaysTrue, listener);
        this.tile = tile;
        //1.12 loads/upgrades the tier after construction, so keep this dynamic while exposing the high-version field shape.
        rate = () -> tile.tier.getOutput();
    }

    @Override
    protected int getRate(@Nullable AutomationType automationType) {
        return automationType == AutomationType.INTERNAL ? rate.getAsInt() : super.getRate(automationType);
    }

    @Override
    public int getCapacity() {
        return tile.tier.getStorage();
    }

    public void setFluid(@Nullable FluidStack stack) {
        setStackUnchecked(stack);
    }

    public FluidTankFluidTank readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("Empty")) {
            setFluid(FluidStack.loadFluidStackFromNBT(nbt));
        } else {
            setFluid(null);
        }
        return this;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        if (stored != null) {
            stored.writeToNBT(nbt);
        } else {
            nbt.setString("Empty", "");
        }
        return nbt;
    }

    @Override
    @Nullable
    public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        FluidStack remainder;
        boolean isCreative = isCreative();
        if (isCreative && isEmpty() && action.execute() && automationType != AutomationType.EXTERNAL) {
            remainder = super.insert(stack, Action.SIMULATE, automationType);
            if (remainder == null && stack != null && stack.amount > 0) {
                setStackUnchecked(new FluidStack(stack, getCapacity()));
            }
        } else {
            remainder = super.insert(stack, action.combine(!isCreative), automationType);
        }
        if (!ExtendedFluidHandlerUtils.isEmpty(remainder)) {
            TileEntityFluidTank tileAbove = getTileAbove();
            if (tileAbove != null) {
                remainder = tileAbove.fluidTank.insert(remainder, action, AutomationType.EXTERNAL);
            }
        }
        return remainder;
    }

    @Override
    public int growStack(int amount, Action action) {
        int grownAmount = super.growStack(amount, action);
        if (amount > 0 && grownAmount < amount && !tile.getActive() && stored != null) {
            TileEntityFluidTank tileAbove = getTileAbove();
            if (tileAbove != null) {
                int leftOverToInsert = amount - grownAmount;
                FluidStack remainder = tileAbove.fluidTank.insert(new FluidStack(stored, leftOverToInsert), action, AutomationType.EXTERNAL);
                grownAmount += leftOverToInsert - (remainder == null ? 0 : remainder.amount);
            }
        }
        return grownAmount;
    }

    @Override
    @Nullable
    public FluidStack extract(int amount, Action action, AutomationType automationType) {
        return super.extract(amount, action.combine(!isCreative()), automationType);
    }

    @Override
    public int setStackSize(int amount, Action action) {
        return super.setStackSize(amount, action.combine(!isCreative()));
    }

    public void fillToCapacity() {
        if (!isEmpty()) {
            super.setStackSize(getCapacity(), Action.EXECUTE);
        }
    }

    private boolean isCreative() {
        return tile.tier == FluidTankTier.CREATIVE;
    }

    @Nullable
    private TileEntityFluidTank getTileAbove() {
        BlockPos above = tile.getPos().up();
        return WorldUtils.getTileEntity(tile.getWorld(), above) instanceof TileEntityFluidTank tileAbove ? tileAbove : null;
    }
}
