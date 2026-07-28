package mekanism.api.qio.external;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

/** Forge capabilities owned by the public QIO integration API. */
public final class QIOCapabilities {

    @CapabilityInject(IQIOStorageAccessor.class)
    public static Capability<IQIOStorageAccessor> STORAGE_ACCESSOR = null;

    private QIOCapabilities() {
    }
}
