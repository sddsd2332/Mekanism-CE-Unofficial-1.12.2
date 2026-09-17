package mekanism.common.content.sps;

import mekanism.api.gas.GasStack;
import mekanism.common.multiblock.MultiblockCache;
import net.minecraft.nbt.NBTTagCompound;

public class SPSCache extends MultiblockCache<SynchronizedSPSData> {

    @Override
    public void validateMerge(MultiblockCache<SynchronizedSPSData> incoming) throws java.io.IOException {
        super.validateMerge(incoming);
        SPSCache other = (SPSCache) incoming;
        validateGasMerge(inputGas, other.inputGas);
        validateGasMerge(outputGas, other.outputGas);
        validateSum(progress, other.progress, Double.MAX_VALUE, "SPS progress");
        validateSum(receivedEnergy, other.receivedEnergy, Double.MAX_VALUE, "SPS energy");
        requireMerge(inputProcessed >= 0 && other.inputProcessed >= 0 && (long) inputProcessed + other.inputProcessed <= Integer.MAX_VALUE,
              "Merged SPS processed input exceeds integer capacity");
    }

    @Override
    public void validateCapacity(SynchronizedSPSData target) throws java.io.IOException {
        super.validateCapacity(target);
        requireMerge(inputGas == null || target.inputTank.isValid(inputGas), "Invalid SPS input gas; source inventory is retained");
        requireMerge(outputGas == null || target.outputTank.isValid(outputGas), "Invalid SPS output gas; source inventory is retained");
        validateSum(progress, 0, Double.MAX_VALUE, "SPS progress");
        validateSum(receivedEnergy, 0, Double.MAX_VALUE, "SPS energy");
        validateSum(lastReceivedEnergy, 0, Double.MAX_VALUE, "SPS previous energy");
        validateSum(lastProcessed, 0, Double.MAX_VALUE, "SPS previous processing");
        requireMerge(inputProcessed >= 0, "Invalid SPS processed input; source inventory is retained");
        if (inputGas != null) validateAmount(inputGas.amount, target.inputTank.getMaxGas(), "SPS input");
        if (outputGas != null) validateAmount(outputGas.amount, target.outputTank.getMaxGas(), "SPS output");
    }

    public GasStack inputGas;
    public GasStack outputGas;
    public double progress;
    public int inputProcessed;
    public double receivedEnergy;
    public double lastReceivedEnergy;
    public double lastProcessed;
    public boolean couldOperate;

    @Override
    public void apply(SynchronizedSPSData data) {
        data.inputTank.setGas(inputGas == null ? null : inputGas.copy());
        data.outputTank.setGas(outputGas == null ? null : outputGas.copy());
        data.progress = progress;
        data.inputProcessed = inputProcessed;
        data.receivedEnergy = receivedEnergy;
        data.lastReceivedEnergy = lastReceivedEnergy;
        data.lastProcessed = lastProcessed;
        data.couldOperate = couldOperate;
    }

    @Override
    public void sync(SynchronizedSPSData data) {
        inputGas = syncGasStack(inputGas, data.inputTank.getGas());
        outputGas = syncGasStack(outputGas, data.outputTank.getGas());
        progress = data.progress;
        inputProcessed = data.inputProcessed;
        receivedEnergy = data.receivedEnergy;
        lastReceivedEnergy = data.lastReceivedEnergy;
        lastProcessed = data.lastProcessed;
        couldOperate = data.couldOperate;
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        inputGas = null;
        outputGas = null;
        if (nbtTags.hasKey("cachedSPSInput")) {
            inputGas = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedSPSInput"));
        }
        if (nbtTags.hasKey("cachedSPSOutput")) {
            outputGas = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedSPSOutput"));
        }
        progress = nbtTags.getDouble("spsProgress");
        inputProcessed = nbtTags.getInteger("spsInputProcessed");
        receivedEnergy = nbtTags.getDouble("spsReceivedEnergy");
        lastReceivedEnergy = nbtTags.getDouble("spsLastReceivedEnergy");
        lastProcessed = nbtTags.getDouble("spsLastProcessed");
        couldOperate = nbtTags.getBoolean("spsCouldOperate");
    }

    @Override
    public void save(NBTTagCompound nbtTags) {
        if (inputGas != null) {
            nbtTags.setTag("cachedSPSInput", inputGas.write(new NBTTagCompound()));
        }
        if (outputGas != null) {
            nbtTags.setTag("cachedSPSOutput", outputGas.write(new NBTTagCompound()));
        }
        nbtTags.setDouble("spsProgress", progress);
        nbtTags.setInteger("spsInputProcessed", inputProcessed);
        nbtTags.setDouble("spsReceivedEnergy", receivedEnergy);
        nbtTags.setDouble("spsLastReceivedEnergy", lastReceivedEnergy);
        nbtTags.setDouble("spsLastProcessed", lastProcessed);
        nbtTags.setBoolean("spsCouldOperate", couldOperate);
    }
}
