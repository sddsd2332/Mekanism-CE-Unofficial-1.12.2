package mekanism.common.recipe.cache;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.machines.MachineRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** Exactly one operation is supplied at a time, on real lanes with their normal installed upgrades. */
final class RecipeCycleTracker {
    final List<CycleLane> lanes = new ArrayList<>();
    final int[] recipeIndexes = new int[3];
    final MachineRecipe<?, ?, ?>[] choices = new MachineRecipe[3];
    final int target;
    final boolean switching;
    private boolean measuring;

    RecipeCycleTracker(MachineStressBindings bindings, List<MachineRecipe<?, ?, ?>> definitions,
          MachineRecipe<?, ?, ?> first, String pattern, int target) {
        this.target = target;
        switching = "cycle".equals(pattern);
        choices[0] = first; recipeIndexes[0] = definitions.indexOf(first);
        int selected = 1;
        for (int offset = 1; selected < 3 && offset < definitions.size(); offset++) {
            int index = (recipeIndexes[0] + offset) % definitions.size();
            MachineRecipe<?, ?, ?> candidate = definitions.get(index);
            boolean distinct = true;
            for (int i = 0; i < selected; i++) distinct &= !sameInput(candidate, choices[i]);
            if (distinct) { choices[selected] = candidate; recipeIndexes[selected++] = index; }
        }
        MachineStressFixtures.check(selected == 3 || !switching,
              "Cycle requires three different matching inputs: " + first.getClass().getName());
        for (MachineStressBindings.Lane lane : bindings.lanes) lanes.add(new CycleLane(lane, switching ? 2 : 0));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean sameInput(MachineRecipe first, MachineRecipe second) {
        return first.getInput().testEquality(second.getInput()) || second.getInput().testEquality(first.getInput());
    }

    void begin(long tick) {
        measuring = true;
        for (CycleLane lane : lanes) if (lane.atBoundary) lane.arm(tick);
    }

    void prepare() {
        for (CycleLane lane : lanes) {
            for (Object output : lane.binding.outputs) MachineStressFixtures.Entry.clear(output);
            if (lane.done()) continue;
            if (lane.needsSupply) MachineStressBindings.definition(lane.binding, choices[lane.choice]);
            for (MachineStressBindings.Input input : lane.binding.inputs) {
                if (input.perTick) MachineStressFixtures.Entry.fill(input.container, input.value);
                else if (lane.needsSupply) fillOperation(input);
            }
            lane.needsSupply = false;
        }
    }

    private static void fillOperation(MachineStressBindings.Input input) {
        if (input.container instanceof IInventorySlot) {
            IInventorySlot slot = (IInventorySlot) input.container;
            ItemStack value = ((ItemStack) input.value).copy();
            if (value.getMetadata() == 32767) value.setItemDamage(0);
            MachineStressFixtures.check(value.getCount() <= slot.getLimit(value), "Recipe input exceeds real slot capacity");
            slot.setStack(value);
        } else if (input.container instanceof IExtendedGasTank) {
            IExtendedGasTank tank = (IExtendedGasTank) input.container;
            GasStack value = ((GasStack) input.value).copy();
            MachineStressFixtures.check(value.amount <= tank.getCapacity(), "Recipe input exceeds gas tank capacity");
            tank.setStack(value);
        } else {
            IExtendedFluidTank tank = (IExtendedFluidTank) input.container;
            FluidStack value = ((FluidStack) input.value).copy();
            MachineStressFixtures.check(value.amount <= tank.getCapacity(), "Recipe input exceeds fluid tank capacity");
            tank.setStack(value);
        }
    }

    void observe(long tick) {
        for (CycleLane lane : lanes) {
            if (lane.done()) continue;
            Object cached = lane.binding.cached();
            if (lane.checkCache) {
                if (lane.armed) {
                    if (cached != null && cached == lane.previousCache) lane.cacheReuses++;
                    else lane.cacheReplacements++;
                }
                lane.previousCache = cached;
                lane.checkCache = false;
            }
            long completed = MachineStressBindings.completed(lane.binding, choices[lane.choice]);
            MachineStressFixtures.check(completed <= 1, "Cycle fixture supplied more than one operation");
            lane.atBoundary = completed == 1;
            if (completed == 0) continue;
            lane.needsSupply = true; lane.checkCache = true;
            if (measuring && !lane.armed) { lane.arm(tick); continue; }
            if (lane.armed) {
                lane.completed++; lane.byChoice[lane.choice]++;
                if (lane.done()) { lane.finishedTick = tick; continue; }
                lane.choice = switching ? lane.completed % 3 : 0;
            }
        }
    }

    boolean done() {
        for (CycleLane lane : lanes) if (!lane.done()) return false;
        return true;
    }

    final class CycleLane {
        final MachineStressBindings.Lane binding;
        final int[] byChoice = new int[3];
        int choice;
        int completed;
        long startedTick;
        long finishedTick;
        long cacheReuses;
        long cacheReplacements;
        Object previousCache;
        boolean needsSupply = true;
        boolean checkCache = true;
        boolean atBoundary;
        boolean armed;
        CycleLane(MachineStressBindings.Lane binding, int choice) { this.binding = binding; this.choice = choice; }
        void arm(long tick) { armed = true; choice = 0; startedTick = tick; needsSupply = true; checkCache = true; }
        boolean done() { return completed == target; }
    }
}
