package mekanism.common.integration.ic2;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.item.ElectricItem;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Optional.Method;

public class IC2Integration {

    private static final int MIN_TIER = 0;
    private static final int MAX_TIER = 30;
    private static final int MAX_PACKETS_PER_TRANSFER = 65_536;

    public static double toEU(double joules) {
        return joules * MekanismConfig.current().general.TO_IC2.val();
    }

    public static int toEUAsInt(double joules) {
        return MekanismUtils.clampToInt(toEU(joules));
    }

    public static double fromEU(double eu) {
        return eu * MekanismConfig.current().general.FROM_IC2.val();
    }

    public static int getConfiguredInputTier() {
        return clampTier(MekanismConfig.current().general.ic2InputTier.val());
    }

    public static int getOutputTierForJoules(double joulesPerTick) {
        // IEnergySource does not expose the receiver. Advertise the lowest tier that can carry
        // the fixed Mekanism output without allowing a configured tier to throttle the amount.
        return getTierFromPower(toEU(joulesPerTick));
    }

    public static int getMekanismItemTier(ItemStack stack) {
        return getOutputTierForJoules(StorageUtils.getMaxTransfer(stack));
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static int getItemOutputTier(ItemStack stack) {
        if (stack.isEmpty() || ElectricItem.manager == null) {
            return getConfiguredInputTier();
        }
        return clampTier(ElectricItem.manager.getTier(stack));
    }

    /**
     * Charges an IC2 item using the item's own tier as the target voltage tier.
     */
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static double chargeItem(ItemStack stack, double amount, boolean ignoreTransferLimit, boolean simulate) {
        if (stack.isEmpty() || ElectricItem.manager == null || !(amount > 0)) {
            return 0;
        }
        double transferred = ElectricItem.manager.charge(stack, amount, getItemOutputTier(stack), ignoreTransferLimit, simulate);
        return clampTransfer(amount, transferred);
    }

    /**
     * Discharges an IC2 item into a target using that target's voltage tier.
     */
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static double dischargeItem(ItemStack stack, double amount, int targetTier, boolean ignoreTransferLimit, boolean simulate) {
        if (stack.isEmpty() || ElectricItem.manager == null || !(amount > 0)) {
            return 0;
        }
        double transferred = ElectricItem.manager.discharge(stack, amount, clampTier(targetTier), ignoreTransferLimit, true, simulate);
        return clampTransfer(amount, transferred);
    }

    /**
     * Discharges an IC2 item into Mekanism using the tier Mekanism exposes as an IC2 sink.
     */
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static double dischargeItemToMekanism(ItemStack stack, double amount, boolean ignoreTransferLimit, boolean simulate) {
        return dischargeItem(stack, amount, getConfiguredInputTier(), ignoreTransferLimit, simulate);
    }

    /**
     * Transfers a fixed EU amount to an IC2 sink as one or more packets at the sink's own voltage tier.
     * IC2 Classic silently drops direct injections larger than its tier packet limit, so the tier must
     * determine the packet size without also limiting the total amount transferred this tick.
     *
     * @return amount of EU accepted by the sink
     */
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static double transferToSink(IEnergySink sink, EnumFacing side, double amount, boolean simulate) {
        if (sink == null || !(amount > 0)) {
            return 0;
        }
        double demand = sink.getDemandedEnergy();
        if (!(demand > 0)) {
            return 0;
        }
        double toTransfer = Math.min(amount, demand);
        if (Mekanism.hooks.IC2CLoaded) {
            //IC2 Classic stores machine energy as whole EU and otherwise reports fractional EU as accepted.
            toTransfer = Math.floor(toTransfer);
            if (!(toTransfer > 0)) {
                return 0;
            }
        }

        double packetVoltage = getPowerFromTier(sink.getSinkTier());
        if (!(packetVoltage > 0) || Double.isNaN(packetVoltage)) {
            packetVoltage = toTransfer;
        }
        double packetTransferLimit = packetVoltage * MAX_PACKETS_PER_TRANSFER;
        if (Double.isFinite(packetTransferLimit)) {
            toTransfer = Math.min(toTransfer, packetTransferLimit);
        }
        if (simulate) {
            //IC2 has no built in way to simulate, so calculate the accepted amount ourselves.
            return toTransfer;
        }

        double accepted = 0;
        double remaining = toTransfer;
        for (int packets = 0; remaining > 0 && packets < MAX_PACKETS_PER_TRANSFER; packets++) {
            double packetAmount = Math.min(remaining, packetVoltage);
            double rejected = sink.injectEnergy(side, packetAmount, packetVoltage);
            double acceptedPacket = clampTransfer(packetAmount, packetAmount - rejected);
            accepted += acceptedPacket;

            // A partial or full rejection normally means the sink filled up or stopped accepting energy.
            if (acceptedPacket < packetAmount) {
                break;
            }
            remaining = Math.max(0, remaining - packetAmount);
        }
        return clampTransfer(toTransfer, accepted);
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static int getTierFromPower(double power) {
        if (Double.isNaN(power) || power <= 0) {
            return MIN_TIER;
        }
        if (!Double.isFinite(power)) {
            return MAX_TIER;
        }
        if (EnergyNet.instance != null) {
            return clampTier(EnergyNet.instance.getTierFromPower(power));
        }
        return clampTier((int) Math.ceil(Math.log(power / 8D) / Math.log(4D)));
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static double getPowerFromTier(int tier) {
        int clampedTier = clampTier(tier);
        if (EnergyNet.instance != null) {
            return EnergyNet.instance.getPowerFromTier(clampedTier);
        }
        if (clampedTier < 14) {
            return 8D * (1 << (clampedTier * 2));
        }
        return clampedTier < 30 ? 8D * Math.pow(4D, clampedTier) : Long.MAX_VALUE;
    }

    private static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    private static double clampTransfer(double requested, double transferred) {
        if (Double.isNaN(transferred) || transferred <= 0) {
            return 0;
        }
        return Math.min(requested, transferred);
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static boolean isOutputter(TileEntity tileEntity, EnumFacing side) {
        IEnergyTile tile = EnergyNet.instance.getSubTile(tileEntity.getWorld(), tileEntity.getPos());
        return tile instanceof IEnergySource source&& source.emitsEnergyTo(null, side.getOpposite());

    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static boolean isAcceptor(TileEntity tileEntity, EnumFacing side) {
        IEnergyTile tile = EnergyNet.instance.getSubTile(tileEntity.getWorld(), tileEntity.getPos());
        if (tile instanceof IEnergySink sink) {
            return sink.acceptsEnergyFrom(null, side.getOpposite());
        }
        return false;
    }
}
