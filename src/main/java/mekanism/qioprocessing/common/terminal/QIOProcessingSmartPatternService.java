package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternValidator;
import mekanism.qioprocessing.common.pattern.WorkbenchValidationResult;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.workbench.WorkbenchBookStore;
import mekanism.qioprocessing.common.workbench.WorkbenchRecipeBook;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-thread validation boundary from a Smart Processing window to an immutable Pattern.
 */
public final class QIOProcessingSmartPatternService {
    public enum SaveStatus {SAVED, ALREADY_PRESENT, NOT_AUTHORIZED, STALE_BOOK, REJECTED}

    public static final class SaveResult {
        private final SaveStatus status;
        private final WorkbenchValidationResult validation;
        private final WorkbenchSnapshot snapshot;

        private SaveResult(SaveStatus status, WorkbenchValidationResult validation, WorkbenchSnapshot snapshot) {
            this.status = status;
            this.validation = validation;
            this.snapshot = snapshot;
        }

        public SaveStatus status() {
            return status;
        }

        public WorkbenchValidationResult validation() {
            return validation;
        }

        public WorkbenchSnapshot snapshot() {
            return snapshot;
        }
    }

    private QIOProcessingSmartPatternService() {
    }

    public static WorkbenchValidationResult validate(QIOProcessingSmartTerminalAccess tile,
                                                     int window, RecipeDeclaration declaration, String catalogGeneration, UUID patternId) {
        Objects.requireNonNull(tile, "tile");
        if (tile.getWorld() == null || tile.getWorld().isRemote) {
            throw new IllegalStateException("Smart Pattern validation requires a server Tile");
        }
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(catalogGeneration, "catalogGeneration");
        Objects.requireNonNull(patternId, "patternId");
        return WorkbenchPatternValidator.validate(tile.getWorld(), declaration, catalogGeneration,
                tile.craftingGrid(window), patternId);
    }

    public static SaveResult saveToFrequencyBook(QIOProcessingSmartTerminalAccess tile,
                                                 int window, RecipeDeclaration declaration, RecipeCatalog catalog, UUID patternId,
                                                 long expectedBookRevision, UUID playerId, WorkbenchBookStore store) throws IOException {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(playerId, "playerId");
        if (declaration == null) return new SaveResult(SaveStatus.REJECTED, null, null);
        QIOFrequencyReference reference = tile.endpointReference();
        if (reference == null || !playerId.equals(reference.getBindingPlayerUUID())
                || !QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, playerId)) {
            return new SaveResult(SaveStatus.NOT_AUTHORIZED, null, null);
        }
        WorkbenchValidationResult validation = validate(tile, window, declaration,
                catalog.generation(), patternId);
        if (!validation.valid()) return new SaveResult(SaveStatus.REJECTED, validation, null);
        WorkbenchSnapshot base = store.load(reference.getFrequencyUUID());
        if (base.revision() != expectedBookRevision) {
            return new SaveResult(SaveStatus.STALE_BOOK, validation, base);
        }
        WorkbenchRecipeBook book = new WorkbenchRecipeBook(base);
        try {
            WorkbenchSnapshot next = book.addPattern(expectedBookRevision, catalog, validation.pattern());
            if (next == base) return new SaveResult(SaveStatus.ALREADY_PRESENT, validation, base);
            store.save(next);
            tile.markPatternConfigurationChanged();
            return new SaveResult(SaveStatus.SAVED, validation, next);
        } catch (WorkbenchRecipeBook.Rejected rejected) {
            return new SaveResult(rejected.reason == WorkbenchRecipeBook.Rejection.STALE_REVISION
                    ? SaveStatus.STALE_BOOK : SaveStatus.REJECTED, validation, base);
        }
    }
}
