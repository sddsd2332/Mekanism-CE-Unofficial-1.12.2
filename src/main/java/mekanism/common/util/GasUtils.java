package mekanism.common.util;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.network.distribution.GasHandlerTarget;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * A handy class containing several utilities for efficient gas transfer.
 *
 * @author AidanBrady
 */
public final class GasUtils {

    private GasUtils() {
    }

    /**
     * Removes a specified amount of gas from a gas container item.
     *
     * @param itemStack - ItemStack of the gas container
     * @param type      - type of gas to remove from the container, null if it doesn't matter
     * @param amount    - amount of gas to remove from the ItemStack
     * @return the GasStack removed by the container
     */
    public static GasStack removeGas(ItemStack itemStack, Gas type, int amount) {
        return GasInventorySlot.useGas(itemStack, type, amount);
    }

    /**
     * Adds a specified amount of gas to a gas container item.
     *
     * @param itemStack - ItemStack of the gas container
     * @param stack     - stack to add to the container
     * @return amount of gas accepted by the container
     */
    public static int addGas(ItemStack itemStack, GasStack stack) {
        return GasInventorySlot.insertGas(itemStack, stack, true);
    }

    public static int simulateAddGas(ItemStack itemStack, GasStack stack) {
        return GasInventorySlot.insertGas(itemStack, stack, false);
    }

    public static boolean hasGasHandler(ItemStack stack) {
        return GasInventorySlot.isGasContainerItem(stack);
    }

    public static boolean isGasItem(ItemStack stack) {
        return GasInventorySlot.isGasContainerItem(stack);
    }

    public static boolean canProvideGas(ItemStack stack, @Nullable Gas type) {
        return GasInventorySlot.canProvideGas(stack, type);
    }

    public static boolean canReceiveGas(ItemStack stack, @Nullable Gas type) {
        return GasInventorySlot.canReceiveGas(stack, type);
    }

    public static boolean canReceiveGas(@Nullable IGasHandler gasHandler, @Nullable Gas type) {
        return GasInventorySlot.canReceiveGas(gasHandler, type);
    }

