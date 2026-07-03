package mekanism.common.tile.interfaces;

import mekanism.common.content.filter.FilterManager;
import mekanism.common.content.filter.IFilter;
import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;

public interface ITileFilterHolder<FILTER extends IFilter> {

    FilterManager<FILTER> getFilterManager();

    default void sendFilterUpdate(@Nullable EntityPlayerMP player) {
    }
}
