package mekanism.common.capabilities;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ItemCapabilityWrapper implements ICapabilityProvider {

    protected final ItemStack itemStack;
    private List<ItemCapability> capabilities = new ArrayList<>();
    private final boolean exposeWhenStacked;

    public ItemCapabilityWrapper(ItemStack stack, ItemCapability... caps) {
        this(stack, false, caps);
    }

    public ItemCapabilityWrapper(ItemStack stack, boolean exposeWhenStacked, ItemCapability... caps) {
        itemStack = stack;
        this.exposeWhenStacked = exposeWhenStacked;
        for (ItemCapability c : caps) {
            c.wrapper = this;
            c.init();
            capabilities.add(c);
        }
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        if (!exposeWhenStacked && itemStack.getCount() > 1) {
            return false;
        }
        for (ItemCapability cap : capabilities) {
            if (cap.canProcess(capability)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (!exposeWhenStacked && itemStack.getCount() > 1) {
            return null;
        }
        for (ItemCapability cap : capabilities) {
            if (cap.canProcess(capability)) {
                cap.load();
                return (T) cap;
            }
        }
        return null;
    }

    public abstract static class ItemCapability {

        private ItemCapabilityWrapper wrapper;

        public abstract boolean canProcess(Capability<?> capability);

        protected void init() {
        }

        protected void load() {
        }

        public ItemStack getStack() {
            return wrapper.itemStack;
        }
    }
}