    public static boolean canReceiveGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable Gas type) {
        return GasInventorySlot.canReceiveGas(gasHandler, side, type);
    }

    public static boolean canReceiveGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable GasStack stack) {
        return GasInventorySlot.canReceiveGas(gasHandler, side, stack);
    }

    @Nullable
    public static GasStack getGasContained(ItemStack stack) {
        return GasInventorySlot.getContainedGas(stack);
    }

    @Nullable
    public static <T> T getExtractableGas(ItemStack stack, int needed, BiFunction<Gas, Integer, T> getIfValid) {
        return GasInventorySlot.getExtractableGas(stack, needed, getIfValid);
    }

    @Nullable
    public static IGasHandler getGasHandlerCapability(ItemStack stack) {
        return GasInventorySlot.getCapability(stack);
    }

    @Nullable
    public static IGasHandler getUnstackedGasHandlerCapability(ItemStack stack) {
        return GasInventorySlot.getUnstackedCapability(stack);
    }

    @Nullable
    public static IMekanismGasHandler getCapabilityGasHandler(ItemStack stack) {
        return GasInventorySlot.getMekanismCapability(stack);
    }

    @Nullable
    public static IMekanismGasHandler getUnstackedCapabilityGasHandler(ItemStack stack) {
        return GasInventorySlot.getUnstackedMekanismCapability(stack);
    }

    @Nullable
    public static GasStack getCapabilityStoredGas(ItemStack stack) {
        return GasInventorySlot.getCapabilityStoredGas(stack);
    }

    public static boolean setGasContained(ItemStack stack, @Nullable GasStack gasStack) {
        return GasInventorySlot.setGasContained(stack, gasStack);
    }

    public static boolean setCapabilityStoredGas(ItemStack stack, @Nullable GasStack gasStack) {
        return GasInventorySlot.setCapabilityStoredGas(stack, gasStack);
    }

    @Nullable
    public static GasStack useGas(ItemStack itemStack, @Nullable Gas type, int amount) {
        return GasInventorySlot.useGas(itemStack, type, amount);
    }

    public static int getTankCount(ItemStack stack) {
        return GasInventorySlot.getTankCount(stack);
    }

    @Nullable
    public static GasStack getGasInTank(ItemStack stack, int tank) {
        return GasInventorySlot.getGasInTank(stack, tank);
    }

    public static int getTankCapacity(ItemStack stack, int tank) {
        return GasInventorySlot.getTankCapacity(stack, tank);
    }

    public static int getTankCount(@Nullable IGasHandler gasHandler) {
        return GasInventorySlot.getTankCount(gasHandler);
    }

    @Nullable
    public static GasStack getGasInTank(@Nullable IGasHandler gasHandler, int tank) {
        return GasInventorySlot.getGasInTank(gasHandler, tank);
    }

    public static int getTankCapacity(@Nullable IGasHandler gasHandler, int tank) {
        return GasInventorySlot.getTankCapacity(gasHandler, tank);
    }

    @Nullable
    public static GasStack extractGas(@Nullable IGasHandler gasHandler, @Nullable Gas type, int amount, boolean doTransfer) {
        return extractGas(gasHandler, null, type, amount, doTransfer);
    }

    public static int insertGas(@Nullable IGasHandler gasHandler, @Nullable GasStack stack, boolean doTransfer) {
        return insertGas(gasHandler, null, stack, doTransfer);
    }

    @Nullable
    public static GasStack extractGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable Gas type, int amount, boolean doTransfer) {
        return GasInventorySlot.extractGas(gasHandler, side, type, amount, doTransfer);
    }

    public static int insertGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable GasStack stack, boolean doTransfer) {
        return GasInventorySlot.insertGas(gasHandler, side, stack, doTransfer);
    }

    public static void emit(IExtendedGasTank tank, TileEntity from) {
        emit(EnumSet.allOf(EnumFacing.class), tank, from);
    }

    public static void emit(Set<EnumFacing> outputSides, IExtendedGasTank tank, TileEntity from) {
        emit(outputSides, tank, from, tank.getCapacity());
    }

    public static void emit(Set<EnumFacing> outputSides, IExtendedGasTank tank, TileEntity from, int maxOutput) {
        if (!tank.isEmpty() && maxOutput > 0) {
            GasStack simulated = tank.extract(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
            if (simulated != null && simulated.amount > 0) {
                tank.extract(emit(simulated, from, outputSides), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    /**
     * Emits gas from a central block by splitting the received stack among the sides given.
     *
     * @param stack - the stack to output
     * @param from  - the TileEntity to output from
     * @param sides - the list of sides to output from
     * @return the amount of gas emitted
     */
    public static int emit(GasStack stack, TileEntity from, Set<EnumFacing> sides) {
        if (stack == null || stack.amount == 0 || sides.isEmpty()) {
            return 0;
        }

        //Fake that we have one target given we know that no sides will overlap
        // This allows us to have slightly better performance
        final GasHandlerTarget target = new GasHandlerTarget(stack, 6);
        if (from != null) {
            EmitUtils.forEachSide(from.getWorld(), from.getPos(), sides, (acceptor, side) -> {
                //Invert to get access side
                final EnumFacing accessSide = side.getOpposite();
                //Collect cap
                CapabilityUtils.runIfCap(acceptor, Capabilities.GAS_HANDLER_CAPABILITY, accessSide,
                        (handler) -> {
                            if (canReceiveGas(handler, accessSide, stack)) {
                                target.addHandler(accessSide, handler);
                            }
                        });
            });
        }
        if (target.getHandlerCount() > 0) {
            return EmitUtils.sendToAcceptors(target, stack.amount, stack);
        }
        return 0;
    }

    public static boolean canDrain(@Nullable GasStack tankGas, @Nullable Gas outGas) {
        return tankGas != null && (outGas == null || tankGas.isGasEqual(outGas));
    }
}
