package mekanism.common.integration.lookingat.theoneprobe;

import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.api.IElement;
import mekanism.common.integration.lookingat.FluidElement;
import mekanism.common.util.TileUtils;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

public class TOPFluidElement extends FluidElement implements IElement {

    public TOPFluidElement(@Nonnull FluidStack stored, int capacity) {
        super(stored, capacity);
    }

    public TOPFluidElement(ByteBuf buf) {
        this(TileUtils.readFluidStack2(buf), buf.readInt());
    }


    @Override
    public void toBytes(ByteBuf byteBuf) {
        TileUtils.writeFluidStack(stored, byteBuf);
        byteBuf.writeInt(capacity);
    }

    @Override
    public int getID() {
        return TOPProvider.FLUID_ELEMENT_ID;
    }
}
