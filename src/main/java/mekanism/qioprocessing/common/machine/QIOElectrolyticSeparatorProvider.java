package mekanism.qioprocessing.common.machine;

import mekanism.api.gas.GasStack;
import mekanism.api.processing.*;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only route adapter for the fluid-to-two-gas Electrolytic Separator.
 */
public final class QIOElectrolyticSeparatorProvider implements MachineRecipeProvider<TileEntityElectrolyticSeparator> {
    public static final ResourceLocation ID = new ResourceLocation("mekanism", "qio_electrolytic_separator");
    private static final ProviderConformanceDescriptor CONFORMANCE = ProviderConformanceDescriptor
            .builder("mekanism", "qio/electrolytic_separator")
            .supports(QIOAutomationMode.SCHEDULED)
            .build();

    @Override
    public boolean isAvailable(TileEntityElectrolyticSeparator tile) {
        return tile != null && !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote
                && tile.getWorld().isBlockLoaded(tile.getPos()) && tile.getWorld().getTileEntity(tile.getPos()) == tile;
    }

    @Override
    public List<MachineRecipeRoute> getRecipeRoutes(TileEntityElectrolyticSeparator tile) {
        if (!isAvailable(tile)) {
            return Collections.emptyList();
        }
        List<MachineRecipeRoute> routes = new ArrayList<>();
        for (SeparatorRecipe recipe : tile.getRecipes().values()) {
            FluidInput input = recipe.getInput();
            ChemicalPairOutput output = recipe.getOutput();
            FluidStack fluid = input.ingredient;
            GasStack left = output.leftGas;
            GasStack right = output.rightGas;
            if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0 || left == null
                    || right == null || left.getGas() == null || right.getGas() == null
                    || left.amount <= 0 || right.amount <= 0) {
                continue;
            }
            String logicalKey = "electrolytic_separator/" + fluid.getFluid().getName() + "@" + fluid.amount
                    + "=" + gasName(left) + "@" + left.amount + "+" + gasName(right) + "@" + right.amount;
            addRoute(routes, logicalKey, fluid, left, right);
            if (!left.isGasEqual(right)) {
                addRoute(routes, logicalKey, fluid, right, left);
            }
        }
        return Collections.unmodifiableList(routes);
    }

    @Override
    public List<MachinePort> getPorts(TileEntityElectrolyticSeparator tile) {
        if (!isAvailable(tile)) {
            return Collections.emptyList();
        }
        List<MachinePort> ports = new ArrayList<>(3);
        addPort(ports, MachinePort.fluid("input", MachinePort.Role.INPUT, tile.fluidTank));
        addPort(ports, MachinePort.gas("left", MachinePort.Role.OUTPUT, tile.leftTank));
        addPort(ports, MachinePort.gas("right", MachinePort.Role.OUTPUT, tile.rightTank));
        return Collections.unmodifiableList(ports);
    }

    @Override
    public ProviderConformanceDescriptor getQIOConformance(TileEntityElectrolyticSeparator tile) {
        return CONFORMANCE;
    }

    private static void addRoute(List<MachineRecipeRoute> routes, String logicalKey, FluidStack fluid,
                                 GasStack left, GasStack right) {
        String routeId = logicalKey + "/left=" + gasName(left) + "/right=" + gasName(right);
        routes.add(MachineRecipeRoute.builder(routeId)
                .logicalRecipeKey(logicalKey)
                .inputFluid("input", fluid.copy())
                .outputGas("left", left.copy())
                .outputGas("right", right.copy())
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

