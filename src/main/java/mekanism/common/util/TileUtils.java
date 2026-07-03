package mekanism.common.util;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.common.PacketHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

//TODO: Move this and factor out the parts into proper classes. This is mainly just temp to make organization not as needed
public class TileUtils {

    // N.B. All the tank I/O functions rely on the fact that an empty NBT Compound is a singular
    // byte and that the Gas/Fluid Stacks initialize to null if they are de-serialized from an
    // empty tag.
    private static final NBTTagCompound EMPTY_TAG_COMPOUND = new NBTTagCompound();

    public static void addTankData(TileNetworkList data, GasTank tank) {
        addGasStack(data, tank.getGas());
    }

    public static void addTankData(TileNetworkList data, IExtendedFluidTank tank) {
        addFluidStack(data, tank.getFluid());
    }

    public static void addFluidStack(TileNetworkList data, FluidStack stack) {
        if (stack != null) {
            data.add(stack.writeToNBT(new NBTTagCompound()));
        } else {
            data.add(EMPTY_TAG_COMPOUND);
        }
    }

    public static void addGasStack(TileNetworkList data, GasStack stack) {
        if (stack != null) {
            data.add(stack.write(new NBTTagCompound()));
        } else {
            data.add(EMPTY_TAG_COMPOUND);
        }
    }

    public static void readTankData(ByteBuf dataStream, GasTank tank) {
        tank.setGas(GasStack.readFromNBT(PacketHandler.readNBT(dataStream)));
    }

    public static void readTankData(ByteBuf dataStream, IExtendedFluidTank tank) {
        tank.setStack(readFluidStack(dataStream));
    }

    public static FluidStack readFluidStack(ByteBuf dataStream) {
        return FluidStack.loadFluidStackFromNBT(PacketHandler.readNBT(dataStream));
    }

    public static FluidStack readFluidStack2(ByteBuf dataStream) {
        return !dataStream.readBoolean() ? null : FluidStack.loadFluidStackFromNBT(PacketHandler.readNBT(dataStream));
    }

    public static void writeFluidStack(FluidStack stack, ByteBuf dataStream) {
        if (stack == null) {
            dataStream.writeBoolean(false);
        } else {
            dataStream.writeBoolean(true);
            PacketHandler.writeNBT(dataStream, stack.writeToNBT(new NBTTagCompound()));
        }
    }

    public static void writeGasStack2(GasStack stack, ByteBuf dataStream) {
        if (stack == null) {
            dataStream.writeBoolean(false);
        }else {
            dataStream.writeBoolean(true);
            PacketHandler.writeNBT(dataStream,stack.write(new NBTTagCompound()));
        }
    }

    public static GasStack readGasStack2(ByteBuf dataStream) {
        return  !dataStream.readBoolean() ? null :GasStack.readFromNBT(PacketHandler.readNBT(dataStream));
    }

    public static GasStack readGasStack(ByteBuf dataStream) {
        return GasStack.readFromNBT(PacketHandler.readNBT(dataStream));
    }
}
