package mekanism.api.gear;

//TODO

import net.minecraft.item.ItemStack;

/**
 * 磁吸单元，用于是否一直在身上，用于Mixin，待拆分于扩展
 */
public interface Magnetic {

    boolean isMagnetic(ItemStack stack);


}
