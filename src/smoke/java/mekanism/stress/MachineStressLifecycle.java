package mekanism.stress;

import net.minecraft.server.MinecraftServer;
import java.util.Map;

/** Optional lifecycle fixture; implementations may require APIs absent from the baseline. */
public interface MachineStressLifecycle {
    boolean advance(MinecraftServer server);
    Map<String, Object> report();
}
