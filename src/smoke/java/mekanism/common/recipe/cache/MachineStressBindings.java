package mekanism.common.recipe.cache;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.DissolutionRecipe;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.machine.TileEntityChemicalCrystallizer;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import mekanism.common.tile.machine.TileEntityChemicalOxidizer;
import mekanism.common.tile.machine.TileEntityChemicalWasher;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Main-thread fixture access, independent of the production recipe planner API. */
final class MachineStressBindings {
    final List<Input> inputs = new ArrayList<>();
    final List<Object> outputs = new ArrayList<>();
    final List<Lane> lanes = new ArrayList<>();
    private Lane current;

    static MachineStressBindings bind(TileEntityBasicBlock tile, MachineRecipe<?, ?, ?> recipe) {
        MachineStressBindings bindings = new MachineStressBindings();
        if (!(tile instanceof TileEntityFactory)) bindings.lane((RecipeCacheLookupMonitor<?>) field(tile, "recipeCacheLookupMonitor"), 0);
        if (tile instanceof TileEntityFactory) {
            // Fixed fields of the built-in fixture, resolved once during setup.
            // This is not an extension projector and never runs on a worker.
            RecipeCacheLookupMonitor<?>[] monitors = (RecipeCacheLookupMonitor<?>[]) field(tile, "recipeCacheLookupMonitors");
            int lane = 0;
            for (Object process : (Object[]) field(tile, "processInfoSlots")) {
                bindings.lane(monitors[lane], lane++);
                bindings.input(field(process, "inputSlot"), ((ItemStackInput) recipe.getInput()).ingredient);
                bindings.output(field(process, "outputSlot"));
            }
        } else if (tile instanceof TileEntityElectricMachine<?>) {
            bindings.input(field(tile, "inputSlot"), ((ItemStackInput) recipe.getInput()).ingredient);
            bindings.output(field(tile, "outputSlot"));
        } else if (tile instanceof TileEntityChemicalOxidizer) {
            TileEntityChemicalOxidizer machine = (TileEntityChemicalOxidizer) tile;
            bindings.input(machine.getRecipeInputSlot(), ((ItemStackInput) recipe.getInput()).ingredient);
            bindings.output(machine.gasTank);
        } else if (tile instanceof TileEntityChemicalCrystallizer) {
            TileEntityChemicalCrystallizer machine = (TileEntityChemicalCrystallizer) tile;
            bindings.input(machine.inputTank, ((GasInput) recipe.getInput()).ingredient);
            bindings.output(machine.getRecipeOutputSlot());
        } else if (tile instanceof TileEntityChemicalDissolutionChamber) {
            TileEntityChemicalDissolutionChamber machine = (TileEntityChemicalDissolutionChamber) tile;
            bindings.input(machine.getRecipeInputSlot(), ((ItemStackInput) recipe.getInput()).ingredient);
            bindings.input(machine.injectTank, ((DissolutionRecipe) recipe).getGasInput());
            bindings.output(machine.outputTank);
        } else if (tile instanceof TileEntityChemicalWasher) {
            TileEntityChemicalWasher machine = (TileEntityChemicalWasher) tile;
            GasAndFluidInput input = (GasAndFluidInput) recipe.getInput();
            bindings.input(machine.inputTank, input.ingredientGas);
            bindings.input(machine.fluidTank, input.ingredientFluid);
            bindings.output(machine.outputTank);
        } else throw new IllegalArgumentException("No real fixture binding for " + tile.getClass().getName());
        return bindings;
    }

    private void lane(RecipeCacheLookupMonitor<?> monitor, int index) {
        current = new Lane(monitor, index); lanes.add(current);
    }
    private void input(Object container, Object value) {
        Input input = new Input(container, value); inputs.add(input); current.inputs.add(input);
    }
    private void output(Object container) { outputs.add(container); current.outputs.add(container); }

    static void definition(Lane lane, MachineRecipe<?, ?, ?> recipe) {
        List<Object> values = new ArrayList<>();
        if (recipe.getInput() instanceof ItemStackInput) values.add(((ItemStackInput) recipe.getInput()).ingredient);
        else if (recipe.getInput() instanceof GasInput) values.add(((GasInput) recipe.getInput()).ingredient);
        else if (recipe.getInput() instanceof GasAndFluidInput) {
            values.add(((GasAndFluidInput) recipe.getInput()).ingredientGas);
            values.add(((GasAndFluidInput) recipe.getInput()).ingredientFluid);
        } else throw new IllegalArgumentException("Unsupported cycle fixture input");
        if (recipe instanceof DissolutionRecipe) values.add(((DissolutionRecipe) recipe).getGasInput());
        MachineStressFixtures.check(values.size() == lane.inputs.size(), "Cycle changed resource binding shape");
        for (int i = 0; i < values.size(); i++) {
            lane.inputs.get(i).value = values.get(i);
            lane.inputs.get(i).perTick = recipe instanceof DissolutionRecipe && i == 1;
        }
    }

