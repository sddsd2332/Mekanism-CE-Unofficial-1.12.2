package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TileEntityElectricPump extends TileEntityElectricBlock implements ISustainedTank, IConfigurable, IRedstoneControl, IUpgradeTile,
        ITankManager, IComputerIntegration, ISecurityTile, IComparatorSupport {

    private static final String[] methods = new String[]{"reset"};
    /**
     * This pump's tank
     */
    public BasicFluidTank fluidTank;
    /**
     * The type of fluid this pump is pumping
     */
    public Fluid activeType;
    /**
     * How much energy this machine consumes per-tick.
     */
    public double BASE_ENERGY_PER_TICK = MachineType.ELECTRIC_PUMP.getUsage();
    public double energyPerTick = BASE_ENERGY_PER_TICK;
    /**
     * How many ticks it takes to run an operation.
     */
    public int BASE_TICKS_REQUIRED = 20;
    public int ticksRequired = BASE_TICKS_REQUIRED;
    /**
     * How many ticks this machine has been operating for.
     */
    public int operatingTicks;
    private boolean usedEnergy;
    /**
     * The nodes that have full sources near them or in them
     */
    public Set<Coord4D> recurringNodes = new ObjectOpenHashSet<>();
    /**
     * This machine's current RedstoneControl type.
     */
    public RedstoneControl controlType = RedstoneControl.DISABLED;
    public TileComponentUpgrade upgradeComponent = new TileComponentUpgrade(this);
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    private int currentRedstoneLevel;
    private MachineEnergyContainer energyContainer;
    private FluidInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityElectricPump() {
        super("ElectricPump", MachineType.ELECTRIC_PUMP.getStorage());
        initializeInventorySlots();
        setSupportedUpgrade(Upgrade.FILTER);
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = FluidTankHelper.forSide(() -> facing);
        fluidTank = BasicFluidTank.output(10000, listener);
        builder.addTank(fluidTank, RelativeSide.TOP);
        return builder.build();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = createEnergyContainerHelper();
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this::getEnergy, this::setEnergy, this::getMaxEnergy, () -> energyPerTick, listener),
              RelativeSide.BACK);
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(FluidInventorySlot.drain(fluidTank, listener, 28, 20), RelativeSide.TOP);
        inputSlot.setSlotOverlay(SlotOverlay.INPUT);
        outputSlot = builder.addSlot(OutputInventorySlot.at(listener, 28, 51), RelativeSide.BOTTOM);
        outputSlot.setSlotOverlay(SlotOverlay.OUTPUT);
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(energyContainer, this::getWorld, listener, 143, 35), RelativeSide.BACK);
        return builder.build();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        usedEnergy = false;
        energySlot.fillContainerOrConvert();
        inputSlot.drainTank(outputSlot);

        double clientEnergyUsed = 0;
        if (MekanismUtils.canFunction(this) && (fluidTank.getFluid() == null || estimateIncrementAmount() <= fluidTank.getNeeded()) &&
              Double.compare(energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL), energyPerTick) == 0) {
            if (activeType != null) {
                clientEnergyUsed = energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
            }
            operatingTicks++;
            if (operatingTicks >= ticksRequired) {
                operatingTicks = 0;
                if (suck()) {
                    if (clientEnergyUsed <= 0) {
                        clientEnergyUsed = energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                    }
                } else {
                    reset();
                }
            }
        }
        usedEnergy = clientEnergyUsed > 0;

        if (fluidTank.getFluid() != null) {
            FluidUtils.emit(Collections.singleton(EnumFacing.UP), fluidTank, this,
                  Math.min(256 * (getInstalledUpgrades(Upgrade.SPEED) + 1), fluidTank.getFluidAmount()));
        }
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }

    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public boolean hasFilter() {
        return isUpgradeInstalled(Upgrade.FILTER);
    }

    private boolean suck() {
        List<Coord4D> tempPumpList = Arrays.asList(recurringNodes.toArray(new Coord4D[0]));
        Collections.shuffle(tempPumpList);

            //First see if there are any fluid blocks touching the pump - if so, sucks and adds the location to the recurring list
        for (EnumFacing orientation : EnumFacing.VALUES) {
            Coord4D wrapper = Coord4D.get(this).offset(orientation);
            FluidStack fluid = MekanismUtils.getFluid(world, wrapper, hasFilter());
            if (validFluid(fluid)) {
                suck(fluid, wrapper, true);
                return true;
            }
        }

        //Finally, go over the recurring list of nodes and see if there is a fluid block available to suck - if not, will iterate around the recurring block, attempt to suck,
        //and then add the adjacent block to the recurring list
        for (Coord4D wrapper : tempPumpList) {
            FluidStack fluid = MekanismUtils.getFluid(world, wrapper, hasFilter());
            if (validFluid(fluid)) {
                suck(fluid, wrapper, false);
                return true;
            }

            //Add all the blocks surrounding this recurring node to the recurring node list
            for (EnumFacing orientation : EnumFacing.VALUES) {
                Coord4D side = wrapper.offset(orientation);
                if (Coord4D.get(this).distanceTo(side) <= MekanismConfig.current().general.maxPumpRange.val()) {
                    fluid = MekanismUtils.getFluid(world, side, hasFilter());
                    if (validFluid(fluid)) {
                        suck(fluid, side, true);
                        return true;
                    }
                }
            }
            recurringNodes.remove(wrapper);
        }
        return false;
    }

    private boolean validFluid(@Nullable FluidStack fluid) {
        return fluid != null && (activeType == null || fluid.getFluid() == activeType) &&
              (fluidTank.getFluid() == null || fluidTank.isFluidEqual(fluid)) && fluid.amount <= fluidTank.getNeeded();
    }

    private void suck(FluidStack fluid, Coord4D coord, boolean addRecurring) {
        activeType = fluid.getFluid();
        if (addRecurring) {
            recurringNodes.add(coord);
        }
        fluidTank.insert(fluid, Action.EXECUTE, AutomationType.INTERNAL);
        if (shouldTake(fluid, coord)) {
            world.setBlockToAir(coord.getPos());
        }
    }

    public void reset() {
        activeType = null;
        recurringNodes.clear();
    }

    private boolean shouldTake(FluidStack fluid, Coord4D coord) {
        if (fluid.getFluid() == FluidRegistry.WATER || fluid.getFluid() == MekanismFluids.HeavyWater) {
            return MekanismConfig.current().general.pumpWaterSources.val();
        }
        return true;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, fluidTank);
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            activeType = dataStream.readBoolean() ? FluidRegistry.getFluid(PacketHandler.readString(dataStream)) : null;
            usedEnergy = dataStream.readBoolean();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, fluidTank);
        data.add(controlType.ordinal());
        data.add(activeType != null);
        if (activeType != null) {
            data.add(FluidRegistry.getFluidName(activeType));
        }
        data.add(usedEnergy);
        return data;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("operatingTicks", operatingTicks);

        if (activeType != null) {
            nbtTags.setString("activeType", FluidRegistry.getFluidName(activeType));
        }

        nbtTags.setInteger("controlType", controlType.ordinal());

        NBTTagList recurringList = new NBTTagList();
        recurringNodes.forEach(wrapper -> {
            NBTTagCompound tagCompound = new NBTTagCompound();
            wrapper.write(tagCompound);
            recurringList.appendTag(tagCompound);
        });
        if (recurringList.tagCount() != 0) {
            nbtTags.setTag("recurringNodes", recurringList);
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        operatingTicks = nbtTags.getInteger("operatingTicks");
        if (nbtTags.hasKey("activeType")) {
            activeType = FluidRegistry.getFluid(nbtTags.getString("activeType"));
        }
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        sanitizeAndClampTank();
        if (nbtTags.hasKey("controlType")) {
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        }
        if (nbtTags.hasKey("recurringNodes")) {
            NBTTagList tagList = nbtTags.getTagList("recurringNodes", NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                recurringNodes.add(Coord4D.read(tagList.getCompoundTagAt(i)));
            }
        }
    }

    private void sanitizeAndClampTank() {
        FluidStack stored = fluidTank.getFluid();
        if (stored == null || stored.getFluid() == null || stored.amount <= 0) {
            fluidTank.setEmpty();
        } else {
            fluidTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return facing.getOpposite() == side;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public void setFluidStack(FluidStack fluidStack, Object... data) {
        fluidTank.setFluid(fluidStack);
        sanitizeAndClampTank();
    }

    @Override
    public FluidStack getFluidStack(Object... data) {
        return fluidTank.getFluid();
    }

    @Override
    public boolean hasTank(Object... data) {
        return true;
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        reset();
        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY + LangUtils.localize("tooltip.configurator.pumpReset")));
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.CONFIGURABLE_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return Capabilities.CONFIGURABLE_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean canPulse() {
        return true;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{fluidTank};
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (method == 0) {
            reset();
            return new Object[]{"Pump calculation reset."};
        }
        throw new NoSuchMethodException();
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    public int estimateIncrementAmount() {
        Fluid fluid = fluidTank.getFluid() != null ? fluidTank.getFluid().getFluid() : activeType;
        return fluid == MekanismFluids.HeavyWater ? 10 : Fluid.BUCKET_VOLUME;
    }

    public MachineEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    public boolean usedEnergy() {
        return usedEnergy;
    }

    @Nullable
    public FluidStack getActiveType() {
        return activeType == null ? null : new FluidStack(activeType, estimateIncrementAmount());
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.SPEED) {
            ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
        }
        if (upgrade == Upgrade.SPEED || upgrade == Upgrade.ENERGY) {
            energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
            maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
            setEnergy(Math.min(getMaxEnergy(), getEnergy()));
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }
@Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }
}
