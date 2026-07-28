package mekanism.common.capabilities;

import mekanism.api.qio.external.IQIOStorageAccessor;
import mekanism.common.capabilities.DefaultStorageHelper.NullStorage;
import net.minecraftforge.common.capabilities.CapabilityManager;

/** Default capability factory; real accessors are owned by QIO dashboards. */
public final class DefaultQIOStorageAccessor {

    private DefaultQIOStorageAccessor() {
    }

    public static void register() {
        CapabilityManager.INSTANCE.register(IQIOStorageAccessor.class, new NullStorage<>(), () -> requester -> null);
    }
}
