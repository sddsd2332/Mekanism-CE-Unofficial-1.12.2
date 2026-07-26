package mekanism.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

@SideOnly(Side.CLIENT)
public final class OptifineRenderCompat {

    @Nullable
    private static final Field SHADOW_PASS_FIELD = findShadowPassField();

    private OptifineRenderCompat() {
    }

    public static boolean isShadowPass() {
        if (SHADOW_PASS_FIELD == null) {
            return false;
        }
        try {
            return SHADOW_PASS_FIELD.getBoolean(null);
        } catch (IllegalAccessException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    private static Field findShadowPassField() {
        try {
            Class<?> shaders = Class.forName("net.optifine.shaders.Shaders", false, OptifineRenderCompat.class.getClassLoader());
            Field field = shaders.getDeclaredField("isShadowPass");
            field.setAccessible(true);
            return field;
        } catch (ClassNotFoundException | NoSuchFieldException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
