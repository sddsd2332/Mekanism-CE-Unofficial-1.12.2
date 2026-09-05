package mekanism.common.integration.groovyscript;

import com.cleanroommc.groovyscript.event.ScriptRunEvent;
import com.cleanroommc.groovyscript.sandbox.LoadStage;
import mekanism.common.recipe.RecipeHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Raises a batch generation after GroovyScript has finished publishing POST_INIT recipes. */
public final class GroovyRecipeReloadHandler {

    private static final GroovyRecipeReloadHandler INSTANCE = new GroovyRecipeReloadHandler();
    private static boolean registered;

    private GroovyRecipeReloadHandler() {
    }

    public static synchronized void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(INSTANCE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScriptsFinished(ScriptRunEvent.Post event) {
        if (event.getLoadStage() == LoadStage.POST_INIT) {
            // Per-entry RecipeMap callbacks provide immediate invalidation. This
            // unconditional batch boundary also covers in-place/third-party changes.
            RecipeHandler.markRecipeReloadComplete();
        }
    }
}
