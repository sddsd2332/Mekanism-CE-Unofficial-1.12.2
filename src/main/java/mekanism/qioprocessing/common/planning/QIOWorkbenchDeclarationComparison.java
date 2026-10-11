package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeScanner;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

import java.util.*;

/**
 * Read-only migration gate for the legacy Workbench declaration directory.
 * It never selects a production directory and never invokes recipe execution.
 */
public final class QIOWorkbenchDeclarationComparison {
    private QIOWorkbenchDeclarationComparison() {
    }

    public static Report capture(Iterable<? extends IRecipe> source) {
        List<IRecipe> recipes = new ArrayList<>();
        for (IRecipe recipe : source) recipes.add(recipe);
        RecipeCatalog rewritten = RecipeScanner.capture(recipes);
        QIOWorkbenchDeclarations legacy = QIOWorkbenchDeclarations.index(
                QIOWorkbenchDeclarations.capture(recipes));
        return compare(rewritten, legacy);
    }

    public static Report compare(RecipeCatalog rewritten, QIOWorkbenchDeclarations legacy) {
        Set<String> rewrittenIds = new LinkedHashSet<>();
        for (RecipeDeclaration recipe : rewritten.recipes()) rewrittenIds.add(recipe.id());
        Set<String> legacyIds = new LinkedHashSet<>();
        for (QIOWorkbenchDeclarations.Recipe recipe : legacy.getRecipes()) {
            legacyIds.add(recipe.getId().toString());
        }
        Set<String> missingInLegacy = difference(rewrittenIds, legacyIds);
        Set<String> missingInRewritten = difference(legacyIds, rewrittenIds);
        List<String> mismatches = new ArrayList<>();
        for (String id : rewrittenIds) {
            if (!legacyIds.contains(id)) continue;
            RecipeDeclaration next = rewritten.byId(id);
            QIOWorkbenchDeclarations.Recipe old = legacy.get(new ResourceLocation(id));
            if (old == null) continue;
            if (!layoutMatches(next, old) || next.width() != old.getWidth() || next.height() != old.getHeight() ||
                    next.outputCount() != old.getOutput().getAmount()) {
                mismatches.add(id);
            }
        }
        Collections.sort(mismatches);
        return new Report(rewritten.scanned(), rewrittenIds.size(), legacyIds.size(),
                missingInLegacy, missingInRewritten, mismatches,
                rewritten.diagnostics().size(), legacy.getDiagnostics().size(), rewritten.generation());
    }

    private static boolean layoutMatches(RecipeDeclaration rewritten, QIOWorkbenchDeclarations.Recipe legacy) {
        switch (rewritten.layout()) {
            case SHAPED:
                return legacy.getLayout() == QIOWorkbenchDeclarations.Layout.SHAPED;
            case SHAPELESS:
                return legacy.getLayout() == QIOWorkbenchDeclarations.Layout.SHAPELESS;
            default:
                return legacy.getLayout() == QIOWorkbenchDeclarations.Layout.ORDERED_UNKNOWN;
        }
    }

    private static Set<String> difference(Collection<String> left, Collection<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return Collections.unmodifiableSet(result);
    }

    public static final class Report {
        private final int scanned;
        private final int rewrittenCount, legacyCount;
        private final Set<String> missingInLegacy, missingInRewritten;
        private final List<String> metadataMismatches;
        private final int rewrittenDiagnostics, legacyDiagnostics;
        private final String rewrittenGeneration;

        private Report(int scanned, int rewrittenCount, int legacyCount,
                       Set<String> missingInLegacy, Set<String> missingInRewritten,
                       List<String> metadataMismatches, int rewrittenDiagnostics,
                       int legacyDiagnostics, String rewrittenGeneration) {
            this.scanned = scanned;
            this.rewrittenCount = rewrittenCount;
            this.legacyCount = legacyCount;
            this.missingInLegacy = missingInLegacy;
            this.missingInRewritten = missingInRewritten;
            this.metadataMismatches = Collections.unmodifiableList(new ArrayList<>(metadataMismatches));
            this.rewrittenDiagnostics = rewrittenDiagnostics;
            this.legacyDiagnostics = legacyDiagnostics;
            this.rewrittenGeneration = rewrittenGeneration;
        }

        public int scanned() {
            return scanned;
        }

        public int rewrittenCount() {
            return rewrittenCount;
        }

        public int legacyCount() {
            return legacyCount;
        }

        public Set<String> missingInLegacy() {
            return missingInLegacy;
        }

        public Set<String> missingInRewritten() {
            return missingInRewritten;
        }

        public List<String> metadataMismatches() {
            return metadataMismatches;
        }

        public int rewrittenDiagnostics() {
            return rewrittenDiagnostics;
        }

        public int legacyDiagnostics() {
            return legacyDiagnostics;
        }

        public String rewrittenGeneration() {
            return rewrittenGeneration;
        }

        public boolean exact() {
            return missingInLegacy.isEmpty() && missingInRewritten.isEmpty() && metadataMismatches.isEmpty();
        }
    }
}
