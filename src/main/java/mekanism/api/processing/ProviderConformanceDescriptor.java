package mekanism.api.processing;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned, explicit declaration of the QIO automation modes implemented by a machine provider.
 *
 * <p>A provider that does not override {@link MachineRecipeProvider#getQIOConformance(net.minecraft.tileentity.TileEntity)}
 * is deliberately unavailable to QIO automation. Declaring a mode is only the first admission gate; the QIO module also
 * validates the bound routes and ports before publishing a provider.</p>
 */
public final class ProviderConformanceDescriptor {

    public static final int CURRENT_VERSION = 1;
    private static final ProviderConformanceDescriptor UNREGISTERED = new ProviderConformanceDescriptor();

    private final int version;
    private final String ownerModId;
    private final String stableProviderSlotId;
    private final Set<QIOAutomationMode> modes;
    private final boolean registered;

    private ProviderConformanceDescriptor() {
        version = CURRENT_VERSION;
        ownerModId = "";
        stableProviderSlotId = "";
        modes = Collections.emptySet();
        registered = false;
    }

    private ProviderConformanceDescriptor(Builder builder) {
        version = builder.version;
        ownerModId = requireIdentifier(builder.ownerModId, "Owner mod id");
        stableProviderSlotId = requireIdentifier(builder.stableProviderSlotId, "Provider slot id");
        if (builder.modes.isEmpty()) {
            throw new IllegalStateException("A registered provider conformance descriptor must support at least one mode");
        }
        modes = Collections.unmodifiableSet(EnumSet.copyOf(builder.modes));
        registered = true;
    }

    @Nonnull
    public static ProviderConformanceDescriptor unregistered() {
        return UNREGISTERED;
    }

    @Nonnull
    public static Builder builder(@Nonnull String ownerModId, @Nonnull String stableProviderSlotId) {
        return new Builder(ownerModId, stableProviderSlotId);
    }

    public int version() {
        return version;
    }

    @Nonnull
    public String ownerModId() {
        return ownerModId;
    }

    @Nonnull
    public String stableProviderSlotId() {
        return stableProviderSlotId;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean supports(QIOAutomationMode mode) {
        return mode != null && modes.contains(mode);
    }

    @Nonnull
    public Set<QIOAutomationMode> modes() {
        return modes;
    }

    private static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= 'a' && character <= 'z') && !(character >= '0' && character <= '9') &&
                character != '_' && character != '-' && character != '.' && character != '/') {
                throw new IllegalArgumentException(name + " contains an unsupported character: " + value);
            }
        }
        return value;
    }

    public static final class Builder {

        private final String ownerModId;
        private final String stableProviderSlotId;
        private final EnumSet<QIOAutomationMode> modes = EnumSet.noneOf(QIOAutomationMode.class);
        private int version = CURRENT_VERSION;

        private Builder(String ownerModId, String stableProviderSlotId) {
            this.ownerModId = ownerModId;
            this.stableProviderSlotId = stableProviderSlotId;
        }

        /** Intended for a future descriptor schema negotiated by the QIO module. */
        public Builder version(int version) {
            if (version <= 0) {
                throw new IllegalArgumentException("Conformance version must be positive");
            }
            this.version = version;
            return this;
        }

        public Builder supports(QIOAutomationMode... modes) {
            Objects.requireNonNull(modes, "Modes cannot be null");
            for (QIOAutomationMode mode : modes) {
                this.modes.add(Objects.requireNonNull(mode, "Mode cannot be null"));
            }
            return this;
        }

        public ProviderConformanceDescriptor build() {
            return new ProviderConformanceDescriptor(this);
        }
    }
}
