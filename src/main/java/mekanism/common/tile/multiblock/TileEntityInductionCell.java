package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.content.matrix.SynchronizedMatrixData;
import mekanism.common.tier.InductionCellTier;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import java.math.BigDecimal;

public class TileEntityInductionCell extends TileEntityBasicBlock implements IStrictEnergyStorage {

    public InductionCellTier tier = InductionCellTier.BASIC;

    public double electricityStored;
    private SynchronizedMatrixData matrix;
    private BigDecimal exactEnergy;
    private net.minecraft.nbt.NBTBase invalidExactEnergy;

    public void bindMatrix(SynchronizedMatrixData owner) {
        if (matrix != null && matrix != owner) matrix.setFormed(false);
        matrix = owner;
    }

    public void unbindMatrix(SynchronizedMatrixData owner) {
        if (matrix == owner) matrix = null;
    }

    @Override
    public void onChunkUnload() {
        if (matrix != null) matrix.internalRemoved();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        if (matrix != null) matrix.internalRemoved();
        super.invalidate();
    }


    public String getName() {
        return LangUtils.localize(getBlockType().getTranslationKey() + ".InductionCell" + tier.getBaseTier().getSimpleName() + ".name");
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            InductionCellTier prevTier = tier;
            tier = MekanismUtils.getByIndex(InductionCellTier.values(), dataStream.readInt(), tier);
            super.handlePacketData(dataStream);
            electricityStored = dataStream.readDouble();
            if (prevTier != tier) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(tier.ordinal());
        super.getNetworkedData(data);
        data.add(electricityStored);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        tier = MekanismUtils.getByIndex(InductionCellTier.values(), nbtTags.getInteger("tier"), tier);
        electricityStored = nbtTags.getDouble("electricityStored");
        exactEnergy = null;
        invalidExactEnergy = null;
        if (nbtTags.hasKey("matrixExactEnergy")) {
            String encoded = nbtTags.getString("matrixExactEnergy");
            try {
                if (!nbtTags.hasKey("matrixExactEnergy", 8) || encoded.length() > 2048) throw new NumberFormatException();
                BigDecimal decoded = new BigDecimal(encoded);
                if (decoded.scale() < 0 || decoded.scale() > 1074 || decoded.precision() > 1400 ||
                      !Double.isFinite(decoded.doubleValue()) || decoded.doubleValue() != electricityStored) throw new NumberFormatException();
                exactEnergy = decoded;
            } catch (NumberFormatException invalid) {
                invalidExactEnergy = nbtTags.getTag("matrixExactEnergy").copy();
            }
        }
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        if (matrix != null) matrix.flushEnergy();
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
        nbtTags.setDouble("electricityStored", electricityStored);
        if (invalidExactEnergy != null) nbtTags.setTag("matrixExactEnergy", invalidExactEnergy.copy());
        else if (Double.isFinite(electricityStored)) nbtTags.setString("matrixExactEnergy", getExactEnergy().toPlainString());
    }

    @Override
    public double getEnergy() {
        if (matrix != null) matrix.flushEnergy();
        return electricityStored;
    }

    @Override
    public void setEnergy(double energy) {
        if (matrix != null) matrix.flushEnergy();
        if (!Double.isFinite(energy)) return;
        setExactEnergy(new BigDecimal(Math.max(0, Math.min(energy, getMaxEnergy()))));
    }

    public boolean hasValidExactEnergy() {
        if (invalidExactEnergy != null || !Double.isFinite(electricityStored) || !Double.isFinite(getMaxEnergy())) return false;
        BigDecimal energy = getExactEnergy();
        return energy.signum() >= 0 && energy.compareTo(new BigDecimal(getMaxEnergy())) <= 0;
    }

    public BigDecimal getExactEnergy() {
        if (matrix != null) matrix.flushEnergy();
        if (invalidExactEnergy != null) throw new IllegalStateException("Invalid persisted matrix cell energy");
        // Public legacy field writes replace the value when their double projection changes.
        if (exactEnergy == null || exactEnergy.doubleValue() != electricityStored) exactEnergy = new BigDecimal(electricityStored);
        return exactEnergy;
    }

    public void setExactEnergy(BigDecimal energy) {
        if (matrix != null) matrix.flushEnergy();
        BigDecimal before = getExactEnergy();
        BigDecimal bounded = energy.max(BigDecimal.ZERO).min(new BigDecimal(getMaxEnergy()));
        if (before.compareTo(bounded) != 0) {
            exactEnergy = bounded;
            electricityStored = bounded.doubleValue();
            if (matrix != null) matrix.cellEnergyChanged(before, bounded);
            MekanismUtils.saveChunk(this);
        }
    }

    @Override
    public double getMaxEnergy() {
        return tier.getMaxEnergy();
    }
}
