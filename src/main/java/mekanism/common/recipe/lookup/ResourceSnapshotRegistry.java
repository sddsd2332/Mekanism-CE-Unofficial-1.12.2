package mekanism.common.recipe.lookup;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseType;
import mekanism.common.InfuseStorage;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-thread capture/restore registry. It is owned by a planning session and
 * is never part of an entry, machine snapshot, calculator or worker result.
 */
public final class ResourceSnapshotRegistry implements AutoCloseable {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final Thread owner = Thread.currentThread();
    private final IdentityHashMap<Item, ResourceType> itemTypes = new IdentityHashMap<>();
    private final IdentityHashMap<Fluid, ResourceType> fluidTypes = new IdentityHashMap<>();
    private final IdentityHashMap<Gas, ResourceType> gasTypes = new IdentityHashMap<>();
    private final IdentityHashMap<InfuseType, ResourceType> infusionTypes = new IdentityHashMap<>();
    private final Map<Long, Item> items = new HashMap<>();
    private final Map<Long, Fluid> fluids = new HashMap<>();
    private final Map<Long, Gas> gases = new HashMap<>();
    private final Map<Long, InfuseType> infusions = new HashMap<>();
    private boolean closed;

    public void checkOwner() {
        if (Thread.currentThread() != owner || closed) throw new IllegalStateException("Recipe resource capture/restore requires its live server session");
    }

    private static ResourceType type(ResourceType.Kind kind, String name) {
        long id = NEXT_ID.updateAndGet(previous -> Math.addExact(previous, 1));
        return new ResourceType(id, kind, name);
    }

    /** Input matching excludes capabilities and never initializes or copies an ItemStack. */
    public ResourceIdentity captureIngredient(ItemStack stack) {
        checkOwner();
        if (stack == null || stack.isEmpty()) return ResourceIdentity.EMPTY;
        Item item = stack.getItem();
        ResourceType type = itemTypes.get(item);
        if (type == null) {
            type = type(ResourceType.Kind.ITEM, Objects.toString(item.getRegistryName(), "unregistered-item"));
            itemTypes.put(item, type); items.put(type.getId(), item);
        }
        return new ResourceIdentity(type, stack.getItemDamage(), stack.getMetadata(), NbtSnapshotCodec.freeze(stack.getTagCompound()), null);
    }

    public ResourceSnapshot capture(@Nullable ItemStack stack) {
        checkOwner();
        if (stack == null || stack.isEmpty()) return ResourceSnapshot.empty();
        Item item = stack.getItem();
        ResourceType type = itemTypes.get(item);
        if (type == null) {
            type = type(ResourceType.Kind.ITEM, Objects.toString(item.getRegistryName(), "unregistered-item"));
            itemTypes.put(item, type); items.put(type.getId(), item);
        }
        // Forge serializes capabilities here on the server thread. No ItemStack
        // copy or live capability provider is retained in the detached value.
        NBTTagCompound serialized = stack.serializeNBT();
        FrozenNbt capabilities = serialized.hasKey("ForgeCaps", 10) ? NbtSnapshotCodec.freeze(serialized.getCompoundTag("ForgeCaps")) : null;
        ResourceIdentity identity = new ResourceIdentity(type, stack.getItemDamage(), stack.getMetadata(),
              NbtSnapshotCodec.freeze(stack.getTagCompound()), capabilities);
        return ResourceSnapshot.of(identity, stack.getCount(), stack.getMaxStackSize());
    }

    public ResourceSnapshot capture(@Nullable FluidStack stack) {
        checkOwner();
        if (stack == null || stack.amount == 0) return ResourceSnapshot.empty();
        Fluid fluid = Objects.requireNonNull(stack.getFluid(), "Fluid type");
        ResourceType type = fluidTypes.get(fluid);
        if (type == null) {
            type = type(ResourceType.Kind.FLUID, fluid.getName());
            fluidTypes.put(fluid, type); fluids.put(type.getId(), fluid);
        }
        return ResourceSnapshot.of(new ResourceIdentity(type, 0, 0, NbtSnapshotCodec.freeze(stack.tag), null), stack.amount, 0);
    }

