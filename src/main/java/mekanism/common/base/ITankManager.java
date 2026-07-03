package mekanism.common.base;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.MekanismAPI;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.gas.IMekanismGasHandler;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.ItemGaugeDropper;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

/**
 * 复合储罐管理程序，
 * 含有多个可存储的储罐的机器可用
 * 建议是流体类型排前面气体排后面
 */

public interface ITankManager {

    Object[] getManagedTanks();

    class DropperHandler {

        public static void useDropper(EntityPlayer player, Object tank, int button) {
            ItemStack stack = player.inventory.getItemStack();

            if (stack.isEmpty() || !(stack.getItem() instanceof ItemGaugeDropper)) {
                return;
            }

            if (!stack.isEmpty()) {
                if (tank instanceof IExtendedGasTank gasTank) {
                    IExtendedGasTank dropperTank = getDropperGasTank(stack);
                    GasStack dropperGas = dropperTank == null ? null : dropperTank.getGas();

                    if (dropperGas != null && gasTank.getGas() != null && !dropperGas.isGasEqual(gasTank.getGas())) {
                        return;
                    }

                    if (button == 0) { //Insert gas into dropper
                        if (dropperTank == null || FluidContainerUtils.getFluidContained(stack) != null) {
                            return;
                        }
                        transferBetweenTanks(gasTank, dropperTank, player);
                    } else if (button == 1) { //Extract gas from dropper
                        if (dropperTank == null || FluidContainerUtils.getFluidContained(stack) != null) {
                            return;
                        }
                        transferBetweenTanks(dropperTank, gasTank, player);
                    } else if (button == 2) { //Dump the tank
                        GasStack gas = gasTank.getGas();
                        if (gas != null){
                            MekanismAPI.getRadiationManager().dumpRadiation(new Coord4D(player), gas);
                            triggerDropperUse(player);
                        }
                        gasTank.setEmpty();
                    }
                } else if (tank instanceof IExtendedFluidTank fluidTank) {
                    FluidStack dropperFluid = FluidContainerUtils.getFluidContained(stack);
                    GasStack dropperGas = GasInventorySlot.getContainedGas(stack);

                    if (dropperFluid != null && fluidTank.getFluid() != null && !dropperFluid.isFluidEqual(fluidTank.getFluid())) {
                        return;
                    }

                    IExtendedFluidTank dropperTank = getDropperFluidTank(stack);
                    if (button == 0) { //Insert fluid into dropper
                        if (dropperTank == null || dropperGas != null) {
                            return;
                        }
                        transferBetweenTanks(fluidTank, dropperTank, player);
                    } else if (button == 1) { //Extract fluid from dropper
                        if (dropperTank == null || dropperGas != null) {
                            return;
                        }
                        transferBetweenTanks(dropperTank, fluidTank, player);
                    } else if (button == 2) { //Dump the tank
                        if (!fluidTank.isEmpty()) {
                            triggerDropperUse(player);
                        }
                        fluidTank.setEmpty();
                    }
                }
            }
        }

        @Nullable
        private static IExtendedGasTank getDropperGasTank(ItemStack stack) {
            IMekanismGasHandler handler = GasInventorySlot.getMekanismCapability(stack);
            return handler == null ? null : handler.getGasTank(0, null);
        }

        @Nullable
        private static IExtendedFluidTank getDropperFluidTank(ItemStack stack) {
            IFluidHandlerItem handler = FluidContainerUtils.getFluidHandlerCapability(stack);
            return handler instanceof IMekanismFluidHandler mekanismHandler ? mekanismHandler.getFluidTank(0, null) : null;
        }

        private static void transferBetweenTanks(IExtendedGasTank drainTank, IExtendedGasTank fillTank, EntityPlayer player) {
            if (!drainTank.isEmpty() && fillTank.getNeeded() > 0) {
                GasStack gasInDrainTank = drainTank.getGas();
                GasStack simulatedRemainder = fillTank.insert(gasInDrainTank, Action.SIMULATE, AutomationType.MANUAL);
                int remainder = simulatedRemainder == null ? 0 : simulatedRemainder.amount;
                int amount = gasInDrainTank.amount;
                if (remainder < amount) {
                    GasStack extractedGas = drainTank.extract(amount - remainder, Action.EXECUTE, AutomationType.MANUAL);
                    if (extractedGas != null && extractedGas.amount > 0) {
                        GasStack remainderAfterInsert = fillTank.insert(extractedGas, Action.EXECUTE, AutomationType.MANUAL);
                        MekanismUtils.logMismatchedStackSize(remainderAfterInsert == null ? 0 : remainderAfterInsert.amount, 0);
                        triggerDropperUse(player);
                        ((EntityPlayerMP) player).sendContainerToPlayer(player.openContainer);
                    }
                }
            }
        }

        private static void transferBetweenTanks(IExtendedFluidTank drainTank, IExtendedFluidTank fillTank, EntityPlayer player) {
            if (!drainTank.isEmpty() && fillTank.getNeeded() > 0) {
                FluidStack fluidInDrainTank = drainTank.getFluid();
                FluidStack simulatedRemainder = fillTank.insert(fluidInDrainTank, Action.SIMULATE, AutomationType.MANUAL);
                int remainder = simulatedRemainder == null ? 0 : simulatedRemainder.amount;
                int amount = fluidInDrainTank.amount;
                if (remainder < amount) {
                    FluidStack extractedFluid = drainTank.extract(amount - remainder, Action.EXECUTE, AutomationType.MANUAL);
                    if (extractedFluid != null && extractedFluid.amount > 0) {
                        MekanismUtils.logMismatchedStackSize(getAmount(fillTank.insert(extractedFluid, Action.EXECUTE, AutomationType.MANUAL)), 0);
                        triggerDropperUse(player);
                        ((EntityPlayerMP) player).sendContainerToPlayer(player.openContainer);
                    }
                }
            }
        }

        private static void triggerDropperUse(EntityPlayer player) {
            if (player instanceof EntityPlayerMP playerMP) {
                MekanismCriteriaTriggers.USE_GAUGE_DROPPER.trigger(playerMP);
            }
        }

        private static int getAmount(@Nullable FluidStack stack) {
            return stack == null ? 0 : stack.amount;
        }
    }
}
