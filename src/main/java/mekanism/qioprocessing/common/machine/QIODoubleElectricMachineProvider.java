package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.*;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.prefab.TileEntityDoubleElectricMachine;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only route adapter for Mekanism's two-item electric machines.
 */
public final class QIODoubleElectricMachineProvider implements MachineRecipeProvider<TileEntityDoubleElectricMachine> {
    public static final ResourceLocation ID = new ResourceLocation("mekanism", "qio_double_electric_machine");
    private static final ProviderConformanceDescriptor CONFORMANCE = ProviderConformanceDescriptor
            .builder("mekanism", "qio/double_electric_machine")
            .supports(QIOAutomationMode.SCHEDULED)
            .build();

    @Override
    public boolean isAvailable(TileEntityDoubleElectricMachine tile) {
        return tile != null && !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote
                && tile.getWorld().isBlockLoaded(tile.getPos()) && tile.getWorld().getTileEntity(tile.getPos()) == tile;
    }

    @Override
    public List<MachineRecipeRoute> getRecipeRoutes(TileEntityDoubleElectricMachine tile) {
        if (!isAvailable(tile)) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (Object value : tile.getRecipes().values()) {
            if (!(value instanceof DoubleMachineRecipe)) {
                continue;
            }
            DoubleMachineRecipe recipe = (DoubleMachineRecipe) value;
            if (!(recipe.getInput() instanceof DoubleMachineInput)
                    || !(recipe.getOutput() instanceof ItemStackOutput)) {
                continue;
            }
            DoubleMachineInput input = (DoubleMachineInput) recipe.getInput();
            ItemStack first = input.itemStack;
            ItemStack second = input.extraStack;
            ItemStack output = ((ItemStackOutput) recipe.getOutput()).output;
            if (first == null || first.isEmpty() || second == null || second.isEmpty()
                    || output == null || output.isEmpty()) {
                continue;
            }
            String routeId = "double_electric/" + recipe.getClass().getName() + ":"
                    + itemIdentity(first) + ":" + itemIdentity(second) + ":" + itemIdentity(output);
            routes.add(MachineRecipeRoute.builder(routeId)
                    .inputItem("input", first.copy())
                    .inputItem("extra", second.copy())
                    .outputItem("output", output.copy())
                    .build());
        }
        return Collections.unmodifiableList(routes);
    }

    @Override
    public List<MachinePort> getPorts(TileEntityDoubleElectricMachine tile) {
        if (!isAvailable(tile)) {
            return Collections.emptyList();
        }
        List<mekanism.api.inventory.IInventorySlot> slots = tile.getInventorySlots(null);
        if (slots.size() < 3) {
            return Collections.emptyList();
        }
        List<MachinePort> ports = new ArrayList<>(3);
        addPort(ports, MachinePort.item("input", MachinePort.Role.INPUT, slots.get(0)));
        addPort(ports, MachinePort.item("extra", MachinePort.Role.INPUT, slots.get(1)));
        addPort(ports, MachinePort.item("output", MachinePort.Role.OUTPUT, slots.get(2)));
        return Collections.unmodifiableList(ports);
    }

    @Override
    public ProviderConformanceDescriptor getQIOConformance(TileEntityDoubleElectricMachine tile) {
        return CONFORMANCE;
    }

    private static void addPort(List<MachinePort> ports, MachinePort port) {
        if (port != null) {
            ports.add(port);
        }
    }

    private static String itemIdentity(ItemStack stack) {
        return QIOResourceCodecs.item(stack).toString();
    }
}

