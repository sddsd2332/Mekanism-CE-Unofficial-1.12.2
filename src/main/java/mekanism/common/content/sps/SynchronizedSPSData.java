package mekanism.common.content.sps;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.tank.ValidatingGasTank;
import mekanism.common.multiblock.SynchronizedData;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;

public class SynchronizedSPSData extends SynchronizedData<SynchronizedSPSData> {

    public static final int INPUT_PER_ANTIMATTER = 1_000;
    public static final int INPUT_CAPACITY = INPUT_PER_ANTIMATTER * 2;
    public static final int OUTPUT_CAPACITY = 1_000;
    public static final double ENERGY_PER_INPUT = 1_000_000D;

    public final ValidatingGasTank inputTank = new ValidatingGasTank(INPUT_CAPACITY, gas -> gas == MekanismFluids.Polonium);
    public final ValidatingGasTank outputTank = new ValidatingGasTank(OUTPUT_CAPACITY, gas -> gas == MekanismFluids.Antimatter);

    public final Map<Coord4D, Coord4D> portToCoilMap = new Object2ObjectOpenHashMap<>();
    public final Map<Coord4D, Integer> coilLevels = new Object2ObjectOpenHashMap<>();
    public final Map<Coord4D, Integer> prevCoilLevels = new Object2ObjectOpenHashMap<>();

    public double progress;
    public int inputProcessed;
    public double receivedEnergy;
    public double lastReceivedEnergy;
    public double lastProcessed;
    public boolean couldOperate;
    private int prevCoilHash;
    private boolean coilLevelsDirty;

    private GasStack prevInput;
    private GasStack prevOutput;

    public SynchronizedSPSData() {
        gasTanks.add(inputTank);
        gasTanks.add(outputTank);
    }

    public void tick(World world) {
        double processed = 0;
        couldOperate = canOperate();

        if (couldOperate && receivedEnergy > 0) {
            double lastProgress = progress;
            int inputNeeded = (INPUT_PER_ANTIMATTER - inputProcessed) + INPUT_PER_ANTIMATTER * (outputTank.getNeeded() - 1);
            double processable = receivedEnergy / ENERGY_PER_INPUT;
            if (processable + progress >= inputNeeded) {
                processed = process(inputNeeded);
                progress = 0;
            } else {
                processed = processable;
                progress += processable;
                int toProcess = MathUtils.clampToInt(progress);
                long actualProcessed = process(toProcess);
                if (actualProcessed < toProcess) {
                    long processedDif = toProcess - actualProcessed;
                    progress -= processedDif;
                    processed -= processedDif;
                }
                progress %= 1;
            }
            if (lastProgress != progress) {
                // no-op: kept for parity with legacy SPS flow where progress transitions are tracked
            }
        }

        lastReceivedEnergy = receivedEnergy;
        receivedEnergy = 0;
        lastProcessed = processed;
        prevCoilLevels.clear();
        prevCoilLevels.putAll(coilLevels);
        coilLevels.clear();
        int newCoilHash = prevCoilLevels.hashCode();
        coilLevelsDirty = newCoilHash != prevCoilHash;
        prevCoilHash = newCoilHash;

        kill(world);
    }

    private long process(int operations) {
        if (operations == 0) {
            return 0;
        }
        GasStack extracted = inputTank.extract(operations, Action.EXECUTE, AutomationType.INTERNAL);
        long processed = extracted == null ? 0 : extracted.amount;
        inputProcessed += MathUtils.clampToInt(processed);
        if (inputProcessed >= INPUT_PER_ANTIMATTER) {
            GasStack toAdd = new GasStack(MekanismFluids.Antimatter, inputProcessed / INPUT_PER_ANTIMATTER);
            outputTank.insert(toAdd, Action.EXECUTE, AutomationType.INTERNAL);
            inputProcessed %= INPUT_PER_ANTIMATTER;
        }
        return processed;
    }

    public boolean canOperate() {
        return inputTank.getGas() != null && outputTank.getNeeded() > 0;
    }

    public void clampTanksToCapacity() {
        clampTank(inputTank);
        clampTank(outputTank);
    }

    public boolean canSupplyCoilEnergy(Coord4D coilPos, Coord4D portPos) {
        Coord4D mappedCoil = portToCoilMap.get(portPos);
        return mappedCoil != null && mappedCoil.equals(coilPos) && (couldOperate || canOperate());
    }

