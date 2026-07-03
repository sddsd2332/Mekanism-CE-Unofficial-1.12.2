package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.ItemStackToEnergyRecipe;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class EnergyInventorySlot extends BasicInventorySlot {

    @Nullable
    private static ItemStackToEnergyRecipe getConversionRecipe(@Nullable Object world, ItemStack stack) {
        return RecipeHandler.getItemStackToEnergyRecipe(stack);
    }

    private static double getPotentialConversion(@Nullable Object world, ItemStack stack) {
        ItemStackToEnergyRecipe recipe = getConversionRecipe(world, stack);
        return recipe != null && StackUtils.equalsWildcardWithNBT(recipe.getInput().ingredient, stack) ? recipe.getOutput().energyOutput : 0;
    }

    public static EnergyInventorySlot fillOrConvert(IEnergyContainer energyContainer, Supplier<?> worldSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        Objects.requireNonNull(energyContainer, "Energy container cannot be null");
        return new EnergyInventorySlot(energyContainer, worldSupplier,
              stack -> !fillInsertCheck(stack) && getPotentialConversion(worldSupplier.get(), stack) <= 0,
              stack -> fillInsertCheck(stack) || getPotentialConversion(worldSupplier.get(), stack) > 0,
              stack -> isEnergyContainerItem(stack) || getPotentialConversion(worldSupplier.get(), stack) > 0, listener, x, y, true);
    }

    public static EnergyInventorySlot fill(IEnergyContainer energyContainer, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(energyContainer, "Energy container cannot be null");
        return fill(energyContainer, EnergyInventorySlot::fillExtractCheck, listener, x, y);
    }

    public static EnergyInventorySlot fill(IEnergyContainer energyContainer, Predicate<ItemStack> canExtract, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(energyContainer, "Energy container cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        return new EnergyInventorySlot(energyContainer, canExtract, EnergyInventorySlot::fillInsertCheck, EnergyInventorySlot::isEnergyContainerItem, listener, x, y,
              true);
    }

    public static EnergyInventorySlot drain(IEnergyContainer energyContainer, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(energyContainer, "Energy container cannot be null");
        return drain(energyContainer, stack -> drainExtractCheck(energyContainer, stack), listener, x, y);
    }

    public static EnergyInventorySlot drain(IEnergyContainer energyContainer, Predicate<ItemStack> canExtract, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(energyContainer, "Energy container cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        return new EnergyInventorySlot(energyContainer, canExtract, stack -> drainInsertCheck(energyContainer, stack), EnergyInventorySlot::isEnergyContainerItem,
              listener, x, y, false);
    }

    public static boolean isEnergyContainerItem(ItemStack stack) {
        return EnergyCompatUtils.hasStrictEnergyHandler(stack);
    }

    public static boolean fillInsertCheck(ItemStack stack) {
        IStrictEnergyHandler energyHandler = EnergyCompatUtils.getStrictEnergyHandler(stack);
        return energyHandler != null && energyHandler.extractEnergy(Double.MAX_VALUE, Action.SIMULATE) > 0;
    }

    public static boolean fillExtractCheck(ItemStack stack) {
        return !fillInsertCheck(stack);
    }

    public static boolean drainInsertCheck(ItemStack stack) {
        IStrictEnergyHandler energyHandler = EnergyCompatUtils.getStrictEnergyHandler(stack);
        if (energyHandler != null) {
            for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
                if (energyHandler.getNeededEnergy(container) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean drainInsertCheck(IEnergyContainer energyContainer, ItemStack stack) {
        Objects.requireNonNull(energyContainer, "Energy container cannot be null");
        IStrictEnergyHandler energyHandler = EnergyCompatUtils.getStrictEnergyHandler(stack);
        if (energyHandler != null) {
            double storedEnergy = energyContainer.getEnergy();
            double neededEnergy = getNeededEnergy(energyHandler);
            if (storedEnergy <= 0) {
                return neededEnergy > 0;
            }
            double toTransfer = Math.min(storedEnergy, neededEnergy);
            return toTransfer > 0 && energyHandler.insertEnergy(toTransfer, Action.SIMULATE) < toTransfer;
        }
        return false;
    }

    public static boolean drainExtractCheck(ItemStack stack) {
        return !drainInsertCheck(stack);
    }

    public static boolean drainExtractCheck(IEnergyContainer energyContainer, ItemStack stack) {
        return !drainInsertCheck(energyContainer, stack);
    }

    private final Supplier<?> worldSupplier;
    private final IEnergyContainer energyContainer;
    private final boolean fillContainer;

    private EnergyInventorySlot(IEnergyContainer energyContainer, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert, Predicate<ItemStack> validator,
          @Nullable IContentsListener listener, int x, int y, boolean fillContainer) {
        this(energyContainer, () -> null, canExtract, canInsert, validator, listener, x, y, fillContainer);
    }

    private EnergyInventorySlot(IEnergyContainer energyContainer, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y, boolean fillContainer) {
        super(canExtract, canInsert, validator, listener, x, y);
        this.worldSupplier = worldSupplier;
        this.energyContainer = energyContainer;
        this.fillContainer = fillContainer;
        setSlotType(ContainerSlotType.POWER);
        setSlotOverlay(SlotOverlay.POWER);
    }

    public void fillContainer() {
        if (!isEmpty() && fillContainer && energyContainer.getNeeded() > 0) {
            fillContainerFromItem();
        }
    }

    public void drainContainer() {
        if (!isEmpty() && !fillContainer && energyContainer.getEnergy() > 0) {
            drainContainerIntoItem();
        }
    }

    public void transfer() {
        if (fillContainer) {
            fillContainer();
        } else {
            drainContainer();
        }
    }

    public void fillContainerOrConvert() {
        if (!isEmpty() && fillContainer && energyContainer.getNeeded() > 0 && !fillContainerFromItem()) {
            convertItemToEnergy();
        }
    }

    @Override
    public int growStack(int amount, Action action) {
        return super.growStack(amount, action);
    }

    private boolean fillContainerFromItem() {
        IStrictEnergyHandler energyHandler = EnergyCompatUtils.getStrictEnergyHandler(current);
        if (energyHandler != null) {
            boolean didTransfer = false;
            for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
                double needed = energyContainer.getNeeded();
                if (needed <= 0) {
                    break;
                }
                double energyInItem = energyHandler.extractEnergy(container, needed, Action.SIMULATE);
                if (energyInItem <= 0) {
                    continue;
                }
                double simulatedRemainder = energyContainer.insert(energyInItem, Action.SIMULATE, AutomationType.INTERNAL);
                if (simulatedRemainder >= energyInItem) {
                    continue;
                }
                double extracted = energyHandler.extractEnergy(container, energyInItem - simulatedRemainder, Action.EXECUTE);
                if (extracted <= 0) {
                    continue;
                }
                didTransfer |= energyContainer.insert(extracted, Action.EXECUTE, AutomationType.INTERNAL) < extracted;
                if (energyContainer.getNeeded() <= 0) {
                    break;
                }
            }
            if (didTransfer) {
                onContentsChanged();
                return true;
            }
            return false;
        }
        return false;
    }

    private void drainContainerIntoItem() {
        IStrictEnergyHandler energyHandler = EnergyCompatUtils.getStrictEnergyHandler(current);
        if (energyHandler != null) {
            double storedEnergy = Math.min(energyContainer.getEnergy(), getNeededEnergy(energyHandler));
            if (storedEnergy <= 0) {
                return;
            }
            double simulatedRemainder = energyHandler.insertEnergy(storedEnergy, Action.SIMULATE);
            double toTransfer = storedEnergy - simulatedRemainder;
            if (toTransfer > 0) {
                double extracted = energyContainer.extract(toTransfer, Action.EXECUTE, AutomationType.INTERNAL);
                if (extracted > 0) {
                    energyHandler.insertEnergy(extracted, Action.EXECUTE);
                    onContentsChanged();
                }
            }
            return;
        }
    }

    private static double getNeededEnergy(IStrictEnergyHandler energyHandler) {
        double needed = 0;
        for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
            needed += energyHandler.getNeededEnergy(container);
        }
        return needed;
    }

    private void convertItemToEnergy() {
        ItemStackToEnergyRecipe recipe = getConversionRecipe(worldSupplier.get(), current);
        if (recipe == null) {
            return;
        }
        ItemStack recipeInput = recipe.getInput().ingredient;
        if (!MachineInput.inputContains(current, recipeInput)) {
            return;
        }
        double output = recipe.getOutput().energyOutput;
        int operations = getSupportedConversionOperations(output, recipeInput.getCount());
        if (operations <= 0) {
            return;
        }
        double toInsert = output * operations;
        MekanismUtils.logMismatchedStackSize((long) energyContainer.insert(toInsert, Action.EXECUTE, AutomationType.MANUAL), 0);
        int amountUsed = recipeInput.getCount() * operations;
        MekanismUtils.logMismatchedStackSize(shrinkStack(amountUsed, Action.EXECUTE), amountUsed);
    }

    private int getSupportedConversionOperations(double output, int amountUsed) {
        if (output <= 0 || amountUsed <= 0) {
            return 0;
        }
        int maxOperations = current.getCount() / amountUsed;
        if (!MekanismConfig.current().general.bulkSlotItemConversion.val()) {
            maxOperations = Math.min(maxOperations, 1);
        }
        if (maxOperations <= 0) {
            return 0;
        }
        double toInsert = output * maxOperations;
        double accepted = toInsert - energyContainer.insert(toInsert, Action.SIMULATE, AutomationType.MANUAL);
        if (accepted < output) {
            return 0;
        }
        return Math.min(maxOperations, (int) Math.floor(accepted / output));
    }
}
