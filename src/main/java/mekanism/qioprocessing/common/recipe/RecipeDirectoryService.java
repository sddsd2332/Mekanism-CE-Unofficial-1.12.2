package mekanism.qioprocessing.common.recipe;

import mekanism.common.Mekanism;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Initial frozen registry capture only. Later world/network remaps must not rebuild the directory.
 */
public final class RecipeDirectoryService {
    public static final String MODID = "mekanismqioprocessing";
    public static final RecipeDirectoryService INSTANCE = new RecipeDirectoryService();

    public enum State {WAITING, ARMED, BUILDING, READY, FAILED}

    private volatile RecipeCatalog catalog = RecipeCatalog.empty();
    private volatile State state = State.WAITING;
    private volatile String failure = "";
    private Thread owner;

    public RecipeCatalog catalog() {
        return catalog;
    }

    public State state() {
        return state;
    }

    public String failure() {
        return failure;
    }

    public synchronized void armInitialFreeze() {
        if (state == State.WAITING) {
            owner = Thread.currentThread();
            state = State.ARMED;
        }
    }

    public void onMapping(boolean frozen) {
        boolean initialAttempt = frozen && state == State.ARMED;
        if (captureInitial(frozen, () -> ForgeRegistries.RECIPES.getValuesCollection())) {
            RecipeCatalog result = catalog;
            Mekanism.logger.info("[QIO Recipes] Published: scanned={}, declarations={}, diagnostics={}, uniqueIngredients={}, "
                            + "uniqueItems={}, elapsedMs={}, estimatedMemoryBytes={}, estimatedMemoryMiB={}, generation={}, reasons={}",
                    result.scanned(), result.recipes().size(), result.diagnostics().size(), result.uniqueIngredients(),
                    result.uniqueItems(), result.elapsedNanos() / 1_000_000L, result.estimatedBytes(),
                    result.estimatedBytes() / (1024L * 1024L), result.generation(), result.diagnosticCounts());
        } else if (initialAttempt && state == State.FAILED) {
            Mekanism.logger.error("[QIO Recipes] Initial capture failed: {}", failure);
        }
    }

    boolean captureInitial(boolean frozen, Supplier<? extends Iterable<? extends IRecipe>> source) {
        synchronized (this) {
            if (!frozen || state != State.ARMED) return false;
            if (Thread.currentThread() != owner)
                throw new IllegalStateException("Recipe capture must run on the loading thread");
            state = State.BUILDING;
        }
        try {
            RecipeCatalog next = RecipeScanner.capture(source.get());
            catalog = next;
            state = State.READY;
            return true;
        } catch (RuntimeException | LinkageError error) {
            failure = error.getClass().getName() + ": " + error.getMessage();
            state = State.FAILED;
            return false;
        }
    }

    /**
     * Called before server startup so that a missed initial event cannot become a world-load scan.
     */
    public synchronized void closeInitialWindow() {
        if (state == State.WAITING || state == State.ARMED) {
            failure = "Initial frozen registry notification was not observed";
            state = State.FAILED;
            Mekanism.logger.error("[QIO Recipes] {}", failure);
        }
    }
}

