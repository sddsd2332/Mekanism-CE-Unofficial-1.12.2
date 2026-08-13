package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class QIOPlanningRouteSignatureTest {

    @Test
    void priorityCopiesReuseImmutableStructureAndSignature() {
        PortableResourceDescriptor input = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:input", 0, null);
        PortableResourceDescriptor output = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:output", 0, null);
        QIOPlanningRoute route = QIOPlanningRoute.builder(ProviderKind.MEKANISM,
                    new ResourceLocation("test", "provider"), "route", "recipe")
              .input(input, 2).output(output, 1).build();

        QIOPlanningRoute prioritized = route.withPriority(42).withVariantPriority(7);

        assertEquals(route.getSignature(), prioritized.getSignature());
        assertEquals(route.getStableId(), prioritized.getStableId());
        assertSame(route.getExactInputs(), prioritized.getExactInputs());
        assertSame(route.getGuaranteedOutputs(), prioritized.getGuaranteedOutputs());
        assertSame(route.getCandidateInputs(), prioritized.getCandidateInputs());
        assertEquals(42, prioritized.getRoutePriority());
        assertEquals(7, prioritized.getVariantPriority());
    }
}
