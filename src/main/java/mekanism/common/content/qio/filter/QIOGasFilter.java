package mekanism.common.content.qio.filter;

import mekanism.api.gas.GasStack;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import net.minecraft.nbt.NBTTagCompound;

public class QIOGasFilter extends QIOFilter {

    public static final String TYPE = "gas";
    private GasStack gas;

    public QIOGasFilter() {
    }

    public QIOGasFilter(GasStack gas) {
        this.gas = gas == null || gas.getGas() == null ? null : new GasStack(gas.getGas(), 1);
    }

    public GasStack getGas() {
        return gas == null ? null : gas.copy();
    }

    public GasStack getGasStack() {
        return getGas();
    }

    public void setGas(GasStack gas) {
        this.gas = gas == null || gas.getGas() == null ? null : new GasStack(gas.getGas(), 1);
    }

    @Override public QIOResourceFamilyMatcher getMatcher() {
        return QIOResourceFamilyMatcher.family(QIOResourceCodecs.GAS_FAMILY);
    }

    @Override
    public boolean matches(QIOResourceEntry entry) {
        GasStack candidate = entry.getGas();
        return gas != null && candidate != null && gas.isGasEqual(candidate);
    }

    @Override
    public boolean matches(GasStack stack) {
        return gas != null && stack != null && gas.isGasEqual(stack);
    }

    @Override public String getType() { return TYPE; }

    @Override public boolean hasFilter() { return gas != null && gas.getGas() != null; }

    @Override
    public void writePayload(NBTTagCompound data) {
        if (gas != null) {
            gas.write(data);
        }
    }

    @Override
    protected void readPayload(NBTTagCompound data) {
        gas = GasStack.readFromNBT(data);
    }
}
