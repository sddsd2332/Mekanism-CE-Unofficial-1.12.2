package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAcyclicPlannerTest {

    private static final ResourceLocation PROVIDER = new ResourceLocation("test", "machine");
    private static final QIOPlanSourceRevisions REVISIONS =
          new QIOPlanSourceRevisions(1, 2, 3, 4, 5, 6, 7, 8);

    private static PortableResourceDescriptor log;
    private static PortableResourceDescriptor planks;
    private static PortableResourceDescriptor sticks;
    private static PortableResourceDescriptor stone;
    private static PortableResourceDescriptor dirt;
    private static PortableResourceDescriptor iron;
    private static PortableResourceDescriptor gold;
    private static PortableResourceDescriptor diamond;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        log = item(new ItemStack(Blocks.LOG));
        planks = item(new ItemStack(Blocks.PLANKS));
        sticks = item(new ItemStack(Items.STICK));
        stone = item(new ItemStack(Blocks.STONE));
        dirt = item(new ItemStack(Blocks.DIRT));
        iron = item(new ItemStack(Items.IRON_INGOT));
        gold = item(new ItemStack(Items.GOLD_INGOT));
        diamond = item(new ItemStack(Items.DIAMOND));
    }

    @Test
    void recursivePlanPersistsExactTopologicalDag() throws Exception {
        QIOPlanningRoute plankRoute = route("planks", "planks_recipe", 0)
              .input(log, 1).output(planks, 4).build();
        QIOPlanningRoute stickRoute = route("sticks", "sticks_recipe", 0)
              .input(planks, 2).output(sticks, 4).build();

        QIOPlanningResult result = plan(sticks, 8, Collections.emptyMap(),
              Arrays.asList(stickRoute, plankRoute), 32, 32, 1_000, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(3, result.getPlannedOperations());
        QIOCraftPlan plan = result.getPlan();
        assertNotNull(plan);
        assertEquals(Collections.singletonMap(log, 1L), plan.getExternalRequirements());
        assertEquals(2, plan.getSteps().size());
        QIOPlanStep planksStep = step(plan, "planks");
        QIOPlanStep sticksStep = step(plan, "sticks");
        assertEquals(1, planksStep.getOperations());
        assertEquals(2, sticksStep.getOperations());
        assertEquals(Collections.singletonList(planksStep.getNodeId()),
              sticksStep.getDependencies());
        assertTrue(planksStep.getDependencies().isEmpty());

        QIOCraftPlan restored = QIOCraftPlan.read(plan.write());
        assertEquals(plan.getStructuralSignature(), restored.getStructuralSignature());
        assertEquals(2, restored.getSteps().size());
        assertEquals("sticks_recipe", step(restored, "sticks").getRecipeKey());

        NBTTagCompound developmentPlan = plan.write();
        developmentPlan.setInteger("planSchemaVersion", 1);
        assertThrows(QIOProcessingDataException.class,
              () -> QIOCraftPlan.read(developmentPlan));

        NBTTagCompound developmentStep = plan.write();
        developmentStep.getTagList("steps", NBT.TAG_COMPOUND).getCompoundTagAt(0)
              .setInteger("planStepSchemaVersion", 1);
        assertThrows(QIOProcessingDataException.class,
              () -> QIOCraftPlan.read(developmentStep));
    }

    @Test
    void availableIntermediateStockIsClaimedBeforeRecursiveProduction() {
        QIOPlanningRoute stickRoute = route("sticks", "sticks_recipe", 0)
              .input(planks, 2).output(sticks, 4).build();

        QIOPlanningResult result = plan(sticks, 4, Collections.singletonMap(planks, 2L),
              Collections.singletonList(stickRoute), 32, 32, 100, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(Collections.singletonMap(planks, 2L),
              result.getPlan().getExternalRequirements());
        assertEquals(1, result.getPlan().getSteps().size());
    }

    @Test
    void strictPriorityKeepsPreferredRouteEvenWhenItsInputsAreMissing() {
        QIOPlanningRoute preferred = route("diamond_expensive", "preferred", 10)
              .input(dirt, 5).output(diamond, 1).build();
        QIOPlanningRoute fallback = route("diamond_cheap", "fallback", 0)
              .input(stone, 1).output(diamond, 1).build();

        QIOPlanningResult result = plan(diamond, 1, Collections.emptyMap(),
              Arrays.asList(fallback, preferred), 32, 32, 100, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals("diamond_expensive", result.getPlan().getSteps().get(0).getRouteId());
        assertEquals(Collections.singletonMap(dirt, 5L),
              result.getPlan().getExternalRequirements());
    }

    @Test
    void guaranteedByproductsAreSharedAcrossSiblingInputs() {
        QIOPlanningRoute split = route("split_metals", "split", 0)
              .input(stone, 1).output(iron, 1).output(gold, 1).build();
        QIOPlanningRoute assemble = route("assemble", "assemble", 0)
              .input(iron, 1).input(gold, 1).output(diamond, 1).build();

        QIOPlanningResult result = plan(diamond, 1, Collections.emptyMap(),
              Arrays.asList(assemble, split), 32, 32, 100, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(Collections.singletonMap(stone, 1L),
              result.getPlan().getExternalRequirements());
        assertEquals(2, result.getPlan().getSteps().size());
        QIOPlanStep splitStep = step(result.getPlan(), "split_metals");
        assertEquals(Collections.singletonList(splitStep.getNodeId()),
              step(result.getPlan(), "assemble").getDependencies());
    }

    @Test
    void sharedDependencyIsAggregatedIntoOneTopologicalStep() {
        QIOPlanningRoute plankRoute = route("shared_planks", "shared_planks", 0)
              .input(log, 1).output(planks, 1).build();
        QIOPlanningRoute ironRoute = route("shared_iron", "shared_iron", 0)
              .input(planks, 1).output(iron, 1).build();
        QIOPlanningRoute targetRoute = route("shared_target", "shared_target", 0)
              .input(iron, 1).input(planks, 1).output(diamond, 1).build();

        QIOPlanningResult result = plan(diamond, 1, Collections.emptyMap(),
              Arrays.asList(targetRoute, ironRoute, plankRoute), 32, 32, 100,
              () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(4, result.getPlannedOperations());
        assertEquals(Collections.singletonMap(log, 2L),
              result.getPlan().getExternalRequirements());
        assertEquals(3, result.getPlan().getSteps().size());
        assertEquals(2, step(result.getPlan(), "shared_planks").getOperations());
        assertTrue(step(result.getPlan(), "shared_target").getDependencies().contains(
              step(result.getPlan(), "shared_planks").getNodeId()));
        assertTrue(step(result.getPlan(), "shared_iron").getDependencies().contains(
              step(result.getPlan(), "shared_planks").getNodeId()));
    }

    @Test
    void topologyCacheIgnoresDynamicRevisionsButRecalculatesLiveStock() {
        QIOTopologicalPlanner.INSTANCE.clearCache();
        QIOPlanningRoute plankRoute = route("cached_planks", "cached_planks", 0)
              .input(log, 1).output(planks, 1).build();
        QIOPlanningRoute stickRoute = route("cached_sticks", "cached_sticks", 0)
              .input(planks, 2).output(sticks, 1).build();
        List<QIOPlanningRoute> routes = Arrays.asList(stickRoute, plankRoute);

        QIOPlanningSnapshot stocked = new QIOPlanningSnapshot(REVISIONS,
              Collections.singletonMap(planks, 1L), routes, 32, 32, 100);
        QIOPlanningResult first = QIOAcyclicPlanner.INSTANCE.plan(
              new QIOPlanningRequest(UUID.randomUUID(), 1, stocked, sticks, 1),
              () -> false);
        assertEquals(QIOPlanningResult.Status.SUCCESS, first.getStatus());
        assertEquals(2, first.getPlannedOperations());
        Map<PortableResourceDescriptor, Long> stockedRequirements = new LinkedHashMap<>();
        stockedRequirements.put(log, 1L);
        stockedRequirements.put(planks, 1L);
        assertEquals(stockedRequirements, first.getPlan().getExternalRequirements());
        assertEquals(1, QIOTopologicalPlanner.INSTANCE.cachedTopologyCount());

        QIOPlanSourceRevisions changedDynamicRevisions = new QIOPlanSourceRevisions(
              101, 102, 103, 104, REVISIONS.getRecipeCatalogRevision(),
              REVISIONS.getProviderCatalogRevision(), REVISIONS.getPolicyRevision(), 108);
        QIOPlanningSnapshot empty = new QIOPlanningSnapshot(changedDynamicRevisions,
              Collections.emptyMap(), routes, 32, 32, 100);
        QIOPlanningResult second = QIOAcyclicPlanner.INSTANCE.plan(
              new QIOPlanningRequest(UUID.randomUUID(), 1, empty, sticks, 1),
              () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, second.getStatus());
        assertEquals(3, second.getPlannedOperations());
        assertEquals(Collections.singletonMap(log, 2L),
              second.getPlan().getExternalRequirements());
        assertEquals(1, QIOTopologicalPlanner.INSTANCE.cachedTopologyCount());
    }

    @Test
    void preferredCycleIsReportedInsteadOfSilentlyUsingLowerPriorityRoute() {
        QIOPlanningRoute cyclicIron = route("cyclic_iron", "cyclic_iron", 10)
              .input(gold, 1).output(iron, 1).build();
        QIOPlanningRoute cyclicGold = route("cyclic_gold", "cyclic_gold", 10)
              .input(iron, 1).output(gold, 1).build();
        QIOPlanningRoute lowerPriority = route("direct_iron", "direct_iron", 0)
              .input(stone, 1).output(iron, 1).build();

        QIOPlanningResult result = plan(iron, 1, Collections.emptyMap(),
              Arrays.asList(lowerPriority, cyclicGold, cyclicIron), 32, 32, 100,
              () -> false);

        assertEquals(QIOPlanningResult.Status.CYCLE_REQUIRES_SCC, result.getStatus());
        assertNull(result.getPlan());
        assertTrue(result.getDiagnostic().contains("cycle"));
    }

    @Test
    void failedRouteRetainsThePartialTreeAndExactErrorResource() {
        QIOPlanningRoute cyclicIron = route("trace_iron", "trace_iron", 10)
              .input(gold, 1).output(iron, 1).build();
        QIOPlanningRoute cyclicGold = route("trace_gold", "trace_gold", 10)
              .input(iron, 1).output(gold, 1).build();

        QIOPlanningResult result = plan(iron, 1, Collections.emptyMap(),
              Arrays.asList(cyclicIron, cyclicGold), 32, 32, 100, () -> false);

        assertEquals(QIOPlanningResult.Status.CYCLE_REQUIRES_SCC, result.getStatus());
        assertEquals(2, result.getTrace().getSteps().size());
        assertEquals(Collections.singleton(iron), result.getTrace().getErrorResources());
        assertEquals(1, result.getTrace().getErrorNodeIds().size());
        long errorNode = result.getTrace().getErrorNodeIds().iterator().next();
        assertTrue(result.getTrace().getSteps().stream().anyMatch(step ->
              step.getNodeId() == errorNode));
        assertFalse(result.getTrace().isRootError());
    }

    @Test
    void compositePlannerCondensesASeededProductionCycleIntoStableRounds() throws Exception {
        QIOPlanningRoute ironToGold = route("iron_to_gold", "iron_to_gold", 10)
              .input(iron, 1).output(gold, 2).build();
        QIOPlanningRoute goldToIron = route("gold_to_iron", "gold_to_iron", 10)
              .input(gold, 1).output(iron, 1).build();

        QIOPlanningResult result = planWith(QIOPlanner.INSTANCE, gold, 5,
              Collections.singletonMap(iron, 1L), Arrays.asList(ironToGold, goldToIron),
              64, 128, 128, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(Collections.singletonMap(iron, 1L),
              result.getPlan().getExternalRequirements());
        assertEquals(10, result.getPlannedOperations());
        assertEquals(2, result.getPlan().getSteps().size());
        assertEquals(1, result.getPlan().getCycleNodes().size());
        QIOCyclePlanNode cycle = result.getPlan().getCycleNodes().get(0);
        assertEquals(5, cycle.getTotalRounds());
        assertEquals(Collections.singletonMap(iron, 1L), cycle.getSeedRequirements());
        assertEquals(2, cycle.getRoundSchedule().size());
        assertEquals(5, step(result.getPlan(), "iron_to_gold").getOperations());
        assertEquals(5, step(result.getPlan(), "gold_to_iron").getOperations());

        QIOCraftPlan restored = QIOCraftPlan.read(result.getPlan().write());
        assertEquals(result.getPlan().getStructuralSignature(), restored.getStructuralSignature());
        assertEquals(cycle.getMatrixSignature(),
              restored.getCycleNodes().get(0).getMatrixSignature());
        assertEquals(cycle.getMemberQuotas(), restored.getCycleNodes().get(0).getMemberQuotas());
        assertEquals(cycle.getRoundSchedule(), restored.getCycleNodes().get(0).getRoundSchedule());
    }

    @Test
    void billionRoundCycleKeepsAConstantPlanShapeAndHonorsOperationBudget() {
        QIOPlanningRoute ironToGold = route("huge_iron_to_gold", "huge_iron_to_gold", 10)
              .input(iron, 1).output(gold, 2).build();
        QIOPlanningRoute goldToIron = route("huge_gold_to_iron", "huge_gold_to_iron", 10)
              .input(gold, 1).output(iron, 1).build();

        QIOPlanningResult result = planWith(QIOPlanner.INSTANCE, gold, 1_000_000_000L,
              Collections.singletonMap(iron, 1L), Arrays.asList(ironToGold, goldToIron),
              64, 64, 2_000_000_000L, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(2_000_000_000L, result.getPlannedOperations());
        assertEquals(2, result.getPlan().getSteps().size());
        assertEquals(1, result.getPlan().getCycleNodes().size());
        assertEquals(1_000_000_000L,
              result.getPlan().getCycleNodes().get(0).getTotalRounds());

        assertEquals(QIOPlanningResult.Status.TOO_COMPLEX,
              planWith(QIOPlanner.INSTANCE, gold, 1_000_000_000L,
                    Collections.singletonMap(iron, 1L), Arrays.asList(ironToGold, goldToIron),
                    64, 64, 1_999_999_999L, () -> false).getStatus());
    }

    @Test
    void compositePlannerKeepsAProductiveCycleWaitingForItsMissingSeed() {
        QIOPlanningRoute ironToGold = route("iron_to_gold", "iron_to_gold", 10)
              .input(iron, 1).output(gold, 2).build();
        QIOPlanningRoute goldToIron = route("gold_to_iron", "gold_to_iron", 10)
              .input(gold, 1).output(iron, 1).build();

        QIOPlanningResult result = planWith(QIOPlanner.INSTANCE, gold, 1,
              Collections.emptyMap(), Arrays.asList(ironToGold, goldToIron),
              64, 128, 128, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(Collections.singletonMap(gold, 1L),
              result.getPlan().getExternalRequirements());
        assertEquals(2, result.getPlannedOperations());
    }

    @Test
    void selfReferentialAndCatalyticCyclesUseTheSameConservationRules() {
        QIOPlanningRoute self = route("self", "self", 10)
              .input(iron, 1).output(iron, 2).build();
        QIOPlanningResult selfResult = planWith(QIOPlanner.INSTANCE, iron, 3,
              Collections.emptyMap(), Collections.singletonList(self),
              64, 128, 128, () -> false);
        assertEquals(QIOPlanningResult.Status.SUCCESS, selfResult.getStatus());
        assertEquals(Collections.singletonMap(iron, 1L),
              selfResult.getPlan().getExternalRequirements());
        assertEquals(3, selfResult.getPlannedOperations());

        QIOPlanningRoute catalystForward = route("catalyst_forward", "forward", 10)
              .input(iron, 1).output(gold, 1).output(diamond, 1).build();
        QIOPlanningRoute catalystReturn = route("catalyst_return", "return", 10)
              .input(gold, 1).output(iron, 1).build();
        QIOPlanningResult catalytic = planWith(QIOPlanner.INSTANCE, diamond, 2,
              Collections.emptyMap(), Arrays.asList(catalystForward, catalystReturn),
              64, 128, 128, () -> false);
        assertEquals(QIOPlanningResult.Status.SUCCESS, catalytic.getStatus());
        assertEquals(Collections.singletonMap(iron, 1L),
              catalytic.getPlan().getExternalRequirements());
        assertEquals(4, catalytic.getPlannedOperations());
    }

    @Test
    void multiResourceIntegerCycleFindsTheMinimumPositiveVector() {
        QIOPlanningRoute ironToGold = route("ratio_forward", "ratio_forward", 10)
              .input(iron, 2).output(gold, 5).build();
        QIOPlanningRoute goldToIron = route("ratio_return", "ratio_return", 10)
              .input(gold, 2).output(iron, 1).build();

        QIOPlanningResult result = planWith(QIOPlanner.INSTANCE, gold, 2,
              Collections.emptyMap(), Arrays.asList(ironToGold, goldToIron),
              128, 256, 256, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(Collections.singletonMap(iron, 2L),
              result.getPlan().getExternalRequirements());
        assertEquals(6, result.getPlannedOperations());
    }

    @Test
    void neutralAndDissipativeCyclesCannotSatisfyNetDemand() {
        QIOPlanningRoute neutralForward = route("neutral_forward", "neutral_forward", 10)
              .input(iron, 1).output(gold, 1).build();
        QIOPlanningRoute neutralReturn = route("neutral_return", "neutral_return", 10)
              .input(gold, 1).output(iron, 1).build();
        assertEquals(QIOPlanningResult.Status.UNRESOLVABLE_CYCLE,
              planWith(QIOPlanner.INSTANCE, gold, 1, Collections.emptyMap(),
                    Arrays.asList(neutralForward, neutralReturn), 64, 128, 128,
                    () -> false).getStatus());

        QIOPlanningRoute dissipativeForward = route("loss_forward", "loss_forward", 10)
              .input(iron, 2).output(gold, 1).build();
        QIOPlanningRoute dissipativeReturn = route("loss_return", "loss_return", 10)
              .input(gold, 1).output(iron, 1).build();
        assertEquals(QIOPlanningResult.Status.UNRESOLVABLE_CYCLE,
              planWith(QIOPlanner.INSTANCE, gold, 1, Collections.emptyMap(),
                    Arrays.asList(dissipativeForward, dissipativeReturn),
                    64, 128, 128, () -> false).getStatus());
    }

    @Test
    void reciprocalCompressionLoopReportsItsExactCycleWithoutSearchingTheBudget() {
        PortableResourceDescriptor steelBlock = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:steel_block", 0, null);
        PortableResourceDescriptor steelIngot = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:steel_ingot", 0, null);
        QIOPlanningRoute compress = route("steel_compress", "steel_compress", 10)
              .input(steelIngot, 9).output(steelBlock, 1).build();
        QIOPlanningRoute unpack = route("steel_unpack", "steel_unpack", 10)
              .input(steelBlock, 1).output(steelIngot, 9).build();

        QIOPlanningResult result = assertTimeout(Duration.ofSeconds(1), () ->
              planWith(QIOPlanner.INSTANCE, steelBlock, 1, Collections.emptyMap(),
                    Arrays.asList(compress, unpack), 128, 65_536, 65_536,
                    () -> false));

        assertEquals(QIOPlanningResult.Status.UNRESOLVABLE_CYCLE, result.getStatus());
        assertEquals("Circular QIO recipe route: test:steel_block -> test:steel_ingot -> " +
              "test:steel_block", result.getDiagnostic());
        assertTrue(result.getExploredNodes() < 32);
    }

    @Test
    void reciprocalCompressionUsesAvailableInputsBeforeItBecomesACycle() {
        PortableResourceDescriptor steelBlock = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:steel_block", 0, null);
        PortableResourceDescriptor steelIngot = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:steel_ingot", 0, null);
        QIOPlanningRoute compress = route("steel_compress", "steel_compress", 10)
              .input(steelIngot, 9).output(steelBlock, 1).build();
        QIOPlanningRoute unpack = route("steel_unpack", "steel_unpack", 10)
              .input(steelBlock, 1).output(steelIngot, 9).build();

        QIOPlanningResult result = planWith(QIOPlanner.INSTANCE, steelBlock, 1,
              Collections.singletonMap(steelIngot, 9L), Arrays.asList(compress, unpack),
              128, 65_536, 65_536, () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(Collections.singletonMap(steelIngot, 9L),
              result.getPlan().getExternalRequirements());
        assertEquals(1, result.getPlannedOperations());
    }

    @Test
    void unrelatedLowerCycleIsIgnoredUntilItsStockIsActuallyMissing() {
        PortableResourceDescriptor earlyBlock = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:aaa_block", 0, null);
        PortableResourceDescriptor earlyIngot = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:aaa_ingot", 0, null);
        QIOPlanningRoute compress = route("irrelevant_compress", "irrelevant_compress", 10)
              .input(earlyIngot, 9).output(earlyBlock, 1).build();
        QIOPlanningRoute unpack = route("irrelevant_unpack", "irrelevant_unpack", 10)
              .input(earlyBlock, 1).output(earlyIngot, 9).build();
        QIOPlanningRoute productiveForward = route("productive_forward", "productive_forward", 10)
              .input(iron, 1).input(earlyBlock, 1).output(gold, 2).build();
        QIOPlanningRoute productiveReturn = route("productive_return", "productive_return", 10)
              .input(gold, 1).output(iron, 1).output(earlyBlock, 1).build();

        QIOPlanningResult result = assertTimeout(Duration.ofSeconds(1), () ->
              planWith(QIOPlanner.INSTANCE, gold, 3,
                    Collections.singletonMap(earlyBlock, 1L),
                    Arrays.asList(compress, unpack, productiveForward, productiveReturn),
                    128, 65_536, 65_536, () -> false));

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(1, result.getPlan().getCycleNodes().size());
    }

    @Test
    void planningBudgetsCancellationAndOverflowHaveDistinctResults() {
        QIOPlanningRoute plankRoute = route("planks", "planks_recipe", 0)
              .input(log, 1).output(planks, 4).build();
        QIOPlanningRoute stickRoute = route("sticks", "sticks_recipe", 0)
              .input(planks, 2).output(sticks, 4).build();
        List<QIOPlanningRoute> chain = Arrays.asList(stickRoute, plankRoute);

        assertEquals(QIOPlanningResult.Status.TOO_COMPLEX,
              plan(sticks, 4, Collections.emptyMap(), chain, 32, 1, 100,
                    () -> false).getStatus());
        assertEquals(QIOPlanningResult.Status.CANCELLED,
              plan(sticks, 4, Collections.emptyMap(), chain, 32, 32, 100,
                    () -> true).getStatus());

        QIOPlanningRoute overflow = route("overflow", "overflow", 0)
              .input(stone, Long.MAX_VALUE).output(diamond, 1).build();
        assertEquals(QIOPlanningResult.Status.AMOUNT_OVERFLOW,
              plan(diamond, 2, Collections.emptyMap(), Collections.singletonList(overflow),
                    32, 32, Long.MAX_VALUE, () -> false).getStatus());
    }

    @Test
    void craftPlanRejectsCyclicPersistedDag() {
        QIOPlanStep first = new QIOPlanStep(1, ProviderKind.MEKANISM, PROVIDER.toString(),
              "first", "first", "exact", "sig-first", 1, Collections.singletonMap(stone, 1L),
              Collections.singletonMap(iron, 1L), Collections.emptyMap(),
              Collections.singletonList(2L));
        QIOPlanStep second = new QIOPlanStep(2, ProviderKind.MEKANISM, PROVIDER.toString(),
              "second", "second", "exact", "sig-second", 1, Collections.singletonMap(iron, 1L),
              Collections.singletonMap(gold, 1L), Collections.emptyMap(),
              Collections.singletonList(1L));

        assertThrows(IllegalArgumentException.class, () -> new QIOCraftPlan(UUID.randomUUID(), 1,
              REVISIONS, gold, 1, Collections.singletonMap(stone, 1L),
              Arrays.asList(first, second)));
    }

    @Test
    void exactVariantsRemainDistinctAndStorageSnapshotUsesAvailableAmounts() {
        QIOPlanningRoute first = route("variant_route", "variant_recipe", 0)
              .variantId("iron").input(iron, 1).output(diamond, 1).build();
        QIOPlanningRoute second = route("variant_route", "variant_recipe", 0)
              .variantId("gold").input(gold, 1).output(diamond, 1).build();
        QIOStorageSnapshot storage = new QIOStorageSnapshot(UUID.randomUUID(), "planning", 11,
              12, 13, 14, Arrays.asList(
                    QIOStorageEntry.item(UUID.randomUUID(), BigInteger.TEN,
                          BigInteger.valueOf(4), new ItemStack(Items.IRON_INGOT)),
                    QIOStorageEntry.item(UUID.randomUUID(), BigInteger.valueOf(Long.MAX_VALUE)
                                .add(BigInteger.TEN), BigInteger.ZERO,
                          new ItemStack(Items.GOLD_INGOT))),
              BigInteger.ZERO, BigInteger.ZERO, 0, 0);

        QIOPlanningSnapshot snapshot = QIOPlanningSnapshotFactory.create(storage, 15, 16, 17,
              18, Arrays.asList(second, first));

        assertEquals(6, snapshot.getAvailableResources().get(iron));
        assertEquals(Long.MAX_VALUE, snapshot.getAvailableResources().get(gold));
        assertEquals(2, snapshot.getRoutesProducing(diamond).size());
        assertFalse(first.getStableId().equals(second.getStableId()));
        assertEquals(11, snapshot.getSourceRevisions().getContentRevision());
        assertEquals(13, snapshot.getSourceRevisions().getClaimRevision());
        assertEquals(18, snapshot.getSourceRevisions().getTaskCommitmentRevision());
        assertEquals(Integer.MAX_VALUE, snapshot.getMaximumDepth());
        assertEquals(Integer.MAX_VALUE, snapshot.getMaximumNodes());
        assertEquals(Long.MAX_VALUE, snapshot.getMaximumOperations());
    }

    @Test
    void exactIngredientVariantUsesAvailableStockWithoutFallingBackToAnotherRoute() {
        QIOPlanningRoute ironVariant = route("variant_route", "variant_recipe", 10)
              .variantId("iron").input(iron, 1).output(diamond, 1).build();
        QIOPlanningRoute goldVariant = route("variant_route", "variant_recipe", 10)
              .variantId("gold").input(gold, 1).output(diamond, 1).build();
        QIOPlanningRoute lowerPriority = route("fallback_route", "fallback_recipe", 0)
              .input(stone, 1).output(diamond, 1).build();

        QIOPlanningResult result = plan(diamond, 1, Collections.singletonMap(gold, 1L),
              Arrays.asList(ironVariant, lowerPriority, goldVariant), 32, 32, 100,
              () -> false);

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals("variant_route", result.getPlan().getSteps().get(0).getRouteId());
        assertEquals("gold", result.getPlan().getSteps().get(0).getVariantId());
        assertEquals(Collections.singletonMap(gold, 1L),
              result.getPlan().getExternalRequirements());
    }

    @Test
    void cachedTopologySelectsExactVariantFromEachRequestsStock() {
        QIOTopologicalPlanner.INSTANCE.clearCache();
        QIOPlanningRoute ironVariant = route("cached_variant", "cached_variant", 10)
              .variantId("iron").input(iron, 1).output(diamond, 1).build();
        QIOPlanningRoute goldVariant = route("cached_variant", "cached_variant", 10)
              .variantId("gold").input(gold, 1).output(diamond, 1).build();
        List<QIOPlanningRoute> routes = Arrays.asList(ironVariant, goldVariant);

        QIOPlanningResult goldPlan = plan(diamond, 1,
              Collections.singletonMap(gold, 1L), routes, 32, 32, 100,
              () -> false);
        assertEquals("gold", goldPlan.getPlan().getSteps().get(0).getVariantId());
        assertEquals(1, QIOTopologicalPlanner.INSTANCE.cachedTopologyCount());

        QIOPlanningResult ironPlan = plan(diamond, 1,
              Collections.singletonMap(iron, 1L), routes, 32, 32, 100,
              () -> false);
        assertEquals("iron", ironPlan.getPlan().getSteps().get(0).getVariantId());
        assertEquals(1, QIOTopologicalPlanner.INSTANCE.cachedTopologyCount());
    }

    @Test
    void plannerRunsThroughBoundedExecutorAndPublishesOnDrainThread() throws Exception {
        QIOPlanningRoute route = route("async", "async", 0)
              .input(stone, 1).output(diamond, 1).build();
        QIOPlanningSnapshot snapshot = new QIOPlanningSnapshot(REVISIONS,
              Collections.emptyMap(), Collections.singletonList(route), 32, 32, 100);
        QIOPlanningRequest request = new QIOPlanningRequest(UUID.randomUUID(), 1, snapshot,
              diamond, 1);
        QIOPlanningExecutor executor = new QIOPlanningExecutor();
        AtomicReference<QIOPlanningExecutor.PlanningResult<QIOPlanningResult>> completed =
              new AtomicReference<>();
        try {
            assertTrue(executor.submit(request, QIOAcyclicPlanner.INSTANCE, completed::set)
                  .isAccepted());
            executor.runPlanningSlice(5, TimeUnit.MILLISECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (executor.getPendingCompletionCount() == 0 && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertEquals(1, executor.drainCompleted(1));
            assertNotNull(completed.get());
            assertEquals(QIOPlanningExecutor.ResultStatus.SUCCESS, completed.get().getStatus());
            assertEquals(QIOPlanningResult.Status.SUCCESS, completed.get().getValue().getStatus());
        } finally {
            executor.shutdown(5, TimeUnit.SECONDS);
        }
    }

    private static QIOPlanningResult plan(PortableResourceDescriptor root, long amount,
          Map<PortableResourceDescriptor, Long> available, List<QIOPlanningRoute> routes,
          int maximumDepth, int maximumNodes, long maximumOperations,
          QIOPlanningExecutor.CancellationToken cancellationToken) {
        QIOPlanningSnapshot snapshot = new QIOPlanningSnapshot(REVISIONS, available, routes,
              maximumDepth, maximumNodes, maximumOperations);
        return QIOAcyclicPlanner.INSTANCE.plan(new QIOPlanningRequest(UUID.randomUUID(), 1,
              snapshot, root, amount), cancellationToken);
    }

    private static QIOPlanningResult planWith(
          QIOPlanningExecutor.PlanningTask<QIOPlanningRequest, QIOPlanningResult> planner,
          PortableResourceDescriptor root, long amount,
          Map<PortableResourceDescriptor, Long> available, List<QIOPlanningRoute> routes,
          int maximumDepth, int maximumNodes, long maximumOperations,
          QIOPlanningExecutor.CancellationToken cancellationToken) {
        QIOPlanningSnapshot snapshot = new QIOPlanningSnapshot(REVISIONS, available, routes,
              maximumDepth, maximumNodes, maximumOperations);
        try {
            return planner.plan(new QIOPlanningRequest(UUID.randomUUID(), 1, snapshot, root,
                  amount), cancellationToken);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static QIOPlanningRoute.Builder route(String routeId, String recipeKey,
          long priority) {
        return QIOPlanningRoute.builder(ProviderKind.MEKANISM, PROVIDER, routeId, recipeKey)
              .priority(priority);
    }

    private static QIOPlanStep step(QIOCraftPlan plan, String routeId) {
        return plan.getSteps().stream().filter(step -> routeId.equals(step.getRouteId()))
              .findFirst().orElseThrow(AssertionError::new);
    }

    private static PortableResourceDescriptor item(ItemStack stack) {
        return PortableResourceDescriptor.item(stack);
    }
}
