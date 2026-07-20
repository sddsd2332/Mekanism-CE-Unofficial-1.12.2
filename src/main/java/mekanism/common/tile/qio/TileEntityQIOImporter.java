package mekanism.common.tile.qio;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IMekanismGasHandler;
import mekanism.api.gas.IExtendedGasHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIORollback;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.lib.inventory.HashedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.HashSet;
import java.util.Set;

/** Pulls item, fluid and gas resources from the adjacent block into QIO. */
public class TileEntityQIOImporter extends TileEntityQIOFilterHandler {

    private static final int MAX_DELAY = 10;
    private int delay;

    public TileEntityQIOImporter() {
        super("QIOImporter");
    }

    public boolean getImportWithoutFilter() {
        return isFilterless();
    }

    public void setImportWithoutFilter(boolean value) {
        setFilterless(value);
    }

    public void toggleImportWithoutFilter() {
        toggleFilterless();
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (!MekanismUtils.canFunction(this)) {
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        QIOFrequency frequency = getFrequencyForTransfer();
        TileEntity adjacent = getAdjacentTile();
        if (frequency != null && adjacent != null) {
            importItems(frequency, adjacent);
            importFluids(frequency, adjacent);
            importGases(frequency, adjacent);
        }
        delay = MAX_DELAY;
    }

    private boolean importItems(QIOFrequency frequency, TileEntity adjacent) {
        IItemHandler handler = CapabilityUtils.getCapability(adjacent, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, getHandlerSide());
        return handler != null && importItems(frequency, handler);
    }

    boolean importItems(QIOFrequency frequency, IItemHandler handler) {
        if (frequency == null || handler == null) {
            return false;
        }
        int count = 0;
        int types = 0;
        boolean moved = false;
        Set<HashedItem> seenTypes = new HashSet<>();
        for (int slot = handler.getSlots() - 1; slot >= 0 && count < getMaxTransitCount(); slot--) {
            ItemStack simulated = handler.extractItem(slot, getMaxTransitCount() - count, true);
            if (simulated.isEmpty() || !acceptsItem(simulated)) {
                continue;
            }
            HashedItem type = HashedItem.create(simulated);
            boolean newType = !seenTypes.contains(type);
            if (newType && types >= getMaxTransitTypes()) {
                continue;
            }
            long accepted = frequency.massInsert(simulated, simulated.getCount(), Action.SIMULATE);
            if (accepted <= 0) {
                continue;
            }
            int toExtract = (int) Math.min(simulated.getCount(), accepted);
            ItemStack extracted = handler.extractItem(slot, toExtract, false);
            if (extracted.isEmpty()) {
                continue;
            }
            long inserted = frequency.massInsert(extracted, extracted.getCount(), Action.EXECUTE);
            if (inserted < extracted.getCount()) {
                ItemStack remainder = extracted.copy();
                remainder.shrink((int) Math.min(Integer.MAX_VALUE, inserted));
                ItemStack unreturned = handler.insertItem(slot, remainder, false);
                if (!unreturned.isEmpty()) {
                    inserted += QIORollback.restore(frequency, unreturned, unreturned.getCount(), "importer item transfer");
                }
            }
            if (inserted > 0) {
                if (newType) {
                    seenTypes.add(type);
                    types++;
                }
                count += (int) inserted;
                moved = true;
            }
        }
        return moved;
    }

    private boolean importFluids(QIOFrequency frequency, TileEntity adjacent) {
        IFluidHandler handler = CapabilityUtils.getCapability(adjacent, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, getHandlerSide());
        return handler != null && importFluids(frequency, handler);
    }

    boolean importFluids(QIOFrequency frequency, IFluidHandler handler) {
        if (frequency == null || handler == null) {
            return false;
        }
        int remaining = getMaxTransitCount() * 1000;
        int types = 0;
        Set<FluidStack> seenTypes = new HashSet<>();
        boolean moved = false;
        IFluidTankProperties[] tanks = handler.getTankProperties();
        if (tanks == null) {
            return false;
        }
        for (int tank = 0; tank < tanks.length && remaining > 0; tank++) {
            FluidStack contents = tanks[tank].getContents();
            if (contents == null || contents.amount <= 0 || !acceptsFluid(contents)) {
                continue;
            }
            FluidStack type = new FluidStack(contents, 1);
            boolean newType = !seenTypes.contains(type);
            if (newType && types >= getMaxTransitTypes()) {
                continue;
            }
            FluidStack simulated = handler.drain(new FluidStack(contents, Math.min(contents.amount, remaining)), false);
            if (simulated == null || simulated.amount <= 0) {
                continue;
            }
            long accepted = frequency.massInsert(simulated, simulated.amount, Action.SIMULATE);
            if (accepted <= 0) {
                continue;
            }
            int amount = (int) Math.min(simulated.amount, accepted);
            FluidStack drained = handler.drain(new FluidStack(simulated, amount), true);
            if (drained == null || drained.amount <= 0) {
                continue;
            }
            long inserted = frequency.massInsert(drained, drained.amount, Action.EXECUTE);
            if (inserted < drained.amount) {
                int remainder = (int) (drained.amount - inserted);
                int restored = handler.fill(new FluidStack(drained, remainder), true);
                if (restored < remainder) {
                    long unresolved = remainder - restored;
                    inserted += QIORollback.restore(frequency, new FluidStack(drained, (int) unresolved), unresolved,
                          "importer fluid transfer");
                }
            }
            if (inserted > 0) {
                moved = true;
                if (newType) {
                    seenTypes.add(type);
                    types++;
                }
                remaining -= (int) Math.min(Integer.MAX_VALUE, inserted);
            }
        }
        return moved;
    }

    private boolean importGases(QIOFrequency frequency, TileEntity adjacent) {
        mekanism.api.gas.IGasHandler handler = CapabilityUtils.getCapability(adjacent, Capabilities.GAS_HANDLER_CAPABILITY, getHandlerSide());
        return handler != null && importGases(frequency, handler);
    }

    boolean importGases(QIOFrequency frequency, mekanism.api.gas.IGasHandler handler) {
        if (frequency == null || handler == null) {
            return false;
        }
        int remaining = getMaxTransitCount() * 1000;
        int types = 0;
        Set<String> seenTypes = new HashSet<>();
        boolean moved = false;
        int attemptLimit = getGasImportAttemptLimit(handler);
        for (int attempt = 0; attempt < attemptLimit && remaining > 0; attempt++) {
            GasStack simulated = drawMatchingGas(handler, remaining, false, seenTypes, types < getMaxTransitTypes());
            if (simulated == null || simulated.amount <= 0 || !acceptsGas(simulated)) {
                break;
            }
            String type = simulated.getGas().getName();
            boolean newType = !seenTypes.contains(type);
            if (newType && types >= getMaxTransitTypes()) {
                break;
            }
            long accepted = frequency.massInsert(simulated, simulated.amount, Action.SIMULATE);
            if (accepted <= 0) {
                break;
            }
            GasStack drawn = drawMatchingGas(handler, (int) Math.min(simulated.amount, accepted), true,
                  java.util.Collections.singleton(type), false);
            if (drawn == null || drawn.amount <= 0) {
                break;
            }
            long inserted = frequency.massInsert(drawn, drawn.amount, Action.EXECUTE);
            if (inserted < drawn.amount) {
                GasStack remainder = drawn.copy();
                remainder.amount -= (int) inserted;
                int restored = handler.receiveGas(getHandlerSide(), remainder, true);
                if (restored < remainder.amount) {
                    long unresolved = remainder.amount - restored;
                    inserted += QIORollback.restore(frequency, new GasStack(remainder.getGas(), (int) unresolved), unresolved,
                          "importer gas transfer");
                }
            }
            if (inserted > 0) {
                moved = true;
                if (newType) {
                    seenTypes.add(type);
                    types++;
                }
                remaining -= (int) Math.min(Integer.MAX_VALUE, inserted);
            }
        }
        return moved;
    }

    @javax.annotation.Nullable
    private GasStack drawMatchingGas(mekanism.api.gas.IGasHandler handler, int amount, boolean execute,
          Set<String> seenTypes, boolean allowNewType) {
        net.minecraft.util.EnumFacing side = getHandlerSide();
        mekanism.api.Action action = mekanism.api.Action.get(execute);
        if (handler instanceof IMekanismGasHandler) {
            IMekanismGasHandler extended = (IMekanismGasHandler) handler;
            java.util.List<mekanism.api.gas.IExtendedGasTank> tanks = extended.getGasTanks(side);
            for (int i = 0; i < tanks.size(); i++) {
                GasStack stored = extended.getGasInTank(i, side);
                if (isGasImportCandidate(stored, seenTypes, allowNewType)) {
                    return extended.extractGas(i, amount, side, action);
                }
            }
            return null;
        }
        if (handler instanceof IExtendedGasHandler) {
            IExtendedGasHandler extended = (IExtendedGasHandler) handler;
            for (int i = 0; i < extended.getCountGasTanks(); i++) {
                GasStack stored = extended.getGasInTank(i);
                if (isGasImportCandidate(stored, seenTypes, allowNewType)) {
                    return extended.extractGas(i, amount, action);
                }
            }
            return null;
        }
        GasStack drawn = handler.drawGas(side, amount, execute);
        if (!isGasImportCandidate(drawn, seenTypes, allowNewType)) {
            if (execute && drawn != null && drawn.amount > 0) {
                handler.receiveGas(side, drawn, true);
            }
            return null;
        }
        return drawn;
    }

    private boolean isGasImportCandidate(GasStack stack, Set<String> seenTypes, boolean allowNewType) {
        return stack != null && stack.amount > 0 && stack.getGas() != null && acceptsGas(stack) &&
              (allowNewType || seenTypes.contains(stack.getGas().getName()));
    }

    private int getGasImportAttemptLimit(mekanism.api.gas.IGasHandler handler) {
        if (handler instanceof IMekanismGasHandler) {
            return Math.max(getMaxTransitTypes(), ((IMekanismGasHandler) handler).getCountGasTanks(getHandlerSide()));
        }
        if (handler instanceof IExtendedGasHandler) {
            return Math.max(getMaxTransitTypes(), ((IExtendedGasHandler) handler).getCountGasTanks());
        }
        return getMaxTransitTypes();
    }
}
