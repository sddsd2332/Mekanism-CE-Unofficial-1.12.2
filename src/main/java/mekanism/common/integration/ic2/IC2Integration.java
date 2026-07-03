package mekanism.common.integration.ic2;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.item.ElectricItem;
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
        if (!MekanismConfig.current().general.dynamicIC2OutputTier.val()) {
            return getConfiguredInputTier();
        }
        return getTierFromPower(toEU(joulesPerTick));
    }

    public static int getMekanismItemTier(ItemStack stack) {
        return getOutputTierForJoules(StorageUtils.getMaxTransfer(stack));
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public static int getItemOutputTier(ItemStack stack) {
        if (!MekanismConfig.current().general.dynamicIC2OutputTier.val()) {
            return getConfiguredInputTier();
        }
        if (stack.isEmpty() || ElectricItem.manager == null) {
            return getConfiguredInputTier();
        }
        return clampTier(ElectricItem.manager.getTier(stack));
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
