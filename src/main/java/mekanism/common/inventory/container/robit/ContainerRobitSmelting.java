package mekanism.common.inventory.container.robit;

import mekanism.common.entity.EntityRobit;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerRobitSmelting extends ContainerRobit {

    public ContainerRobitSmelting(InventoryPlayer inventory, EntityRobit entity) {
        super(inventory, entity);
    }

    @Override
    protected EntityRobit.ContainerType getType() {
        return EntityRobit.ContainerType.SMELTING;
    }
}
