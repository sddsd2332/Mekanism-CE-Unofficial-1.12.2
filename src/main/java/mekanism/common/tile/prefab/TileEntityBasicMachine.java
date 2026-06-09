package mekanism.common.tile.prefab;

import mekanism.api.IConfigCardAccess;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.IElectricMachine;
import mekanism.common.base.IMachineSlotTip;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 基本类型机器方块
 *
 * @param <INPUT>  用于机器的输入
 * @param <OUTPUT> 用于机器的输出
 * @param <RECIPE> 用于机器的配方
 */

public abstract class TileEntityBasicMachine<INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>> extends
        TileEntityOperationalMachine implements IElectricMachine<INPUT, OUTPUT, RECIPE>, IComputerIntegration, ISideConfiguration, IConfigCardAccess, IMachineSlotTip {

    protected int successCounter = 0;
    protected boolean inventoryChanged = false;

    public RECIPE cachedRecipe = null;

    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;

    /**
     * The foundation of all machines - a simple tile entity with a facing, active state, initialized state, sound effect, and animated texture.
     *
     * @param soundPath         - location of the sound effect
     * @param type              - the type of this machine
     * @param baseTicksRequired - how many ticks it takes to run a cycle
     */
    public TileEntityBasicMachine(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super("machine." + soundPath, type, upgradeSlot, baseTicksRequired);
    }

    public TileEntityBasicMachine(String soundPath, String name, double energyStorge, double energUsage, int upgradeSlot, int baseTicksRequired) {
        super("machine." + soundPath, name, energyStorge, energUsage, upgradeSlot, baseTicksRequired);
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return configComponent.hasSideForData(TransmissionType.ENERGY, facing, 1, side);
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return configComponent.getOutput(TransmissionType.ITEM, side, facing).availableSlots;
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public EnumFacing getOrientation() {
        return facing;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        }
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    protected void AutomaticallyExtractItems(int dataIndex, int inputSlotID) {
        if (getWorld().isRemote || !canWork(5, 60)) {
            return;
        }
        InputItems(dataIndex, inputSlotID);
    }

    protected void BetterEjectingItem(int dataIndex, int outputSlotID) {
        if (getWorld().isRemote || !canWork(5, 60)) {
            return;
        }
        outputItems(dataIndex, outputSlotID);
    }

    private void outputItems(int dataIndex, int outputSlotID) {
        if (!configComponent.isEjecting(TransmissionType.ITEM)) {
            return;
        }
        for (EnumFacing facing :configComponent.getSidesForData(TransmissionType.ITEM,facing,dataIndex)){
            BlockPos offset = getPos().offset(facing);
            TileEntity te = getWorld().getTileEntity(offset);
            if (!InventoryUtils.isItemHandler(te, facing.getOpposite())) {
                continue;
            }
            IItemHandler itemHandler = InventoryUtils.getItemHandler(te, facing.getOpposite());
            if (itemHandler == null) {
                continue;
            }
            try {
                outputToExternal(itemHandler, outputSlotID);
            }catch (Exception e) {
                Mekanism.logger.error("Exception when insert item: ", e);
            }
        }
    }

    private synchronized void outputToExternal(IItemHandler external, int outputSlotID) {
        for (int externalSlotId = 0; externalSlotId < external.getSlots(); externalSlotId++) {
            ItemStack externalStack = external.getStackInSlot(externalSlotId);
            int slotLimit = external.getSlotLimit(externalSlotId);
            if (!externalStack.isEmpty() && externalStack.getCount() >= slotLimit) {
                continue;
            }
            ItemStack internalStack = inventory.get(outputSlotID);
            if (internalStack.isEmpty()) {
                continue;
            }
            if (externalStack.isEmpty()) {
                ItemStack notInserted = external.insertItem(externalSlotId, internalStack, false);
                // Safeguard against Storage Drawers virtual slot
                if (notInserted.getCount() == internalStack.getCount()) {
                    break;
                }
                inventory.set(outputSlotID, notInserted);
                if (notInserted.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!matchStacks(internalStack, externalStack)) {
                continue;
            }
            // Extract internal item to external.
            ItemStack notInserted = external.insertItem(externalSlotId, internalStack, false);
            inventory.set(outputSlotID, notInserted);
            if (notInserted.isEmpty()) {
                break;
            }
        }
    }

    private void InputItems(int dataIndex, int inputSlotID) {
        for (EnumFacing facing : configComponent.getSidesForData(TransmissionType.ITEM, facing, dataIndex)) {
            BlockPos offset = getPos().offset(facing);
            TileEntity te = getWorld().getTileEntity(offset);
            if (!InventoryUtils.isItemHandler(te, facing.getOpposite())) {
                continue;
            }
            IItemHandler itemHandler = InventoryUtils.getItemHandler(te, facing.getOpposite());
            if (itemHandler == null) {
                continue;
            }
            inputFromExternal(itemHandler, inputSlotID);
        }
    }

    private synchronized void inputFromExternal(IItemHandler external, int inputSlotID) {
        boolean successAtLeastOnce = false;
        external:
        for (int i = external.getSlots() - 1; i >= 0; i--) {
            ItemStack externalStack = external.getStackInSlot(i);
            if (externalStack.isEmpty()) {
                continue;
            }
            ItemStack internalStack = inventory.get(inputSlotID);
            int maxCanExtract = Math.min(externalStack.getCount(), externalStack.getMaxStackSize());
            if (internalStack.isEmpty()) {
                if (!isItemValidForSlot(inputSlotID, externalStack)) {
                    continue;
                }
                ItemStack extracted = external.extractItem(i, maxCanExtract, false);
                inventory.set(inputSlotID, extracted);
                successAtLeastOnce = true;
                // If there are no more items in the current slot, check the next external slot.
                if (external.getStackInSlot(i).isEmpty()) {
                    continue external;
                }
                continue;
            }
            if (internalStack.getCount() >= internalStack.getMaxStackSize() || !matchStacks(internalStack, externalStack)) {
                continue;
            }
            int extractAmt = Math.min(internalStack.getMaxStackSize() - internalStack.getCount(), maxCanExtract);
            ItemStack extracted = external.extractItem(i, extractAmt, false);
            inventory.set(inputSlotID, copyStackWithSize(extracted, internalStack.getCount() + extracted.getCount()));
            successAtLeastOnce = true;
            // If there are no more items in the current slot, check the next external slot.
            if (external.getStackInSlot(i).isEmpty()) {
                continue external;
            }
        }

        if (successAtLeastOnce) {
            incrementSuccessCounter(60, 5);
            markNoUpdate();
        } else {
            decrementSuccessCounter();
        }
    }

    public static ItemStack copyStackWithSize(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack s = stack.copy();
        s.setCount(amount);
        return s;
    }

    public static boolean matchStacks(@Nonnull ItemStack stack, @Nonnull ItemStack other) {
        if (!ItemStack.areItemsEqual(stack, other)) return false;
        return ItemStack.areItemStackTagsEqual(stack, other);
    }

    protected boolean canWork(int minWorkDelay, int maxWorkDelay) {
        if (inventoryChanged) {
            inventoryChanged = false;
            return true;
        }

        if (successCounter <= 0) {
            return ticksExisted % maxWorkDelay == 0;
        }
        int workDelay = Math.max(minWorkDelay, maxWorkDelay - (successCounter * 5));
        return ticksExisted % workDelay == 0;
    }

    protected void incrementSuccessCounter(int maxWorkDelay, int minWorkDelay) {
        int max = (maxWorkDelay - minWorkDelay) / 5;
        if (successCounter < max) {
            successCounter++;
        }
    }

    protected void decrementSuccessCounter() {
        if (successCounter > 0) {
            successCounter--;
        }
    }


    protected void setupVariableValues() {
    }

    protected void setUpOtherActions() {
    }

    protected void setClearOperatingTicks() {
    }

    protected void setFinish() {

    }

    protected void setNoFinish() {

    }

    public void getProcess(RECIPE recipe) {
        getProcess(recipe, true);
    }

    public void getProcess(RECIPE recipe, boolean canOperate) {
        getProcess(recipe, canOperate, energyPerTick, true, true);
    }

    public void getProcess(RECIPE recipe, boolean canOperate, double energyTick) {
        getProcess(recipe, canOperate, energyTick, true, true);
    }


    public void getProcess(RECIPE recipe, boolean canOperate, double energyTick, boolean clear, boolean defaultEnergy) {
        if (canOperate(recipe) && MekanismUtils.canFunction(this) && getEnergy() >= energyTick && canOperate) {
            setupVariableValues();
            setActive(true);
            operatingTicks++;
            if (defaultEnergy) {
                electricityStored.addAndGet(-energyTick);
            }
            if (operatingTicks >= ticksRequired) {
                int actionTicksRequired = ticksRequired;
                if (defaultEnergy && MekanismConfig.current().mekce.EnableUpgradeConfigure.val() && ticksRequired <= 0 && energyTick > 0) {
                    int requestedOperations = 1 - ticksRequired;
                    int availableOperations = 1 + (int) (getEnergy() / energyTick);
                    actionTicksRequired = 1 - Math.min(requestedOperations, availableOperations);
                }
                int operations = MultipleActions(recipe, actionTicksRequired);
                if (defaultEnergy && operations > 1) {
                    electricityStored.addAndGet(-energyTick * (operations - 1));
                }
                operatingTicks = 0;
                setFinish();
            }
            setUpOtherActions();
        } else {
            setNoFinish();
            if (prevEnergy >= getEnergy()) {
                setActive(false);
            }
        }
        if (clear) {
            if (!canOperate(recipe)) {
                operatingTicks = 0;
            }
        } else {
            setClearOperatingTicks();
        }
    }


    public int MultipleActions(RECIPE recipe) {
        return MultipleActions(recipe, ticksRequired);
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }
}
