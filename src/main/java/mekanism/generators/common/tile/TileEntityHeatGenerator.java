package mekanism.generators.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.heat.HeatCapacitorWrapper;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.Mekanism;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.base.ISustainedData;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.heat.ProxiedHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.util.*;
import mekanism.generators.client.render.bloom.BloomRenderHeatGenerator;
import mekanism.generators.common.slot.FluidFuelInventorySlot;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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

public class TileEntityHeatGenerator extends TileEntityGenerator implements ISustainedData, IComparatorSupport, ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getFuel", "getFuelNeeded"};
    private static final double HEAT_CAPACITY = 10;
    private static final double INVERSE_CONDUCTION_COEFFICIENT = 5;
    private static final double INVERSE_INSULATION_COEFFICIENT = 100;
    private static final double THERMAL_EFFICIENCY = 0.5;
    private static final double DEFAULT_ACTIVE_HEAT = 100;
    private static final double DEFAULT_LAVA_HEAT = 7;
    private static final double DEFAULT_NETHER_HEAT = 10;
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_180 = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180.0D, 0.5D, 0.5D, 0.5D)
    };
    /**
     * The FluidTank for this generator.
     */
    public BasicFluidTank lavaTank;
    public double producingEnergy;
    public double lastTransferLoss;
    public double lastEnvironmentLoss;
    private int currentRedstoneLevel;
    private FluidFuelInventorySlot fuelSlot;
    private EnergyInventorySlot energySlot;
    private BasicHeatCapacitor heatCapacitor;
    private IHeatCapacitor bottomHeatCapacitor;

    public TileEntityHeatGenerator() {
        super("heat", "HeatGenerator", getInitialStorage(), getActiveHeat());
        initializeInventorySlots();
    }

    private static double getActiveHeat() {
        return finiteConfig(MekanismConfig.current().generators.heatGeneration.val(), DEFAULT_ACTIVE_HEAT);
    }

    private static double getLavaHeat() {
        return finiteConfig(MekanismConfig.current().generators.heatGenerationLava.val(), DEFAULT_LAVA_HEAT);
    }

    private static double getNetherHeat() {
        return finiteConfig(MekanismConfig.current().generators.heatGenerationNether.val(), DEFAULT_NETHER_HEAT);
    }

    private static double getInitialStorage() {
        double configured = MekanismConfig.current().generators.heatGeneratorStorage.val();
        return HeatAPI.isFinite(configured) && configured >= 0 ? Math.min(HeatAPI.MAX_HEAT, configured) : HeatAPI.multiplyHeat(getActiveHeat(), 2);
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        lavaTank = VariableCapacityFluidTank.input(TileEntityHeatGenerator::getHeatTankCapacity,
              fluid -> fluid.getFluid() == FluidRegistry.LAVA, listener);
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
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        heatCapacitor = BasicHeatCapacitor.create(HEAT_CAPACITY, INVERSE_CONDUCTION_COEFFICIENT, INVERSE_INSULATION_COEFFICIENT,
              () -> getAmbientTemperature(null), listener);
        bottomHeatCapacitor = new DefaultInsulationCapacitor(heatCapacitor);
        return ProxiedHeatCapacitorHolder.create(side -> true, side -> true, side -> {
            if (side == null) {
                return java.util.Collections.singletonList(heatCapacitor);
            }
            return java.util.Collections.singletonList(side == EnumFacing.DOWN ? bottomHeatCapacitor : heatCapacitor);
        });
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        energySlot.drainContainer();
        fuelSlot.fillOrBurn();

        double prev = getEnergy();
        heatCapacitor.handleHeat(getBoost());
        if (canOperate()) {
            setActive(true);
            lavaTank.extract(getHeatGenerationFluidRate(), Action.EXECUTE, AutomationType.INTERNAL);
            double activeHeat = getActiveHeat();
            heatCapacitor.handleHeat(activeHeat);
        } else {
            setActive(false);
        }

        HeatTransfer loss = simulateGeneratorHeat();
        lastTransferLoss = sanitizeLoss(loss.adjacentTransfer());
        lastEnvironmentLoss = sanitizeLoss(loss.environmentTransfer());
        double produced = getEnergy() - prev;
        producingEnergy = HeatAPI.isFinite(produced) ? Math.max(0, produced) : 0;
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public boolean canOperate() {
        int fluidRate = getHeatGenerationFluidRate();
        FluidStack extracted = lavaTank.extract(fluidRate, Action.SIMULATE, AutomationType.INTERNAL);
        return MekanismUtils.canFunction(this) && getEnergyContainer().getNeeded() > 0 && extracted != null && extracted.amount == fluidRate;
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
        if (world == null) {
            return 0;
        }
        int lavaBoost = 0;
        double netherBoost = 0D;
        for (EnumFacing side : EnumFacing.VALUES) {
            Coord4D coord = Coord4D.get(this).offset(side);
            if (isLava(coord.getPos())) {
                lavaBoost++;
            }
        }
        if (world.provider.getDimension() == -1) {
            netherBoost = getNetherHeat();
        }
        double lavaHeat = getLavaHeat();
        double boost = saturatingMultiply(lavaHeat, lavaBoost);
        return netherBoost >= HeatAPI.MAX_HEAT - boost ? HeatAPI.MAX_HEAT : boost + netherBoost;
    }

    private boolean isLava(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return block == Blocks.LAVA || block == Blocks.FLOWING_LAVA;
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
        int capacity = lavaTank.getCapacity();
        return capacity <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE,
              (long) Math.max(0, lavaTank.getFluidAmount()) * Math.max(0, i) / capacity);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            double syncedProduction = dataStream.readDouble();
            producingEnergy = HeatAPI.isFinite(syncedProduction) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, syncedProduction)) : 0;

            heatCapacitor.setHeatCapacityFromPacket(dataStream.readDouble());
            heatCapacitor.setHeat(dataStream.readDouble());

            lastTransferLoss = sanitizeLoss(dataStream.readDouble());
            lastEnvironmentLoss = sanitizeLoss(dataStream.readDouble());

            TileUtils.readTankData(dataStream, lavaTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(HeatAPI.isFinite(producingEnergy) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, producingEnergy)) : 0);
        data.add(heatCapacitor.getHeatCapacity());
        data.add(heatCapacitor.getHeat());
        data.add(sanitizeLoss(lastTransferLoss));
        data.add(sanitizeLoss(lastEnvironmentLoss));
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

    public double getTemp() {
        return heatCapacitor.getTemperature();
    }

    private HeatTransfer simulateGeneratorHeat() {
        double ambient = HeatAPI.sanitizeTemperature(getAmbientTemperature(null));
        double temperature = HeatAPI.sanitizeTemperature(getTemp());
        double maximum = Math.max(ambient, temperature);
        double carnotEfficiency = maximum <= 0 ? 0 : 1 - Math.min(ambient, temperature) / maximum;
        carnotEfficiency = HeatAPI.isFinite(carnotEfficiency) ? Math.max(0, Math.min(1, carnotEfficiency)) : 0;
        double heatLost = THERMAL_EFFICIENCY * (temperature - ambient);
        heatCapacitor.handleHeat(-heatLost);
        double energyFromHeat = saturatingMultiply(Math.abs(heatLost), carnotEfficiency);
        if (HeatAPI.isFinite(energyFromHeat) && energyFromHeat > 0) {
            getEnergyContainer().insert(Math.min(energyFromHeat, getMaxHeatConversion()), Action.EXECUTE, AutomationType.INTERNAL);
        }
        return simulate();
    }

    private static double getMaxHeatConversion() {
        double active = getActiveHeat();
        double lava = saturatingMultiply(getLavaHeat(), EnumFacing.VALUES.length);
        double nether = getNetherHeat();
        double max = active >= HeatAPI.MAX_HEAT - lava ? HeatAPI.MAX_HEAT : active + lava;
        return nether >= HeatAPI.MAX_HEAT - max ? HeatAPI.MAX_HEAT : max + nether;
    }

    static int getHeatTankCapacity() {
        return Math.max(1, MekanismConfig.current().generators.heatTankCapacity.val());
    }

    static int getHeatGenerationFluidRate() {
        return Math.max(1, Math.min(getHeatTankCapacity(), MekanismConfig.current().generators.heatGenerationFluidRate.val()));
    }

    @Override
    public double extractEnergy(int container, double amount, EnumFacing side, Action action) {
        return super.extractEnergy(container, finiteAmount(amount), side, action);
    }

    @Override
    public double extractEnergy(double amount, EnumFacing side, Action action) {
        return super.extractEnergy(finiteAmount(amount), side, action);
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return super.pullEnergy(side, finiteAmount(amount), simulate);
    }

    @Override
    public double getMaxOutput() {
        return getMaxHeatConversion();
    }

    @Override
    public IHeatHandler getAdjacent(EnumFacing side) {
        return side == EnumFacing.DOWN ? super.getAdjacent(side) : null;
    }

    private static double finiteNonNegative(double value) {
        return HeatAPI.isFinite(value) && value >= 0 ? Math.min(HeatAPI.MAX_HEAT, value) : 0;
    }

    private static double finiteConfig(double value, double fallback) {
        return HeatAPI.isFinite(value) && value >= 0 ? Math.min(HeatAPI.MAX_HEAT, value) : fallback;
    }

    private static double sanitizeLoss(double loss) {
        return HeatAPI.isFinite(loss) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, loss)) : 0;
    }

    private static double saturatingMultiply(double first, double second) {
        if (!HeatAPI.isFinite(first) || !HeatAPI.isFinite(second) || first < 0 || second < 0 || first == 0 || second == 0) {
            return 0;
        }
        return first >= HeatAPI.MAX_HEAT / second ? HeatAPI.MAX_HEAT : first * second;
    }

    private static double finiteAmount(double amount) {
        return HeatAPI.isFinite(amount) && amount > 0 ? Math.min(amount, getMaxHeatConversion()) : 0;
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

    private static class DefaultInsulationCapacitor extends HeatCapacitorWrapper {

        private DefaultInsulationCapacitor(IHeatCapacitor internal) {
            super(internal);
        }

        @Override
        public double getInverseInsulation() {
            return HeatAPI.DEFAULT_INVERSE_INSULATION;
        }
    }
}
