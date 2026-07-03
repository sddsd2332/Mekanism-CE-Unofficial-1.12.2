package mekanism.common.inventory.container.robit;

import mekanism.common.entity.EntityRobit;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerRobitMain extends ContainerRobit {

    public ContainerRobitMain(InventoryPlayer inventory, EntityRobit entity) {
        super(inventory, entity);
    }

    @Override
    protected EntityRobit.ContainerType getType() {
        return EntityRobit.ContainerType.MAIN;
    }
}
