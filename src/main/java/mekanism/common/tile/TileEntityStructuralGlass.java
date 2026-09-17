package mekanism.common.tile;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.common.multiblock.IMultiblock;
import mekanism.common.multiblock.IStructuralMultiblock;
import mekanism.common.multiblock.SynchronizedData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;

import java.util.Set;

public class TileEntityStructuralGlass extends TileEntity implements IStructuralMultiblock {

    public Coord4D master;
    private SynchronizedData<?> owner;

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (master != null) {
            TileEntity masterTile = master.getTileEntity(world);
            if (masterTile instanceof IMultiblock<?> multiblock) {
                return multiblock.onActivate(player, hand, stack);
            }
            setController(null);
        }
        return false;
    }

    @Override
    public void doUpdate() {
        if (master != null) {
            TileEntity masterTile = master.getTileEntity(world);
            if (masterTile instanceof IMultiblock<?> multiblock) {
                multiblock.doUpdate();
            } else {
                setController(null);
            }
        } else {
            IMultiblock<?> multiblock = new ControllerFinder().find();
            if (multiblock != null) {
                multiblock.doUpdate();
            }
        }
    }

    @Override
    public boolean canInterface(TileEntity controller) {
        return true;
    }

    @Override
    public void setController(Coord4D coord) {
        master = coord;
        owner = null;
    }

    public void setController(Coord4D coord, SynchronizedData<?> structure) {
        master = coord;
        owner = structure;
    }

    public void clearController(SynchronizedData<?> structure) {
        if (owner == structure) setController(null);
    }

    public class ControllerFinder {

        public IMultiblock<?> found;

        public Set<Coord4D> iterated = new ObjectOpenHashSet<>();

        public void loop(Coord4D pos) {
            if (iterated.size() > 2048 || found != null) {
                return;
            }
            iterated.add(pos);
            for (EnumFacing side : EnumFacing.VALUES) {
                Coord4D coord = pos.offset(side);
                TileEntity tile = coord.getTileEntity(world);
                if (!iterated.contains(coord)) {
                    if (tile instanceof IMultiblock<?> multiblock) {
                        found = multiblock;
                        return;
                    } else if (tile instanceof IStructuralMultiblock) {
                        loop(coord);
                    }
                }
            }
        }

        public IMultiblock<?> find() {
            loop(Coord4D.get(TileEntityStructuralGlass.this));
            return found;
        }
    }
}
