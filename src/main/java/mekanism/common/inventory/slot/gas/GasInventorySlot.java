package mekanism.common.inventory.slot.gas;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.gas.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.IFluidHandlerSlot;
import mekanism.common.inventory.slot.IGasHandlerSlot;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.*;

/**
 * Gas-only high-version bridge for inventory slots.
 */
public class GasInventorySlot extends GasHandlerInventorySlot {

    @Nullable
    public static IGasHandler getCapability(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
    }

    @Nullable
    public static IGasHandler getUnstackedCapability(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ItemStack toCheck = stack.getCount() > 1 ? StackUtils.size(stack, 1) : stack.copy();
        return toCheck.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
    }

    @Nullable
    public static IMekanismGasHandler getMekanismCapability(ItemStack stack) {
        IGasHandler gasHandler = getCapability(stack);
        return gasHandler instanceof IMekanismGasHandler mekanismGasHandler ? mekanismGasHandler : null;
    }

    @Nullable
    public static IMekanismGasHandler getUnstackedMekanismCapability(ItemStack stack) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        return gasHandler instanceof IMekanismGasHandler mekanismGasHandler ? mekanismGasHandler : null;
    }

    public static boolean isGasContainerItem(ItemStack stack) {
        return getTankCount(stack) > 0;
    }

    @Nullable
    public static GasStack getContainedGas(ItemStack stack) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            GasStack contained = null;
            for (int tank = 0, tanks = getTankCount(gasHandler); tank < tanks; tank++) {
                GasStack gasInTank = getGasInTank(gasHandler, tank);
                if (gasInTank == null || gasInTank.amount <= 0) {
                    continue;
                }
                if (contained == null) {
                    contained = gasInTank.copy();
                } else if (contained.isGasEqual(gasInTank)) {
                    contained = contained.copy().withAmount(contained.amount + gasInTank.amount);
                } else {
                    return contained;
                }
            }
            return contained;
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        if (gasItem != null) {
            return getLegacyGas(gasItem, stack);
        }
        return null;
    }

    @Nullable
    public static GasStack getCapabilityStoredGas(ItemStack stack) {
        IMekanismGasHandler gasHandler = getMekanismCapability(stack);
        if (gasHandler != null && gasHandler.getCountGasTanks(null) > 0) {
            return gasHandler.getGasInTank(0, null);
        }
        IMekanismGasHandler unstackedGasHandler = getUnstackedMekanismCapability(stack);
        if (unstackedGasHandler != null && unstackedGasHandler.getCountGasTanks(null) > 0) {
            return unstackedGasHandler.getGasInTank(0, null);
        }
        return null;
    }

    public static boolean setCapabilityStoredGas(ItemStack stack, @Nullable GasStack gasStack) {
        IMekanismGasHandler gasHandler = getMekanismCapability(stack);
        if (gasHandler != null && gasHandler.getCountGasTanks(null) > 0) {
            gasHandler.setGasInTank(0, gasStack, null);
            return true;
        }
        IMekanismGasHandler unstackedGasHandler = getUnstackedMekanismCapability(stack);
        if (unstackedGasHandler != null && unstackedGasHandler.getCountGasTanks(null) > 0) {
            unstackedGasHandler.setGasInTank(0, gasStack, null);
            return true;
        }
        return false;
    }

    @Nullable
    public static GasStack getStoredGas(ItemStack stack, @Nullable String legacyKey) {
        GasStack stored = getCapabilityStoredGas(stack);
        return stored != null ? stored : legacyKey == null ? null : ItemDataUtils.getStoredGas(stack, legacyKey);
    }

    public static boolean setStoredGas(ItemStack stack, @Nullable GasStack gasStack, @Nullable String legacyKey, int capacity) {
        if (setCapabilityStoredGas(stack, gasStack)) {
            return true;
        }
        if (legacyKey != null) {
            ItemDataUtils.setStoredGas(stack, legacyKey, gasStack, capacity);
            return true;
        }
        return false;
    }

    public static boolean setGasContained(ItemStack stack, @Nullable GasStack gasStack) {
        if (setCapabilityStoredGas(stack, gasStack)) {
            return true;
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        if (gasItem != null) {
            gasItem.setGas(stack, gasStack);
            return true;
        }
        return false;
    }

    public static int getTankCount(ItemStack stack) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            return getTankCount(gasHandler);
        }
        return getLegacyGasItem(stack) == null ? 0 : 1;
    }

    public static int getTankCount(@Nullable IGasHandler gasHandler) {
        if (gasHandler instanceof IExtendedGasHandler extendedGasHandler) {
            return extendedGasHandler.getCountGasTanks();
        }
        return gasHandler == null ? 0 : gasHandler.getLegacyTankCount();
    }

    @Nullable
    public static GasStack getGasInTank(ItemStack stack, int tank) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            return getGasInTank(gasHandler, tank);
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        if (tank == 0 && gasItem != null) {
            return getLegacyGas(gasItem, getLegacySingleStack(stack));
        }
        return null;
    }

    @Nullable
    public static GasStack getGasInTank(@Nullable IGasHandler gasHandler, int tank) {
        if (gasHandler instanceof IExtendedGasHandler extendedGasHandler) {
            return extendedGasHandler.getGasInTank(tank);
        }
        return gasHandler == null ? null : gasHandler.getLegacyGasInTank(tank);
    }

    public static int getTankCapacity(ItemStack stack, int tank) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            return getTankCapacity(gasHandler, tank);
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        if (tank == 0 && gasItem != null) {
            return getLegacyMaxGas(gasItem, getLegacySingleStack(stack));
        }
        return 0;
    }

    public static int getTankCapacity(@Nullable IGasHandler gasHandler, int tank) {
        if (gasHandler instanceof IExtendedGasHandler extendedGasHandler) {
            return extendedGasHandler.getGasTankCapacity(tank);
        }
        return gasHandler == null ? 0 : gasHandler.getLegacyTankCapacity(tank);
    }

    public static int insertGas(ItemStack stack, GasStack gasStack, boolean doTransfer) {
        if (stack.isEmpty() || gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null) {
            return 0;
        }
        IGasHandler gasHandler = getCapability(stack);
        if (gasHandler != null) {
            return insertGas(gasHandler, gasStack.copy(), doTransfer);
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        GasStack stored = getLegacyGas(gasItem, stack);
        if (gasItem != null && gasItem.canReceiveGas(stack, gasStack.getGas()) &&
            (stored == null || stored.amount != getLegacyMaxGas(gasItem, stack))) {
            ItemStack target = doTransfer ? stack : stack.getCount() > 1 ? StackUtils.size(stack, 1) : stack.copy();
            return gasItem.addGas(target, gasStack.copy());
        }
        return 0;
    }

    public static int insertGas(@Nullable IGasHandler gasHandler, @Nullable GasStack gasStack, boolean doTransfer) {
        return insertGas(gasHandler, null, gasStack, doTransfer);
    }

    public static int insertGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable GasStack gasStack, boolean doTransfer) {
        if (gasHandler == null || gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null) {
            return 0;
        }
        Action action = Action.get(doTransfer);
        if (gasHandler instanceof IMekanismGasHandler mekanismGasHandler) {
            GasStack remainder = mekanismGasHandler.insertGas(gasStack.copy(), side, action);
            return gasStack.amount - getAmount(remainder);
        } else if (gasHandler instanceof IExtendedGasHandler extendedGasHandler) {
            GasStack remainder = extendedGasHandler.insertGas(gasStack.copy(), action);
            return gasStack.amount - getAmount(remainder);
        }
        return clampTransferredAmount(gasHandler.receiveGas(side, gasStack.copy(), doTransfer), gasStack.amount);
    }

    @Nullable
    public static GasStack useGas(ItemStack stack, @Nullable Gas type, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return null;
        }
        IGasHandler gasHandler = getCapability(stack);
        if (gasHandler != null) {
            return useGas(gasHandler, type, amount);
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        if (gasItem != null) {
            GasStack stored = getLegacyGas(gasItem, stack);
            if (stored == null || stored.amount <= 0 || type != null && stored.getGas() != type || !gasItem.canProvideGas(stack, type)) {
                return null;
            }
            return gasItem.removeGas(stack, amount);
        }
        return null;
    }

    @Nullable
    public static GasStack extractGas(@Nullable IGasHandler gasHandler, @Nullable Gas type, int amount, boolean doTransfer) {
        return extractGas(gasHandler, null, type, amount, doTransfer);
    }

    @Nullable
    public static GasStack extractGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable Gas type, int amount, boolean doTransfer) {
        if (gasHandler == null || amount <= 0) {
            return null;
        }
        Action action = Action.get(doTransfer);
        if (gasHandler instanceof IMekanismGasHandler mekanismGasHandler) {
            return type == null ? mekanismGasHandler.extractGas(amount, side, action) : mekanismGasHandler.extractGas(new GasStack(type, amount), side, action);
        } else if (gasHandler instanceof IExtendedGasHandler extendedGasHandler) {
            return type == null ? extendedGasHandler.extractGas(amount, action) : extendedGasHandler.extractGas(new GasStack(type, amount), action);
        }
        GasStack simulated = gasHandler.drawGas(side, amount, false);
        if (!isMatchingGas(simulated, type)) {
            return null;
        }
        if (!doTransfer) {
            return simulated;
        }
        GasStack extracted = gasHandler.drawGas(side, simulated.amount, true);
        return isMatchingGas(extracted, type) ? extracted : null;
    }

    @Nullable
    public static GasStack useGas(@Nullable IGasHandler gasHandler, @Nullable Gas type, int amount) {
        return extractGas(gasHandler, type, amount, true);
    }

    public static boolean canProvideGas(ItemStack stack, @Nullable Gas type) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            return extractGas(gasHandler, type, 1, false) != null;
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        return gasItem != null && gasItem.canProvideGas(stack, type);
    }

    public static boolean canReceiveGas(ItemStack stack, @Nullable Gas type) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            return canReceiveGas(gasHandler, type);
        }
        IGasItem gasItem = getLegacyGasItem(stack);
        if (gasItem == null) {
            return false;
        } else if (type == null) {
            return gasItem.canReceiveGas(stack, null);
        }
        return insertGas(stack, new GasStack(type, 1), false) > 0;
    }

    public static boolean canReceiveGas(@Nullable IGasHandler gasHandler, @Nullable Gas type) {
        return canReceiveGas(gasHandler, null, type);
    }

    public static boolean canReceiveGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable Gas type) {
        if (gasHandler == null) {
            return false;
        } else if (type == null) {
            for (int tank = 0, tanks = getTankCount(gasHandler); tank < tanks; tank++) {
                GasStack stored = getGasInTank(gasHandler, tank);
                if (stored == null || stored.amount < getTankCapacity(gasHandler, tank)) {
                    return true;
                }
            }
            return false;
        }
        return insertGas(gasHandler, side, new GasStack(type, 1), false) > 0;
    }

    public static boolean canReceiveGas(@Nullable IGasHandler gasHandler, @Nullable EnumFacing side, @Nullable GasStack stack) {
        return insertGas(gasHandler, side, stack, false) > 0;
    }

    public static GasTransferResult getTransferableGas(ItemStack stack) {
        if (stack.isEmpty()) {
            return GasTransferResult.empty();
        }
        ItemStack simulatedInput = stack.getCount() > 1 ? StackUtils.size(stack, 1) : stack.copy();
        IGasHandler gasHandler = getCapability(simulatedInput);
        GasStack gasFound = null;
        for (int tank = 0, tanks = gasHandler == null ? getTankCount(simulatedInput) : getTankCount(gasHandler); tank < tanks; tank++) {
            GasStack stored = gasHandler == null ? getGasInTank(simulatedInput, tank) : getGasInTank(gasHandler, tank);
            if (stored == null || stored.amount <= 0) {
                continue;
            }
            GasStack extracted = gasHandler == null ? useGas(simulatedInput, stored.getGas(), stored.amount) : useGas(gasHandler, stored.getGas(), stored.amount);
            if (extracted == null || extracted.amount <= 0) {
                continue;
            }
            if (gasFound == null) {
                gasFound = extracted.copy();
            } else if (gasFound.getGas() != extracted.getGas()) {
                return GasTransferResult.invalid();
            } else {
                gasFound = gasFound.copy().withAmount(gasFound.amount + extracted.amount);
            }
        }
        return GasTransferResult.of(gasFound);
    }

    @Nullable
    public static GasStack getContainedGas(ItemStack stack, @Nullable Gas type, @Nullable String legacyKey) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            GasStack contained = null;
            for (int tank = 0, tanks = getTankCount(gasHandler); tank < tanks; tank++) {
                GasStack stored = getGasInTank(gasHandler, tank);
                if (stored != null && stored.amount > 0 && (type == null || stored.getGas() == type)) {
                    if (contained == null) {
                        contained = stored.copy();
                    } else if (contained.isGasEqual(stored)) {
                        contained = contained.copy().withAmount(contained.amount + stored.amount);
                    } else {
                        return contained;
                    }
                }
            }
            return contained;
        }
        GasStack stored = legacyKey == null ? null : getStoredGas(stack, legacyKey);
        if (stored == null) {
            IGasItem gasItem = getLegacyGasItem(stack);
            if (gasItem != null) {
                stored = getLegacyGas(gasItem, getLegacySingleStack(stack));
            }
        }
        if (stored != null && stored.amount > 0 && (type == null || stored.getGas() == type)) {
            return stored.copy();
        }
        return null;
    }

    @Nullable
    public static GasStack getContainedGas(ItemStack stack, @Nullable Gas type) {
        return getContainedGas(stack, type, null);
    }

    @Nullable
    public static <T> T getExtractableGas(ItemStack stack, int needed, BiFunction<Gas, Integer, T> getIfValid) {
        if (stack.isEmpty() || needed <= 0) {
            return null;
        }
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (gasHandler != null) {
            for (int tank = 0, tanks = getTankCount(gasHandler); tank < tanks; tank++) {
                GasStack gas = getGasInTank(gasHandler, tank);
                if (gas == null || gas.amount <= 0) {
                    continue;
                }
                GasStack simulatedExtraction = extractGas(gasHandler, gas.getGas(), Math.min(needed, gas.amount), false);
                if (simulatedExtraction != null && simulatedExtraction.amount > 0) {
                    T result = getIfValid.apply(simulatedExtraction.getGas(), simulatedExtraction.amount);
                    if (result != null) {
                        return result;
                    }
                }
            }
        } else {
            IGasItem item = getLegacyGasItem(stack);
            if (item == null) {
                return null;
            }
            ItemStack singleStack = getLegacySingleStack(stack);
            GasStack gas = getLegacyGas(item, singleStack);
            if (gas != null && gas.amount > 0 && item.canProvideGas(singleStack, gas.getGas())) {
                int amount = Math.min(needed, Math.min(gas.amount, item.getRate(singleStack)));
                if (amount > 0) {
                    return getIfValid.apply(gas.getGas(), amount);
                }
            }
        }
        return null;
    }

    /**
     * Drains the tank depending on if this item has any contents in it AND if the supplied boolean's mode supports it.
     */
    public static GasInventorySlot rotaryDrain(IExtendedGasTank gasTank, BooleanSupplier modeSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null");
        Predicate<ItemStack> drainInsertPredicate = getDrainInsertPredicate(gasTank, GasInventorySlot::getCapability);
        Predicate<ItemStack> insertPredicate = stack -> modeSupplier.getAsBoolean() && drainInsertPredicate.test(stack);
        return new GasInventorySlot(gasTank, insertPredicate.negate(), insertPredicate, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    /**
     * Fills the tank depending on if this item has any contents in it AND if the supplied boolean's mode supports it.
     */
    public static GasInventorySlot rotaryFill(IExtendedGasTank gasTank, BooleanSupplier modeSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null");
        return new GasInventorySlot(gasTank, getFillExtractPredicate(gasTank, GasInventorySlot::getCapability),
              stack -> !modeSupplier.getAsBoolean() && fillInsertCheck(gasTank, stack, GasInventorySlot::getCapability), GAS_ITEM_VALIDATOR, listener, x, y);
    }

    /**
     * Fills the tank from this item.
     */
    public static GasInventorySlot fill(IExtendedGasTank gasTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        return new GasInventorySlot(gasTank, getFillExtractPredicate(gasTank, GasInventorySlot::getCapability),
              stack -> fillInsertCheck(gasTank, stack, GasInventorySlot::getCapability), GAS_ITEM_VALIDATOR, listener, x, y);
    }

    public static boolean fillExtractCheck(IExtendedGasTank gasTank, ItemStack stack) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        return getFillExtractPredicate(gasTank, GasInventorySlot::getCapability).test(stack);
    }

    public static boolean fillExtractCheck(IExtendedGasTank gasTank, @Nullable IGasHandler handler) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        return !fillInsertCheck(gasTank, handler);
    }

    public static Predicate<ItemStack> getFillExtractPredicate(IExtendedGasTank gasTank, Function<ItemStack, IGasHandler> handlerFunction) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(handlerFunction, "Gas handler function cannot be null");
        return stack -> !fillInsertCheck(gasTank, stack, handlerFunction);
    }

    public static boolean fillInsertCheck(IExtendedGasTank gasTank, @Nullable IGasHandler handler) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        if (handler != null) {
            for (int tank = 0, tanks = getTankCount(handler); tank < tanks; tank++) {
                GasStack gasStack = getGasInTank(handler, tank);
                if (getAcceptedAmount(gasTank, gasStack) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean fillInsertCheck(IExtendedGasTank gasTank, ItemStack stack) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        IGasHandler handler = getCapability(stack);
        if (handler != null) {
            return fillInsertCheck(gasTank, handler);
        }
        return canFillTankFromItem(gasTank, stack);
    }

    public static boolean fillInsertCheck(IExtendedGasTank gasTank, ItemStack stack, Function<ItemStack, IGasHandler> handlerFunction) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(handlerFunction, "Gas handler function cannot be null");
        IGasHandler handler = handlerFunction.apply(stack);
        return handler == null ? fillInsertCheck(gasTank, stack) : fillInsertCheck(gasTank, handler);
    }

    public static boolean fillOrConvertInsertCheck(IExtendedGasTank gasTank, Supplier<?> worldSupplier, ItemStack stack) {
        return getFillOrConvertInsertPredicate(gasTank, GasInventorySlot::getCapability, worldSupplier).test(stack);
    }

    /**
     * Accepts any items that can be filled with the current contents of the gas tank, or if it is a gas tank container and the tank is currently empty.
     * Drains the tank into this item.
     */
    public static GasInventorySlot drain(IExtendedGasTank gasTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Predicate<ItemStack> insertPredicate = getDrainInsertPredicate(gasTank, GasInventorySlot::getCapability);
        return new GasInventorySlot(gasTank, insertPredicate.negate(), insertPredicate, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    public static boolean drainInsertCheck(IExtendedGasTank gasTank, ItemStack stack) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        return canDrainTankIntoItem(gasTank, stack);
    }

    public static boolean drainExtractCheck(IExtendedGasTank gasTank, ItemStack stack) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        return !drainInsertCheck(gasTank, stack);
    }

    public static Predicate<ItemStack> getDrainInsertPredicate(IExtendedGasTank gasTank, Function<ItemStack, IGasHandler> handlerFunction) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(handlerFunction, "Gas handler function cannot be null");
        return stack -> drainInsertCheck(gasTank, stack, handlerFunction);
    }

    public static boolean drainInsertCheck(IExtendedGasTank gasTank, ItemStack stack, Function<ItemStack, IGasHandler> handlerFunction) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(handlerFunction, "Gas handler function cannot be null");
        IGasHandler handler = handlerFunction.apply(stack);
        if (handler == null && !isGasContainerItem(stack)) {
            return false;
        }
        GasStack stored = gasTank.getGas();
        if (stored == null) {
            int tanks = handler == null ? getTankCount(stack) : getTankCount(handler);
            for (int tank = 0; tank < tanks; tank++) {
                GasStack gasInTank = handler == null ? getGasInTank(stack, tank) : getGasInTank(handler, tank);
                int tankCapacity = handler == null ? getTankCapacity(stack, tank) : getTankCapacity(handler, tank);
                if (getAmount(gasInTank) < tankCapacity) {
                    return true;
                }
            }
            return false;
        }
        return (handler == null ? insertGas(stack, stored, false) : insertGas(handler, stored, false)) > 0;
    }

    public static boolean fillTank(IInventorySlot slot, IExtendedGasTank gasTank) {
        Objects.requireNonNull(slot, "Inventory slot cannot be null");
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        if (slot.isEmpty() || gasTank.getNeeded() <= 0) {
            return false;
        }
        ItemStack current = slot.getStack();
        IGasHandler unstackedGasHandler = getUnstackedCapability(current);
        if (unstackedGasHandler != null) {
            if (slot.getCount() != 1) {
                return false;
            }
            ItemStack stack = current.copy();
            IGasHandler gasHandler = getCapability(stack);
            if (transferItemGasToTank(stack, gasHandler, gasTank)) {
                slot.setStack(stack);
                setFilling(slot, true);
                return true;
            }
            return false;
        }
        if (!hasGasHandlerOrLegacy(current, unstackedGasHandler)) {
            return false;
        }
        ItemStack stack = current.copy();
        if (transferItemGasToTank(stack, null, gasTank)) {
            slot.setStack(stack);
            setFilling(slot, true);
            return true;
        }
        return false;
    }

    public static boolean fillTank(IInventorySlot slot, IExtendedGasTank gasTank, IInventorySlot outputSlot) {
        Objects.requireNonNull(slot, "Inventory slot cannot be null");
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(outputSlot, "Output slot cannot be null");
        if (slot.isEmpty() || gasTank.getNeeded() <= 0) {
            return false;
        }
        ItemStack inputCopy = StackUtils.size(slot.getStack().copy(), 1);
        IGasHandler gasHandler = getCapability(inputCopy);
        if (gasHandler != null) {
            if (transferSingleItemGasToTankAndMove(slot, outputSlot, inputCopy, gasHandler, gasTank)) {
                setFilling(slot, true);
                return true;
            }
            return false;
        }
        if (!isGasContainerItem(inputCopy)) {
            return false;
        }
        if (transferSingleItemGasToTankAndMove(slot, outputSlot, inputCopy, null, gasTank)) {
            setFilling(slot, true);
            return true;
        }
        return false;
    }

    public static boolean drainTank(IInventorySlot slot, IExtendedGasTank gasTank) {
        return drainTank(slot, gasTank, true);
    }

    public static boolean drainTank(IInventorySlot slot, IExtendedGasTank gasTank, boolean doDrain) {
        Objects.requireNonNull(slot, "Inventory slot cannot be null");
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        if (slot.isEmpty() || gasTank.getGas() == null) {
            return false;
        }
        ItemStack current = slot.getStack();
        IGasHandler unstackedGasHandler = getUnstackedCapability(current);
        if (unstackedGasHandler != null) {
            if (slot.getCount() != 1) {
                return false;
            }
            ItemStack stack = current.copy();
            IGasHandler gasHandler = getCapability(stack);
            if (fillItemFromGasTank(stack, gasHandler, gasTank, doDrain)) {
                if (doDrain) {
                    slot.setStack(stack);
                    setDraining(slot, true);
                }
                return true;
            }
            return false;
        }
        if (!hasGasHandlerOrLegacy(current, unstackedGasHandler)) {
            return false;
        }
        ItemStack stack = current.copy();
        if (fillItemFromGasTank(stack, null, gasTank, doDrain)) {
            if (doDrain) {
                slot.setStack(stack);
                setDraining(slot, true);
            }
            return true;
        }
        return false;
    }

    public static boolean drainTank(IInventorySlot slot, IExtendedGasTank gasTank, IInventorySlot outputSlot) {
        Objects.requireNonNull(slot, "Inventory slot cannot be null");
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(outputSlot, "Output slot cannot be null");
        if (slot.isEmpty() || gasTank.getGas() == null) {
            return false;
        }
        ItemStack inputCopy = StackUtils.size(slot.getStack().copy(), 1);
        IGasHandler gasHandler = getCapability(inputCopy);
        if (gasHandler != null) {
            if (fillSingleItemFromGasTankAndMove(slot, outputSlot, inputCopy, gasHandler, gasTank)) {
                setDraining(slot, true);
                return true;
            }
            return false;
        }
        if (!isGasContainerItem(inputCopy)) {
            return false;
        }
        if (fillSingleItemFromGasTankAndMove(slot, outputSlot, inputCopy, null, gasTank)) {
            setDraining(slot, true);
            return true;
        }
        return false;
    }

    public static boolean fillTankOrConvert(IInventorySlot slot, IExtendedGasTank gasTank) {
        return fillTankOrConvert(slot, gasTank, () -> null);
    }

    public static boolean fillTankOrConvert(IInventorySlot slot, IExtendedGasTank gasTank, Supplier<?> worldSupplier) {
        Objects.requireNonNull(slot, "Inventory slot cannot be null");
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        if (!slot.isEmpty() && gasTank.getNeeded() > 0) {
            if (fillTank(slot, gasTank)) {
                return true;
            }
            GasStack conversion = getValidConversion(gasTank, worldSupplier, slot.getStack());
            if (conversion != null) {
                int operations = getSupportedConversionOperations(gasTank, conversion, slot.getCount());
                if (operations > 0) {
                    GasStack toInsert = conversion.copy().withAmount(conversion.amount * operations);
                    MekanismUtils.logMismatchedStackSize(getAmount(gasTank.insert(toInsert, Action.EXECUTE, AutomationType.MANUAL)), 0);
                    MekanismUtils.logMismatchedStackSize(slot.shrinkStack(operations, Action.EXECUTE), operations);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fills the tank from this item OR converts the given item to a gas.
     */
    public static GasInventorySlot fillOrConvert(IExtendedGasTank gasTank, Supplier<?> worldSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        return new GasInventorySlot(gasTank, worldSupplier, getFillOrConvertExtractPredicate(gasTank, GasInventorySlot::getCapability, worldSupplier),
              getFillOrConvertInsertPredicate(gasTank, GasInventorySlot::getCapability, worldSupplier),
              getFillOrConvertValidator(gasTank, worldSupplier), listener, x, y);
    }

    @Override
    public boolean fillTankOrConvert() {
        return fillTankOrConvert(this, getGasTank(), getWorldSupplier());
    }

    @Override
    public boolean fillTank() {
        return fillTank(this, getGasTank());
    }

    @Override
    public boolean drainTank(boolean doDraw) {
        return drainTank(this, getGasTank(), doDraw);
    }

    private static int getAmount(@Nullable GasStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    @Nullable
    private static IGasItem getLegacyGasItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IGasItem gasItem ? gasItem : null;
    }

    private static ItemStack getLegacySingleStack(ItemStack stack) {
        return stack.getCount() > 1 ? StackUtils.size(stack, 1) : stack;
    }

    @Nullable
    private static GasStack getLegacyGas(@Nullable IGasItem gasItem, ItemStack stack) {
        return gasItem == null ? null : gasItem.getGas(stack);
    }

    private static int getLegacyMaxGas(@Nullable IGasItem gasItem, ItemStack stack) {
        return gasItem == null ? 0 : gasItem.getMaxGas(stack);
    }

    private static int clampTransferredAmount(int transferred, int maxAmount) {
        return Math.max(0, Math.min(transferred, maxAmount));
    }

    private static boolean isMatchingGas(@Nullable GasStack gasStack, @Nullable Gas type) {
        return gasStack != null && gasStack.amount > 0 && (type == null || gasStack.getGas() == type);
    }

    static int getAcceptedAmount(IExtendedGasTank gasTank, @Nullable GasStack gasStack) {
        if (getAmount(gasStack) <= 0 || !gasTank.isValid(gasStack)) {
            return 0;
        }
        GasStack simulatedRemainder = gasTank.insert(gasStack, Action.SIMULATE, AutomationType.INTERNAL);
        return gasStack.amount - getAmount(simulatedRemainder);
    }

    private static boolean hasGasHandlerOrLegacy(ItemStack stack, @Nullable IGasHandler gasHandler) {
        return gasHandler != null || isGasContainerItem(stack);
    }

    private static int getTankCount(ItemStack stack, @Nullable IGasHandler gasHandler) {
        return gasHandler == null ? getTankCount(stack) : getTankCount(gasHandler);
    }

    @Nullable
    private static GasStack getGasInTank(ItemStack stack, @Nullable IGasHandler gasHandler, int tank) {
        return gasHandler == null ? getGasInTank(stack, tank) : getGasInTank(gasHandler, tank);
    }

    private static int getTankCapacity(ItemStack stack, @Nullable IGasHandler gasHandler, int tank) {
        return gasHandler == null ? getTankCapacity(stack, tank) : getTankCapacity(gasHandler, tank);
    }

    private static boolean canFillTankFromItem(IExtendedGasTank gasTank, ItemStack stack) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (!hasGasHandlerOrLegacy(stack, gasHandler)) {
            return false;
        }
        for (int tank = 0, tanks = getTankCount(stack, gasHandler); tank < tanks; tank++) {
            GasStack gasStack = getGasInTank(stack, gasHandler, tank);
            if (getAcceptedAmount(gasTank, gasStack) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean canDrainTankIntoItem(IExtendedGasTank gasTank, ItemStack stack) {
        IGasHandler gasHandler = getUnstackedCapability(stack);
        if (!hasGasHandlerOrLegacy(stack, gasHandler)) {
            return false;
        }
        GasStack stored = gasTank.getGas();
        if (stored == null) {
            for (int tank = 0, tanks = getTankCount(stack, gasHandler); tank < tanks; tank++) {
                GasStack gasInTank = getGasInTank(stack, gasHandler, tank);
                if (getAmount(gasInTank) < getTankCapacity(stack, gasHandler, tank)) {
                    return true;
                }
            }
            return false;
        }
        return simulateInsertIntoItem(stack, gasHandler, stored) > 0;
    }

    private static int getSupportedConversionOperations(IExtendedGasTank gasTank, GasStack conversion, int availableItems) {
        if (conversion == null || conversion.amount <= 0 || availableItems <= 0) {
            return 0;
        }
        int maxOperations = MekanismConfig.current().general.bulkSlotItemConversion.val() ? availableItems : 1;
        long maxAmount = (long) conversion.amount * maxOperations;
        GasStack toInsert = conversion.copy().withAmount((int) Math.min(Integer.MAX_VALUE, maxAmount));
        int accepted = toInsert.amount - getAmount(gasTank.insert(toInsert, Action.SIMULATE, AutomationType.MANUAL));
        if (accepted < conversion.amount) {
            return 0;
        }
        return Math.min(maxOperations, accepted / conversion.amount);
    }

    @Nullable
    private static GasStack extractGasFromItem(ItemStack stack, @Nullable IGasHandler gasHandler, Gas gas, int amount) {
        return gasHandler == null ? useGas(stack, gas, amount) : useGas(gasHandler, gas, amount);
    }

    private static int simulateInsertIntoItem(ItemStack stack, @Nullable IGasHandler gasHandler, GasStack gasStack) {
        return gasHandler == null ? insertGas(stack, gasStack, false) : insertGas(gasHandler, gasStack, false);
    }

    private static int insertIntoItem(ItemStack stack, @Nullable IGasHandler gasHandler, GasStack gasStack) {
        return gasHandler == null ? insertGas(stack, gasStack, true) : insertGas(gasHandler, gasStack, true);
    }

    private static boolean transferItemGasToTank(ItemStack stack, @Nullable IGasHandler gasHandler, IExtendedGasTank gasTank) {
        int tanks = getTankCount(stack, gasHandler);
        boolean transferred = false;
        for (int tank = 0; tank < tanks; tank++) {
            GasStack gasStack = getGasInTank(stack, gasHandler, tank);
            int accepted = getAcceptedAmount(gasTank, gasStack);
            if (accepted <= 0) {
                continue;
            }
            GasStack drained = extractGasFromItem(stack, gasHandler, gasStack.getGas(), accepted);
            if (getAmount(drained) <= 0) {
                continue;
            }
            MekanismUtils.logMismatchedStackSize(getAmount(gasTank.insert(drained, Action.EXECUTE, AutomationType.INTERNAL)), 0);
            transferred = true;
            if (gasTank.getNeeded() <= 0) {
                break;
            }
        }
        return transferred;
    }

    private static boolean transferSingleItemGasToTankAndMove(IInventorySlot slot, IInventorySlot outputSlot, ItemStack stack,
          @Nullable IGasHandler gasHandler, IExtendedGasTank gasTank) {
        int tanks = getTankCount(stack, gasHandler);
        for (int tank = 0; tank < tanks; tank++) {
            GasStack gasStack = getGasInTank(stack, gasHandler, tank);
            int accepted = getAcceptedAmount(gasTank, gasStack);
            if (accepted <= 0) {
                continue;
            }
            GasStack drained = extractGasFromItem(stack, gasHandler, gasStack.getGas(), accepted);
            if (getAmount(drained) <= 0 || !moveItem(slot, outputSlot, stack)) {
                return false;
            }
            MekanismUtils.logMismatchedStackSize(getAmount(gasTank.insert(drained, Action.EXECUTE, AutomationType.INTERNAL)), 0);
            return true;
        }
        return false;
    }

    private static boolean fillItemFromGasTank(ItemStack stack, @Nullable IGasHandler gasHandler, IExtendedGasTank gasTank, boolean doDraw) {
        GasStack gasInTank = gasTank.getGas();
        if (gasInTank == null) {
            return false;
        }
        int toDraw = simulateInsertIntoItem(stack, gasHandler, gasInTank);
        if (toDraw <= 0) {
            return false;
        }
        GasStack extracted = gasTank.extract(toDraw, Action.get(doDraw), AutomationType.INTERNAL);
        if (getAmount(extracted) <= 0) {
            return false;
        }
        return doDraw ? insertIntoItem(stack, gasHandler, extracted) > 0 : simulateInsertIntoItem(stack, gasHandler, extracted) > 0;
    }

    private static boolean fillSingleItemFromGasTankAndMove(IInventorySlot slot, IInventorySlot outputSlot, ItemStack stack,
          @Nullable IGasHandler gasHandler, IExtendedGasTank gasTank) {
        GasStack storedGas = gasTank.getGas();
        if (storedGas == null) {
            return false;
        }
        int added = simulateInsertIntoItem(stack, gasHandler, storedGas);
        if (added <= 0) {
            return false;
        }
        GasStack extracted = gasTank.extract(added, Action.SIMULATE, AutomationType.INTERNAL);
        if (getAmount(extracted) <= 0) {
            return false;
        }
        if (extracted.amount < added) {
            stack = StackUtils.size(slot.getStack().copy(), 1);
            gasHandler = gasHandler == null ? null : getCapability(stack);
            if (gasHandler == null && !isGasContainerItem(stack)) {
                return false;
            }
            added = simulateInsertIntoItem(stack, gasHandler, extracted);
            if (added <= 0) {
                return false;
            }
        }
        extracted = gasTank.extract(added, Action.SIMULATE, AutomationType.INTERNAL);
        if (getAmount(extracted) <= 0 || insertIntoItem(stack, gasHandler, extracted) <= 0 || !moveItem(slot, outputSlot, stack)) {
            return false;
        }
        gasTank.extract(added, Action.EXECUTE, AutomationType.INTERNAL);
        return true;
    }

    private static boolean moveItem(IInventorySlot inputSlot, IInventorySlot outputSlot, ItemStack stackToMove) {
        if (!stackToMove.isEmpty()) {
            ItemStack remainder = outputSlot.insertItem(stackToMove, Action.SIMULATE, AutomationType.INTERNAL);
            if (!remainder.isEmpty()) {
                return false;
            }
        }
        MekanismUtils.logMismatchedStackSize(inputSlot.shrinkStack(1, Action.EXECUTE), 1);
        if (!stackToMove.isEmpty()) {
            MekanismUtils.logMismatchedStackSize(outputSlot.insertItem(stackToMove, Action.EXECUTE, AutomationType.INTERNAL).getCount(), 0);
        }
        return true;
    }

    private static void setFilling(IInventorySlot slot, boolean filling) {
        if (slot instanceof IGasHandlerSlot gasHandlerSlot) {
            gasHandlerSlot.setFilling(filling);
        } else if (slot instanceof IFluidHandlerSlot fluidHandlerSlot) {
            fluidHandlerSlot.setFilling(filling);
        }
    }

    private static void setDraining(IInventorySlot slot, boolean draining) {
        if (slot instanceof IGasHandlerSlot gasHandlerSlot) {
            gasHandlerSlot.setDraining(draining);
        } else if (slot instanceof IFluidHandlerSlot fluidHandlerSlot) {
            fluidHandlerSlot.setDraining(draining);
        }
    }

    public static final class GasTransferResult {

        private static final GasTransferResult EMPTY = new GasTransferResult(true, null);
        private static final GasTransferResult INVALID = new GasTransferResult(false, null);

        private final boolean valid;
        @Nullable
        private final GasStack stack;

        private GasTransferResult(boolean valid, @Nullable GasStack stack) {
            this.valid = valid;
            this.stack = stack;
        }

        private static GasTransferResult of(@Nullable GasStack stack) {
            return stack == null ? empty() : new GasTransferResult(true, stack);
        }

        private static GasTransferResult empty() {
            return EMPTY;
        }

        private static GasTransferResult invalid() {
            return INVALID;
        }

        public boolean isValid() {
            return valid;
        }

        @Nullable
        public GasStack getStack() {
            return stack;
        }
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        this(gasTank, canExtract, canInsert, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, canExtract, canInsert, validator, listener, x, y);
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, @Nullable IContentsListener listener, int x, int y) {
        this(gasTank, worldSupplier, canExtract, canInsert, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, worldSupplier, canExtract, canInsert, validator, listener, x, y);
    }
}
