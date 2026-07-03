package mekanism.common.inventory.slot.gas;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.capabilities.merged.MergedTank.CurrentType;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Gas-only bridge for high-version merged inventory slots.
 */
public class MergedGasInventorySlot<MERGED extends MergedTank> extends BasicInventorySlot {

    @FunctionalInterface
    protected interface SlotCreator<SLOT extends MergedGasInventorySlot<MergedTank>> {

        SLOT create(MergedTank mergedTank, BiPredicate<ItemStack, AutomationType> canExtract, BiPredicate<ItemStack, AutomationType> canInsert,
              Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y);
    }

    public static MergedGasInventorySlot<MergedTank> drain(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        return createDrainSlot(mergedTank, "Merged gas tank cannot be null", MergedGasInventorySlot::new, listener, x, y);
    }

    protected static <SLOT extends MergedGasInventorySlot<MergedTank>> SLOT createDrainSlot(MergedTank mergedTank, String nullMessage,
          SlotCreator<SLOT> slotCreator, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(mergedTank, nullMessage);
        Objects.requireNonNull(slotCreator, "Merged gas slot creator cannot be null");
        BiPredicate<ItemStack, AutomationType> insertPredicate = (stack, automationType) -> GasInventorySlot.drainInsertCheck(mergedTank.getGasTank(), stack);
        return slotCreator.create(mergedTank, (stack, automationType) -> automationType == AutomationType.MANUAL ||
              !insertPredicate.test(stack, automationType), insertPredicate, GasHandlerInventorySlot.GAS_ITEM_VALIDATOR, listener, x, y);
    }

    public static MergedGasInventorySlot<MergedTank> fill(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        return createFillSlot(mergedTank, "Merged gas tank cannot be null", MergedGasInventorySlot::new, listener, x, y);
    }

    protected static <SLOT extends MergedGasInventorySlot<MergedTank>> SLOT createFillSlot(MergedTank mergedTank, String nullMessage,
          SlotCreator<SLOT> slotCreator, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(mergedTank, nullMessage);
        Objects.requireNonNull(slotCreator, "Merged gas slot creator cannot be null");
        Predicate<ItemStack> gasExtractPredicate = stack -> GasInventorySlot.fillExtractCheck(mergedTank.getGasTank(), stack);
        Predicate<ItemStack> gasInsertPredicate = stack -> GasInventorySlot.fillInsertCheck(mergedTank.getGasTank(), stack);
        return slotCreator.create(mergedTank, (stack, automationType) -> automationType == AutomationType.MANUAL ||
              gasExtractPredicate.test(stack), (stack, automationType) -> gasInsertPredicate.test(stack),
              GasHandlerInventorySlot.GAS_ITEM_VALIDATOR, listener, x, y);
    }

    protected final MERGED mergedTank;

    protected MergedGasInventorySlot(MERGED mergedTank, BiPredicate<ItemStack, AutomationType> canExtract,
          BiPredicate<ItemStack, AutomationType> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(canExtract, canInsert, validator, listener, x, y);
        setSlotType(ContainerSlotType.EXTRA);
        this.mergedTank = mergedTank;
    }

    public boolean drainGasTanks() {
        return drainGasTank(CurrentType.GAS);
    }

    public boolean drainGasTank() {
        return drainGasTank(CurrentType.GAS);
    }

    public boolean drainGasTank(CurrentType type) {
        return type.isGas() && GasInventorySlot.drainTank(this, mergedTank.getGasTank());
    }

    public boolean fillGasTanks() {
        return fillGasTank(CurrentType.GAS);
    }

    public boolean fillGasTank() {
        return fillGasTank(CurrentType.GAS);
    }

    public boolean fillGasTank(CurrentType type) {
        return type.isGas() && GasInventorySlot.fillTank(this, mergedTank.getGasTank());
    }
}
