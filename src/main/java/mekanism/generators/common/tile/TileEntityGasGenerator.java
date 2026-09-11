package mekanism.generators.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.base.ISustainedData;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.GasStackFuelToEnergyRecipe;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntityGasGenerator extends TileEntityGenerator implements ISustainedData, IComparatorSupport, ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getGas", "getGasNeeded"};
    /**
     * The maximum amount of gas this block can store.
     */
    public static final int MAX_GAS = 18000;
    /**
     * The tank this block is storing fuel in.
     */
    public BasicGasTank fuelTank;
    public int burnTicks = 0;
    public int maxBurnTicks;
    public double generationRate = 0;
    public double clientUsed;
    private int currentRedstoneLevel;
    public GasStackFuelToEnergyRecipe cachedRecipe;
    private int cachedRecipeVersion = -1;
    private GasInventorySlot fuelSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityGasGenerator() {
        super("gas", "GasGenerator", MekanismConfig.current().general.FROM_H2.val() * 1000, MekanismConfig.current().general.FROM_H2.val() * 2);
        initializeInventorySlots();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        fuelTank = builder.addTank(new FuelTank(listener), RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        fuelSlot = builder.addSlot(GasInventorySlot.fill(fuelTank, listener, 17, 35),
              RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        fuelSlot.setSlotOverlay(SlotOverlay.MINUS);
        energySlot = builder.addSlot(EnergyInventorySlot.drain(this, listener, 143, 35), RelativeSide.RIGHT);
        return builder.build();
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.drainContainer();
        boolean wasEmpty = fuelTank.getGas() == null;
        if (fuelSlot.fillTank() && wasEmpty && RecipeHandler.getGasStackFuelToEnergyRecipe(fuelTank.getGas()) != null) {
            output = RecipeHandler.getGasStackFuelToEnergyRecipe(fuelTank.getGas()).getOutput().energyOutput * 2;
        }

        GasStackFuelToEnergyRecipe recipe = getRecipe();
        boolean operate = recipe != null && canOperate();
        if (operate && getEnergyContainer().insert(generationRate, Action.SIMULATE, AutomationType.INTERNAL) == 0) {
            setActive(true);
            if (fuelTank.getStored() != 0) {
                maxBurnTicks = recipe.getInput().ingredient.amount;
                generationRate = recipe.getOutput().energyOutput;
            }

            int toUse = getToUse();
            output = Math.max(MekanismConfig.current().general.FROM_H2.val() * 2, generationRate * getToUse() * 2);

            int total = burnTicks + fuelTank.getStored() * maxBurnTicks;
            total -= toUse;
            getEnergyContainer().insert(generationRate * toUse, Action.EXECUTE, AutomationType.INTERNAL);

            if (fuelTank.getStored() > 0) {
                fuelTank.setStackSize(total / maxBurnTicks, Action.EXECUTE);
            }
            burnTicks = total % maxBurnTicks;
            clientUsed = toUse /(double)  maxBurnTicks;
        } else {
            if (!operate) {
                reset();
            }
            clientUsed = 0;
            setActive(false);
        }
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    public void reset() {
        burnTicks = 0;
        maxBurnTicks = 0;
        generationRate = 0;
        output = MekanismConfig.current().general.FROM_H2.val() * 2;
    }

    public int getToUse() {
        if (generationRate == 0 || fuelTank.getGas() == null) {
            return 0;
        }
        int max = (int) Math.ceil(((float) fuelTank.getStored() / (float) fuelTank.getMaxGas()) * 256F);
        max = Math.min((fuelTank.getStored() * maxBurnTicks) + burnTicks, max);
        max = (int) Math.min((getMaxEnergy() - getEnergy()) / generationRate, max);
        return max;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return EnergyInventorySlot.drainExtractCheck(this, itemstack);
        } else if (slotID == 0) {
            return GasInventorySlot.fillExtractCheck(fuelTank, itemstack);
        }
        return false;
    }

    @Override
    public boolean canOperate() {
        return (fuelTank.getStored() > 0 || burnTicks > 0) && getRecipe() != null && MekanismUtils.canFunction(this);
    }


    /**
     * Gets the scaled gas level for the GUI.
     *
     * @param i - multiplier
     * @return Scaled gas level
     */
    public int getScaledGasLevel(int i) {
        return fuelTank.getStored() * i / MAX_GAS;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{output};
            case 2 -> new Object[]{getMaxEnergy()};
            case 3 -> new Object[]{getNeedEnergy()};
            case 4 -> new Object[]{fuelTank.getStored()};
            case 5 -> new Object[]{fuelTank.getNeeded()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, fuelTank);
            generationRate = dataStream.readDouble();
            output = dataStream.readDouble();
            clientUsed = dataStream.readDouble();
            maxBurnTicks = dataStream.readInt();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, fuelTank);
        data.add(generationRate);
        data.add(output);
        data.add(clientUsed);
        data.add(maxBurnTicks);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("fuelTank")) {
            fuelTank.read(nbtTags.getCompoundTag("fuelTank"));
        }
        sanitizeFuelTank();
        boolean isTankEmpty = fuelTank.getGas() == null;
        GasStackFuelToEnergyRecipe recipe = RecipeHandler.getGasStackFuelToEnergyRecipe(fuelTank.getGas());
        if (!isTankEmpty && recipe != null) {
            output = recipe.getOutput().energyOutput * 2;
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGasTank(itemStack, "fuelTank", fuelTank);
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        boolean loadedTank = readSustainedGasTanks(itemStack);
        loadedTank = loadedTank || ItemDataUtils.readLegacyGasTank(itemStack, "fuelTank", fuelTank);
        if (loadedTank) {
            sanitizeFuelTank();
            boolean isTankEmpty = fuelTank.getGas() == null;
            //Update energy output based on any existing fuel in tank
            GasStackFuelToEnergyRecipe recipe = RecipeHandler.getGasStackFuelToEnergyRecipe(fuelTank.getGas());
            if (!isTankEmpty && recipe != null) {
                output = recipe.getOutput().energyOutput * 2;
            }
        }
    }

    private void sanitizeFuelTank() {
        GasStack stored = fuelTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            fuelTank.setEmpty();
        } else if (stored != null) {
            fuelTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fuelTank.getStored(), fuelTank.getMaxGas());
    }
