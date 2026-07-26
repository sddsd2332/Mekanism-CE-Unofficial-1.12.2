package mekanism.common.lib.distribution;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.common.TestBootstrap;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import mekanism.common.content.network.distribution.GasHandlerTarget;
import mekanism.common.util.EmitUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DistributionTargetReuseTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void energyTargetResetClearsHandlersAndPendingNeeds() {
        EnergyAcceptorTarget target = new EnergyAcceptorTarget(4);
        RecordingEnergyAcceptor oldFirst = new RecordingEnergyAcceptor();
        RecordingEnergyAcceptor oldSecond = new RecordingEnergyAcceptor();
        target.addHandler(EnumFacing.NORTH, oldFirst);
        target.addHandler(EnumFacing.SOUTH, oldSecond);

        assertEquals(100, EmitUtils.sendToAcceptors(target, 100), 0);
        assertEquals(50, oldFirst.received, 0);
        assertEquals(50, oldSecond.received, 0);

        target.reset();
        RecordingEnergyAcceptor current = new RecordingEnergyAcceptor();
        target.addHandler(EnumFacing.UP, current);

        assertEquals(30, EmitUtils.sendToAcceptors(target, 30), 0);
        assertEquals(1, target.getHandlerCount());
        assertEquals(50, oldFirst.received, 0);
        assertEquals(50, oldSecond.received, 0);
        assertEquals(30, current.received, 0);
    }

    @Test
    void fluidTargetResetReplacesHandlersTypeAndNbtTemplate() {
        FluidHandlerTarget target = new FluidHandlerTarget(taggedFluid(FluidRegistry.WATER, 100, "cold"), 4);
        RecordingFluidHandler oldFirst = new RecordingFluidHandler();
        RecordingFluidHandler oldSecond = new RecordingFluidHandler();
        target.addHandler(oldFirst);
        target.addHandler(oldSecond);

        FluidStack coldWater = taggedFluid(FluidRegistry.WATER, 100, "cold");
        assertEquals(100, EmitUtils.sendToAcceptors(target, coldWater.amount, coldWater));
        assertEquals(50, oldFirst.received);
        assertEquals(50, oldSecond.received);

        FluidStack lava = taggedFluid(FluidRegistry.LAVA, 30, "hot");
        target.reset(lava);
        RecordingFluidHandler lavaHandler = new RecordingFluidHandler();
        target.addHandler(lavaHandler);
        assertEquals(30, EmitUtils.sendToAcceptors(target, lava.amount, lava));
        assertSame(FluidRegistry.LAVA, lavaHandler.lastReceived.getFluid());
        assertEquals("hot", lavaHandler.lastReceived.tag.getString("state"));

        FluidStack hotWater = taggedFluid(FluidRegistry.WATER, 20, "hot");
        target.reset(hotWater);
        RecordingFluidHandler hotWaterHandler = new RecordingFluidHandler();
        target.addHandler(hotWaterHandler);
        assertEquals(20, EmitUtils.sendToAcceptors(target, hotWater.amount, hotWater));
        assertSame(FluidRegistry.WATER, hotWaterHandler.lastReceived.getFluid());
        assertEquals("hot", hotWaterHandler.lastReceived.tag.getString("state"));
        assertEquals(50, oldFirst.received);
        assertEquals(50, oldSecond.received);
    }

    @Test
    void gasTargetResetReplacesHandlersAndGasTemplate() {
        Gas firstGas = new Gas("distribution_target_first", 0x112233);
        Gas secondGas = new Gas("distribution_target_second", 0x332211);
        GasStack firstStack = new GasStack(firstGas, 80);
        GasHandlerTarget target = new GasHandlerTarget(firstStack, 4);
        RecordingGasHandler oldFirst = new RecordingGasHandler();
        RecordingGasHandler oldSecond = new RecordingGasHandler();
        target.addHandler(EnumFacing.WEST, oldFirst);
        target.addHandler(EnumFacing.EAST, oldSecond);

        assertEquals(80, EmitUtils.sendToAcceptors(target, firstStack.amount, firstStack));
        assertEquals(40, oldFirst.received);
        assertEquals(40, oldSecond.received);

        GasStack secondStack = new GasStack(secondGas, 25);
        target.reset(secondStack);
        RecordingGasHandler current = new RecordingGasHandler();
        target.addHandler(EnumFacing.DOWN, current);
        assertEquals(25, EmitUtils.sendToAcceptors(target, secondStack.amount, secondStack));
        assertSame(secondGas, current.lastReceived.getGas());
        assertEquals(40, oldFirst.received);
        assertEquals(40, oldSecond.received);
    }

    private static FluidStack taggedFluid(Fluid fluid, int amount, String state) {
        FluidStack stack = new FluidStack(fluid, amount);
        stack.tag = new NBTTagCompound();
        stack.tag.setString("state", state);
        return stack;
    }

    private static class RecordingEnergyAcceptor extends EnergyAcceptorWrapper {

        private double received;

        @Override
        public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
            if (!simulate) {
                received += amount;
            }
            return amount;
        }

        @Override
        public boolean canReceiveEnergy(EnumFacing side) {
            return true;
        }

        @Override
        public boolean needsEnergy(EnumFacing side) {
            return true;
        }
    }

    private static class RecordingFluidHandler implements IFluidHandler {

        private int received;
        private FluidStack lastReceived;

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[0];
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (doFill) {
                received += resource.amount;
                lastReceived = resource.copy();
            }
            return resource.amount;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }

    private static class RecordingGasHandler implements IGasHandler {

        private int received;
        private GasStack lastReceived;

        @Override
        public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
            if (doTransfer) {
                received += stack.amount;
                lastReceived = stack.copy();
            }
            return stack.amount;
        }

        @Nullable
        @Override
        public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
            return null;
        }

        @Override
        public boolean canReceiveGas(EnumFacing side, Gas type) {
            return true;
        }

        @Override
        public boolean canDrawGas(EnumFacing side, Gas type) {
            return false;
        }
    }
}
