package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.api.gas.GasStack;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismContainer;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

public class GasStackPropertyData extends PropertyData {

    @Nullable
    private final GasStack value;

    public GasStackPropertyData(short property, @Nullable GasStack value) {
        super(PropertyType.GAS_STACK, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        PacketHandler.writeNBT(buffer, value == null ? null : value.write(new NBTTagCompound()));
    }
}