    public boolean canSupplyPortEnergy(Coord4D portPos) {
        return portToCoilMap.containsKey(portPos) && (couldOperate || canOperate());
    }

    public void addEnergy(Coord4D portPos, double energy) {
        if (energy <= 0) {
            return;
        }
        receivedEnergy += energy;
        Coord4D coilPos = portToCoilMap.get(portPos);
        if (coilPos != null) {
            int current = coilLevels.getOrDefault(coilPos, 0);
            coilLevels.put(coilPos, current + getCoilLevel(energy));
        }
    }

    public boolean handlesCoil(Coord4D coilPos) {
        return portToCoilMap.containsValue(coilPos);
    }

    public int getRenderCoilLevel(Coord4D coilPos) {
        return prevCoilLevels.getOrDefault(coilPos, 0);
    }

    public boolean pullCoilLevelsDirty() {
        boolean dirty = coilLevelsDirty;
        coilLevelsDirty = false;
        return dirty;
    }

    public double getProcessRate() {
        return (double) Math.round((lastProcessed / INPUT_PER_ANTIMATTER) * 1_000) / 1_000;
    }

    public double getScaledProgress() {
        return (inputProcessed + progress) / INPUT_PER_ANTIMATTER;
    }

    public boolean shouldPlaySoundAt(BlockPos pos) {
        if (pos == null || minLocation == null || maxLocation == null) {
            return false;
        }
        return pos.equals(new BlockPos(minLocation.x + 3, minLocation.y, minLocation.z)) ||
              pos.equals(new BlockPos(maxLocation.x - 3, maxLocation.y, maxLocation.z));
    }

    public boolean sanitizeStoredGases() {
        boolean changed = false;
        changed |= sanitizeTank(inputTank);
        changed |= sanitizeTank(outputTank);
        return changed;
    }

    private static void clampTank(ValidatingGasTank tank) {
        GasStack gas = tank.getGas();
        if (gas != null) {
            tank.setStackSize(gas.amount, Action.EXECUTE);
        }
        sanitizeTank(tank);
    }

    private static boolean sanitizeTank(ValidatingGasTank tank) {
        GasStack gas = tank.getGas();
        if (gas != null && (gas.amount <= 0 || !tank.isValid(gas))) {
            tank.setEmpty();
            return true;
        }
        return false;
    }

    public boolean needsRenderUpdate() {
        if ((inputTank.getGas() == null) != (prevInput == null)) {
            return true;
        }
        if (inputTank.getGas() != null && (prevInput == null || !inputTank.getGas().isGasEqual(prevInput) || inputTank.getGas().amount != prevInput.amount)) {
            return true;
        }
        if ((outputTank.getGas() == null) != (prevOutput == null)) {
            return true;
        }
        return outputTank.getGas() != null && (prevOutput == null || !outputTank.getGas().isGasEqual(prevOutput) || outputTank.getGas().amount != prevOutput.amount);
    }

    public void syncPrevTanks() {
        prevInput = inputTank.getGas() == null ? null : inputTank.getGas().copy();
        prevOutput = outputTank.getGas() == null ? null : outputTank.getGas().copy();
    }

    private void kill(World world) {
        if (lastReceivedEnergy <= 0 || !couldOperate || world.rand.nextInt(20) != 0) {
            return;
        }
        AxisAlignedBB deathZone = new AxisAlignedBB(minLocation.x + 2, minLocation.y + 2, minLocation.z + 2,
              maxLocation.x, maxLocation.y, maxLocation.z);
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, deathZone)) {
            entity.attackEntityFrom(DamageSource.MAGIC, (float) (lastReceivedEnergy / 1_000F));
        }
    }

    public EnumFacing getPortInnerSide(Coord4D pos) {
        if (pos.x == minLocation.x) {
            return EnumFacing.EAST;
        } else if (pos.x == maxLocation.x) {
            return EnumFacing.WEST;
        } else if (pos.y == minLocation.y) {
            return EnumFacing.UP;
        } else if (pos.y == maxLocation.y) {
            return EnumFacing.DOWN;
        } else if (pos.z == minLocation.z) {
            return EnumFacing.SOUTH;
        } else if (pos.z == maxLocation.z) {
            return EnumFacing.NORTH;
        }
        return null;
    }

    private static int getCoilLevel(double energy) {
        if (energy <= 0) {
            return 0;
        }
        return 1 + Math.max(0, (int) ((Math.log10(energy) - 3) * 1.8));
    }
}
