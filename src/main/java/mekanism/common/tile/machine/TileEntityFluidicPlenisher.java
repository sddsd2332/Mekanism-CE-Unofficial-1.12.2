package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.common.Mekanism;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class TileEntityFluidicPlenisher extends TileEntityElectricBlock implements IComputerIntegration, IConfigurable, ISustainedTank,
        IUpgradeTile, IRedstoneControl, ISecurityTile, IComparatorSupport {

    private static final String[] methods = new String[]{"reset"};
    private static EnumSet<EnumFacing> dirs = EnumSet.complementOf(EnumSet.of(EnumFacing.UP));
    public Set<Coord4D> activeNodes = new LinkedHashSet<>();
    public Set<Coord4D> usedNodes = new ObjectOpenHashSet<>();
    public boolean finishedCalc = false;
    public BasicFluidTank fluidTank;
    /**
     * How much energy this machine consumes per-tick.
     */
    public double BASE_ENERGY_PER_TICK = MachineType.FLUIDIC_PLENISHER.getUsage();
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
    public RedstoneControl controlType = RedstoneControl.DISABLED;
    public TileComponentUpgrade upgradeComponent = new TileComponentUpgrade(this);
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    private int currentRedstoneLevel;
    private MachineEnergyContainer energyContainer;
    private FluidInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityFluidicPlenisher() {
        super("FluidicPlenisher", MachineType.FLUIDIC_PLENISHER.getStorage());
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = FluidTankHelper.forSide(() -> facing);
        fluidTank = BasicFluidTank.input(10000, fluid -> fluid.getFluid().canBePlacedInWorld(), listener);
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
        inputSlot = builder.addSlot(FluidInventorySlot.fill(fluidTank, listener, 28, 20), RelativeSide.TOP);
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
        inputSlot.fillTank(outputSlot);

        double clientEnergyUsed = 0;
        if (MekanismUtils.canFunction(this) && fluidTank.getFluid() != null && fluidTank.getFluid().getFluid().canBePlacedInWorld() &&
              Double.compare(energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL), energyPerTick) == 0) {
            if (!finishedCalc) {
                clientEnergyUsed = energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
            }
            operatingTicks++;
            if (operatingTicks >= ticksRequired) {
                operatingTicks = 0;
                if (!finishedCalc) {
                    doPlenish();
                } else {
                    Coord4D below = Coord4D.get(this).offset(EnumFacing.DOWN);

                    if (canReplace(below, false, false) && canExtractBucket()) {
                        world.setBlockState(below.getPos(), MekanismUtils.getFlowingBlock(fluidTank.getFluid().getFluid()).getDefaultState(), 3);
                        clientEnergyUsed = energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                        fluidTank.extract(Fluid.BUCKET_VOLUME, Action.EXECUTE, AutomationType.INTERNAL);
                    }
                }
            }
        }
        usedEnergy = clientEnergyUsed > 0;

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

    private boolean canExtractBucket() {
        FluidStack extracted = fluidTank.extract(Fluid.BUCKET_VOLUME, Action.SIMULATE, AutomationType.INTERNAL);
        return extracted != null && extracted.amount == Fluid.BUCKET_VOLUME;
    }

    private void doPlenish() {
        if (usedNodes.size() >= MekanismConfig.current().general.maxPlenisherNodes.val()) {
            finishedCalc = true;
            return;
        }
        if (activeNodes.isEmpty()) {
            if (usedNodes.isEmpty()) {
                Coord4D below = Coord4D.get(this).offset(EnumFacing.DOWN);
                if (!canReplace(below, true, true)) {
                    finishedCalc = true;
                    return;
                }
                activeNodes.add(below);
            } else {
                finishedCalc = true;
                return;
            }
        }

        Set<Coord4D> toRemove = new ObjectOpenHashSet<>();
        for (Coord4D coord : activeNodes) {
            if (coord.exists(world)) {
                FluidStack fluid = fluidTank.getFluid();
                if (canReplace(coord, true, false) && fluid != null && canExtractBucket()) {
                    world.setBlockState(coord.getPos(), MekanismUtils.getFlowingBlock(fluid.getFluid()).getDefaultState(), 3);
                    fluidTank.extract(Fluid.BUCKET_VOLUME, Action.EXECUTE, AutomationType.INTERNAL);
                }
                dirs.forEach(dir -> {
                    Coord4D sideCoord = coord.offset(dir);
                    if (sideCoord.exists(world) && canReplace(sideCoord, true, true)) {
                        activeNodes.add(sideCoord);
                    }
                });
                toRemove.add(coord);
                break;
            } else {
                toRemove.add(coord);
            }
        }
        usedNodes.addAll(toRemove);
        activeNodes.removeAll(toRemove);
    }

    public boolean canReplace(Coord4D coord, boolean checkNodes, boolean isPathfinding) {
        if (checkNodes && usedNodes.contains(coord)) {
            return false;
        }
        if (coord.isAirBlock(world) || MekanismUtils.isDeadFluid(world, coord)) {
            return true;
        }
        if (MekanismUtils.isFluid(world, coord)) {
            return isPathfinding;
        }
        return coord.getBlock(world).isReplaceable(world, coord.getPos());
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            finishedCalc = dataStream.readBoolean();
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            TileUtils.readTankData(dataStream, fluidTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(finishedCalc);
        data.add(controlType.ordinal());
        TileUtils.addTankData(data, fluidTank);
        return data;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("operatingTicks", operatingTicks);
        nbtTags.setBoolean("finishedCalc", finishedCalc);
        nbtTags.setInteger("controlType", controlType.ordinal());

        NBTTagList activeList = new NBTTagList();
        activeNodes.forEach(wrapper -> {
            NBTTagCompound tagCompound = new NBTTagCompound();
            wrapper.write(tagCompound);
            activeList.appendTag(tagCompound);
        });
        if (activeList.tagCount() != 0) {
            nbtTags.setTag("activeNodes", activeList);
        }

        NBTTagList usedList = new NBTTagList();
        usedNodes.forEach(obj -> usedList.appendTag(obj.write(new NBTTagCompound())));
        if (usedList.tagCount() != 0) {
            nbtTags.setTag("usedNodes", usedList);
        }

    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        operatingTicks = nbtTags.getInteger("operatingTicks");
        finishedCalc = nbtTags.getBoolean("finishedCalc");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);

        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        sanitizeAndClampTank();

        if (nbtTags.hasKey("activeNodes")) {
            NBTTagList tagList = nbtTags.getTagList("activeNodes", NBT.TAG_COMPOUND);

            for (int i = 0; i < tagList.tagCount(); i++) {
                activeNodes.add(Coord4D.read(tagList.getCompoundTagAt(i)));
            }
        }
        if (nbtTags.hasKey("usedNodes")) {
            NBTTagList tagList = nbtTags.getTagList("usedNodes", NBT.TAG_COMPOUND);

            for (int i = 0; i < tagList.tagCount(); i++) {
                usedNodes.add(Coord4D.read(tagList.getCompoundTagAt(i)));
            }
        }
    }

    private void sanitizeAndClampTank() {
        FluidStack stored = fluidTank.getFluid();
        if (stored == null || stored.getFluid() == null || stored.amount <= 0 || !fluidTank.isFluidValid(stored)) {
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
        activeNodes.clear();
        usedNodes.clear();
        finishedCalc = false;
        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY + LangUtils.localize("tooltip.configurator.plenisherReset")));
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
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (method == 0) {
            activeNodes.clear();
            usedNodes.clear();
            finishedCalc = false;
            return new Object[]{"Plenisher calculation reset."};
        }
        throw new NoSuchMethodException();
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.SPEED) {
            ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
        }
        if (!isRecalculatingAllUpgradables() && (upgrade == Upgrade.SPEED || upgrade == Upgrade.ENERGY)) {
            recalculateEnergyAndCapacity();
        }
    }

    @Override
    protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
        super.onAllUpgradablesRecalculated(upgrades);
        if (upgrades.contains(Upgrade.SPEED) || upgrades.contains(Upgrade.ENERGY)) {
            recalculateEnergyAndCapacity();
        }
    }

    private void recalculateEnergyAndCapacity() {
        energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
        maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
        setEnergy(Math.min(getMaxEnergy(), getEnergy()));
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }
public boolean usedEnergy() {
        return usedEnergy;
    }

    public MachineEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }
}
