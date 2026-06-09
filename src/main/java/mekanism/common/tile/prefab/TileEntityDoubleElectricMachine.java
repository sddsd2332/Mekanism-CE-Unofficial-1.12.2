package mekanism.common.tile.prefab;

import mekanism.api.transmitters.TransmissionType;
import mekanism.common.MekanismItems;
import mekanism.common.SideData;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.NonNullListSynchronized;
import mekanism.common.util.OperationUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * 双输入机器类型方块
 */

public abstract class TileEntityDoubleElectricMachine<RECIPE extends DoubleMachineRecipe<RECIPE>> extends TileEntityUpgradeableMachine<DoubleMachineInput, ItemStackOutput, RECIPE> {

    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};

    /**
     * Double Electric Machine -- a machine like this has a total of 4 slots. Input slot (0), secondary slot (1), output slot (2), energy slot (3), and the upgrade slot
     * (4). The machine will not run if it does not have enough electricity.
     *
     * @param soundPath     - location of the sound effect
     * @param type          - the type of this machine
     * @param ticksRequired - how many ticks it takes to smelt an item.
     */
    public TileEntityDoubleElectricMachine(String soundPath, MachineType type, int ticksRequired) {
        super(soundPath, type, 4, ticksRequired);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);

        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.NONE, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.OUTPUT, new int[]{2}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.ENERGY, new int[]{3}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.EXTRA, new int[]{1}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_EXTRA, new int[]{0, 1}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(new int[]{0, 2}, new boolean[]{false, true}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED_OUTPUT_ENHANCED, new int[]{0, 2}, new boolean[]{false, true}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_EXTRA_OUTPUT, new int[]{1, 0, 2}, new boolean[]{false, false, true}));

        configComponent.setConfig(TransmissionType.ITEM, new byte[]{4, 1, 1, 3, 1, 2});
        configComponent.setInputConfig(TransmissionType.ENERGY);

        inventory = NonNullListSynchronized.withSize(5, ItemStack.EMPTY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(2));
        ejectorComponent.setInputOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(6));
        ejectorComponent.setInputExtraOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(9));
    }

    @Override
    protected void upgradeInventory(TileEntityFactory factory) {
        //Double Machine
        setInputSlotItem(factory, inventory.get(0));
        setExtraSlotItem(factory, inventory.get(1));
        setOutputSlotItem(factory, inventory.get(2));
        setEnergySlotItem(factory, inventory.get(3));
        setUpgradeSlot(factory, inventory.get(4));
    }


    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
            ChargeUtils.discharge(3, this);
            RECIPE recipe = getRecipe();
            getProcess(recipe);
            prevEnergy = getEnergy();
    }

    @Override
    public void addTileSyncTask() {
        AutomaticallyExtractItems(7, 0);
        AutomaticallyExtractItems(8, 0);
        BetterEjectingItem(8, 2);
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 2) {
            return false;
        } else if (slotID == 4) {
            return itemstack.getItem() == MekanismItems.SpeedUpgrade || itemstack.getItem() == MekanismItems.EnergyUpgrade;
        } else if (slotID == 0) {
            for (DoubleMachineInput input : getRecipes().keySet()) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemStack, itemstack)) {
                    return true;
                }
            }
        } else if (slotID == 3) {
            return ChargeUtils.canBeDischarged(itemstack);
        } else if (slotID == 1) {
            for (DoubleMachineInput input : getRecipes().keySet()) {
                if (ItemHandlerHelper.canItemStacksStack(input.extraStack, itemstack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public DoubleMachineInput getInput() {
        return new DoubleMachineInput(inventory.get(0), inventory.get(1));
    }

    @Override
    public RECIPE getRecipe() {
        DoubleMachineInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getRecipe(input, getRecipes());
        }
        return cachedRecipe;
    }

    @Override
    public void operate(RECIPE recipe) {
        recipe.operate(inventory, 0, 1, 2);
        markNoUpdateSync();
    }

    @Override
    public int operate(RECIPE recipe, int operations) {
        int actualOperations = Math.min(operations, inventory.get(0).getCount() / recipe.getInput().itemStack.getCount());
        actualOperations = Math.min(actualOperations, inventory.get(1).getCount() / recipe.getInput().extraStack.getCount());
        actualOperations = Math.min(actualOperations, OperationUtils.getMaxOutputOperations(inventory, 2, recipe.getOutput().output));
        if (actualOperations > 0) {
            OperationUtils.shrinkStack(inventory, 0, recipe.getInput().itemStack, actualOperations);
            OperationUtils.shrinkStack(inventory, 1, recipe.getInput().extraStack, actualOperations);
            OperationUtils.growOutput(inventory, 2, recipe.getOutput().output, actualOperations);
            markNoUpdateSync();
        }
        return actualOperations;
    }

    @Override
    public boolean canOperate(RECIPE recipe) {
        return recipe != null && recipe.canOperate(inventory, 0, 1, 2);
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 3) {
            return ChargeUtils.canBeOutputted(itemstack, false);
        }
        return slotID == 2;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{operatingTicks};
            case 2 -> new Object[]{isActive};
            case 3 -> new Object[]{facing};
            case 4 -> new Object[]{canOperate(getRecipe())};
            case 5 -> new Object[]{maxEnergy};
            case 6 -> new Object[]{maxEnergy - getEnergy()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public boolean getEnergySlot() {
        return inventory.get(3).isEmpty();
    }

    @Override
    public boolean getInputSlot() {
        return inventory.get(0).isEmpty();
    }

    @Override
    public boolean getOuputSlot() {
        return inventory.get(3).isEmpty();
    }

    @Override
    public boolean getExtraSlot() {
        return inventory.get(1).isEmpty();
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return false;
    }
}
