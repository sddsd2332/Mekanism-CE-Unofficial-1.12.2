package mekanism.common.inventory.container.slot;

import mekanism.common.inventory.container.sync.ISyncableData;
import net.minecraft.entity.player.EntityPlayer;

import java.util.function.Consumer;

public interface IHasExtraData {

    void addTrackers(EntityPlayer player, Consumer<ISyncableData> tracker);
}
