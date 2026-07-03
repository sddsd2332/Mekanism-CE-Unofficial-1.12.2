package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.Mekanism;
import mekanism.common.content.matrix.MatrixCache;
import mekanism.common.content.matrix.MatrixUpdateProtocol;
import mekanism.common.content.matrix.SynchronizedMatrixData;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;

public class TileEntityInductionCasing extends TileEntityMultiblock<SynchronizedMatrixData> implements IStrictEnergyStorage, IComputerIntegration {

    protected static final int[] CHARGE_SLOT = {0};
    protected static final int[] DISCHARGE_SLOT = {1};

    public static final String[] methods = new String[]{"getEnergy", "getMaxEnergy", "getInput", "getOutput", "getTransferCap"};

    public TileEntityInductionCasing() {
        this("InductionCasing");
    }

    public TileEntityInductionCasing(String name) {
        super(name);
    }

    @Override
    public void onUpdateServer(){
        super.onUpdateServer();
        if (structure != null && isRendering) {
            structure.tick(world);
            structure.manageInventory();
        }
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!player.isSneaking() && structure != null) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(Mekanism.instance, 49, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        if (structure != null) {
            structure.addStructureData(data);
        }
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (clientHasStructure) {
                structure.readStructureData(dataStream);
            }
        }
    }

    @Override
    protected SynchronizedMatrixData getNewStructure() {
        return new SynchronizedMatrixData(this);
    }

    @Override
    public MatrixCache getNewCache() {
        return new MatrixCache();
    }

    @Override
    protected MatrixUpdateProtocol getProtocol() {
        return new MatrixUpdateProtocol(this);
    }

    @Override
    public MultiblockManager<SynchronizedMatrixData> getManager() {
        return Mekanism.matrixManager;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.inductionMatrix");
    }

    public int getScaledEnergyLevel(int i) {
        double maxEnergy = getMaxEnergy();
        return maxEnergy <= 0 ? 0 : (int) (getEnergy() * i / maxEnergy);
    }

    @Override
    public double getEnergy() {
        //Uses post queue as that is the actual total we just haven't saved it yet
        return structure != null ? structure.getEnergyPostQueue() : 0;
    }

    @Override
    public void setEnergy(double energy) {
        if (structure != null) {
            structure.queueSetEnergy(Math.max(Math.min(energy, getMaxEnergy()), 0));
        }
    }

    public double addEnergy(double energy, boolean simulate) {
        return structure != null ? structure.queueEnergyAddition(energy, simulate) : 0;
    }

    public double removeEnergy(double energy, boolean simulate) {
        return structure != null ? structure.queueEnergyRemoval(energy, simulate) : 0;
    }

    @Override
    public double getMaxEnergy() {
        return structure != null ? structure.getStorageCap() : 0;
    }

    public double getLastInput() {
        return structure != null ? structure.getLastInput() : 0;
    }

    public double getLastOutput() {
        return structure != null ? structure.getLastOutput() : 0;
    }

    public double getTransferCap() {
        return structure != null ? structure.getTransferCap() : 0;
    }

    public int getCellCount() {
        return structure != null ? structure.getCellCount() : 0;
    }

    public int getProviderCount() {
        return structure != null ? structure.getProviderCount() : 0;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (structure == null) {
            return new Object[]{"Unformed."};
        }
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{getMaxEnergy()};
            case 2 -> new Object[]{structure.getLastInput()};
            case 3 -> new Object[]{structure.getLastOutput()};
            case 4 -> new Object[]{structure.getTransferCap()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return exposesInventoryToAutomation() ? super.getSlotsForFace(side) : InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && !exposesInventoryToAutomation()) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    protected boolean exposesInventoryToAutomation() {
        return false;
    }
}
