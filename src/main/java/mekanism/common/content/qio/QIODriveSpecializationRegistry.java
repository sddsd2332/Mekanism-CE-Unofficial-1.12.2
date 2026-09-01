package mekanism.common.content.qio;

import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import mekanism.common.Mekanism;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Process-wide registry of stable QIO drive specializations. */
public final class QIODriveSpecializationRegistry {

    public static final ResourceLocation MIXED_ID = new ResourceLocation(Mekanism.MODID, "mixed");
    public static final ResourceLocation ITEM_ID = new ResourceLocation(Mekanism.MODID, "item");
    public static final ResourceLocation FLUID_ID = new ResourceLocation(Mekanism.MODID, "fluid");
    public static final ResourceLocation GAS_ID = new ResourceLocation(Mekanism.MODID, "gas");

    public static final QIODriveSpecializationRegistry INSTANCE = new QIODriveSpecializationRegistry();

    private final Map<ResourceLocation, QIODriveSpecialization> specializations = new LinkedHashMap<>();
    private long revision;

    private QIODriveSpecializationRegistry() {
        registerBuiltin(QIODriveSpecialization.builder(MIXED_ID).mixed()
              .translationKey("qio.mekanism.drive_type.mixed"));
        registerBuiltin(QIODriveSpecialization.builder(ITEM_ID)
              .matcher(QIOResourceFamilyMatcher.family(QIOResourceCodecs.ITEM_FAMILY))
              .capacityFromDefinition().translationKey("qio.mekanism.drive_type.item"));
        registerBuiltin(QIODriveSpecialization.builder(FLUID_ID)
              .matcher(QIOResourceFamilyMatcher.family(QIOResourceCodecs.FLUID_FAMILY))
              .capacityFromDefinition().translationKey("qio.mekanism.drive_type.fluid"));
        registerBuiltin(QIODriveSpecialization.builder(GAS_ID)
              .matcher(QIOResourceFamilyMatcher.family(QIOResourceCodecs.GAS_FAMILY))
              .capacityFromDefinition().translationKey("qio.mekanism.drive_type.gas"));
    }

    private void registerBuiltin(QIODriveSpecialization.Builder builder) {
        register(new QIODriveSpecialization(builder));
    }

    @Nonnull
    public synchronized QIODriveSpecialization register(@Nonnull QIODriveSpecialization specialization) {
        Objects.requireNonNull(specialization, "QIO drive specialization cannot be null");
        ResourceLocation id = specialization.getRegistryName();
        QIODriveSpecialization previous = specializations.putIfAbsent(id, specialization);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate QIO drive specialization registered: " + id);
        }
        revision++;
        return specialization;
    }

    @Nullable
    public synchronized QIODriveSpecialization get(@Nullable ResourceLocation registryName) {
        return registryName == null ? null : specializations.get(registryName);
    }

    @Nullable
    public synchronized QIODriveSpecialization get(@Nullable String registryName) {
        if (registryName == null || registryName.isEmpty()) {
            return null;
        }
        try {
            return get(registryName.indexOf(':') < 0 ? new ResourceLocation(Mekanism.MODID, registryName) :
                  new ResourceLocation(registryName));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized boolean isRegistered(@Nullable QIODriveSpecialization specialization) {
        return specialization != null && specializations.get(specialization.getRegistryName()) == specialization;
    }

    @Nonnull
    public synchronized Map<ResourceLocation, QIODriveSpecialization> getSpecializations() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(specializations));
    }

    public synchronized long getRevision() {
        return revision;
    }

    /** Exact, order-independent sum used by every mixed drive of this definition. */
    @Nonnull
    public synchronized QIOAmount getMixedCountCapacity(@Nonnull QIODriveDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        QIOAmount total = QIOAmount.ZERO;
        for (QIODriveSpecialization specialization : specializations.values()) {
            if (specialization.contributesToMixed()) {
                total = total.add(specialization.getExactCountCapacity(definition));
            }
        }
        if (total.isZero()) {
            throw new IllegalStateException("No QIO drive specialization contributes capacity for " +
                  definition.getRegistryName());
        }
        return total;
    }
}
