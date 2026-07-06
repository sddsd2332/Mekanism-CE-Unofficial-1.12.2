package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.MekanismFluids;
import mekanism.common.Upgrade;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityMachine;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class TileEntitySPS extends TileEntityMachine implements ISideConfiguration, ISustainedData, ITankManager, IConfigCardAccess {

    public double progress;
    public BasicGasTank inputTank;
    public BasicGasTank outputTank;
    public int inputProcessed = 0;
    public double receivedEnergy = 0;
    public double lastReceivedEnergy = 0;
    public double lastProcessed;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    public boolean couldOperate;

    public TileEntitySPS() {
        super("machine.sps", BlockStateMachine.MachineType.SPS, 0);
        removeSupportedUpgrade(Upgrade.SPEED);
        removeSupportedUpgrade(Upgrade.ENERGY);
        removeSupportedUpgrade(Upgrade.MUFFLING);

        configComponent = new TileComponentConfig(this, TransmissionType.ENERGY, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupGasIOConfig(inputTank, outputTank);
        configComponent.setConfig(TransmissionType.GAS, DataType.NONE, DataType.NONE, DataType.INPUT, DataType.NONE, DataType.INPUT, DataType.OUTPUT);

        configComponent.setInputConfig(TransmissionType.ENERGY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.GAS);
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        inputTank = BasicGasTank.input(1000, gas -> gas == MekanismFluids.Polonium, listener);
        outputTank = BasicGasTank.output(1000, gas -> gas == MekanismFluids.Antimatter, listener);
        builder.addTank(inputTank);
        builder.addTank(outputTank);
        return builder.build();
    }


    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        double processed = 0;
        couldOperate = canOperate();
        receivedEnergy = getEnergy();
        if (couldOperate && receivedEnergy != 0 && MekanismUtils.canFunction(this)) {
            setActive(true);
            double lastProgress = progress;
            final int inputPerAntimatter = 1000;
            int inputNeeded = (inputPerAntimatter - inputProcessed) + inputPerAntimatter * (outputTank.getNeeded() - 1);
            double processable = receivedEnergy / 1000000;
            if (processable + progress >= inputNeeded) {
                processed = process(inputNeeded);
                progress = 0;
            } else {
                processed = processable;
                progress += processable;
                getMainEnergyContainer().extract(receivedEnergy, Action.EXECUTE, AutomationType.INTERNAL);
                int toProcess = MathUtils.clampToInt(progress);
                long actualProcessed = process(toProcess);
                if (actualProcessed < toProcess) {
                    //If we processed less than we intended to we need to adjust how much our values actually changed by
                    long processedDif = toProcess - actualProcessed;
                    progress -= processedDif;
                    processed -= processedDif;
                }
                progress %= 1;
            }
            if (lastProgress != progress) {
                markNoUpdateSync();
            }
        } else {
            setActive(false);
        }

        /*
        if (receivedEnergy != lastReceivedEnergy || processed != lastProcessed) {
            needsPacket = true;
        }

         */
        lastReceivedEnergy = receivedEnergy;
        receivedEnergy = 0;
        lastProcessed = processed;
    }

    private long process(int operations) {
        if (operations == 0) {
            return 0;
        }
        GasStack extracted = inputTank.extract(operations, Action.EXECUTE, AutomationType.INTERNAL);
        long processed = extracted == null ? 0 : extracted.amount;
        int lastInputProcessed = inputProcessed;
        //Limit how much input we actually increase the input processed by to how much we were actually able to remove from the input tank
        inputProcessed += MathUtils.clampToInt(processed);
        final int inputPerAntimatter = 1000;
        if (inputProcessed >= inputPerAntimatter) {
            GasStack toAdd = new GasStack(MekanismFluids.Antimatter, inputProcessed / inputPerAntimatter);
            outputTank.insert(toAdd, Action.EXECUTE, AutomationType.INTERNAL);
            inputProcessed %= inputPerAntimatter;
        }
        if (lastInputProcessed != inputProcessed) {
            markDirty();
        }
        return processed;
    }


    private boolean canOperate() {
        return inputTank.getGas() != null && outputTank.getNeeded() > 0;
    }

    public double getProcessRate() {
        return (double) Math.round((lastProcessed / 1000) * 1000) / 1000;
    }

    public double getScaledProgress() {
        return (inputProcessed + progress) / 1000;
    }


    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "inputTank", inputTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "outputTank", outputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            inputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputTank"));
            outputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(inputTank, MekanismFluids.Polonium);
        sanitizeAndClampTank(outputTank, MekanismFluids.Antimatter);
    }

    private void sanitizeAndClampTank(BasicGasTank tank, Gas expectedGas) {
        GasStack stored = tank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null || stored.getGas() != expectedGas)) {
            tank.setEmpty();
        } else if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{inputTank, outputTank};
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@NotNull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public EnumFacing getOrientation() {
        return facing;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }


    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            lastReceivedEnergy = dataStream.readDouble();
            lastProcessed = dataStream.readDouble();
            TileUtils.readTankData(dataStream, inputTank);
            TileUtils.readTankData(dataStream, outputTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(lastReceivedEnergy);
        data.add(lastProcessed);
        TileUtils.addTankData(data, inputTank);
        TileUtils.addTankData(data, outputTank);
        return data;
    }


    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        lastReceivedEnergy = nbtTags.getDouble("lastReceivedEnergy");
        lastProcessed = nbtTags.getDouble("lastProcessed");
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("inputTank")) {
            inputTank.read(nbtTags.getCompoundTag("inputTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("outputTank")) {
            outputTank.read(nbtTags.getCompoundTag("outputTank"));
        }
        sanitizeAndClampTanks();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setDouble("lastReceivedEnergy", lastReceivedEnergy);
        nbtTags.setDouble("lastProcessed", lastProcessed);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
