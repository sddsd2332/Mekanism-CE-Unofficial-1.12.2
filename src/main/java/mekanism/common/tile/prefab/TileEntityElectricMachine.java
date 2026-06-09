package mekanism.common.tile.prefab;

import mekanism.api.transmitters.TransmissionType;
import mekanism.common.MekanismItems;
import mekanism.common.SideData;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.BasicMachineRecipe;
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

import javax.annotation.Nonnull;
import java.util.Map;


/**
 * 使用电力的机器类型
 */

public abstract class TileEntityElectricMachine<RECIPE extends BasicMachineRecipe<RECIPE>> extends TileEntityUpgradeableMachine<ItemStackInput, ItemStackOutput, RECIPE> {

    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};

    /**
     * A simple electrical machine. This has 3 slots - the input slot (0), the energy slot (1), output slot (2), and the upgrade slot (3). It will not run if it does not
     * have enough energy.
     *
     * @param soundPath     - location of the sound effect
     * @param type          - type of this machine
     * @param ticksRequired - ticks required to operate -- or smelt an item.
     */
    public TileEntityElectricMachine(String soundPath, MachineType type, int ticksRequired) {
        super(soundPath, type, 3, ticksRequired);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);

        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.NONE, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.OUTPUT, new int[]{2}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.ENERGY, new int[]{1}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(new int[]{0, 2}, new boolean[]{false, true}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED_OUTPUT_ENHANCED, new int[]{0, 2}, new boolean[]{false, true}));
        configComponent.setConfig(TransmissionType.ITEM, new byte[]{1, 1, 1, 3, 1, 2});
        configComponent.setInputConfig(TransmissionType.ENERGY);

        inventory = NonNullListSynchronized.withSize(4, ItemStack.EMPTY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(2));
        ejectorComponent.setInputOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(4));
    }



    @Override
    protected void upgradeInventory(TileEntityFactory factory) {
        setInputSlotItem(factory, inventory.get(0));
        setEnergySlotItem(factory, inventory.get(1));
        setOutputSlotItem(factory, inventory.get(2));
        setUpgradeSlot(factory, inventory.get(3));
    }


    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        ChargeUtils.discharge(1, this);
        RECIPE recipe = getRecipe();
        getProcess(recipe);
        prevEnergy = getEnergy();
    }

    @Override
    public void addTileSyncTask() {
        AutomaticallyExtractItems(5, 0);
        AutomaticallyExtractItems(6, 0);
        BetterEjectingItem(6, 2);
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 2) {
            return false;
        } else if (slotID == 3) {
            return itemstack.getItem() == MekanismItems.SpeedUpgrade || itemstack.getItem() == MekanismItems.EnergyUpgrade;
        } else if (slotID == 0) {
            return RecipeHandler.isInRecipe(itemstack, getRecipes());
        } else if (slotID == 1) {
            return ChargeUtils.canBeDischarged(itemstack);
        }
        return false;
    }

    @Override
    public ItemStackInput getInput() {
        return new ItemStackInput(inventory.get(0));
    }

    @Override
    public RECIPE getRecipe() {
        ItemStackInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getRecipe(input, getRecipes());
        }
        return cachedRecipe;
    }

    @Override
    public Map<ItemStackInput, RECIPE> getRecipes() {
        return null;
    }

    @Override
    public void operate(RECIPE recipe) {
        recipe.operate(inventory, 0, 2);
        markNoUpdateSync();
    }

    @Override
    public int operate(RECIPE recipe, int operations) {
        int actualOperations = Math.min(operations, inventory.get(0).getCount() / recipe.getInput().ingredient.getCount());
        actualOperations = Math.min(actualOperations, OperationUtils.getMaxOutputOperations(inventory, 2, recipe.getOutput().output));
        if (actualOperations > 0) {
            OperationUtils.shrinkStack(inventory, 0, recipe.getInput().ingredient, actualOperations);
            OperationUtils.growOutput(inventory, 2, recipe.getOutput().output, actualOperations);
            markNoUpdateSync();
        }
        return actualOperations;
    }

    @Override
    public boolean canOperate(RECIPE recipe) {
        return recipe != null && recipe.canOperate(inventory, 0, 2);
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
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
            case 5 -> new Object[]{getMaxEnergy()};
            case 6 -> new Object[]{getMaxEnergy() - getEnergy()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public boolean getEnergySlot() {
        return inventory.get(1).isEmpty();
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
    protected boolean shouldDumpRadiation() {
        return false;
    }
}
