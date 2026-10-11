package mekanism.qioprocessing.common.machine;

import mekanism.api.gas.GasStack;
import mekanism.api.processing.*;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mekanism.common.tile.machine.TileEntityChemicalInfuser;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only route adapter for the two-gas Chemical Infuser.
 */
public final class QIOChemicalInfuserProvider implements MachineRecipeProvider<TileEntityChemicalInfuser> {
    public static final ResourceLocation ID = new ResourceLocation("mekanism", "qio_chemical_infuser");
    private static final ProviderConformanceDescriptor CONFORMANCE = ProviderConformanceDescriptor
            .builder("mekanism", "qio/chemical_infuser")
            .supports(QIOAutomationMode.SCHEDULED)
            .build();

    @Override
    public boolean isAvailable(TileEntityChemicalInfuser tile) {
        return tile != null && !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote
                && tile.getWorld().isBlockLoaded(tile.getPos()) && tile.getWorld().getTileEntity(tile.getPos()) == tile;
    }

    @Override
    public List<MachineRecipeRoute> getRecipeRoutes(TileEntityChemicalInfuser tile) {
        if (!isAvailable(tile)) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (ChemicalInfuserRecipe recipe : tile.getRecipes().values()) {
            ChemicalPairInput input = recipe.getInput();
            GasStack left = input.leftGas;
            GasStack right = input.rightGas;
            GasStack output = recipe.getOutput().output;
            if (left == null || right == null || output == null || left.getGas() == null
                    || right.getGas() == null || output.getGas() == null || left.amount <= 0
                    || right.amount <= 0 || output.amount <= 0) {
                continue;
            }
            String logicalKey = "chemical_infuser/" + gasName(left) + "@" + left.amount + "+"
                    + gasName(right) + "@" + right.amount + "=" + gasName(output) + "@" + output.amount;
            addRoute(routes, logicalKey, "left", left, "right", right, output);
            if (!left.isGasEqual(right)) {
                addRoute(routes, logicalKey, "left", right, "right", left, output);
            }
        }
        return Collections.unmodifiableList(routes);
    }

    @Override
    public List<MachinePort> getPorts(TileEntityChemicalInfuser tile) {
        if (!isAvailable(tile)) {
            return Collections.emptyList();
        }
        List<MachinePort> ports = new ArrayList<>(3);
        addPort(ports, MachinePort.gas("left", MachinePort.Role.INPUT, tile.leftTank));
        addPort(ports, MachinePort.gas("right", MachinePort.Role.INPUT, tile.rightTank));
        addPort(ports, MachinePort.gas("output", MachinePort.Role.OUTPUT, tile.centerTank));
        return Collections.unmodifiableList(ports);
    }

    @Override
    public ProviderConformanceDescriptor getQIOConformance(TileEntityChemicalInfuser tile) {
        return CONFORMANCE;
    }

    private static void addRoute(List<MachineRecipeRoute> routes, String logicalKey, String leftId,
                                 GasStack left, String rightId, GasStack right, GasStack output) {
        String routeId = logicalKey + "/" + leftId + "=" + gasName(left) + "/" + rightId + "=" + gasName(right);
        routes.add(MachineRecipeRoute.builder(routeId)
                .logicalRecipeKey(logicalKey)
                .inputGas(leftId, left.copy())
                .inputGas(rightId, right.copy())
                .outputGas("output", output.copy())
                .build());
    }

    private static String gasName(GasStack stack) {
        return stack.getGas().getName();
    }

    private static void addPort(List<MachinePort> ports, MachinePort port) {
        if (port != null) {
            ports.add(port);
        }
    }
}

