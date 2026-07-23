package mekanism.generators.common.tile.reactor;

import mekanism.api.Coord4D;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.util.InventoryUtils;
import mekanism.generators.common.FusionReactor;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public abstract class TileEntityReactorBlock extends TileEntityElectricBlock {

    public FusionReactor fusionReactor;

    public boolean attempted;

    public boolean changed;

    public TileEntityReactorBlock() {
        super("ReactorBlock", 0);
    }

    public TileEntityReactorBlock(String name, double maxEnergy) {
        super(name, maxEnergy);
    }

    public abstract boolean isFrame();

    public FusionReactor getReactor() {
        return fusionReactor;
    }

    public void setReactor(FusionReactor reactor) {
        if (reactor != fusionReactor) {
            changed = true;
        }
        fusionReactor = reactor;
        invalidateCapability(Capabilities.HEAT_HANDLER_CAPABILITY, null);
        invalidateCapability(Capabilities.HEAT_TRANSFER_CAPABILITY, null);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (getReactor() != null) {
            getReactor().formMultiblock(false);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (changed) {
            changed = false;
        }
        attempted = false;
    }

    @Override
    public void onUpdateServer(){
        super.onUpdateServer();
        if (ticker == 5 && !attempted && (getReactor() == null || !getReactor().isFormed())){
            updateController();
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return false;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (!(this instanceof TileEntityReactorController) && getReactor() != null) {
            getReactor().formMultiblock(true);
        }
    }

    @Override
    public void onAdded() {
        super.onAdded();
        if (!isRemote()) {
            if (getReactor() != null) {
                getReactor().formMultiblock(true);
            } else {
                updateController();
            }
        }
    }

    public void updateController() {
        if (!(this instanceof TileEntityReactorController)) {
            TileEntityReactorController found = new ControllerFinder().find();
            if (found != null && (found.getReactor() == null || !found.getReactor().isFormed())) {
                found.formMultiblock(true);
            }
        }
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    public class ControllerFinder {

        public TileEntityReactorController found;

        public Set<Coord4D> iterated = new HashSet<>();

        public void loop(Coord4D pos) {
            if (iterated.size() > 512 || found != null) {
                return;
            }

            iterated.add(pos);
            for (EnumFacing side : EnumFacing.VALUES) {
                Coord4D coord = pos.offset(side);
                if (!iterated.contains(coord) && coord.getTileEntity(world) instanceof TileEntityReactorBlock block) {
                    block.attempted = true;
                    if (coord.getTileEntity(world) instanceof TileEntityReactorController controller) {
                        found = controller;
                        return;
                    }
                    loop(coord);
                }
            }
        }

        public TileEntityReactorController find() {
            loop(Coord4D.get(TileEntityReactorBlock.this));
            return found;
        }
    }
}
