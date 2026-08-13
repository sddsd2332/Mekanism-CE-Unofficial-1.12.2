package mekanism.qioprocessing.common.content.material;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import mekanism.qioprocessing.common.content.job.QIOOperationAssignment;
import mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCandidateMaterialRequirementTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void laterAlternativeCanReplaceTheOriginallyPlannedExternalResource() throws Exception {
        PortableResourceDescriptor iron = PortableResourceDescriptor.item(
              new ItemStack(Items.IRON_INGOT));
        PortableResourceDescriptor gold = PortableResourceDescriptor.item(
              new ItemStack(Items.GOLD_INGOT));
        PortableResourceDescriptor output = PortableResourceDescriptor.item(
              new ItemStack(Items.DIAMOND));
        QIOCandidateOption preferred = new QIOCandidateOption(hash('a'), iron, 1, false);
        QIOCandidateOption alternative = new QIOCandidateOption(hash('b'), gold, 1, false);
        QIOCandidateInputGroup group = new QIOCandidateInputGroup(
              Collections.singletonList(0), Arrays.asList(preferred, alternative),
              Collections.singletonMap(iron, 1L));
        QIOPlanStep step = new QIOPlanStep(0, ProviderKind.WORKBENCH,
              "mekanism:qio_workbench", "test:lead_recipe", "test:lead_recipe",
              "i1.0.0.0.0.0.0.0.0.0", hash('c'), 1,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(output, 1L),
              Collections.emptyMap(), Collections.emptyList(),
              Collections.singletonList(group));
        QIOCraftPlan plan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(1, 2, 3, 4, 5, 6, 7, 8), output, 1,
              Collections.singletonMap(iron, 1L), Collections.singletonList(step));

        assertTrue(plan.getMaterialRequirements().getExactAmounts().isEmpty());
        assertEquals(1, plan.getMaterialRequirements().getCandidateRequirements().size());
        assertEquals(2, plan.getMaterialRequirements().getCandidateRequirements().get(0)
              .getOptions().size());

        Map<PortableResourceDescriptor, Long> available = new LinkedHashMap<>();
        available.put(gold, 1L);
        Map<PortableResourceDescriptor, Long> binding = QIOCandidateClaimResolver.resolve(
              plan.getMaterialRequirements(), Collections.emptyMap(), Collections.emptyMap(),
              available);
        assertEquals(Collections.singletonMap(gold, 1L), binding);
        assertEquals(Collections.singletonMap(gold, 1L), QIOCandidateClaimResolver.resolve(
              plan.getMaterialRequirements(), Collections.singletonMap(iron, 1L),
              Collections.emptyMap(), available));

        QIOMaterialCommitment commitment = new QIOMaterialCommitment(UUID.randomUUID(), 1,
              UUID.randomUUID(), 0, 0, plan.getMaterialRequirements());
        assertTrue(commitment.getMissingResourceKeys().contains(iron));
        assertTrue(commitment.getMissingResourceKeys().contains(gold));
        commitment.applyActiveClaim(binding, 1);
        assertTrue(commitment.isFullyCommitted());

        QIOJobBuffer buffer = new QIOJobBuffer(UUID.randomUUID());
        buffer.add(QIOJobBuffer.Compartment.RESERVED, gold, 1);
        assertTrue(buffer.canSupply(step.getFixedInputs(), step.getCandidateInputs()));

        QIOCraftPlan restored = QIOCraftPlan.read(plan.write());
        assertEquals(plan.getStructuralSignature(), restored.getStructuralSignature());
        assertEquals(2, restored.getSteps().get(0).getCandidateInputs().get(0)
              .getOptions().size());

        QIOOperationAssignment assignment = new QIOOperationAssignment(UUID.randomUUID(),
              ProviderKind.WORKBENCH, UUID.randomUUID(), 0, 1, 20,
              "mekanism:qio_workbench/test:lead_recipe/test:lead_recipe/i1.1");
        assertEquals(assignment.getExecutionRouteKey(), QIOOperationAssignment.read(
              assignment.write()).getExecutionRouteKey());
    }

    private static String hash(char character) {
        char[] value = new char[64];
        java.util.Arrays.fill(value, character);
        return new String(value);
    }
}
