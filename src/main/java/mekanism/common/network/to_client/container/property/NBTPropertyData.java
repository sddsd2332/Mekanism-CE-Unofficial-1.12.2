package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismContainer;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;

public final class NBTPropertyData extends PropertyData {

    private final NBTTagCompound value;

    public NBTPropertyData(short property, @Nonnull NBTTagCompound value) {
        super(PropertyType.NBT, property);
        this.value = Objects.requireNonNull(value, "Container NBT property cannot be null").copy();
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value.copy());
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        PacketHandler.writeNBT(buffer, value);
    }
}
