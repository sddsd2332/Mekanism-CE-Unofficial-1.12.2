package mekanism.common.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface IGuiProvider {

    /**
     * Validates a client-requested tile GUI before the provider performs any type casts.
     */
    default boolean isValidServerGui(int ID, TileEntity tile) {
        return false;
    }

    /**
     * Get the container for a GUI. Common.
     *
     * @param ID     - gui ID
     * @param player - player that opened the GUI
     * @param world  - world the GUI was opened in
     * @param pos    - gui's position
     * @return the Container of the GUI
     */
    Container getServerGui(int ID, EntityPlayer player, World world, BlockPos pos);

    /**
     * Get the actual interface for a GUI. Client-only.
     *
     * @param ID     - gui ID
     * @param player - player that opened the GUI
     * @param world  - world the GUI was opened in
     * @param pos    - gui's position
     * @return the GuiScreen of the GUI
     */
    Object getClientGui(int ID, EntityPlayer player, World world, BlockPos pos);
}
