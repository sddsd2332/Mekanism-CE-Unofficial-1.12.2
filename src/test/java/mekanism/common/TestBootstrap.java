package mekanism.common;

import net.minecraft.init.Bootstrap;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.util.Collections;

public final class TestBootstrap {

    private TestBootstrap() {
    }

    public static void bootstrapMinecraft() {
        try {
            Loader loader = Loader.instance();
            Field namedMods = Loader.class.getDeclaredField("namedMods");
            namedMods.setAccessible(true);
            if (namedMods.get(loader) == null) {
                namedMods.set(loader, Collections.emptyMap());
            }
            Bootstrap.register();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to initialize Forge test state", e);
        }
    }
}
