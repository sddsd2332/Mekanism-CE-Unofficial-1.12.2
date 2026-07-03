package mekanism.common.lib.inventory;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

public class TileTransitRequest extends HandlerTransitRequest {

    private final TileEntity tile;
    private EnumFacing side;

    public TileTransitRequest(TileEntity tile, EnumFacing side) {
        super(null);
        this.tile = tile;
        this.side = side;
    }

    protected EnumFacing getSide() {
        return side;
    }

    public void setSide(EnumFacing side) {
        this.side = side;
    }

    @Override
    protected IItemHandler getHandler() {
        return mekanism.common.util.InventoryUtils.getItemHandler(tile, side);
    }
}
