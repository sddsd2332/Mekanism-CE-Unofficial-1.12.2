package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismContainer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class ItemStackPropertyData extends PropertyData {

    @Nonnull
    private final ItemStack value;

    public ItemStackPropertyData(short property, @Nonnull ItemStack value) {
        super(PropertyType.ITEM_STACK, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        PacketHandler.writeStack(buffer, value);
    }
}
