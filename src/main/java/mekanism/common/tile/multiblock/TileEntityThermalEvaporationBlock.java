package mekanism.common.tile.multiblock;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;

public class TileEntityThermalEvaporationBlock extends TileEntityContainerBlock implements IComputerIntegration {

    private static final String[] methods = new String[]{"getTemperature", "getHeight", "isFormed", "getInput", "getOutput"};
    public Coord4D master;
    public boolean attempted;

    public TileEntityThermalEvaporationBlock() {
        super("ThermalEvaporationBlock");
        initializeInventorySlots();
    }

    public TileEntityThermalEvaporationBlock(String fullName) {
        super(fullName);
    }


    @Override
    public void onUpdate() {
        super.onUpdate();
        attempted = false;
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (ticker == 5 && !attempted && master == null) {
            updateController();
        }

    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public void addToStructure(Coord4D controller) {
        master = controller;
    }

    public void controllerGone() {
        master = null;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (master != null) {
            TileEntityThermalEvaporationController tile = getController();
            if (tile != null) {
                tile.refresh();
            }
        }
    }

    @Override
    public void onNeighborChange(Block block) {
        super.onNeighborChange(block);
        if (!isRemote()) {
            TileEntityThermalEvaporationController tile = getController();
            if (tile != null) {
                tile.refresh();
            } else {
                updateController();
            }
        }
    }

    public void updateController() {
        if (!(this instanceof TileEntityThermalEvaporationController)) {
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos checkPos = pos.offset(side);
                TileEntity check = MekanismUtils.getTileEntity(world, checkPos);
                if (check instanceof TileEntityThermalEvaporationBlock) {
                    if (check instanceof TileEntityThermalEvaporationController controller) {
                        controller.refresh();
                        return;
                    }
                }
            }
            TileEntityThermalEvaporationController found = new ControllerFinder().find();
            if (found != null) {
                found.refresh();
            }
        }
    }

    public TileEntityThermalEvaporationController getController() {
        if (master != null) {
            TileEntity tile = master.getTileEntity(world);
            if (tile instanceof TileEntityThermalEvaporationController controller) {
                return controller;
            }
        }
        return null;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] args) throws NoSuchMethodException {
        TileEntityThermalEvaporationController controller = getController();
        if (controller == null) {
            return new Object[]{"Unformed."};
        }
        return switch (method) {
            case 0 -> new Object[]{controller.temperature};
            case 1 -> new Object[]{controller.height};
            case 2 -> new Object[]{controller.structured};
            case 3 -> new Object[]{controller.inputTank.getFluidAmount()};
            case 4 -> new Object[]{controller.outputTank.getFluidAmount()};
            default -> throw new NoSuchMethodException();
        };
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

        public TileEntityThermalEvaporationController found;

        public Set<BlockPos> iterated = new ObjectOpenHashSet<>();

        private Deque<BlockPos> checkQueue = new LinkedList<>();

        public void loop(BlockPos startPos) {
            checkQueue.add(startPos);

            while (checkQueue.peek() != null) {
                BlockPos checkPos = checkQueue.pop();
                if (iterated.contains(checkPos)) {
                    continue;
                }
                iterated.add(checkPos);

                TileEntity te = MekanismUtils.getTileEntity(world, checkPos);
                if (te instanceof TileEntityThermalEvaporationController controller) {
                    found = controller;
                    return;
                }

                if (te instanceof TileEntityThermalEvaporationBlock block) {
                    block.attempted = true;
                    for (EnumFacing side : EnumFacing.VALUES) {
                        BlockPos coord = checkPos.offset(side);
                        if (!iterated.contains(coord)) {
                            checkQueue.addLast(coord);
                        }
                    }
                }
            }
        }

        public TileEntityThermalEvaporationController find() {
            loop(TileEntityThermalEvaporationBlock.this.pos);
            return found;
        }
    }
}
