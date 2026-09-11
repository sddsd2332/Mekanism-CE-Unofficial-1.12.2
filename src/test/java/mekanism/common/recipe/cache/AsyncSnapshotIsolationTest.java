package mekanism.common.recipe.cache;

import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.concurrent.AsyncPlanSafetyValidator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncSnapshotIsolationTest {

    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void workerValuesDeclareNoLiveMachineReferencesOrCallbacks() {
        List<Class<?>> values = Arrays.asList(RecipeRunSnapshot.class, RecipeExecutionPlan.class,
              ImmutableResourceSnapshot.class);
        List<Class<?>> forbidden = Arrays.asList(TileEntity.class, World.class, Capability.class,
              IInventorySlot.class, IExtendedFluidTank.class, IExtendedGasTank.class,
              IEnergyContainer.class, MachineRecipe.class, MachineInput.class, MachineOutput.class,
              Supplier.class, Consumer.class, Runnable.class);
        for (Class<?> value : values) {
            for (Field field : value.getDeclaredFields()) {
                for (Class<?> type : forbidden) {
                    assertFalse(type.isAssignableFrom(field.getType()),
                          value.getSimpleName() + '.' + field.getName() + " exposes " + type.getName());
                }
            }
        }
    }

    @Test
    void specializedRecipeMarkerUsesOnlyCapturedValuesOnWorker() {
        TestRecipeTile tile = new TestRecipeTile();
        RecipeRunSnapshot snapshot = tile.captureSnapshot();
        RecipeExecutionPlan plan = tile.calculatePlan(snapshot);

        assertTrue(plan.isValidFor(snapshot));
        assertTrue(tile.isPlanStillValid(snapshot, plan));
        tile.invalidateProcessingState();
        assertFalse(tile.isPlanStillValid(snapshot, plan));
    }

    @Test
    void schedulerAcceptsStatelessCalculatorAndRejectsTileCapture() {
        TestRecipeTile tile = new TestRecipeTile();
        assertTrue(AsyncPlanSafetyValidator.isDetached(RecipeExecutionPlanner.detachedCalculator()));
        assertFalse(AsyncPlanSafetyValidator.isDetached(snapshot -> {
            tile.invalidateProcessingState();
            return snapshot;
        }));
    }

    @Test
    void pendingWorkerEnvelopeDoesNotStorePlannerOrTile() {
        Class<?> pending = Arrays.stream(TileEntityBasicBlock.class.getDeclaredClasses())
              .filter(type -> type.getSimpleName().equals("PendingAsyncPlan"))
              .findFirst().orElseThrow(AssertionError::new);
        for (Field field : pending.getDeclaredFields()) {
            assertFalse(TileEntity.class.isAssignableFrom(field.getType()), field.getName());
            assertFalse(IAsyncMachinePlanner.class.isAssignableFrom(field.getType()), field.getName());
        }
    }

    @Test
    void realResourceSnapshotsPassDetachedValidation() {
        ItemStack item = new ItemStack(net.minecraft.init.Items.IRON_INGOT, 4);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setString("variant", "server");
        item.setTagCompound(tag);
        mekanism.api.gas.Gas gas = new mekanism.api.gas.Gas("test_gas", 0xFFFFFF);
        mekanism.api.gas.GasStack gasStack = new mekanism.api.gas.GasStack(gas, 8);
        net.minecraftforge.fluids.FluidStack fluid = new net.minecraftforge.fluids.FluidStack(FluidRegistry.WATER, 100);
        RecipeResourceFlow semanticInput = new RecipeResourceFlow("item.0", ImmutableResourceSnapshot.of(item),
              RecipeResourceFlow.Phase.COMPLETION);
        RecipeResourceFlow semanticOutput = new RecipeResourceFlow("item.0", ImmutableResourceSnapshot.of(item),
              RecipeResourceFlow.Phase.OUTPUT);
        RecipeSemanticsSnapshot semantics = new RecipeSemanticsSnapshot(
              java.util.Collections.singletonList(semanticInput), java.util.Collections.singletonList(semanticOutput),
              0, 0, true);
        RecipeLaneSnapshot lane = RecipeLaneSnapshot.builder(0).operatingTicks(2).requiredTicks(10)
              .input("item.0", ImmutableResourceSnapshot.of(item))
              .input("gas.0", ImmutableResourceSnapshot.of(gasStack))
              .input("fluid.0", ImmutableResourceSnapshot.of(fluid))
              .output("item.0", ImmutableResourceSnapshot.of(item), 64)
              .recipeSemantics(semantics).build();
        RecipeRunSnapshot snapshot = RecipeRunSnapshot.builder("mekanism:resource_snapshot")
              .recipeGeneration(1).machineStateVersion(1).energy(100, 5)
              .input("item.0", ImmutableResourceSnapshot.of(item))
              .input("gas.0", ImmutableResourceSnapshot.of(gasStack))
              .input("fluid.0", ImmutableResourceSnapshot.of(fluid))
              .output("item.0", ImmutableResourceSnapshot.of(item))
              .recipeSemantics(semantics).lane(lane)
              .build();

        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(snapshot));
    }

    @Test
    void trustedCoreSnapshotsUseTheClosedValueFastPath() {
        RecipeRunSnapshot snapshot = RecipeRunSnapshot.builder("mekanism:empty")
              .recipeGeneration(1).machineStateVersion(1).build();
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot);
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(snapshot));
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(plan));
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(RecipeSemanticsSnapshot.empty()));
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(RecipeLaneSnapshot.builder(0).build()));
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(new RecipeLanePlan(0, 0, 0, false,
              java.util.Collections.emptyMap(), java.util.Collections.emptySet())));
    }

    private static final class TestRecipeTile extends TileEntityBasicBlock implements IAsyncRecipeMachine {
        @Override
        public Object getAsyncRecipeSnapshotSource() {
            return "semantic recipe values";
        }

        @Override
        public void commitAsyncRecipeTick() {
        }
    }

    @Test
    void replacingLaneSemanticsPreservesFrozenResourcesAndProcessingSettings() {
        ImmutableResourceSnapshot resource = ImmutableResourceSnapshot.descriptor("test:resource", 8);
        java.util.Map<String, ImmutableResourceSnapshot> input = new java.util.LinkedHashMap<>();
        input.put("item.0", resource);
        RecipeLaneSnapshot.Builder builder = RecipeLaneSnapshot.builder(3).input("item.0", resource, true).inputs(input)
              .operatingTicks(4).requiredTicks(20).baselineMaxOperations(7).maxProcessingPasses(2)
              .energyPerTick(9).active(true).recipePresent(false).keepProgressWithoutRecipe(true)
              .pausedForErrors(true).pooledOutputs(true).interchangeableOutputs(true)
              .output("item.0", resource, 64, true).outputLimit("item.0", resource, 32)
              .templateInputKeys(java.util.Collections.singleton("item.0"))
              .perTickInputMultipliers(java.util.Collections.singletonMap("item.0", 3L));
        RecipeLaneSnapshot original = builder.build();
        RecipeSemanticsSnapshot semantics = new RecipeSemanticsSnapshot(java.util.Collections.emptyList(),
              java.util.Collections.emptyList(), 3, 5, true);
        RecipeLaneSnapshot derived = original.withRecipeSemantics(true, semantics);
        input.clear();
        builder.outputLimit("item.0", resource, 1);
        assertEquals(resource, derived.getInputs().get("item.0"));
        assertEquals(32, derived.getOutputCapacity("item.0", resource));
        assertEquals(64L, derived.getOutputCapacities().get("item.0").longValue());
        assertEquals(resource, derived.getOutputContents().get("item.0"));
        assertEquals(3, derived.getLaneIndex());
        assertEquals(4, derived.getOperatingTicks());
        assertEquals(20, derived.getRequiredTicks());
        assertEquals(7, derived.getBaselineMaxOperations());
        assertEquals(2, derived.getMaxProcessingPasses());
        assertEquals(9, derived.getEnergyPerTick());
        assertEquals(3, derived.getPerTickInputMultiplier("item.0"));
        assertTrue(derived.isActive() && derived.isRecipePresent() && derived.shouldKeepProgressWithoutRecipe());
        assertTrue(derived.isPausedForErrors() && derived.hasPooledOutputs() && derived.hasInterchangeableOutputs());
        assertEquals(original.getSharedInputKeys(), derived.getSharedInputKeys());
        assertEquals(original.getSharedOutputKeys(), derived.getSharedOutputKeys());
        assertEquals(original.getTemplateInputKeys(), derived.getTemplateInputKeys());
        assertEquals(semantics, derived.getRecipeSemantics());
        assertFalse(original.isRecipePresent());
        assertFalse(original.getRecipeSemantics().isSupported());
        assertThrows(UnsupportedOperationException.class, () -> derived.getInputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> derived.getOutputCapacities().clear());
        assertThrows(NullPointerException.class, () -> derived.withRecipeSemantics(true, null));
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(derived));
    }
}
