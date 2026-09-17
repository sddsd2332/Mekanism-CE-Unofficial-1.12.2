package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.content.matrix.SynchronizedMatrixData;
import mekanism.common.tier.InductionProviderTier;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class TileEntityInductionProvider extends TileEntityBasicBlock {

    public InductionProviderTier tier = InductionProviderTier.BASIC;
    private SynchronizedMatrixData matrix;
    private long transferTick = Long.MIN_VALUE;
    private double inputUsed;
    private double outputUsed;

    public double getTransferCapacity() {
        return tier.getOutput();
    }

    public double getRemainingTransfer(long tick, boolean input) {
        if (transferTick != tick) {
            transferTick = tick;
            inputUsed = 0;
            outputUsed = 0;
        }
        return Math.max(0, getTransferCapacity() - (input ? inputUsed : outputUsed));
    }

    public double useTransfer(long tick, double amount, boolean input) {
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        double accepted = Math.min(amount, getRemainingTransfer(tick, input));
        if (accepted <= 0 || !Double.isFinite(accepted)) return 0;
        double used = input ? inputUsed : outputUsed;
        double updated = used + accepted;
        // Round usage towards exhaustion: a sub-ULP transfer must not get a free allowance.
        if (updated - used < accepted) updated = Math.nextUp(updated);
        updated = Math.min(updated, getTransferCapacity());
        if (input) inputUsed = updated;
        else outputUsed = updated;
        return accepted;
    }

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
        return LangUtils.localize(getBlockType().getTranslationKey() + ".InductionProvider" + tier.getBaseTier().getSimpleName() + ".name");
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            InductionProviderTier prevTier = tier;
            tier = MekanismUtils.getByIndex(InductionProviderTier.values(), dataStream.readInt(), tier);
            if (prevTier != tier) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(tier.ordinal());
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        tier = MekanismUtils.getByIndex(InductionProviderTier.values(), nbtTags.getInteger("tier"), tier);
    }

    @Override
   public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
    }
}
