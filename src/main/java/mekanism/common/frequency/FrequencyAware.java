package mekanism.common.frequency;

import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.util.SecurityUtils;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public class FrequencyAware<FREQ extends Frequency> {

    @Nullable
    private final FrequencyIdentity identity;
    @Nullable
    private final FREQ frequency;

    private FrequencyAware(@Nullable FrequencyIdentity identity, @Nullable FREQ frequency) {
        this.identity = identity;
        this.frequency = frequency;
    }

    public static <FREQ extends Frequency> FrequencyAware<FREQ> none() {
        return new FrequencyAware<>(null, null);
    }

    public FrequencyAware(FREQ frequency) {
        this(frequency.getIdentity(), frequency);
    }

    public static <FREQ extends Frequency> FrequencyAware<FREQ> identity(FrequencyIdentity identity) {
        return new FrequencyAware<>(identity, null);
    }

    @Nullable
    public FrequencyIdentity identity() {
        return identity;
    }

    @Nullable
    public FREQ frequency() {
        return frequency;
    }

    @Nullable
    public UUID getOwner() {
        return identity == null ? null : identity.ownerUUID();
    }

    @Nullable
    public FREQ resolve(FrequencyType<FREQ> frequencyType, UUID player) {
        return identity == null ? null : frequencyType.getFrequency(identity, player);
    }

    public boolean hasIdentity(FrequencyIdentity other) {
        return Objects.equals(identity, other);
    }

    public boolean canAccessTrusted(UUID ownerUUID) {
        if (frequency != null && frequency.getSecurity() == SecurityMode.TRUSTED && ownerUUID != null && !frequency.ownerMatches(ownerUUID)) {
            SecurityFrequency security = FrequencyType.SECURITY.getManager(null, SecurityMode.PUBLIC).getFrequency(frequency.getOwner());
            return security == null || security.isTrusted(ownerUUID);
        }
        return true;
    }

    public static <FREQ extends Frequency> FrequencyAware<FREQ> create(FrequencyType<FREQ> frequencyType, FrequencyIdentity data, UUID player) {
        FrequencyManager<FREQ> manager;
        FREQ freq = null;
        if (!Objects.equals(data.ownerUUID(), player) && SecurityUtils.isTrusted(data.securityMode(), data.ownerUUID(), player)) {
            manager = frequencyType.getManager(data, data.ownerUUID());
            freq = manager == null ? null : manager.getFrequency(data.key());
            if (freq == null) {
                data = new FrequencyIdentity(data.key(), data.securityMode(), player);
            }
        }
        if (freq == null) {
            manager = frequencyType.getManager(data, player);
            if (manager == null) {
                return none();
            }
            freq = manager.getOrCreateFrequency(data, player);
        }
        return new FrequencyAware<>(freq.getIdentity(), freq);
    }
}
