package mekanism.qioprocessing.api.machine;

import mekanism.common.recipe.RecipeHandler;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Stable policy namespaces. Source identity is never serialized or compared by map contents.
 */
public final class QIORecipeFamilyRegistry {

    private static final Map<Object, String> SOURCES = new IdentityHashMap<>();
    private static boolean builtinsRegistered;

    private QIORecipeFamilyRegistry() {
    }

    /**
     * Register a shared recipe source during integration initialization, before machines publish routes.
     */
    public static synchronized void registerSource(@Nonnull Object source,
                                                   @Nonnull ResourceLocation family) {
        registerBuiltins();
        String id = Objects.requireNonNull(family, "family").toString();
        String previous = SOURCES.get(Objects.requireNonNull(source, "source"));
        if (previous != null && !previous.equals(id)) {
            throw new IllegalArgumentException("A QIO recipe source already belongs to " + previous);
        }
        SOURCES.put(source, id);
    }

    @Nullable
    public static synchronized String resolve(@Nullable Object source) {
        registerBuiltins();
        return source == null ? null : SOURCES.get(source);
    }

    private static void registerBuiltins() {
        if (!builtinsRegistered) {
            for (RecipeHandler.Recipe<?, ?, ?> recipe : RecipeHandler.Recipe.values()) {
                SOURCES.put(recipe.get(), "mekanism:recipe/" +
                        recipe.getRecipeName().toLowerCase(Locale.ROOT));
            }
            builtinsRegistered = true;
        }
    }
}
