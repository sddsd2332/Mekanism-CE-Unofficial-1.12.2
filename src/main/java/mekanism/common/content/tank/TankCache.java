package mekanism.common.content.tank;

import mekanism.api.gas.GasStack;
import mekanism.common.base.IFluidContainerManager.ContainerEditMode;
import mekanism.common.multiblock.MultiblockCache;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class TankCache extends MultiblockCache<SynchronizedTankData> {

    public FluidStack fluid;

    public GasStack gas;

    public ContainerEditMode editMode = ContainerEditMode.BOTH;

    private void sanitizeStoredSubstances() {
        if (fluid != null && fluid.amount <= 0) {
            fluid = null;
        }
        if (gas != null && gas.amount <= 0) {
            gas = null;
        }
        if (fluid != null && gas != null) {
            if (fluid.amount >= gas.amount) {
                gas = null;
            } else {
                fluid = null;
            }
        }
    }

    @Override
    public void apply(SynchronizedTankData data) {
        sanitizeStoredSubstances();
        applyInventory(data);
        data.fluidStored = fluid == null ? null : fluid.copy();
        data.gasstored = gas == null ? null : gas.copy();
        data.editMode = editMode;
    }

    @Override
    public void sync(SynchronizedTankData data) {
        syncInventory(data);
        fluid = data.fluidStored == null ? null : data.fluidStored.copy();
        gas = data.gasstored == null ? null : data.gasstored.copy();
        sanitizeStoredSubstances();
        editMode = data.editMode;
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        editMode = ContainerEditMode.byIndexStatic(nbtTags.getInteger("editMode"));
        loadInventory(nbtTags);
        if (nbtTags.hasKey("cachedFluid")) {
            fluid = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cachedFluid"));
        }
        if (nbtTags.hasKey("cachedGas")){
            gas = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedGas"));
        }
        sanitizeStoredSubstances();
    }

    @Override
    public void save(NBTTagCompound nbtTags) {
        sanitizeStoredSubstances();
        nbtTags.setInteger("editMode", editMode.ordinal());
        saveInventory(nbtTags);
        if (fluid != null) {
            nbtTags.setTag("cachedFluid", fluid.writeToNBT(new NBTTagCompound()));
        }
        if (gas !=null){
            nbtTags.setTag("cachedGas",gas.write(new NBTTagCompound()));
        }
    }
}
