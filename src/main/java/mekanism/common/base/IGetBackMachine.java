package mekanism.common.base;

import mekanism.common.Mekanism;
import net.minecraft.block.Block;

public interface IGetBackMachine {

    int getBlockGuiID(Block block, int metadata);

    default IGuiProvider guiProvider() {
        return Mekanism.proxy;
    }
}