public GasStackFuelToEnergyRecipe getRecipe() {
        int recipeVersion = RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.getRecipeVersion();
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipe = null;
            cachedRecipeVersion = recipeVersion;
        }
        GasInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getGasStackFuelToEnergyRecipe(getInput());
        }
        return cachedRecipe;
    }

    public GasInput getInput() {
        return new GasInput(fuelTank.getGas());
    }


    public double getUsed() {
        return Math.round(clientUsed * 100) / 100D;
    }

    public int getMaxBurnTicks() {
        return maxBurnTicks;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.generators.client.model.ModelGasGenerator.class;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }

    private class FuelTank extends BasicGasTank {

        private FuelTank(IContentsListener listener) {
            super(MAX_GAS, BasicGasTank.notExternal, BasicGasTank.alwaysTrueBi,
                  gas -> RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.containsRecipe(gas), listener);
        }

        @Override
        public void setStack(GasStack stack) {
            boolean wasEmpty = isEmpty();
            super.setStack(stack);
            recheckOutput(stack, wasEmpty);
        }

        @Override
        public void setStackUnchecked(GasStack stack) {
            boolean wasEmpty = isEmpty();
            super.setStackUnchecked(stack);
            recheckOutput(stack, wasEmpty);
        }

        private void recheckOutput(GasStack stack, boolean wasEmpty) {
            if (wasEmpty && stack != null && stack.amount > 0) {
                GasStackFuelToEnergyRecipe recipe = RecipeHandler.getGasStackFuelToEnergyRecipe(stack);
                if (recipe != null) {
                    output = recipe.getOutput().energyOutput * 2;
                }
            }
        }
    }
}
