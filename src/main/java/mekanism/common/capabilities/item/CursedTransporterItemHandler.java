package mekanism.common.capabilities.item;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.transmitters.TransporterImpl;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public class CursedTransporterItemHandler implements IItemHandler {

    private final Map<Coord4D, Set<TransporterStack>> simulatedFlowingStacks = new HashMap<>();
    private final Set<ItemStack> seenStacks = new ReferenceOpenHashSet<>();
    private final Set<ItemStack> seenExecutedStacks = new ReferenceOpenHashSet<>();
    private final TransporterImpl transporter;
    private final Coord4D fromPos;
    private final LongSupplier currentTickSupplier;
    private final BooleanSupplier canInsert;
    private long lastTick;

    public CursedTransporterItemHandler(TransporterImpl transporter, Coord4D fromPos, LongSupplier currentTickSupplier, BooleanSupplier canInsert) {
        this.transporter = transporter;
        this.fromPos = fromPos;
        this.currentTickSupplier = currentTickSupplier;
        this.canInsert = canInsert;
    }

    @Override
    public int getSlots() {
        return 9;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    private TransitRequest getRequest(int limit, ItemStack stack) {
        if (stack.getCount() <= limit) {
            return TransitRequest.simple(stack);
        }
        return TransitRequest.simple(StackUtils.size(stack, limit));
    }

    public TransporterImpl getTransporter() {
        return transporter;
    }

    public Coord4D getFromPos() {
        return fromPos;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack itemStack, boolean simulate) {
        if (itemStack.isEmpty() || !canInsert.getAsBoolean() || !transporter.hasTransmitterNetwork()) {
            return itemStack;
        }
        long currentTick = currentTickSupplier.getAsLong();
        if (currentTick != lastTick) {
            seenStacks.clear();
            seenExecutedStacks.clear();
            simulatedFlowingStacks.clear();
            lastTick = currentTick;
        }

        int limit = getSlotLimit(slot);
        TransitResponse response;
        if (simulate) {
            if (seenExecutedStacks.contains(itemStack) || !seenStacks.add(itemStack)) {
                return itemStack;
            }
            TransitRequest request = getRequest(limit, itemStack);
            TransporterStack stack = transporter.createInsertStack(fromPos, transporter.getColor());
            response = stack.recalculatePath(request, transporter, 1, simulatedFlowingStacks);
            if (response.isEmpty()) {
                return itemStack;
            }
            stack.itemStack = response.getStack();
            if (stack.getPathType() != TransporterStack.Path.NONE) {
                simulatedFlowingStacks.computeIfAbsent(stack.getDest(), ignored -> new HashSet<>()).add(stack);
            }
        } else {
            if (!seenExecutedStacks.add(itemStack)) {
                return itemStack;
            }
            seenStacks.clear();
            simulatedFlowingStacks.clear();

            TransitRequest request = getRequest(limit, itemStack);
            response = transporter.insertUnchecked(fromPos, request, transporter.getColor(), true, 1);
            if (response.isEmpty()) {
                return itemStack;
            }
        }

        ItemStack remainder = response.getRejected();
        if (itemStack.getCount() > limit) {
            int extra = itemStack.getCount() - limit;
            if (remainder.isEmpty()) {
                remainder = StackUtils.size(itemStack, extra);
            } else {
                remainder = remainder.copy();
                remainder.grow(extra);
            }
        }

        if (!remainder.isEmpty()) {
            if (simulate) {
                seenStacks.add(remainder);
            } else {
                seenExecutedStacks.add(remainder);
            }
        }
        return remainder;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return transporter.getPullAmount();
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return true;
    }
}
