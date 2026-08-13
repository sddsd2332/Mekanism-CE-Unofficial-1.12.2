package mekanism.qioprocessing.client.gui;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class QIOCraftingPlanLayoutTest {

    @Test
    void twentyThousandStepChainIsIterativeAndBounded() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            int size = 20_000;
            Set<Long> nodes = new LinkedHashSet<>();
            Map<Long, List<Long>> dependencies = new LinkedHashMap<>();
            for (long node = 0; node < size; node++) {
                nodes.add(node);
                dependencies.put(node, node == 0 ? Collections.emptyList() :
                      Collections.singletonList(node - 1));
            }
            Map<Long, Integer> depths = QIOCraftingPlanLayout.depths(nodes, dependencies);
            assertEquals(size, depths.size());
            assertEquals(size - 1, depths.get((long) size - 1));
        });
    }

    @Test
    void aeTreeDuplicatesSharedDependenciesForEveryBranch() {
        Set<Long> nodes = new LinkedHashSet<>(Arrays.asList(1L, 2L, 3L, 4L));
        Map<Long, List<Long>> dependencies = new LinkedHashMap<>();
        dependencies.put(1L, Collections.emptyList());
        dependencies.put(2L, Collections.singletonList(1L));
        dependencies.put(3L, Collections.singletonList(1L));
        dependencies.put(4L, Arrays.asList(2L, 3L));

        QIOCraftingPlanLayout.Tree tree = QIOCraftingPlanLayout.tree(nodes,
              dependencies, Collections.singletonList(4L));
        long sharedOccurrences = tree.getNodes().stream()
              .filter(node -> !node.isRoot() && node.getNodeId() == 1L).count();
        Set<Integer> sharedColumns = new LinkedHashSet<>();
        tree.getNodes().stream().filter(node -> node.getNodeId() == 1L)
              .forEach(node -> sharedColumns.add(node.getX()));

        assertEquals(2, sharedOccurrences);
        assertEquals(2, sharedColumns.size());
        assertEquals(6, tree.getNodes().size());
        assertEquals(QIOCraftingPlanLayout.ROOT_MARGIN_TOP +
              QIOCraftingPlanLayout.LINE_HEIGHT, tree.getRoot().getY());
        assertTrue(tree.getNodes().stream().allMatch(node -> node.isRoot() ||
              node.getY() > tree.getNodes().get(0).getY()));
    }

    @Test
    void twentyThousandStepTreeDoesNotUseTheJavaCallStack() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            int size = 20_000;
            Set<Long> nodes = new LinkedHashSet<>();
            Map<Long, List<Long>> dependencies = new LinkedHashMap<>();
            for (long node = 0; node < size; node++) {
                nodes.add(node);
                dependencies.put(node, node == 0 ? Collections.emptyList() :
                      Collections.singletonList(node - 1));
            }

            QIOCraftingPlanLayout.Tree tree = QIOCraftingPlanLayout.tree(nodes,
                  dependencies, Collections.singletonList((long) size - 1));
            assertEquals(size + 1, tree.getNodes().size());
            assertEquals(size, tree.getNodes().get(tree.getNodes().size() - 1).getDepth());
        });
    }

    @Test
    void exponentiallySharedDagStopsAtTheViewOccurrenceLimit() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Set<Long> nodes = new LinkedHashSet<>();
            Map<Long, List<Long>> dependencies = new LinkedHashMap<>();
            for (long node = 0; node < 64; node++) {
                nodes.add(node);
                if (node == 0) {
                    dependencies.put(node, Collections.emptyList());
                } else if (node == 1) {
                    dependencies.put(node, Collections.singletonList(0L));
                } else {
                    dependencies.put(node, Arrays.asList(node - 1, node - 2));
                }
            }

            QIOCraftingPlanLayout.Tree tree = QIOCraftingPlanLayout.tree(nodes,
                  dependencies, Collections.singletonList(63L));
            assertEquals(QIOCraftingPlanLayout.MAX_TREE_OCCURRENCES,
                  tree.getNodes().size());
            assertTrue(tree.isTruncated());
        });
    }

    @Test
    void panOffsetKeepsTheTreeIntersectingTheViewport() {
        assertEquals(196F, QIOCraftingPlanLayout.clampPanOffset(10_000, 0, 100,
              1, 200, 4));
        assertEquals(-96F, QIOCraftingPlanLayout.clampPanOffset(-10_000, 0, 100,
              1, 200, 4));
        assertEquals(4F, QIOCraftingPlanLayout.clampPanOffset(4, 0, 1_000,
              1, 200, 4));
    }

    @Test
    void materialLeavesOnlyContainInputsWithoutProducerNodes() {
        PortableResourceDescriptor intermediate = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:intermediate", 0, null);
        PortableResourceDescriptor raw = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:raw", 0, null);
        PortableResourceDescriptor external = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:external", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:output", 0, null);
        QIOCraftingMonitorPlanEntry producer = QIOCraftingMonitorPlanEntry.step(step(1,
              Collections.singletonMap(raw, 1L), Collections.singletonMap(intermediate, 1L),
              Collections.emptyList(), 2));
        Map<PortableResourceDescriptor, Long> inputs = new LinkedHashMap<>();
        inputs.put(intermediate, 1L);
        inputs.put(external, 2L);
        QIOCraftingMonitorPlanEntry consumer = QIOCraftingMonitorPlanEntry.step(step(2,
              inputs, Collections.singletonMap(output, 1L), Collections.singletonList(1L), 3));

        Map<Long, QIOCraftingMonitorPlanEntry> entries = new LinkedHashMap<>();
        entries.put(1L, producer);
        entries.put(2L, consumer);
        List<QIOCraftingPlanLayout.MaterialLeaf> leaves =
              QIOCraftingPlanLayout.externalInputLeaves(consumer,
                    Collections.singleton(1L), entries);
        assertEquals(1, leaves.size());
        assertEquals(external, leaves.get(0).getResource());
        assertEquals(6, leaves.get(0).getExactAmount());
    }

    @Test
    void candidateMaterialLeafMatchesMissingPreferredAlternative() {
        PortableResourceDescriptor plannedLead = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "mekanism:lead_ingot", 0, null);
        PortableResourceDescriptor preferredLead = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:preferred_lead_ingot", 0, null);
        PortableResourceDescriptor fallbackLead = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:fallback_lead_ingot", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:output", 0, null);
        QIOCandidateInputGroup group = new QIOCandidateInputGroup(
              Collections.singletonList(0), Arrays.asList(
                    new QIOCandidateOption(hash('a'), preferredLead, 1, false),
                    new QIOCandidateOption(hash('b'), fallbackLead, 1, false),
                    new QIOCandidateOption(hash('c'), plannedLead, 1, false)),
              Collections.singletonMap(plannedLead, 1L));
        QIOPlanStep step = new QIOPlanStep(7, ProviderKind.WORKBENCH, "test:provider",
              "test:route", "test:recipe", "test:variant", "test:signature", 1_000,
              Collections.singletonMap(plannedLead, 4L),
              Collections.singletonMap(output, 1L), Collections.emptyMap(),
              Collections.emptyList(), Collections.singletonList(group));
        QIOCraftingMonitorPlanEntry entry = QIOCraftingMonitorPlanEntry.step(step);
        List<QIOCraftingPlanLayout.MaterialLeaf> leaves =
              QIOCraftingPlanLayout.externalInputLeaves(entry, Collections.emptySet(),
                    Collections.singletonMap(7L, entry));
        QIOCraftingPlanLayout.MaterialLeaf candidate = leaves.stream()
              .filter(leaf -> !leaf.getOptions().isEmpty()).findFirst()
              .orElseThrow(() -> new AssertionError("Missing candidate material leaf"));
        List<QIOCandidateOption> options = candidate.getOptions();
        assertEquals(Arrays.asList(preferredLead, fallbackLead, plannedLead),
              options.stream().map(QIOCandidateOption::getResource)
                    .collect(java.util.stream.Collectors.toList()));
        assertEquals(2_624, QIOCraftingPlanLayout.candidateMissingUnits(options,
              Collections.singletonMap(preferredLead, 2_624L)));
        Map<PortableResourceDescriptor, Long> combinedMissing = new LinkedHashMap<>();
        combinedMissing.put(plannedLead, 4L);
        combinedMissing.put(preferredLead, 2_624L);
        assertEquals(2_628, QIOCraftingPlanLayout.candidateMissingUnits(options,
              combinedMissing));
        assertEquals(preferredLead, QIOCraftingPlanLayout.cyclingOption(options, 0)
              .getResource());
        assertEquals(preferredLead, QIOCraftingPlanLayout.cyclingOption(options,
              QIOCraftingPlanLayout.CANDIDATE_CYCLE_TICKS - 1).getResource());
        assertEquals(fallbackLead, QIOCraftingPlanLayout.cyclingOption(options,
              QIOCraftingPlanLayout.CANDIDATE_CYCLE_TICKS).getResource());
        assertEquals(plannedLead, QIOCraftingPlanLayout.cyclingOption(options,
              QIOCraftingPlanLayout.CANDIDATE_CYCLE_TICKS * 2L).getResource());
        assertEquals(preferredLead, QIOCraftingPlanLayout.cyclingOption(options,
              QIOCraftingPlanLayout.CANDIDATE_CYCLE_TICKS * 3L).getResource());
    }

    @Test
    void repeatedWorkbenchCandidateSlotsCollapseIntoOneMaterialLeaf() {
        PortableResourceDescriptor mekanismLead = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "mekanism:lead_ingot", 0, null);
        PortableResourceDescriptor thermalLead = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "thermal:lead_ingot", 0, null);
        PortableResourceDescriptor ic2Lead = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "ic2:lead_ingot", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:compressed_output", 0, null);
        List<QIOCandidateOption> options = Arrays.asList(
              new QIOCandidateOption(hash('a'), mekanismLead, 1, false),
              new QIOCandidateOption(hash('b'), thermalLead, 1, false),
              new QIOCandidateOption(hash('c'), ic2Lead, 1, false));
        List<QIOCandidateInputGroup> groups = Arrays.asList(
              candidateGroup(0, options, mekanismLead, 1),
              candidateGroup(2, options, mekanismLead, 1),
              candidateGroup(6, options, mekanismLead, 1),
              candidateGroup(8, options, mekanismLead, 1));
        QIOPlanStep step = new QIOPlanStep(10, ProviderKind.WORKBENCH, "test:provider",
              "test:route", "test:recipe", "test:variant", "test:signature", 8_801,
              Collections.singletonMap(mekanismLead, 4L),
              Collections.singletonMap(output, 1L), Collections.emptyMap(),
              Collections.emptyList(), groups);
        QIOCraftingMonitorPlanEntry entry = QIOCraftingMonitorPlanEntry.step(step);

        List<QIOCraftingPlanLayout.MaterialLeaf> leaves =
              QIOCraftingPlanLayout.externalInputLeaves(entry, Collections.emptySet(),
                    Collections.singletonMap(10L, entry));

        assertEquals(1, leaves.size());
        assertEquals(35_204, leaves.get(0).getRequiredUnits());
        assertEquals(35_204, QIOCraftingPlanLayout.displayedAmount(0,
              leaves.get(0).getRequiredUnits(), leaves.get(0).getOptions(), 0));
    }

    @Test
    void candidateSlotsWithDifferentPriorityOrderStaySeparate() {
        PortableResourceDescriptor first = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:first", 0, null);
        PortableResourceDescriptor second = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:second", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:priority_output", 0, null);
        List<QIOCandidateOption> firstOrder = Arrays.asList(
              new QIOCandidateOption(hash('a'), first, 1, false),
              new QIOCandidateOption(hash('b'), second, 1, false));
        List<QIOCandidateOption> secondOrder = Arrays.asList(
              new QIOCandidateOption(hash('b'), second, 1, false),
              new QIOCandidateOption(hash('a'), first, 1, false));
        Map<PortableResourceDescriptor, Long> inputs = new LinkedHashMap<>();
        inputs.put(first, 1L);
        inputs.put(second, 1L);
        QIOPlanStep step = new QIOPlanStep(11, ProviderKind.WORKBENCH, "test:provider",
              "test:route", "test:recipe", "test:variant", "test:signature", 5,
              inputs, Collections.singletonMap(output, 1L), Collections.emptyMap(),
              Collections.emptyList(), Arrays.asList(
                    candidateGroup(0, firstOrder, first, 1),
                    candidateGroup(1, secondOrder, second, 1)));
        QIOCraftingMonitorPlanEntry entry = QIOCraftingMonitorPlanEntry.step(step);

        List<QIOCraftingPlanLayout.MaterialLeaf> leaves =
              QIOCraftingPlanLayout.externalInputLeaves(entry, Collections.emptySet(),
                    Collections.singletonMap(11L, entry));

        assertEquals(2, leaves.size());
    }

    @Test
    void candidateSlotsWithDifferentUnitConversionStaySeparate() {
        PortableResourceDescriptor bucket = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:unit_bucket", 0, null);
        PortableResourceDescriptor fluid = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.FLUID, "test:unit_fluid", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:unit_output", 0, null);
        List<QIOCandidateOption> oneBucket = Arrays.asList(
              new QIOCandidateOption(hash('a'), bucket, 1, false),
              new QIOCandidateOption(hash('b'), fluid, 1_000, true));
        List<QIOCandidateOption> twoBuckets = Arrays.asList(
              new QIOCandidateOption(hash('c'), bucket, 2, false),
              new QIOCandidateOption(hash('d'), fluid, 2_000, true));
        Map<PortableResourceDescriptor, Long> inputs = new LinkedHashMap<>();
        inputs.put(fluid, 3_000L);
        QIOPlanStep step = new QIOPlanStep(12, ProviderKind.WORKBENCH, "test:provider",
              "test:route", "test:recipe", "test:variant", "test:signature", 3,
              inputs, Collections.singletonMap(output, 1L), Collections.emptyMap(),
              Collections.emptyList(), Arrays.asList(
                    candidateGroup(0, oneBucket, fluid, 1_000),
                    candidateGroup(1, twoBuckets, fluid, 2_000)));
        QIOCraftingMonitorPlanEntry entry = QIOCraftingMonitorPlanEntry.step(step);

        List<QIOCraftingPlanLayout.MaterialLeaf> leaves =
              QIOCraftingPlanLayout.externalInputLeaves(entry, Collections.emptySet(),
                    Collections.singletonMap(12L, entry));

        assertEquals(2, leaves.size());
    }

    @Test
    void candidateLeafUsesEachOptionAmountPerUnitWhenCycling() {
        PortableResourceDescriptor plannedFluid = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.FLUID, "test:fluid", 0, null);
        PortableResourceDescriptor bucket = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:bucket", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:output_fluid", 0, null);
        QIOCandidateInputGroup group = new QIOCandidateInputGroup(
              Collections.singletonList(0), Arrays.asList(
                    new QIOCandidateOption(hash('a'), bucket, 1, false),
                    new QIOCandidateOption(hash('b'), plannedFluid, 1_000, true)),
              Collections.singletonMap(plannedFluid, 1_000L));
        QIOPlanStep step = new QIOPlanStep(8, ProviderKind.WORKBENCH, "test:provider",
              "test:route", "test:recipe", "test:variant", "test:signature", 4,
              Collections.singletonMap(plannedFluid, 1_000L),
              Collections.singletonMap(output, 1L), Collections.emptyMap(),
              Collections.emptyList(), Collections.singletonList(group));
        QIOCraftingMonitorPlanEntry entry = QIOCraftingMonitorPlanEntry.step(step);
        List<QIOCraftingPlanLayout.MaterialLeaf> leaves =
              QIOCraftingPlanLayout.externalInputLeaves(entry, Collections.emptySet(),
                    Collections.singletonMap(8L, entry));
        assertEquals(1, leaves.size());
        QIOCraftingPlanLayout.MaterialLeaf leaf = leaves.get(0);
        assertEquals(4, leaf.getRequiredUnits());
        assertEquals(4, QIOCraftingPlanLayout.displayedAmount(0, leaf.getRequiredUnits(),
              leaf.getOptions(), 0));
        assertEquals(4_000, QIOCraftingPlanLayout.displayedAmount(0, leaf.getRequiredUnits(),
              leaf.getOptions(), QIOCraftingPlanLayout.CANDIDATE_CYCLE_TICKS));
        assertEquals(4, QIOCraftingPlanLayout.candidateMissingUnits(leaf.getOptions(),
              Collections.singletonMap(bucket, 4L)));
        assertEquals(4_000, QIOCraftingPlanLayout.displayedAmount(0,
              QIOCraftingPlanLayout.candidateMissingUnits(leaf.getOptions(),
                    Collections.singletonMap(bucket, 4L)), leaf.getOptions(),
              QIOCraftingPlanLayout.CANDIDATE_CYCLE_TICKS));
    }

    @Test
    void fixedInputSharingThePlannedResourceStaysSeparateFromCandidateUnits() {
        PortableResourceDescriptor fluid = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.FLUID, "test:shared_fluid", 0, null);
        PortableResourceDescriptor bucket = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:shared_bucket", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:shared_output", 0, null);
        QIOCandidateInputGroup group = new QIOCandidateInputGroup(
              Collections.singletonList(0), Arrays.asList(
                    new QIOCandidateOption(hash('d'), bucket, 1, false),
                    new QIOCandidateOption(hash('e'), fluid, 1_000, true)),
              Collections.singletonMap(fluid, 1_000L));
        QIOPlanStep step = new QIOPlanStep(9, ProviderKind.WORKBENCH, "test:provider",
              "test:route", "test:recipe", "test:variant", "test:signature", 2,
              Collections.singletonMap(fluid, 1_250L),
              Collections.singletonMap(output, 1L), Collections.emptyMap(),
              Collections.emptyList(), Collections.singletonList(group));
        QIOCraftingMonitorPlanEntry entry = QIOCraftingMonitorPlanEntry.step(step);

        List<QIOCraftingPlanLayout.MaterialLeaf> leaves =
              QIOCraftingPlanLayout.externalInputLeaves(entry, Collections.emptySet(),
                    Collections.singletonMap(9L, entry));
        assertEquals(2, leaves.size());
        QIOCraftingPlanLayout.MaterialLeaf fixed = leaves.get(0);
        QIOCraftingPlanLayout.MaterialLeaf candidate = leaves.get(1);
        assertTrue(fixed.getOptions().isEmpty());
        assertEquals(500, fixed.getExactAmount());
        assertEquals(2, QIOCraftingPlanLayout.displayedAmount(0,
              candidate.getRequiredUnits(), candidate.getOptions(), 0));
        assertEquals(2_000, QIOCraftingPlanLayout.displayedAmount(0,
              candidate.getRequiredUnits(), candidate.getOptions(),
              QIOCraftingPlanLayout.CANDIDATE_CYCLE_TICKS));
    }

    private static QIOPlanStep step(long nodeId,
          Map<PortableResourceDescriptor, Long> inputs,
          Map<PortableResourceDescriptor, Long> outputs, List<Long> dependencies,
          long operations) {
        return new QIOPlanStep(nodeId, ProviderKind.WORKBENCH, "test:provider",
              "test:route", "test:recipe", "test:variant", "test:signature", operations,
              inputs, outputs, Collections.emptyMap(), dependencies);
    }

    private static QIOCandidateInputGroup candidateGroup(int slot,
          List<QIOCandidateOption> options, PortableResourceDescriptor planned,
          long amount) {
        return new QIOCandidateInputGroup(Collections.singletonList(slot), options,
              Collections.singletonMap(planned, amount));
    }

    private static String hash(char character) {
        char[] value = new char[64];
        Arrays.fill(value, character);
        return new String(value);
    }
}
