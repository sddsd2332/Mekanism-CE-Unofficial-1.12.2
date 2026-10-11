package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.*;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only route adapter for Mekanism's item-in/item-out electric machines.
 */
public final class QIOElectricMachineProvider implements MachineRecipeProvider<TileEntityElectricMachine> {
    public static final ResourceLocation ID = new ResourceLocation("mekanism", "qio_electric_machine");
    private static final ProviderConformanceDescriptor CONFORMANCE = ProviderConformanceDescriptor
            .builder("mekanism", "qio/electric_machine")
            .supports(QIOAutomationMode.SCHEDULED)
            .build();

    @Override
    public boolean isAvailable(TileEntityElectricMachine tile) {
        return tile != null && !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote
                && tile.getWorld().isBlockLoaded(tile.getPos()) && tile.getWorld().getTileEntity(tile.getPos()) == tile;
    }

    @Override
    public List<MachineRecipeRoute> getRecipeRoutes(TileEntityElectricMachine tile) {
        if (!isAvailable(tile)) return Collections.emptyList();
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (Object value : tile.getRecipes().values()) {
            if (!(value instanceof BasicMachineRecipe)) continue;
            BasicMachineRecipe<?> recipe = (BasicMachineRecipe<?>) value;
            ItemStack input = recipe.getInput().ingredient;
            ItemStack output = recipe.getOutput().output;
            if (input == null || input.isEmpty() || output == null || output.isEmpty()) continue;
            String routeId = "electric/" + recipe.getClass().getName() + ":"
                    + itemIdentity(input) + ":" + itemIdentity(output);
            routes.add(MachineRecipeRoute.builder(routeId)
                    .inputItem("input", input.copy())
                    .outputItem("output", output.copy())
                    .build());
        }
        return Collections.unmodifiableList(routes);
    }

    @Override
    public List<MachinePort> getPorts(TileEntityElectricMachine tile) {
        if (!isAvailable(tile)) return Collections.emptyList();
        List<mekanism.api.inventory.IInventorySlot> slots = tile.getInventorySlots(null);
        if (slots.size() < 2) return Collections.emptyList();
        return java.util.Arrays.asList(
                MachinePort.item("input", MachinePort.Role.INPUT, slots.get(0)),
                MachinePort.item("output", MachinePort.Role.OUTPUT, slots.get(1)));
    }

    private static String itemIdentity(ItemStack stack) {
        return QIOResourceCodecs.item(stack).toString();
    }

    @Override
    public ProviderConformanceDescriptor getQIOConformance(TileEntityElectricMachine tile) {
        return CONFORMANCE;
    }
}

