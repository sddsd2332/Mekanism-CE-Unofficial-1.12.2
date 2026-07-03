package mekanism.common.base;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.inventory.container.MekanismContainer;
import net.minecraft.nbt.NBTTagCompound;

public interface ITileComponent {

    void tick();

    void read(NBTTagCompound nbtTags);

    void read(ByteBuf dataStream);

    void write(NBTTagCompound nbtTags);

    void write(TileNetworkList data);

    void invalidate();

    default void trackForMainContainer(MekanismContainer container) {
    }
}
