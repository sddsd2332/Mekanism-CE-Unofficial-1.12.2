package mekanism.client.Utils;

import mekanism.api.util.time.Timeticks;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.model.animation.FastTESR;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class RenderFastTileEntityTime<T extends TileEntity> extends FastTESR<T> {

    private Timeticks time = new Timeticks(20, 20, false);

    public double getTime() {
        return time.getValue() / 20F;
    }


}
