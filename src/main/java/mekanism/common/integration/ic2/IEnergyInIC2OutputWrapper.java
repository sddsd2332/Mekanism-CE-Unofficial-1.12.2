package mekanism.common.integration.ic2;

import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergySource;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;

/**
 * 非常坏的IC2发电轮检查
 * 实现此接口的机器会被 IC2 能源网判为电源（IEnergySource），
 * 进入每 tick 的询价名单（getOfferedEnergy / drawEnergy）。
 * 仅实现 IEnergyWrapper 的机器只以用电方（IEnergySink）身份注册，不被询价。
 */
@InterfaceList({
        @Interface(iface = "ic2.api.energy.tile.IEnergySource", modid = MekanismHooks.IC2_MOD_ID),
        @Interface(iface = "ic2.api.energy.tile.IEnergyEmitter", modid = MekanismHooks.IC2_MOD_ID)
})
public interface IEnergyInIC2OutputWrapper extends IEnergyWrapper, IEnergySource {

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    default int getSourceTier() {
        double maxOutput = getMaxOutput();
        return MekanismConfig.current().general.blacklistIC2.val() || !(maxOutput > 0) ? 0 :
                IC2Integration.getOutputTierForJoules(maxOutput);
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    default boolean emitsEnergyTo(IEnergyAcceptor receiver, EnumFacing direction) {
        if (this instanceof TileEntityElectricBlock block) {
            return !MekanismConfig.current().general.blacklistIC2.val() && block.canExtractExternalEnergy(direction) && receiver instanceof IEnergyConductor;
        }
        return false;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    default double getOfferedEnergy() {
        double maxOutput = getMaxOutput();
        return MekanismConfig.current().general.blacklistIC2.val() || !(maxOutput > 0) ? 0 :
                IC2Integration.toEU(Math.min(getEnergy(), maxOutput));
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    default void drawEnergy(double amount) {
        if (this instanceof TileEntityElectricBlock block) {
            block.runContainerTransaction(() -> setEnergy(Math.max(getEnergy() - IC2Integration.fromEU(amount), 0)));
        }
    }
}
