package mekanism.common.capabilities.resolver.manager;

import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.ISidedGasHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.proxy.ProxyGasHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class GasHandlerManager extends CapabilityHandlerManager<IGasTankHolder, IExtendedGasTank, IGasHandler, ISidedGasHandler> {

    public GasHandlerManager(@Nullable IGasTankHolder holder, @Nonnull ISidedGasHandler baseHandler) {
        super(holder, baseHandler, Capabilities.GAS_HANDLER_CAPABILITY, ProxyGasHandler::new, IGasTankHolder::getTanks);
    }
}
