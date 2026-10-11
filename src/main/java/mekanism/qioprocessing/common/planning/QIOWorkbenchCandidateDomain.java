package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;

import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Detached choices of one encoded recipe, bound to one configuration's preferences.
 */
interface QIOWorkbenchCandidateDomain {
    Set<PortableResourceDescriptor> inputs();

    QIOPlanningRoute select(Map<PortableResourceDescriptor, Long> available, long operations,
                            BooleanSupplier cancelled);

    boolean hasAlternatives();

    default QIOPlanningRoute select(Map<PortableResourceDescriptor, Long> available, long operations,
                                    BooleanSupplier cancelled, Set<String> excluded) {
        return select(available, operations, cancelled, excluded, null);
    }

    QIOPlanningRoute select(Map<PortableResourceDescriptor, Long> available, long operations,
                            BooleanSupplier cancelled, Set<String> excluded, @javax.annotation.Nullable PortableResourceDescriptor requiredOutput);

    default java.util.Collection<QIOWorkbenchVariant> validationBatch(QIOPlanningRoute selected,
                                                                      Map<PortableResourceDescriptor, Long> available, long operations, BooleanSupplier cancelled,
                                                                      Set<String> excluded, @javax.annotation.Nullable PortableResourceDescriptor requiredOutput) {
        return java.util.Collections.singletonList(selected.verification());
    }
}
