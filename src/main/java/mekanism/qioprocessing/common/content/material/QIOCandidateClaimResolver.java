package mekanism.qioprocessing.common.content.material;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.content.plan.QIOCandidateRequirement;
import mekanism.qioprocessing.common.content.plan.QIOPlanMaterialRequirements;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Deterministically binds logical candidate units to exact QIO resources. */
final class QIOCandidateClaimResolver {

    private QIOCandidateClaimResolver() {
    }

    @Nonnull
    static Map<PortableResourceDescriptor, Long> resolve(
          @Nonnull QIOPlanMaterialRequirements requirements,
          @Nonnull Map<PortableResourceDescriptor, Long> currentlyClaimed,
          @Nonnull Map<PortableResourceDescriptor, Long> currentlyBacked,
          @Nonnull Map<PortableResourceDescriptor, Long> available) {
        Objects.requireNonNull(requirements, "requirements");
        Map<PortableResourceDescriptor, Long> ownedPool = new LinkedHashMap<>(
              currentlyClaimed);
        Map<PortableResourceDescriptor, Long> backedPool = new LinkedHashMap<>(
              currentlyBacked);
        Map<PortableResourceDescriptor, Long> availablePool = new LinkedHashMap<>(available);
        Map<PortableResourceDescriptor, Long> target = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, Long> exact :
              requirements.getExactAmounts().entrySet()) {
            long granted = takeExactAmount(exact.getKey(), exact.getValue(), ownedPool,
                  backedPool, availablePool);
            if (granted > 0) target.put(exact.getKey(), granted);
        }
        for (QIOCandidateRequirement requirement :
              requirements.getCandidateRequirements()) {
            long remainingUnits = requirement.getRequiredUnits();
            for (QIOCandidateOption option : requirement.getOptions()) {
                if (remainingUnits == 0) break;
                long amountPerUnit = option.getAmountPerUnit();
                long ownedUnits = Math.min(remainingUnits,
                      backedPool.getOrDefault(option.getResource(), 0L) / amountPerUnit);
                if (ownedUnits > 0) {
                    long amount = Math.multiplyExact(ownedUnits, amountPerUnit);
                    consume(backedPool, option.getResource(), amount);
                    target.merge(option.getResource(), amount, Math::addExact);
                    remainingUnits -= ownedUnits;
                }
                long availableUnits = Math.min(remainingUnits,
                      availablePool.getOrDefault(option.getResource(), 0L) / amountPerUnit);
                if (availableUnits > 0) {
                    long amount = Math.multiplyExact(availableUnits, amountPerUnit);
                    consume(availablePool, option.getResource(), amount);
                    target.merge(option.getResource(), amount, Math::addExact);
                    remainingUnits -= availableUnits;
                }
            }
        }
        return target;
    }

    private static long takeExactAmount(PortableResourceDescriptor resource, long requested,
          Map<PortableResourceDescriptor, Long> claimed,
          Map<PortableResourceDescriptor, Long> backed,
          Map<PortableResourceDescriptor, Long> available) {
        long fromOwned = Math.min(requested, claimed.getOrDefault(resource, 0L));
        consume(claimed, resource, fromOwned);
        consume(backed, resource, Math.min(fromOwned, backed.getOrDefault(resource, 0L)));
        long remaining = requested - fromOwned;
        long fromAvailable = Math.min(remaining, available.getOrDefault(resource, 0L));
        consume(available, resource, fromAvailable);
        return Math.addExact(fromOwned, fromAvailable);
    }

    private static void consume(Map<PortableResourceDescriptor, Long> pool,
          PortableResourceDescriptor resource, long amount) {
        if (amount <= 0) return;
        long stored = pool.getOrDefault(resource, 0L);
        if (stored == amount) pool.remove(resource);
        else pool.put(resource, stored - amount);
    }
}
