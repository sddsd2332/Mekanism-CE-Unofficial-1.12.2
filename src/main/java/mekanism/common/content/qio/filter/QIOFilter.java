package mekanism.common.content.qio.filter;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.PacketHandler;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import mekanism.api.gas.GasStack;
import net.minecraftforge.fluids.FluidStack;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.GasUtils;

import javax.annotation.Nullable;
import java.util.Objects;

/** Base for kind-aware QIO automation filters. */
public abstract class QIOFilter implements IFilter {

    private static final int DATA_VERSION = 1;

    /** Creates the most specific resource filter represented by a held stack. */
    @Nullable
    public static QIOFilter fromItemStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (FluidContainerUtils.isFluidContainer(stack)) {
            net.minecraftforge.fluids.capability.IFluidHandlerItem handler = FluidContainerUtils.getUnstackedFluidHandlerCapability(stack);
            if (handler != null) {
                FluidStack fluid = handler.drain(Integer.MAX_VALUE, false);
                if (fluid != null && fluid.amount > 0) {
                    return new QIOFluidFilter(fluid);
                }
            }
        }
        GasStack gas = GasUtils.getCapabilityStoredGas(stack);
        if (gas != null && gas.amount > 0 && gas.getGas() != null) {
            return new QIOGasFilter(gas);
        }
        return new QIOItemStackFilter(stack);
    }

    private boolean enabled = true;

    public abstract QIOResourceKind getKind();

    public abstract boolean matches(QIOResourceEntry entry);

    /**
     * Kind-specific matching helpers used while importing resources.  A
     * resource entering a network does not have a UUID yet, so forcing the
     * caller to manufacture a registry entry would make filtering dependent
     * on a side effect (registering a new resource type).
     */
    public boolean matches(ItemStack stack) {
        return false;
    }

    public boolean matches(FluidStack stack) {
        return false;
    }

    public boolean matches(GasStack stack) {
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public final boolean test(@Nullable QIOResourceEntry entry) {
        return enabled && entry != null && entry.getKind() == getKind() && matches(entry);
    }

    public final boolean test(@Nullable ItemStack stack) {
        return enabled && getKind() == QIOResourceKind.ITEM && stack != null && !stack.isEmpty() && matches(stack);
    }

    public final boolean test(@Nullable FluidStack stack) {
        return enabled && getKind() == QIOResourceKind.FLUID && stack != null && matches(stack);
    }

    public final boolean test(@Nullable GasStack stack) {
        return enabled && getKind() == QIOResourceKind.GAS && stack != null && matches(stack);
    }

    public abstract String getType();

    /** Whether this filter contains a concrete resource after decoding. */
    public abstract boolean hasFilter();

    public abstract void writePayload(NBTTagCompound data);

    protected abstract void readPayload(NBTTagCompound data);

    public final NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", DATA_VERSION);
        data.setString("type", getType());
        data.setBoolean("enabled", enabled);
        NBTTagCompound payload = new NBTTagCompound();
        writePayload(payload);
        data.setTag("payload", payload);
        return data;
    }

    @Nullable
    public final QIOFilter copy() {
        return read(write());
    }

    public final void writeToPacket(TileNetworkList data) {
        data.add(write());
    }

    @Nullable
    public static QIOFilter readFromPacket(ByteBuf buffer) {
        return read(PacketHandler.readNBT(buffer));
    }

    @Nullable
    public static QIOFilter read(NBTTagCompound data) {
        if (data == null) {
            return null;
        }
        if (data.hasKey("version") && data.getInteger("version") != DATA_VERSION) {
            return null;
        }
        String type = data.getString("type");
        QIOFilter filter;
        switch (type) {
            case QIOItemStackFilter.TYPE:
                filter = new QIOItemStackFilter();
                break;
            case QIOOreDictFilter.TYPE:
                filter = new QIOOreDictFilter();
                break;
            case QIOModIDFilter.TYPE:
                filter = new QIOModIDFilter();
                break;
            case QIOFluidFilter.TYPE:
                filter = new QIOFluidFilter();
                break;
            case QIOGasFilter.TYPE:
                filter = new QIOGasFilter();
                break;
            default:
                return null;
        }
        try {
            filter.enabled = !data.hasKey("enabled") || data.getBoolean("enabled");
            filter.readPayload(data.getCompoundTag("payload"));
            return filter.hasFilter() ? filter : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj || obj instanceof QIOFilter && write().equals(((QIOFilter) obj).write());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(write());
    }
}