    public ResourceSnapshot capture(@Nullable GasStack stack) {
        checkOwner();
        if (stack == null || stack.amount == 0) return ResourceSnapshot.empty();
        Gas gas = Objects.requireNonNull(stack.getGas(), "Gas type");
        ResourceType type = gasTypes.get(gas);
        if (type == null) {
            type = type(ResourceType.Kind.GAS, gas.getName());
            gasTypes.put(gas, type); gases.put(type.getId(), gas);
        }
        return ResourceSnapshot.of(new ResourceIdentity(type, 0, 0, null, null), stack.amount, 0);
    }

    public ResourceSnapshot capture(InfuseStorage storage) {
        checkOwner();
        if (storage.getType() == null || storage.getAmount() == 0) return ResourceSnapshot.empty();
        InfuseType infusion = storage.getType();
        ResourceType type = infusionTypes.get(infusion);
        if (type == null) {
            type = type(ResourceType.Kind.INFUSION, infusion.name);
            infusionTypes.put(infusion, type); infusions.put(type.getId(), infusion);
        }
        return ResourceSnapshot.of(new ResourceIdentity(type, 0, 0, null, null), storage.getAmount(), 0);
    }

    public ItemStack item(ResourceSnapshot value) {
        checkOwner();
        if (value.isEmpty()) return ItemStack.EMPTY;
        require(value, ResourceType.Kind.ITEM);
        ResourceIdentity identity = value.getIdentity();
        Item item = Objects.requireNonNull(items.get(identity.getType().getId()), "Item belongs to another planning session");
        ItemStack stack = new ItemStack(item, Math.toIntExact(value.getAmount()), identity.getDamage(),
              (NBTTagCompound) NbtSnapshotCodec.thaw(identity.getCapabilities()));
        stack.setTagCompound((NBTTagCompound) NbtSnapshotCodec.thaw(identity.getTag()));
        return stack;
    }

    @Nullable
    public FluidStack fluid(ResourceSnapshot value) {
        checkOwner();
        if (value.isEmpty()) return null;
        require(value, ResourceType.Kind.FLUID);
        ResourceIdentity identity = value.getIdentity();
        return new FluidStack(Objects.requireNonNull(fluids.get(identity.getType().getId()), "Fluid belongs to another planning session"),
              Math.toIntExact(value.getAmount()), (NBTTagCompound) NbtSnapshotCodec.thaw(identity.getTag()));
    }

    @Nullable
    public GasStack gas(ResourceSnapshot value) {
        checkOwner();
        if (value.isEmpty()) return null;
        require(value, ResourceType.Kind.GAS);
        return new GasStack(Objects.requireNonNull(gases.get(value.getIdentity().getType().getId()), "Gas belongs to another planning session"),
              Math.toIntExact(value.getAmount()));
    }

    public InfuseStorage infusion(ResourceSnapshot value) {
        checkOwner();
        if (value.isEmpty()) return new InfuseStorage();
        require(value, ResourceType.Kind.INFUSION);
        return new InfuseStorage(Objects.requireNonNull(infusions.get(value.getIdentity().getType().getId()), "Infusion belongs to another planning session"),
              Math.toIntExact(value.getAmount()));
    }

    private static void require(ResourceSnapshot value, ResourceType.Kind kind) {
        if (value.getIdentity().getType().getKind() != kind) throw new IllegalArgumentException("Wrong resource kind for " + kind);
    }

    @Override public void close() {
        checkOwner();
        itemTypes.clear(); fluidTypes.clear(); gasTypes.clear(); infusionTypes.clear();
        items.clear(); fluids.clear(); gases.clear(); infusions.clear(); closed = true;
    }
}
