package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class FluidStackPropertyData extends PropertyData {

    @Nullable
    private final FluidStack value;

    public FluidStackPropertyData(short property, @Nullable FluidStack value) {
        super(PropertyType.FLUID_STACK, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        PacketHandler.writeNBT(buffer, value == null ? null : value.writeToNBT(new NBTTagCompound()));
    }
}
