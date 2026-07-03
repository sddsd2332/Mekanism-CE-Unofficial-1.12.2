package mekanism.common.util;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import java.util.Set;

public final class FluidUtils {

    private FluidUtils() {
    }

    public static void emit(IExtendedFluidTank tank, TileEntity from) {
        emit(EnumFacing.VALUES.length == 0 ? java.util.Collections.emptySet() : java.util.EnumSet.allOf(EnumFacing.class), tank, from);
    }

    public static void emit(Set<EnumFacing> outputSides, IExtendedFluidTank tank, TileEntity from) {
        emit(outputSides, tank, from, tank.getCapacity());
    }

    public static void emit(Set<EnumFacing> outputSides, IExtendedFluidTank tank, TileEntity from, int maxOutput) {
        if (!tank.isEmpty() && maxOutput > 0) {
            FluidStack simulated = tank.extract(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
            if (simulated != null && simulated.amount > 0) {
                tank.extract(emit(outputSides, simulated, from), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    public static int emit(Set<EnumFacing> sides, @Nonnull FluidStack stack, TileEntity from) {
        if (stack.amount <= 0 || sides.isEmpty()) {
            return 0;
        }
        FluidHandlerTarget target = new FluidHandlerTarget(stack, 6);
        EmitUtils.forEachSide(from.getWorld(), from.getPos(), sides, (acceptor, side) -> {
            IFluidHandler handler = CapabilityUtils.getCapability(acceptor, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
            if (handler != null && PipeUtils.canFill(handler, stack)) {
                target.addHandler(handler);
            }
        });
        return target.getHandlerCount() == 0 ? 0 : EmitUtils.sendToAcceptors(target, stack.amount, stack);
    }

    public static boolean handleTankInteraction(EntityPlayer player, EnumHand hand, ItemStack itemStack, IExtendedFluidTank fluidTank, boolean drainTank) {
        return handleTankInteraction(player, hand, itemStack, fluidTank, drainTank, true);
    }

    public static boolean handleTankInteraction(EntityPlayer player, EnumHand hand, ItemStack itemStack, IExtendedFluidTank fluidTank, boolean drainTank,
          boolean dropExcessContainer) {
        ItemStack copyStack = StackUtils.size(itemStack.copy(), 1);
        if (!FluidContainerUtils.isFluidContainer(itemStack)) {
            return false;
        }
        IFluidHandlerItem handler = FluidContainerUtils.getFluidHandlerCapability(copyStack);
        if (handler == null) {
            return false;
        }
        FluidStack fluidInItem;
        if (fluidTank.isEmpty()) {
            fluidInItem = handler.drain(Integer.MAX_VALUE, false);
        } else {
            fluidInItem = handler.drain(FluidContainerUtils.copyWithAmount(fluidTank.getFluid(), Integer.MAX_VALUE), false);
        }
        if (fluidInItem == null || fluidInItem.amount <= 0) {
            if (fluidTank.isEmpty()) {
                return false;
            }
            int filled = handler.fill(fluidTank.getFluid().copy(), !player.capabilities.isCreativeMode);
            ItemStack container = handler.getContainer();
            if (filled > 0) {
                if (itemStack.getCount() == 1) {
                    player.setHeldItem(hand, container);
                } else if (itemStack.getCount() > 1 && player.inventory.addItemStackToInventory(container)) {
                    itemStack.shrink(1);
                } else if (dropExcessContainer) {
                    player.dropItem(container, false, true);
                    itemStack.shrink(1);
                } else {
                    return false;
                }
                if (drainTank) {
                    fluidTank.extract(filled, Action.EXECUTE, AutomationType.MANUAL);
                }
                return true;
            }
            return false;
        }
        FluidStack simulatedRemainder = fluidTank.insert(fluidInItem, Action.SIMULATE, AutomationType.MANUAL);
        int remainder = simulatedRemainder == null ? 0 : simulatedRemainder.amount;
        int storedAmount = fluidInItem.amount;
        if (remainder < storedAmount) {
            boolean filled = false;
            FluidStack drained = handler.drain(copyFluidStackWithAmount(fluidInItem, storedAmount - remainder), !player.capabilities.isCreativeMode);
            if (drained != null && drained.amount > 0) {
                ItemStack container = handler.getContainer();
                if (player.capabilities.isCreativeMode) {
                    filled = true;
                } else if (!container.isEmpty()) {
                    if (itemStack.getCount() == 1) {
                        player.setHeldItem(hand, container);
                        filled = true;
                    } else if (player.inventory.addItemStackToInventory(container)) {
                        itemStack.shrink(1);
                        filled = true;
                    }
                } else {
                    itemStack.shrink(1);
                    if (itemStack.getCount() == 0) {
                        player.setHeldItem(hand, ItemStack.EMPTY);
                    }
                    filled = true;
                }
                if (filled) {
                    fluidTank.insert(drained, Action.EXECUTE, AutomationType.MANUAL);
                    return true;
                }
            }
        }
        return false;
    }

    private static FluidStack copyFluidStackWithAmount(FluidStack stack, int amount) {
        return FluidContainerUtils.copyWithAmount(stack, amount);
    }
}
