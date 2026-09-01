package mekanism.common.util;

import mekanism.api.gas.GasStack;
import mekanism.common.Upgrade;
import mekanism.common.base.IFactory;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.base.ITierItem;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.inventory.BinMekanismInventory;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.content.qio.QIODriveDefinition;
import mekanism.common.content.qio.QIODriveType;
import mekanism.common.content.qio.QIODriveSpecialization;
import mekanism.common.content.qio.QIODriveSpecializationRegistry;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.security.ISecurityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.oredict.OreDictionary;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RecipeUtils {

    public static boolean areItemsEqualForCrafting(ItemStack target, ItemStack input) {
        if (target.isEmpty() && !input.isEmpty() || !target.isEmpty() && input.isEmpty()) {
            return false;
        } else if (target.isEmpty()) {
            return true;
        }
        if (target.getItem() != input.getItem()) {
            return false;
        }
        if (target.getItemDamage() != input.getItemDamage() && target.getItemDamage() != OreDictionary.WILDCARD_VALUE) {
            return false;
        }
        if (target.getItem() instanceof ITierItem targetItem && input.getItem() instanceof ITierItem inputItem) {
            if (targetItem.getBaseTier(target) != inputItem.getBaseTier(input)) {
                return false;
            }
        }

        if (target.getItem() instanceof IFactory targetFactory && input.getItem() instanceof IFactory inputFactory) {
            if (isFactory(target) && isFactory(input)) {
                RecipeType recipeTypeInput = inputFactory.getRecipeTypeOrNull(input);
                //If either factory has invalid NBT don't crash it
                return recipeTypeInput != null && targetFactory.getRecipeTypeOrNull(target) == recipeTypeInput;
            }
        }
        return true;
    }

    private static boolean isFactory(ItemStack stack) {
        return MachineType.get(stack).isFactory();
    }

    public static ItemStack getCraftingResult(InventoryCrafting inv, ItemStack toReturn) {
        int invLength = inv.getSizeInventory();
        double outputMaxEnergy = StorageUtils.getMaxEnergy(toReturn);
        if (outputMaxEnergy > 0) {
            double energyFound = 0;
            for (int i = 0; i < invLength; i++) {
                ItemStack itemstack = inv.getStackInSlot(i);
                if (!itemstack.isEmpty()) {
                    energyFound += StorageUtils.getStoredEnergy(itemstack);
                }
            }
            double energyToSet = Math.min(outputMaxEnergy, energyFound);
            if (energyToSet > 0) {
                StorageUtils.setStoredEnergy(toReturn, energyToSet, outputMaxEnergy);
            }
        }

        if (GasInventorySlot.isGasContainerItem(toReturn)) {
            GasStack gasFound = null;
            for (int i = 0; i < invLength; i++) {
                ItemStack itemstack = inv.getStackInSlot(i);
                if (!itemstack.isEmpty()) {
                    GasInventorySlot.GasTransferResult gasResult = GasInventorySlot.getTransferableGas(itemstack);
                    if (!gasResult.isValid()) {
                        return ItemStack.EMPTY;
                    }
                    GasStack gas = gasResult.getStack();
                    if (gas != null && gas.amount > 0) {
                        if (gasFound == null) {
                            gasFound = gas.copy();
                        } else if (gasFound.getGas() != gas.getGas()) {
                            return ItemStack.EMPTY;
                        } else {
                            gasFound = gasFound.copy().withAmount(gasFound.amount + gas.amount);
                        }
                    }
                }
            }

            if (gasFound != null) {
                if (GasInventorySlot.insertGas(toReturn, gasFound.copy(), false) != gasFound.amount ||
                    GasInventorySlot.insertGas(toReturn, gasFound.copy(), true) != gasFound.amount) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (toReturn.getItem() instanceof ISecurityItem toReturnItem) {
            for (int i = 0; i < invLength; i++) {
                ItemStack itemstack = inv.getStackInSlot(i);
                if (!itemstack.isEmpty() && itemstack.getItem() instanceof ISecurityItem item) {
                    toReturnItem.setOwnerUUID(toReturn, item.getOwnerUUID(itemstack));
                    toReturnItem.setSecurity(toReturn, item.getSecurity(itemstack));
                    break;
                }
            }
        }

        if (FluidContainerUtils.isFluidContainer(toReturn)) {
            FluidStack fluidFound = null;
            for (int i = 0; i < invLength; i++) {
                ItemStack itemstack = inv.getStackInSlot(i);
                if (FluidContainerUtils.isFluidContainer(itemstack)) {
                    FluidContainerUtils.FluidTransferResult fluidResult = FluidContainerUtils.getTransferableFluid(itemstack);
                    if (!fluidResult.isValid()) {
                        return ItemStack.EMPTY;
                    }
                    FluidStack fluid = fluidResult.getStack();
                    if (fluid != null && fluid.amount > 0) {
                        if (fluidFound == null) {
                            fluidFound = fluid.copy();
                        } else if (fluidFound.getFluid() != fluid.getFluid()) {
                            return ItemStack.EMPTY;
                        } else {
                            fluidFound = FluidContainerUtils.copyWithAmount(fluidFound, fluidFound.amount + fluid.amount);
                        }
                    }
                }
            }

            if (fluidFound != null) {
                IFluidHandlerItem fluidHandler = FluidContainerUtils.getFluidHandlerCapability(toReturn);
                if (fluidHandler == null || fluidHandler.fill(fluidFound.copy(), false) != fluidFound.amount ||
                    fluidHandler.fill(fluidFound.copy(), true) != fluidFound.amount) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (BasicBlockType.get(toReturn) == BasicBlockType.BIN) {
            int foundCount = 0;
            ItemStack foundType = ItemStack.EMPTY;
            for (int i = 0; i < invLength; i++) {
                ItemStack itemstack = inv.getStackInSlot(i);
                if (!itemstack.isEmpty() && BasicBlockType.get(itemstack) == BasicBlockType.BIN) {
                    BinMekanismInventory binInv = BinMekanismInventory.create(itemstack);
                    if (binInv != null) {
                        foundCount = binInv.getItemCount();
                        foundType = binInv.getItemType();
                    }
                }
            }

            if (foundCount > 0 && !foundType.isEmpty()) {
                BinMekanismInventory binInv = BinMekanismInventory.create(toReturn);
                if (binInv != null) {
                    binInv.setItemCount(foundCount);
                    binInv.setItemType(foundType);
                }
            }
        }

        if (MachineType.get(toReturn) != null && MachineType.get(toReturn).supportsUpgrades) {
            Map<Upgrade, Integer> upgrades = new LinkedHashMap<>();
            for (int i = 0; i < invLength; i++) {
                ItemStack itemstack = inv.getStackInSlot(i);
                if (!itemstack.isEmpty() && MachineType.get(itemstack) != null && MachineType.get(itemstack).supportsUpgrades) {
                    Upgrade.buildComponentMap(ItemDataUtils.getDataMapIfPresent(itemstack)).entrySet().forEach(entry -> {
                        if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                            upgrades.compute(entry.getKey(), (k, val) -> Math.min(entry.getKey().getMaxInstalled(), (val != null ? val : 0) + entry.getValue()));
                        }
                    });
                }
            }
            if (!upgrades.isEmpty() || Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(toReturn))) {
                Upgrade.saveComponentMap(upgrades, ItemDataUtils.getDataMap(toReturn));
            }
        }

        if (toReturn.getItem() instanceof IQIODriveItem outputDrive) {
            IQIODriveItem sourceDrive = null;
            ItemStack sourceStack = ItemStack.EMPTY;
            UUID sourceId = null;
            for (int i = 0; i < invLength; i++) {
                ItemStack input = inv.getStackInSlot(i);
                if (input.isEmpty() || !(input.getItem() instanceof IQIODriveItem inputDrive)) {
                    continue;
                }
                UUID driveId = inputDrive.getDriveId(input);
                if (driveId == null) {
                    continue;
                }
                // Consuming more than one UUID-bearing source would orphan at
                // least one external record. A stacked source would leave a
                // second physical item carrying the inherited UUID behind.
                if (sourceId != null || input.getCount() != 1) {
                    return ItemStack.EMPTY;
                }
                sourceDrive = inputDrive;
                sourceStack = input;
                sourceId = driveId;
            }
            if (sourceId != null) {
                if (toReturn.getCount() != 1 || sourceDrive == null ||
                      !isQIODriveUpgrade(sourceDrive, sourceStack, outputDrive, toReturn)) {
                    return ItemStack.EMPTY;
                }
                outputDrive.setDriveId(toReturn, sourceId);
                IQIODriveItem.DriveMetadata metadata = sourceDrive.getDriveMetadata(sourceStack);
                outputDrive.setDriveMetadata(toReturn, metadata.getCount(), metadata.getTypes(), metadata.getStorageUnits());
            }
        }

        return toReturn;
    }

    private static boolean isQIODriveUpgrade(IQIODriveItem inputDrive, ItemStack input,
          IQIODriveItem outputDrive, ItemStack output) {
        QIODriveSpecialization inputType = inputDrive.getDriveSpecialization(input);
        QIODriveSpecialization outputType = outputDrive.getDriveSpecialization(output);
        if (!QIODriveSpecializationRegistry.INSTANCE.isRegistered(inputType) ||
              !QIODriveSpecializationRegistry.INSTANCE.isRegistered(outputType) ||
              !inputType.getRegistryName().equals(outputType.getRegistryName()) ||
              !QIODriveDefinition.isRegistered(inputDrive.getDriveDefinition(input)) ||
              !QIODriveDefinition.isRegistered(outputDrive.getDriveDefinition(output))) {
            return false;
        }
        QIOAmount inputCountCapacity = inputDrive.getExactCountCapacity(input);
        QIOAmount outputCountCapacity = outputDrive.getExactCountCapacity(output);
        QIOAmount inputStorageCapacity = inputDrive.getExactStorageCapacity(input);
        QIOAmount outputStorageCapacity = outputDrive.getExactStorageCapacity(output);
        int inputTypeCapacity = inputDrive.getTypeCapacity(input);
        int outputTypeCapacity = outputDrive.getTypeCapacity(output);
        return outputCountCapacity.compareTo(inputCountCapacity) >= 0 &&
              outputStorageCapacity.compareTo(inputStorageCapacity) >= 0 && outputTypeCapacity >= inputTypeCapacity &&
              (outputCountCapacity.compareTo(inputCountCapacity) > 0 ||
                    outputStorageCapacity.compareTo(inputStorageCapacity) > 0 || outputTypeCapacity > inputTypeCapacity);
    }

    public static IRecipe getRecipeFromGrid(InventoryCrafting inv, World world) {
        return CraftingManager.findMatchingRecipe(inv, world);
    }
}
