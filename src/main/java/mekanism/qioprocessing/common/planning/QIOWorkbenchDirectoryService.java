package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIORecipeCatalogScanMode;
import mekanism.qioprocessing.common.recipe.RecipeDirectoryService;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Process-scoped declaration generation, independent of server worlds and encoding jobs.
 */
public final class QIOWorkbenchDirectoryService {
    public static final QIOWorkbenchDirectoryService INSTANCE = new QIOWorkbenchDirectoryService();

    public enum State {NOT_STARTED, BUILDING, READY, MANUAL_ONLY, FAILED}

    private State state = State.NOT_STARTED;
    private boolean initialWindow;
    private boolean initialEventHandled;
    private long epoch;
    private String failure = "";
    @Nullable
    private QIOWorkbenchDeclarations directory;

    private QIOWorkbenchDirectoryService() {
    }

    /**
     * Called by this mod's LoadComplete handler; actual capture is in the subsequent freeze.
     */
    public synchronized void armInitialFreeze() {
        if (!initialEventHandled && state == State.NOT_STARTED) initialWindow = true;
    }

    /**
     * Later world/network remaps never reopen this window, including after a failed capture.
     */
    public void firstMapping(boolean frozen) {
        synchronized (this) {
            if (!frozen || !initialWindow || initialEventHandled) return;
            initialEventHandled = true;
            initialWindow = false;
            if (MekanismConfig.local().qioProcessing.recipeCatalogScanMode.val().canonical() == QIORecipeCatalogScanMode.MANUAL_ONLY) {
                directory = QIOWorkbenchDeclarations.empty();
                state = State.MANUAL_ONLY;
                return;
            }
            state = State.BUILDING;
            ++epoch;
        }
        publishRecipeCatalog();
    }

    /**
     * Never treat a mapping event during server startup/world restore as the initial freeze.
     */
    public synchronized void closeInitialWindow() {
        initialWindow = false;
        if (!initialEventHandled && state == State.NOT_STARTED) {
            state = State.FAILED;
            failure = "Initial frozen registry notification was not observed";
        }
    }

    /**
     * Explicit runtime rebuild; no registry access is allowed from a planning worker.
     */
    public void rebuild(World world) {
        requireServerThread(world);
        synchronized (this) {
            initialWindow = false;
            initialEventHandled = true;
            if (state == State.BUILDING) throw new IllegalStateException("Workbench directory is already building");
            state = State.BUILDING;
            failure = "";
            ++epoch;
            if (MekanismConfig.local().qioProcessing.recipeCatalogScanMode.val().canonical() == QIORecipeCatalogScanMode.MANUAL_ONLY) {
                directory = QIOWorkbenchDeclarations.empty();
                state = State.MANUAL_ONLY;
                return;
            }
        }
        publishRecipeCatalog();
    }

    private void publishRecipeCatalog() {
        try {
            if (RecipeDirectoryService.INSTANCE.state() != RecipeDirectoryService.State.READY) {
                failed(epoch, new IllegalStateException("Recipe declaration directory is not ready"));
                return;
            }
            QIOWorkbenchDeclarations next = QIOWorkbenchDeclarations.fromRecipeCatalog(
                    RecipeDirectoryService.INSTANCE.catalog());
            synchronized (this) {
                directory = next;
                failure = "";
                state = State.READY;
            }
        } catch (RuntimeException | LinkageError error) {
            failed(epoch, error);
        }
    }

    private synchronized void failed(long buildEpoch, Throwable error) {
        if (buildEpoch != epoch) return;
        state = State.FAILED;
        failure = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        Mekanism.logger.error("[QIO Workbench] Declaration capture/index failed", error);
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized long getEpoch() {
        return epoch;
    }

    public synchronized String getFailure() {
        return failure;
    }

    public synchronized QIOWorkbenchDeclarations getDirectory() {
        if (state != State.READY && state != State.MANUAL_ONLY) {
            throw new IllegalStateException("Workbench directory " + state + ": " + failure);
        }
        return Objects.requireNonNull(directory, "published directory");
    }

    public static void requireServerThread(World world) {
        if (world == null || world.isRemote || world.getMinecraftServer() == null ||
                !world.getMinecraftServer().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Workbench Forge access requires the current server thread");
        }
    }
}
