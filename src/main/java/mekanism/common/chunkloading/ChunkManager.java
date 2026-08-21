package mekanism.common.chunkloading;

import com.google.common.collect.ListMultimap;
import mekanism.common.tile.component.TileComponentChunkLoader;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.PlayerOrderedLoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

public class ChunkManager implements LoadingCallback, PlayerOrderedLoadingCallback {

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket ticket : tickets) {
            int x = ticket.getModData().getInteger("x");
            int y = ticket.getModData().getInteger("y");
            int z = ticket.getModData().getInteger("z");
            TileEntity tileEntity = world.getTileEntity(new BlockPos(x, y, z));
            if (tileEntity instanceof IChunkLoader iChunkLoader) {
                TileComponentChunkLoader chunkLoader = iChunkLoader.getChunkLoader();
                chunkLoader.refreshChunkSet();
                chunkLoader.forceChunks(ticket);
            } else {
                ForgeChunkManager.releaseTicket(ticket);
            }
        }
    }

    @Override
    public ListMultimap<String, Ticket> playerTicketsLoaded(ListMultimap<String, Ticket> tickets, World world) {
        return tickets;
    }
}
