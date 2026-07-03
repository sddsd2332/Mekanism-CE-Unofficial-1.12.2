package mekanism.generators.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.base.ISustainedData;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.util.*;
import mekanism.generators.client.render.bloom.BloomRenderHeatGenerator;
import mekanism.generators.common.slot.FluidFuelInventorySlot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntityHeatGenerator extends TileEntityGenerator implements ISustainedData, IHeatTransfer, IComparatorSupport, ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getFuel", "getFuelNeeded"};
    private static final int LAVA_USAGE = 10;
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_180 = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180.0D, 0.5D, 0.5D, 0.5D)
    };
    /**
     * The FluidTank for this generator.
     */
    public BasicFluidTank lavaTank;
    public double temperature = 0;
    public double thermalEfficiency = 0.5D;
    public double invHeatCapacity = 1;
    public double heatToAbsorb = 0;
    public double producingEnergy;
    public double lastTransferLoss;
    public double lastEnvironmentLoss;
    private int currentRedstoneLevel;
    private FluidFuelInventorySlot fuelSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityHeatGenerator() {
        super("heat", "HeatGenerator", MekanismConfig.current().generators.heatGeneratorStorage.val(), MekanismConfig.current().generators.heatGeneration.val() * 2);
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        lavaTank = BasicFluidTank.input(24000, fluid -> fluid.getFluid() == FluidRegistry.LAVA, listener);
        builder.addTank(lavaTank, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        fuelSlot = builder.addSlot(FluidFuelInventorySlot.forFuel(lavaTank, this::getFuel, fuel -> new FluidStack(FluidRegistry.LAVA, fuel), listener, 17, 35),
              RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        fuelSlot.setSlotOverlay(SlotOverlay.MINUS);
        energySlot = builder.addSlot(EnergyInventorySlot.drain(this, listener, 143, 35), RelativeSide.RIGHT);
        return builder.build();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        energySlot.drainContainer();
        fuelSlot.fillOrBurn();

        double prev = getEnergy();
        transferHeatTo(getBoost());
        if (canOperate()) {
            setActive(true);
            lavaTank.extract(LAVA_USAGE, Action.EXECUTE, AutomationType.INTERNAL);
            transferHeatTo(MekanismConfig.current().generators.heatGeneration.val());
        } else {
            setActive(false);
        }

        double[] loss = simulateHeat();
        applyTemperatureChange();
        lastTransferLoss = loss[0];
        lastEnvironmentLoss = loss[1];
        producingEnergy = getEnergy() - prev;
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public boolean canOperate() {
        FluidStack extracted = lavaTank.extract(LAVA_USAGE, Action.SIMULATE, AutomationType.INTERNAL);
        return MekanismUtils.canFunction(this) && getEnergyContainer().getNeeded() > 0 && extracted != null && extracted.amount == LAVA_USAGE;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("lavaTank")) {
            lavaTank.readFromNBT(nbtTags.getCompoundTag("lavaTank"));
        }
        sanitizeLavaTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return EnergyInventorySlot.drainExtractCheck(this, itemstack);
        } else if (slotID == 0) {
            return FluidFuelInventorySlot.fillOrBurnExtractCheck(lavaTank, itemstack, this::getFuel);
        }
        return false;
    }

    public double getBoost() {
        int lavaBoost = 0;
        double netherBoost = 0D;
        for (EnumFacing side : EnumFacing.VALUES) {
            Coord4D coord = Coord4D.get(this).offset(side);
            if (isLava(coord.getPos())) {
                lavaBoost++;
            }
        }
        if (world.provider.getDimension() == -1) {
            netherBoost = MekanismConfig.current().generators.heatGenerationNether.val();
        }
        return (MekanismConfig.current().generators.heatGenerationLava.val() * lavaBoost) + netherBoost;
    }

    private boolean isLava(BlockPos pos) {
        return world.getBlockState(pos).getBlock() == Blocks.LAVA;
    }

    public int getFuel(ItemStack itemstack) {
        return TileEntityFurnace.getItemBurnTime(itemstack) / 20;
    }

    /**
     * Gets the scaled fuel level for the GUI.
     *
     * @param i - multiplier
     * @return Scaled fuel level
     */
    public int getScaledFuelLevel(int i) {
        return (lavaTank.getFluid() != null ? lavaTank.getFluid().amount : 0) * i / lavaTank.getCapacity();
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            producingEnergy = dataStream.readDouble();

            lastTransferLoss = dataStream.readDouble();
            lastEnvironmentLoss = dataStream.readDouble();

            TileUtils.readTankData(dataStream, lavaTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(producingEnergy);
        data.add(lastTransferLoss);
        data.add(lastEnvironmentLoss);
        TileUtils.addTankData(data, lavaTank);
        return data;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{electricityStored};
            case 1 -> new Object[]{output};
            case 2 -> new Object[]{BASE_MAX_ENERGY};
            case 3 -> new Object[]{BASE_MAX_ENERGY - electricityStored.get()};
            case 4 -> new Object[]{lavaTank.getFluid() != null ? lavaTank.getFluid().amount : 0};
            case 5 ->
                    new Object[]{lavaTank.getCapacity() - (lavaTank.getFluid() != null ? lavaTank.getFluid().amount : 0)};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        ItemDataUtils.setLegacyFluid(itemStack, "lavaTank", lavaTank.getFluid());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedFluidTanks(itemStack)) {
            lavaTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, "lavaTank"));
        }
        sanitizeLavaTank();
    }

    private void sanitizeLavaTank() {
        FluidStack stored = lavaTank.getFluid();
        if (stored != null && (stored.amount <= 0 || stored.getFluid() != FluidRegistry.LAVA)) {
            lavaTank.setEmpty();
        } else if (stored != null) {
            lavaTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public double getTemp() {
        return temperature;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 1;
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return canConnectHeat(side) ? 0 : 10000;
    }

    @Override
    public void transferHeatTo(double heat) {
        heatToAbsorb += heat;
    }

    @Override
    public double[] simulateHeat() {
        if (getTemp() > 0) {
            double carnotEfficiency = getTemp() / (getTemp() + IHeatTransfer.AMBIENT_TEMP);
            double heatLost = thermalEfficiency * getTemp();
            double workDone = heatLost * carnotEfficiency;
            transferHeatTo(-heatLost);
            getEnergyContainer().insert(workDone, Action.EXECUTE, AutomationType.INTERNAL);
        }
        return HeatUtils.simulate(this);
    }

    @Override
    public double applyTemperatureChange() {
        temperature += invHeatCapacity * heatToAbsorb;
        heatToAbsorb = 0;

        return temperature;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return side == EnumFacing.DOWN;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        if (canConnectHeat(side)) {
            TileEntity adj = Coord4D.get(this).offset(side).getTileEntity(world);
            if (CapabilityUtils.hasCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite())) {
                return CapabilityUtils.getCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite());
            }
        }
        return null;
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(lavaTank.getFluidAmount(), lavaTank.getCapacity());
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderHeatGenerator(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderHeatGenerator", e);
                }
            }
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }
@Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.generators.client.model.ModelHeatGenerator.class;
    }

    @Override
    public ISpecialSelectionWireframeTile.SelectionTransform[] getSelectionWireframeTransforms(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SELECTION_ROTATE_180;
    }
}
