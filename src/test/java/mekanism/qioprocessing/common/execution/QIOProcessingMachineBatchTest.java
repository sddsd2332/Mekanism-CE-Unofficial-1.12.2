package mekanism.qioprocessing.common.execution;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.MachineTransferPlan;
import mekanism.common.TestBootstrap;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingMachineBatchTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void scaledBatchUsesExactInputOutputAndRecoveryAmounts() {
        TileEntityFurnace tile = new TileEntityFurnace();
        BasicInventorySlot inputSlot = BasicInventorySlot.at(null, 0, 0);
        BasicInventorySlot outputSlot = BasicInventorySlot.at(
              BasicInventorySlot.alwaysTrue, null, 0, 0, 7);
        MachinePort input = MachinePort.item("input", MachinePort.Role.INPUT, inputSlot);
        MachinePort output = MachinePort.item("output", MachinePort.Role.OUTPUT, outputSlot);
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        ports.put("input", input);
        ports.put("output", output);
        List<MachinePortBaseline> baselines = Arrays.asList(
              MachinePortBaseline.capture(input), MachinePortBaseline.capture(output));
        MachineRecipeRoute base = MachineRecipeRoute.builder("batch")
              .recipeKey("test:batch").logicalRecipeKey("test:batch")
              .input(MachineResourceStack.item("input", new ItemStack(Items.COAL, 2)))
              .output(MachineResourceStack.item("output", new ItemStack(Items.IRON_INGOT)))
              .build();

        assertTrue(QIOProcessingExecutionService.machineReadyForInput(tile, base, ports));
        assertEquals(7, QIOProcessingExecutionService.maxMachineOperations(
              tile, base, ports, 100));
        assertSame(base, QIOProcessingExecutionService.scaleRoute(base, 1));
        MachineRecipeRoute scaled = QIOProcessingExecutionService.scaleRoute(base, 7);
        assertEquals(14, scaled.inputs().get(0).amount());
        assertEquals(7, scaled.guaranteedOutputs().get(0).amount());

        MachineTransferPlan insertion = MachineTransferPlan.create(tile)
              .addInsert(input, scaled.inputs().get(0));
        assertTrue(insertion.execute());
        assertEquals(14, inputSlot.getCount());
        assertTrue(QIOProcessingExecutionService.machineMatchesAfterInput(
              scaled, ports, baselines));

        inputSlot.setEmpty();
        outputSlot.setStack(new ItemStack(Items.IRON_INGOT, 6));
        assertFalse(QIOProcessingExecutionService.machineOutputsReady(scaled, ports));
        outputSlot.setStack(new ItemStack(Items.IRON_INGOT, 7));
        assertTrue(QIOProcessingExecutionService.machineOutputsReady(scaled, ports));
        assertTrue(QIOProcessingExecutionService.machineMatchesCompletedOperation(
              scaled, ports, baselines));
        assertEquals(7, QIOProcessingExecutionService.machineOutputAmounts(scaled, ports)
              .values().stream().mapToLong(Long::longValue).sum());

        MachineTransferPlan extraction = QIOProcessingExecutionService.outputExtraction(
              tile, scaled, ports);
        assertTrue(extraction.execute());
        assertEquals(7, extraction.getExtracted().get(0).amount());
        assertTrue(outputSlot.isEmpty());
        assertTrue(QIOProcessingExecutionService.machineMatchesLeaseBaseline(
              ports, baselines));
    }

    @Test
    void routeScalingRejectsInvalidOrOverflowingOperationCounts() {
        MachineRecipeRoute route = MachineRecipeRoute.builder("overflow")
              .recipeKey("test:overflow").logicalRecipeKey("test:overflow")
              .input(MachineResourceStack.item("input", new ItemStack(Items.COAL),
                    Long.MAX_VALUE))
              .output(MachineResourceStack.item("output",
                    new ItemStack(Items.IRON_INGOT)))
              .build();

        assertThrows(IllegalArgumentException.class,
              () -> QIOProcessingExecutionService.scaleRoute(route, 0));
        assertThrows(IllegalArgumentException.class,
              () -> QIOProcessingExecutionService.scaleRoute(route, 2));
    }

    @Test
    void retainedConfigurationIsNotScaledConsumedOrCollected() {
        TileEntityFurnace tile = new TileEntityFurnace();
        BasicInventorySlot templateSlot = BasicInventorySlot.at(null, 0, 0);
        BasicInventorySlot inputSlot = BasicInventorySlot.at(null, 0, 0);
        BasicInventorySlot outputSlot = BasicInventorySlot.at(
              BasicInventorySlot.alwaysTrue, null, 0, 0, 10);
        MachinePort template = MachinePort.configurationItem("template", templateSlot,
              "replicator", 0);
        MachinePort input = MachinePort.item("input", MachinePort.Role.INPUT, inputSlot,
              "replicator", 0);
        MachinePort output = MachinePort.item("output", MachinePort.Role.OUTPUT, outputSlot,
              "replicator", 0);
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        ports.put(template.portId(), template);
        ports.put(input.portId(), input);
        ports.put(output.portId(), output);
        MachineRecipeRoute base = MachineRecipeRoute.builder("retained")
              .configurationItem("template", new ItemStack(Items.DIAMOND, 32))
              .inputItem("input", new ItemStack(Items.COAL))
              .outputItem("output", new ItemStack(Items.IRON_INGOT)).build();
        List<MachinePortBaseline> baselines = Arrays.asList(
              MachinePortBaseline.capture(template), MachinePortBaseline.capture(input),
              MachinePortBaseline.capture(output));

        assertTrue(QIOProcessingExecutionService.machineReadyForInput(tile, base, ports));
        assertEquals(10, QIOProcessingExecutionService.maxMachineOperations(
              tile, base, ports, 100));
        MachineRecipeRoute scaled = QIOProcessingExecutionService.scaleRoute(base, 10);
        assertEquals(1, scaled.configurationInputs().get(0).amount());
        assertEquals(10, scaled.inputs().get(0).amount());
        assertTrue(template.insert(scaled.configurationInputs().get(0)));
        assertTrue(input.insert(scaled.inputs().get(0)));
        assertTrue(QIOProcessingExecutionService.machineMatchesAfterInput(
              scaled, ports, baselines));

        inputSlot.setEmpty();
        outputSlot.setStack(new ItemStack(Items.IRON_INGOT, 10));
        assertTrue(QIOProcessingExecutionService.machineMatchesCompletedOperation(
              scaled, ports, baselines));
        MachineTransferPlan extraction = QIOProcessingExecutionService.outputExtraction(
              tile, scaled, ports, baselines);
        assertTrue(extraction.execute());
        assertEquals(1, templateSlot.getCount());
        assertEquals(Items.DIAMOND, templateSlot.getStack().getItem());
        assertTrue(QIOProcessingExecutionService.machineMatchesLeaseBaseline(
              scaled, ports, baselines));
    }

    @Test
    void reusedConfigurationMustKeepItsExactLeaseAmount() {
        BasicInventorySlot templateSlot = BasicInventorySlot.at(null, 0, 0);
        templateSlot.setStack(new ItemStack(Items.DIAMOND, 23));
        MachinePort template = MachinePort.configurationItem("template", templateSlot,
              "replicator", 0);
        MachinePort input = MachinePort.item("input", MachinePort.Role.INPUT,
              BasicInventorySlot.at(null, 0, 0), "replicator", 0);
        MachinePort output = MachinePort.item("output", MachinePort.Role.OUTPUT,
              BasicInventorySlot.at(BasicInventorySlot.alwaysTrue, null, 0, 0),
              "replicator", 0);
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        ports.put(template.portId(), template);
        ports.put(input.portId(), input);
        ports.put(output.portId(), output);
        MachineRecipeRoute route = MachineRecipeRoute.builder("retained-amount")
              .configurationItem("template", new ItemStack(Items.DIAMOND))
              .inputItem("input", new ItemStack(Items.COAL))
              .outputItem("output", new ItemStack(Items.IRON_INGOT))
              .build();
        List<MachinePortBaseline> baselines = Arrays.asList(
              MachinePortBaseline.capture(template));

        assertTrue(QIOProcessingExecutionService.machineMatchesAfterInput(
              route, ports, baselines));
        assertTrue(QIOProcessingExecutionService.machineMatchesLeaseBaseline(
              route, ports, baselines));

        templateSlot.setStack(new ItemStack(Items.DIAMOND, 22));

        assertFalse(QIOProcessingExecutionService.machineMatchesAfterInput(
              route, ports, baselines));
        assertFalse(QIOProcessingExecutionService.machineMatchesLeaseBaseline(
              route, ports, baselines));
    }

    @Test
    void factorySortedBatchCollectsOutputsAcrossEveryLane() {
        TileEntityFurnace tile = new TileEntityFurnace();
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        List<BasicInventorySlot> outputSlots = new ArrayList<>();
        List<MachinePortBaseline> baselines = new ArrayList<>();
        BasicInventorySlot inputSlot = BasicInventorySlot.at(null, 0, 0);
        MachinePort input = MachinePort.item("item_input_0", MachinePort.Role.INPUT,
              inputSlot, "item_input_0", 0);
        ports.put(input.portId(), input);
        baselines.add(MachinePortBaseline.capture(input));
        for (int lane = 0; lane < 9; lane++) {
            BasicInventorySlot outputSlot = BasicInventorySlot.at(
                  BasicInventorySlot.alwaysTrue, null, 0, 0);
            String portId = "item_output_" + lane;
            MachinePort output = MachinePort.item(portId, MachinePort.Role.OUTPUT,
                  outputSlot, portId, lane);
            ports.put(portId, output);
            outputSlots.add(outputSlot);
            baselines.add(MachinePortBaseline.capture(output));
        }
        MachineRecipeRoute route = QIOProcessingExecutionService.scaleRoute(
              MachineRecipeRoute.builder("factory-batch")
                    .recipeKey("test:factory_batch")
                    .logicalRecipeKey("test:factory_batch")
                    .input(MachineResourceStack.item("item_input_0",
                          new ItemStack(Items.REDSTONE)))
                    .output(MachineResourceStack.item("item_output_0",
                          new ItemStack(Items.IRON_INGOT)))
                    .build(), 64);

        outputSlots.get(0).setStack(new ItemStack(Items.IRON_INGOT, 8));
        for (int lane = 1; lane < outputSlots.size(); lane++) {
            outputSlots.get(lane).setStack(new ItemStack(Items.IRON_INGOT, 7));
        }

        assertTrue(QIOProcessingExecutionService.machineOutputsReady(route, ports, baselines));
        assertEquals(64, QIOProcessingExecutionService.machineOutputAmounts(
              route, ports, baselines).values().stream().mapToLong(Long::longValue).sum());
        assertTrue(QIOProcessingExecutionService.machineMatchesCompletedOperation(
              route, ports, baselines));

        MachineTransferPlan extraction = QIOProcessingExecutionService.outputExtraction(
              tile, route, ports, baselines);
        assertTrue(extraction.execute());
        assertEquals(64, extraction.getExtracted().stream()
              .mapToLong(MachineResourceStack::amount).sum());
        assertTrue(outputSlots.stream().allMatch(BasicInventorySlot::isEmpty));
        assertTrue(QIOProcessingExecutionService.machineMatchesLeaseBaseline(ports, baselines));
    }

    @Test
    void groupedFactoryPortsUseAllNineLanesInOneDurableBatch() {
        TileEntityFurnace tile = new TileEntityFurnace();
        List<BasicInventorySlot> inputSlots = new ArrayList<>();
        List<BasicInventorySlot> outputSlots = new ArrayList<>();
        for (int lane = 0; lane < 9; lane++) {
            inputSlots.add(BasicInventorySlot.at(null, 0, 0));
            outputSlots.add(BasicInventorySlot.at(BasicInventorySlot.alwaysTrue,
                  null, 0, 0));
        }
        MachinePort input = MachinePort.itemGroup("item_input", MachinePort.Role.INPUT,
              inputSlots, "item_input", 0);
        MachinePort output = MachinePort.itemGroup("item_output", MachinePort.Role.OUTPUT,
              outputSlots, "item_output", 0);
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        ports.put(input.portId(), input);
        ports.put(output.portId(), output);
        List<MachinePortBaseline> baselines = Arrays.asList(
              MachinePortBaseline.capture(input), MachinePortBaseline.capture(output));
        MachineRecipeRoute route = MachineRecipeRoute.builder("factory-group")
              .recipeKey("test:factory_group").logicalRecipeKey("test:factory_group")
              .input(MachineResourceStack.item("item_input", new ItemStack(Items.QUARTZ)))
              .output(MachineResourceStack.item("item_output",
                    new ItemStack(Items.REDSTONE, 2)))
              .build();

        assertEquals(288, QIOProcessingExecutionService.maxMachineOperations(
              tile, route, ports, 500));
        MachineRecipeRoute scaled = QIOProcessingExecutionService.scaleRoute(route, 288);
        assertTrue(MachineTransferPlan.create(tile).addInsert(input,
              scaled.inputs().get(0)).execute());
        assertTrue(inputSlots.stream().allMatch(slot -> slot.getCount() == 32));
        assertTrue(QIOProcessingExecutionService.machineMatchesAfterInput(
              scaled, ports, baselines));

        inputSlots.forEach(BasicInventorySlot::setEmpty);
        outputSlots.forEach(slot -> slot.setStack(new ItemStack(Items.REDSTONE, 64)));
        assertTrue(QIOProcessingExecutionService.machineOutputsReady(scaled, ports,
              baselines));
        assertEquals(576, QIOProcessingExecutionService.machineOutputAmounts(
              scaled, ports, baselines).values().stream().mapToLong(Long::longValue).sum());
        assertTrue(QIOProcessingExecutionService.machineMatchesCompletedOperation(
              scaled, ports, baselines));
        MachineTransferPlan extraction = QIOProcessingExecutionService.outputExtraction(
              tile, scaled, ports, baselines);
        assertTrue(extraction.execute());
        assertEquals(576, extraction.getExtracted().stream()
              .mapToLong(MachineResourceStack::amount).sum());
        assertTrue(outputSlots.stream().allMatch(BasicInventorySlot::isEmpty));
        assertTrue(QIOProcessingExecutionService.machineMatchesLeaseBaseline(ports,
              baselines));
    }

    @Test
    void groupedFactoryOutputCanDrainOneResourceWithoutDisturbingOtherRecipes() {
        List<BasicInventorySlot> outputSlots = new ArrayList<>();
        for (int lane = 0; lane < 3; lane++) {
            outputSlots.add(BasicInventorySlot.at(BasicInventorySlot.alwaysTrue,
                  null, 0, 0));
        }
        outputSlots.get(0).setStack(new ItemStack(Items.IRON_INGOT, 10));
        outputSlots.get(1).setStack(new ItemStack(Items.REDSTONE, 5));
        outputSlots.get(2).setStack(new ItemStack(Items.IRON_INGOT, 6));
        MachinePort output = MachinePort.itemGroup("item_output", MachinePort.Role.OUTPUT,
              outputSlots, "item_output", 0);

        MachineResourceStack extracted = output.extract(MachineResourceStack.item(
              "item_output", new ItemStack(Items.IRON_INGOT, 16)));

        assertEquals(16, extracted.amount());
        assertTrue(outputSlots.get(0).isEmpty());
        assertEquals(5, outputSlots.get(1).getCount());
        assertTrue(outputSlots.get(2).isEmpty());
    }

    @Test
    void distributedOutputNeverCollectsConfigurationPorts() {
        TileEntityFurnace tile = new TileEntityFurnace();
        BasicInventorySlot inputSlot = BasicInventorySlot.at(null, 0, 0);
        BasicInventorySlot outputSlot = BasicInventorySlot.at(
              BasicInventorySlot.alwaysTrue, null, 0, 0);
        BasicInventorySlot templateSlot = BasicInventorySlot.at(null, 0, 0);
        MachinePort input = MachinePort.item("item_input_0", MachinePort.Role.INPUT,
              inputSlot, "lane", 0);
        MachinePort output = MachinePort.item("item_output_0", MachinePort.Role.OUTPUT,
              outputSlot, "lane", 0);
        MachinePort configuration = MachinePort.configurationItem("item_output_1",
              templateSlot, "lane", 1);
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        ports.put(input.portId(), input);
        ports.put(output.portId(), output);
        ports.put(configuration.portId(), configuration);
        MachineRecipeRoute route = MachineRecipeRoute.builder("factory-config")
              .configurationItem("item_output_1", new ItemStack(Items.IRON_INGOT))
              .inputItem("item_input_0", new ItemStack(Items.COAL))
              .outputItem("item_output_0", new ItemStack(Items.IRON_INGOT, 2)).build();
        List<MachinePortBaseline> baselines = Arrays.asList(
              MachinePortBaseline.capture(input), MachinePortBaseline.capture(output),
              MachinePortBaseline.capture(configuration));
        assertTrue(configuration.insert(route.configurationInputs().get(0)));
        outputSlot.setStack(new ItemStack(Items.IRON_INGOT, 2));

        assertEquals(2, QIOProcessingExecutionService.machineOutputAmounts(
              route, ports, baselines).values().stream().mapToLong(Long::longValue).sum());
        MachineTransferPlan extraction = QIOProcessingExecutionService.outputExtraction(
              tile, route, ports, baselines);
        assertTrue(extraction.execute());
        assertEquals(1, templateSlot.getCount());
        assertTrue(outputSlot.isEmpty());
    }
}
