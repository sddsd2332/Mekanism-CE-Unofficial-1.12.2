package mekanism.common.integration.lookingat.theoneprobe;

import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.api.IElement;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.lookingat.ChemicalElement;
import mekanism.common.util.TileUtils;

import javax.annotation.Nonnull;

public abstract class TOPChemicalElement extends ChemicalElement implements IElement {

    protected TOPChemicalElement(@Nonnull GasStack stored, int capacity) {
        super(stored, capacity);
    }


    @Override
    public void toBytes(ByteBuf buf) {
        TileUtils.writeGasStack2(stored, buf);
        buf.writeInt(capacity);
    }

    public static class GasElement extends TOPChemicalElement {

        public GasElement(@Nonnull GasStack stored, int capacity) {
            super(stored, capacity);
        }

        public GasElement(ByteBuf buf) {
            this(TileUtils.readGasStack2(buf), buf.readInt());
        }

        @Override
        public int getID() {
            return TOPProvider.GAS_ELEMENT_ID;
        }

    }

}