    static long completed(Lane lane, MachineRecipe<?, ?, ?> recipe) {
        MachineStressFixtures.check(lane.outputs.size() == 1, "Cycle fixture requires an explicit deterministic output");
        Object container = lane.outputs.get(0);
        long produced = amount(container);
        if (produced == 0) return 0;
        long perOperation;
        if (recipe.getOutput() instanceof ItemStackOutput) {
            ItemStack expected = ((ItemStackOutput) recipe.getOutput()).output;
            ItemStack actual = ((IInventorySlot) container).getStack();
            // Forge's tag equality also tests capability stackability. An item's
            // fresh output copy may be unstackable with its registered template
            // (e.g. IC2 mugs) while carrying exactly the expected saved state.
            NBTTagCompound expectedData = expected.serializeNBT();
            NBTTagCompound actualData = actual.serializeNBT();
            actualData.setTag("Count", expectedData.getTag("Count").copy());
            if (!ItemStack.areItemsEqual(expected, actual) || !expectedData.equals(actualData)) {
                throw new AssertionError("Cycle produced the wrong item: expected=" + expectedData + " actual=" + actualData +
                      " recipe=" + recipeKey(recipe) + " lane=" + lane.index);
            }
            perOperation = expected.getCount();
        } else {
            GasStack expected = ((GasOutput) recipe.getOutput()).output;
            MachineStressFixtures.check(((IExtendedGasTank) container).getGas().isGasEqual(expected), "Cycle produced the wrong gas");
            perOperation = expected.amount;
        }
        MachineStressFixtures.check(perOperation > 0 && produced % perOperation == 0, "Cycle output is not an integral operation count");
        return produced / perOperation;
    }

    private static Object field(Object owner, String name) {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                // The named built-in field may be inherited.
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalStateException("Missing fixture field " + owner.getClass().getName() + '.' + name);
    }

    static long amount(Object container) {
        if (container instanceof IInventorySlot) return ((IInventorySlot) container).getCount();
        if (container instanceof IExtendedGasTank) return ((IExtendedGasTank) container).getStored();
        if (container instanceof IExtendedFluidTank) return ((IExtendedFluidTank) container).getFluidAmount();
        throw new IllegalArgumentException("Unsupported fixture container " + container.getClass().getName());
    }

    /** A diagnostic ordering key for the explicitly covered fixture recipe shapes. */
    static String recipeKey(MachineRecipe<?, ?, ?> recipe) {
        StringBuilder result = new StringBuilder(recipe.getClass().getName());
        if (recipe.getInput() instanceof ItemStackInput) append(result, ((ItemStackInput) recipe.getInput()).ingredient);
        else if (recipe.getInput() instanceof GasInput) append(result, ((GasInput) recipe.getInput()).ingredient);
        else if (recipe.getInput() instanceof GasAndFluidInput) {
            GasAndFluidInput input = (GasAndFluidInput) recipe.getInput();
            append(result, input.ingredientGas); append(result, input.ingredientFluid);
        } else throw new IllegalArgumentException("Unsupported fixture input " + recipe.getInput().getClass());
        if (recipe instanceof DissolutionRecipe) append(result, ((DissolutionRecipe) recipe).getGasInput());
        if (recipe.getOutput() instanceof ItemStackOutput) append(result, ((ItemStackOutput) recipe.getOutput()).output);
        else if (recipe.getOutput() instanceof GasOutput) append(result, ((GasOutput) recipe.getOutput()).output);
        else throw new IllegalArgumentException("Unsupported fixture output " + recipe.getOutput().getClass());
        return result.toString();
    }

    private static void append(StringBuilder result, Object resource) {
        NBTTagCompound tag = new NBTTagCompound();
        if (resource instanceof ItemStack) ((ItemStack) resource).writeToNBT(tag);
        else if (resource instanceof GasStack) ((GasStack) resource).write(tag);
        else if (resource instanceof FluidStack) ((FluidStack) resource).writeToNBT(tag);
        else throw new IllegalArgumentException("Unsupported fixture resource");
        result.append('|'); canonical(result, tag);
    }

    private static void canonical(StringBuilder result, NBTBase tag) {
        result.append(tag.getId()).append(':');
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            result.append('{');
            for (String key : new TreeSet<>(compound.getKeySet())) {
                result.append(key.length()).append(':').append(key);
                canonical(result, compound.getTag(key));
            }
            result.append('}');
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            result.append('[').append(list.getTagType()).append(':');
            for (int i = 0; i < list.tagCount(); i++) canonical(result, list.get(i));
            result.append(']');
        } else {
            String value = tag.toString();
            result.append(value.length()).append(':').append(value);
        }
    }

    static final class Input {
        final Object container;
        Object value;
        boolean perTick;
        Input(Object container, Object value) { this.container = container; this.value = value; }
    }

    static final class Lane {
        final RecipeCacheLookupMonitor<?> monitor;
        final int index;
        final List<Input> inputs = new ArrayList<>();
        final List<Object> outputs = new ArrayList<>();
        Lane(RecipeCacheLookupMonitor<?> monitor, int index) { this.monitor = monitor; this.index = index; }
        Object cached() { return monitor.getCachedRecipe(index); }
    }
}
