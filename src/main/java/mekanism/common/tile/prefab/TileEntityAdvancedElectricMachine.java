package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.*;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.MekanismItems;
import mekanism.common.SideData;
import mekanism.common.Upgrade;
import mekanism.common.base.ISustainedData;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * 带用气体类型的用电机器
 *
 * @param <RECIPE> 为机器配方类型
 */

public abstract class TileEntityAdvancedElectricMachine<RECIPE extends AdvancedMachineRecipe<RECIPE>> extends
        TileEntityUpgradeableMachine<AdvancedMachineInput, ItemStackOutput, RECIPE> implements IGasHandler, ISustainedData {

    private static final String[] methods = new String[]{"getEnergy", "getSecondaryStored", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy",
            "getEnergyNeeded"};
    public static final int BASE_TICKS_REQUIRED = 200;
    public static final int BASE_GAS_PER_TICK = 1;
    public static int MAX_GAS = 210;
    /**
     * How much secondary energy (fuel) this machine uses per tick, not including upgrades.
     */
    public int BASE_SECONDARY_ENERGY_PER_TICK;
    /**
     * How much secondary energy this machine uses per tick, including upgrades.
     */
    public double secondaryEnergyPerTick;
    public int secondaryEnergyThisTick;
    public GasTank gasTank;
    public Gas prevGas;

    /**
     * Advanced Electric Machine -- a machine like this has a total of 4 slots. Input slot (0), fuel slot (1), output slot (2), energy slot (3), and the upgrade slot (4).
     * The machine will not run if it does not have enough electricity, or if it doesn't have enough fuel ticks.
     *
     * @param soundPath        - location of the sound effect
     * @param type             - the type of this machine
     * @param ticksRequired    - how many ticks it takes to smelt an item.
     * @param secondaryPerTick - how much secondary energy (fuel) this machine uses per tick.
     */
    public TileEntityAdvancedElectricMachine(String soundPath, MachineType type, int ticksRequired, int secondaryPerTick) {
        super(soundPath, type, 4, ticksRequired);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);

        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.NONE, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.OUTPUT, new int[]{2}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.ENERGY, new int[]{3}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.EXTRA, new int[]{1}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(new int[]{0, 2}, new boolean[]{false, true}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED_OUTPUT_ENHANCED, new int[]{0, 2}, new boolean[]{false, true}));
        configComponent.setConfig(TransmissionType.ITEM, new byte[]{4, 1, 1, 3, 1, 2});

        configComponent.setInputConfig(TransmissionType.ENERGY);

        gasTank = new GasTank(MAX_GAS);

        inventory = NonNullListSynchronized.withSize(5, ItemStack.EMPTY);

        BASE_SECONDARY_ENERGY_PER_TICK = secondaryPerTick;
        secondaryEnergyPerTick = secondaryPerTick;

        if (upgradeableSecondaryEfficiency()) {
            upgradeComponent.setSupported(Upgrade.GAS);
        }
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(2));
        ejectorComponent.setInputOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(5));
    }

    @Override
    protected void upgradeInventory(TileEntityFactory factory) {
        //Advanced Machine
        setInputGasTank(factory, gasTank);
        setInputSlotItem(factory, inventory.get(0));
        setExtraSlotItem(factory, inventory.get(1));
        setOutputSlotItem(factory, inventory.get(2));
        setEnergySlotItem(factory, inventory.get(3));
        setUpgradeSlot(factory, inventory.get(4));
    }

    /**
     * Gets the amount of ticks the declared itemstack can fuel this machine.
     *
     * @param itemStack - itemstack to check with
     * @return fuel ticks
     */
    @Nullable
    public GasStack getItemGas(ItemStack itemStack) {
        return GasConversionHandler.getItemGas(itemStack, gasTank, this::isValidGas);
    }

    public abstract boolean isValidGas(Gas gas);

    private boolean inactive;

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        ChargeUtils.discharge(3, this);
        handleSecondaryFuel();
        inactive = false;
        RECIPE recipe = getRecipe();
        secondaryEnergyThisTick = useStatisticalMechanics() ? StatUtils.inversePoisson(secondaryEnergyPerTick) : ceilSecondaryEnergyPerTick(secondaryEnergyPerTick);
        getProcess(recipe, gasTank.getStored() >= secondaryEnergyThisTick, energyPerTick, false, true);
        if (!(canOperate(recipe) && MekanismUtils.canFunction(this) && getEnergy() >= energyPerTick && gasTank.getStored() >= secondaryEnergyThisTick)) {
            inactive = true;
        }
        prevEnergy = getEnergy();
        if (!(gasTank.getGasType() == null || gasTank.getStored() == 0)) {
            prevGas = gasTank.getGasType();
        }
    }

    /**
     * Avoids accidental over-rounding when upgrade scaling lands on an integer but is represented as integer + 1 ULP.
     */
    public static int ceilSecondaryEnergyPerTick(double secondaryEnergyPerTick) {
        return (int) Math.ceil(Math.nextAfter(secondaryEnergyPerTick, Double.NEGATIVE_INFINITY));
    }


    @Override
    public void addTileSyncTask() {
        AutomaticallyExtractItems(6, 0);
        AutomaticallyExtractItems(7, 0);
        BetterEjectingItem(7, 2);
    }

    @Override
    public void setUpOtherActions() {
        gasTank.draw(secondaryEnergyThisTick, true);
    }

    @Override
    public void setClearOperatingTicks() {
        if (inactive && getRecipe() == null) {
            operatingTicks = 0;
        }
    }

    public void handleSecondaryFuel() {
        ItemStack itemStack = inventory.get(1);
        int needed = gasTank.getNeeded();
        if (!itemStack.isEmpty() && needed > 0) {
            GasStack gasStack = getItemGas(itemStack);
            if (gasStack != null && needed >= gasStack.amount) {
                if (itemStack.getItem() instanceof IGasItem item) {
                    gasTank.receive(item.removeGas(itemStack, gasStack.amount), true);
                } else {
                    gasTank.receive(gasStack, true);
                    itemStack.shrink(1);
                }
            }
        }
    }

    public boolean upgradeableSecondaryEfficiency() {
        return false;
    }

    public boolean useStatisticalMechanics() {
        return false;
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 2) {
            return false;
        } else if (slotID == 4) {
            return itemstack.getItem() == MekanismItems.SpeedUpgrade || itemstack.getItem() == MekanismItems.EnergyUpgrade;
        } else if (slotID == 0) {
            for (AdvancedMachineInput input : getRecipes().keySet()) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemStack, itemstack)) {
                    return true;
                }
            }
        } else if (slotID == 3) {
            return ChargeUtils.canBeDischarged(itemstack);
        } else if (slotID == 1) {
            return getItemGas(itemstack) != null;
        }
        return false;
    }

    @Override
    public AdvancedMachineInput getInput() {
        return new AdvancedMachineInput(inventory.get(0), prevGas);
    }

    @Override
    public RECIPE getRecipe() {
        AdvancedMachineInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getRecipe(input, getRecipes());
        }
        return cachedRecipe;
    }

    @Override
    public Map<AdvancedMachineInput, RECIPE> getRecipes() {
        return null;
    }

    @Override
    public void operate(RECIPE recipe) {
        recipe.operate(inventory, 0, 2, gasTank, secondaryEnergyThisTick);
        markNoUpdateSync();
    }

    @Override
    public int operate(RECIPE recipe, int operations) {
        int actualOperations = Math.min(operations, inventory.get(0).getCount() / recipe.getInput().itemStack.getCount());
        actualOperations = Math.min(actualOperations, gasTank.getStored() / secondaryEnergyThisTick);
        actualOperations = Math.min(actualOperations, OperationUtils.getMaxOutputOperations(inventory, 2, recipe.getOutput().output));
        if (actualOperations > 0) {
            OperationUtils.shrinkStack(inventory, 0, recipe.getInput().itemStack, actualOperations);
            gasTank.draw(secondaryEnergyThisTick * actualOperations, true);
            OperationUtils.growOutput(inventory, 2, recipe.getOutput().output, actualOperations);
            markNoUpdateSync();
        }
        return actualOperations;
    }

    @Override
    public boolean canOperate(RECIPE recipe) {
        return recipe != null && recipe.canOperate(inventory, 0, 2, gasTank, secondaryEnergyThisTick);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, gasTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, gasTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        gasTank.read(nbtTags.getCompoundTag("gasTank"));
        gasTank.setMaxGas(MAX_GAS);
        GasUtils.clearIfInvalid(gasTank, this::isValidGas);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setTag("gasTank", gasTank.write(new NBTTagCompound()));
    }

    /**
     * Gets the scaled secondary energy level for the GUI.
     *
     * @param i - multiplier
     * @return scaled secondary energy
     */
    public int getScaledGasLevel(int i) {
        return gasTank.getStored() * i / gasTank.getMaxGas();
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 3) {
            return ChargeUtils.canBeOutputted(itemstack, false);
        }
        return slotID == 2;
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        return 0;
    }

    @Override
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        return null;
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        return false;
    }

    @Override
    public boolean canDrawGas(EnumFacing side, Gas type) {
        return false;
    }

    @Override
    @Nonnull
    public GasTankInfo[] getTankInfo() {
        return new GasTankInfo[]{gasTank};
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.GAS_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.SPEED || (upgradeableSecondaryEfficiency() && upgrade == Upgrade.GAS)) {
            secondaryEnergyPerTick = MekanismUtils.getSecondaryEnergyPerTickMean(this, BASE_SECONDARY_ENERGY_PER_TICK);
        }
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{gasTank.getStored()};
            case 2 -> new Object[]{operatingTicks};
            case 3 -> new Object[]{isActive};
            case 4 -> new Object[]{facing};
            case 5 -> new Object[]{canOperate(getRecipe())};
            case 6 -> new Object[]{maxEnergy};
            case 7 -> new Object[]{maxEnergy - getEnergy()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        GasUtils.writeSustainedData(gasTank, itemStack);
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        GasUtils.readSustainedData(gasTank, itemStack);
    }

    public boolean getEnergySlot() {
        return inventory.get(3).isEmpty();
    }

    @Override
    public boolean getInputSlot() {
        return inventory.get(0).isEmpty();
    }

    @Override
    public boolean getOuputSlot() {
        return inventory.get(2).isEmpty();
    }

    @Override
    public boolean getExtraSlot() {
        return inventory.get(1).isEmpty();
    }
}
