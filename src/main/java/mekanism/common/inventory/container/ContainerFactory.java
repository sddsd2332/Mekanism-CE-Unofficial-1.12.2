package mekanism.common.inventory.container;

import javax.annotation.Nonnull;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.common.base.IFactory;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.inventory.slot.SlotEnergy.SlotDischarge;
import mekanism.common.inventory.slot.SlotOutput;
import mekanism.common.item.ItemBlockMachine;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.TileEntityFactory;
import mekanism.common.util.ChargeUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ContainerFactory extends ContainerMekanism<TileEntityFactory> {

    public ContainerFactory(InventoryPlayer inventory, TileEntityFactory tile) {
        super(tile, inventory);
    }

    @Override
    protected void addSlots() {
        addSlotToContainer(new SlotDischarge(tileEntity, 1, 7, 13));
        int xTypeSlot = tileEntity.tier == FactoryTier.CREATIVE ? 252 : tileEntity.tier == FactoryTier.ULTIMATE ? 214 : 180;
        addSlotToContainer(new Slot(tileEntity, 2, xTypeSlot, 75) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                MachineType swapType = MachineType.get(stack);
                return swapType != null && !swapType.isFactory();
            }
        });
        addSlotToContainer(new SlotOutput(tileEntity, 3, xTypeSlot, 112));
        addSlotToContainer(new Slot(tileEntity, 4, 7, 57));
        int xOffset = tileEntity.tier == FactoryTier.BASIC ? 55 : tileEntity.tier == FactoryTier.ADVANCED ? 35 : tileEntity.tier == FactoryTier.ELITE ? 29 : 27;
        int xDistance = tileEntity.tier == FactoryTier.BASIC ? 38 : tileEntity.tier == FactoryTier.ADVANCED ? 26 : 19;

        for (int i = 0; i < tileEntity.tier.processes; i++) {
            if (tileEntity.NoItemInputMachine()){
                addSlotToContainer(new FactoryInputSlot(tileEntity, getInputSlotIndex(i), 7, 35, i));
            }else {
                addSlotToContainer(new FactoryInputSlot(tileEntity, getInputSlotIndex(i), xOffset + (i * xDistance), 13, i));
            }
        }
        for (int i = 0; i < tileEntity.tier.processes; i++) {
            if (tileEntity.GasOutputMachine()) {
                addSlotToContainer(new SlotOutput(tileEntity, getOutputSlotIndex(i), 7,35));
            }else {
                addSlotToContainer(new SlotOutput(tileEntity, getOutputSlotIndex(i), xOffset + (i * xDistance), 57));
            }
        }

        for (int i = 0; i < tileEntity.tier.processes; i++){
            if (tileEntity.getRecipeType().getFuelType() == IFactory.MachineFuelType.FARM || tileEntity.getRecipeType().getFuelType() == IFactory.MachineFuelType.CHANCE){
                addSlotToContainer(new SlotOutput(tileEntity, getSecondaryOutputSlotIndex(i), xOffset + (i * xDistance), 78));
            }else {
                addSlotToContainer(new SlotOutput(tileEntity, getSecondaryOutputSlotIndex(i), 7, 35)); //Secondary output slots are reserved to prevent errors
            }
        }
    }

    @Override
    protected int getInventorYOffset() {
        if (tileEntity.getRecipeType().getFuelType() == IFactory.MachineFuelType.ADVANCED || tileEntity.getRecipeType() == RecipeType.INFUSING || tileEntity.getRecipeType() == RecipeType.Dissolution|| tileEntity.getRecipeType() == RecipeType.WASHER || tileEntity.getRecipeType() == RecipeType.NUCLEOSYNTHESIZER){
            return 95;
        }else if (tileEntity.getRecipeType() == RecipeType.PRC){
            return 113;
        }else if (tileEntity.getRecipeType() == RecipeType.Crystallizer){
            return 91;
        }else if (tileEntity.getRecipeType().getFuelType() == IFactory.MachineFuelType.FARM){
            return 116;
        }else if (tileEntity.getRecipeType().getFuelType() == IFactory.MachineFuelType.CHANCE){
            return 105;
        }else {
            return 84;
        }
    }

    @Override
    protected int getInventorXOffset() {
        return tileEntity.tier == FactoryTier.CREATIVE ? 44 : tileEntity.tier == FactoryTier.ULTIMATE ? 27 : 8;
    }

    @Nonnull
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotID) {
        ItemStack stack = ItemStack.EMPTY;
        Slot currentSlot = inventorySlots.get(slotID);
        if (currentSlot != null && currentSlot.getHasStack()) {
            ItemStack slotStack = currentSlot.getStack();
            stack = slotStack.copy();
            if (isOutputSlot(slotID)) {
                if (!mergeItemStack(slotStack, tileEntity.inventory.size() - 1, inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            }else if(isSecondaryOutputSlot(slotID)){
                if (!mergeItemStack(slotStack, tileEntity.inventory.size() - 1, inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotID != 1 && slotID != 2 && isProperMachine(slotStack) && !ItemHandlerHelper.canItemStacksStack(slotStack, tileEntity.getMachineStack())) {
                if (!mergeItemStack(slotStack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotID == 2) {
                if (!mergeItemStack(slotStack, tileEntity.inventory.size() - 1, inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (tileEntity.getRecipeType().getAnyRecipe(slotStack, inventorySlots.get(4).getStack(), tileEntity.gasTank.getGasType(), tileEntity.infuseStored, tileEntity.gasTank.getGas(),tileEntity.fluidTank.getFluid()) != null) {
                if (isInputSlot(slotID)) {
                    if (!mergeItemStack(slotStack, tileEntity.inventory.size() - 1, inventorySlots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!mergeItemStack(slotStack, 4, 4 + tileEntity.tier.processes, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (ChargeUtils.canBeDischarged(slotStack)) {
                if (slotID == 0) {
                    if (!mergeItemStack(slotStack, tileEntity.inventory.size() - 1, inventorySlots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!mergeItemStack(slotStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (tileEntity.getItemGas(slotStack) != null) {
                if (transferExtraSlot(slotID, slotStack)) {
                    return ItemStack.EMPTY;
                }
            } else if (tileEntity.getRecipeType() == RecipeType.INFUSING && InfuseRegistry.getObject(slotStack) != null
                    && (tileEntity.infuseStored.getType() == null || tileEntity.infuseStored.getType() == InfuseRegistry.getObject(slotStack).type)) {
                if (transferExtraSlot(slotID, slotStack)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int slotEnd = tileEntity.inventory.size() - 1;
                if (slotID >= slotEnd && slotID <= (slotEnd + 26)) {
                    if (!mergeItemStack(slotStack, slotEnd + 27, inventorySlots.size(), false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotID > (slotEnd + 26)) {
                    if (!mergeItemStack(slotStack, slotEnd, slotEnd + 26, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!mergeItemStack(slotStack, slotEnd, inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.getCount() == 0) {
                currentSlot.putStack(ItemStack.EMPTY);
            } else {
                currentSlot.onSlotChanged();
            }
            if (slotStack.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            currentSlot.onTake(player, slotStack);
        }
        return stack;
    }

    private boolean transferExtraSlot(int slotID, ItemStack slotStack) {
        if (slotID >= tileEntity.inventory.size() - 1) {
            return !mergeItemStack(slotStack, 3, 4, false);
        }
        return !mergeItemStack(slotStack, tileEntity.inventory.size() - 1, inventorySlots.size(), true);
    }

    public boolean isProperMachine(ItemStack itemStack) {
        if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemBlockMachine) {
            for (RecipeType type : RecipeType.values()) {
                if (ItemHandlerHelper.canItemStacksStack(itemStack, type.getStack())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInputSlot(int slot) {
        return slot >= 4 && slot < 4 + tileEntity.tier.processes;
    }

    public boolean isOutputSlot(int slot) {
        return slot >= 4 + tileEntity.tier.processes && slot < 4 + tileEntity.tier.processes * 2;
    }

    public boolean isSecondaryOutputSlot(int slot) {
        return slot >= 4 + tileEntity.tier.processes * 2 && slot < 4 + tileEntity.tier.processes * 3;
    }

    private int getOutputSlotIndex(int processNumber) {
        return tileEntity.tier.processes + getInputSlotIndex(processNumber);
    }

    private int getSecondaryOutputSlotIndex(int processNumber) {
        return tileEntity.tier.processes * 2 + getInputSlotIndex(processNumber);
    }

    private int getInputSlotIndex(int processNumber) {
        return 5 + processNumber;
    }

    private class FactoryInputSlot extends Slot {

        /**
         * The index of the processes slot. 0 <= processNumber < tileEntity.tier.processes For matching the input to output slot
         */
        private final int processNumber;

        private FactoryInputSlot(IInventory inventoryIn, int index, int xPosition, int yPosition, int processNumber) {
            super(inventoryIn, index, xPosition, yPosition);
            this.processNumber = processNumber;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            ItemStack outputSlotStack = tileEntity.inventory.get(getOutputSlotIndex(this.processNumber));
            return tileEntity.inputProducesOutput(getInputSlotIndex(this.processNumber), stack, outputSlotStack, false) && super.isItemValid(stack);
        }
    }
}
