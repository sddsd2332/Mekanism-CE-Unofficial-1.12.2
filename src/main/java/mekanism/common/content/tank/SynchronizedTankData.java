package mekanism.common.content.tank;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Action;
import mekanism.api.Coord4D;
import mekanism.api.gas.GasStack;
import mekanism.common.base.IFluidContainerManager.ContainerEditMode;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.HybridInventorySlot;
import mekanism.common.multiblock.SynchronizedData;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import mekanism.common.util.FluidContainerUtils;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;

import java.util.Set;

public class SynchronizedTankData extends SynchronizedData<SynchronizedTankData> {

    public FluidStack fluidStored;

    public GasStack gasstored;

    /**
     * For use by rendering segment
     */
    public FluidStack prevFluid;
    public GasStack prevGas;
    public int prevFluidStage = 0;
    public int prevGasStage = 0;
    public ContainerEditMode editMode = ContainerEditMode.BOTH;

    public final DynamicFluidTank inventoryFluidTank;
    public final DynamicGasTank inventoryGasTank;
    public final MergedTank inventoryMergedTank;
    public final HybridInventorySlot inputSlot;
    public final HybridInventorySlot outputSlot;

    public Set<ValveData> valves = new ObjectOpenHashSet<>();

    public SynchronizedTankData(TileEntityDynamicTank tile) {
        inventoryFluidTank = new DynamicFluidTank(tile);
        inventoryGasTank = new DynamicGasTank(tile);
        inventoryMergedTank = MergedTank.create(inventoryFluidTank, inventoryGasTank);
        fluidTanks.add(inventoryMergedTank.getFluidTank());
        gasTanks.add(inventoryMergedTank.getGasTank());
        inputSlot = HybridInventorySlot.inputOrDrain(inventoryMergedTank, this, 146, 20);
        inputSlot.setSlotType(ContainerSlotType.INPUT);
        inventorySlots.add(inputSlot);
        outputSlot = HybridInventorySlot.outputOrFill(inventoryMergedTank, this, 146, 51);
        outputSlot.setSlotType(ContainerSlotType.OUTPUT);
        inventorySlots.add(outputSlot);
    }

    public boolean hasFluid() {
        return fluidStored != null && fluidStored.amount > 0;
    }

    public boolean hasGas() {
        return gasstored != null && gasstored.amount > 0;
    }

    public int getFluidCapacity() {
        return volume * TankUpdateProtocol.FLUID_PER_TANK;
    }

    public int getGasCapacity() {
        return volume * TankUpdateProtocol.FLUID_PER_TANK;
    }

    public int getFluidAmount() {
        return fluidStored == null ? 0 : fluidStored.amount;
    }

    public int getGasAmount() {
        return gasstored == null ? 0 : gasstored.amount;
    }

    public int setFluidStackSize(int amount) {
        if (inventoryFluidTank.canMutate(this)) {
            return inventoryFluidTank.setStackSize(amount, Action.EXECUTE);
        }
        if (fluidStored == null) {
            return 0;
        }
        int capacity = getFluidCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            fluidStored = null;
            return 0;
        }
        fluidStored = FluidContainerUtils.copyWithAmount(fluidStored, amount);
        return amount;
    }

    public int setGasStackSize(int amount) {
        if (inventoryGasTank.canMutate(this)) {
            return inventoryGasTank.setStackSize(amount, Action.EXECUTE);
        }
        if (gasstored == null) {
            return 0;
        }
        int capacity = getGasCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            gasstored = null;
            return 0;
        }
        gasstored = gasstored.copy().withAmount(amount);
        return amount;
    }

    public void clampStoredSubstancesToCapacity() {
        sanitizeStoredSubstances();
        setFluidStackSize(getFluidAmount());
        setGasStackSize(getGasAmount());
    }

    /**
     * Dynamic Tank in modern Mekanism only allows one stored medium at a time.
     * Keep the larger stored stack if old data or legacy logic produced both.
     *
     * @return True if any state was changed.
     */
    public boolean sanitizeStoredSubstances() {
        boolean changed = false;
        if (fluidStored != null && fluidStored.amount <= 0) {
            setFluidStackSize(0);
            changed = true;
        }
        if (gasstored != null && gasstored.amount <= 0) {
            setGasStackSize(0);
            changed = true;
        }
        if (fluidStored != null && gasstored != null) {
            if (fluidStored.amount >= gasstored.amount) {
                setGasStackSize(0);
            } else {
                setFluidStackSize(0);
            }
            changed = true;
        }
        return changed;
    }

    public boolean needsRenderUpdate() {
        if ((fluidStored == null && prevFluid != null) || (fluidStored != null && prevFluid == null)) {
            return true;
        } else if ((gasstored == null && prevGas != null) || (gasstored != null && prevGas == null)){
            return true;
        }
        if (fluidStored != null) {
            int totalStage = (volHeight - 2) * (TankUpdateProtocol.FLUID_PER_TANK / 100);
            int currentStage = (int) ((fluidStored.amount / (float) (volume * TankUpdateProtocol.FLUID_PER_TANK)) * totalStage);
            boolean stageChanged = currentStage != prevFluidStage;
            prevFluidStage = currentStage;
            return (fluidStored.getFluid() != prevFluid.getFluid()) || stageChanged;
        } else if (gasstored != null){
            int totalStage = (volHeight - 2) * (TankUpdateProtocol.FLUID_PER_TANK / 100);
            int currentStage = (int) ((gasstored.amount / (float) (volume * TankUpdateProtocol.FLUID_PER_TANK)) * totalStage);
            boolean stageChanged = currentStage != prevGasStage;
            prevGasStage = currentStage;
            return (gasstored.getGas() != prevGas.getGas()) || stageChanged;
        }
        return false;
    }

    public static class ValveData {

        public EnumFacing side;
        public Coord4D location;

        public boolean prevActive;
        public int activeTicks;

        public void onTransfer() {
            activeTicks = 30;
        }

        @Override
        public int hashCode() {
            int code = 1;
            code = 31 * code + side.ordinal();
            code = 31 * code + location.hashCode();
            return code;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ValveData data && data.side == side && data.location.equals(location);
        }
    }
}
