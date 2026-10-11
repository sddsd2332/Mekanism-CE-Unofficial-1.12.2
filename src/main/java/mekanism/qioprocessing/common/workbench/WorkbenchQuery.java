package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure view model. Paging limits a response, never discovery or the number of retained routes.
 */
public final class WorkbenchQuery {
    public enum Status {AVAILABLE, IMPORTED, INCOMPLETE, CHANGED, MISSING, INVALID_CHOICES, PATTERN_STALE}

    private WorkbenchQuery() {
    }

    /**
     * Always search the catalog, including recipes that have not yet been imported.
     */
    public static Page routes(RecipeCatalog catalog, WorkbenchSnapshot book, RecipeItemKey output, int offset, int limit) {
        bounds(offset, limit);
        List<RecipeDeclaration> matches = catalog.outputsFor(output);
        String preferred = book.preferred(output);
        if (preferred != null) {
            for (int i = 1; i < matches.size(); i++)
                if (matches.get(i).id().equals(preferred)) {
                    matches = new ArrayList<>(matches);
                    RecipeDeclaration first = matches.remove(i);
                    matches.add(0, first);
                    break;
                }
        }
        int from = Math.min(offset, matches.size());
        int to = (int) Math.min((long) from + limit, matches.size());
        List<Row> rows = new ArrayList<>();
        for (int i = from; i < to; i++) {
            RecipeDeclaration recipe = matches.get(i);
            rows.add(describe(recipe, book.routes().get(recipe.id()), catalog.generation()));
        }
        return new Page(catalog.generation(), book.revision(), matches.size(), from, Collections.unmodifiableList(rows));
    }

    public static Page routesByFamily(RecipeCatalog catalog, WorkbenchSnapshot book, String family, int offset, int limit) {
        bounds(offset, limit);
        List<RecipeDeclaration> matches = catalog.outputsForFamily(family);
        String preferred = book.preferences().get(family);
        if (preferred != null) for (int i = 1; i < matches.size(); i++)
            if (matches.get(i).id().equals(preferred)) {
                matches = new ArrayList<>(matches);
                RecipeDeclaration first = matches.remove(i);
                matches.add(0, first);
                break;
            }
        int from = Math.min(offset, matches.size()), to = (int) Math.min((long) from + limit, matches.size());
        List<Row> rows = new ArrayList<>();
        for (int i = from; i < to; i++)
            rows.add(describe(matches.get(i), book.routes().get(matches.get(i).id()), catalog.generation()));
        return new Page(catalog.generation(), book.revision(), matches.size(), from, Collections.unmodifiableList(rows));
    }

    /**
     * Includes missing/stale entries so they remain visible and removable after a pack update.
     */
    public static Page imported(RecipeCatalog catalog, WorkbenchSnapshot book, int offset, int limit) {
        bounds(offset, limit);
        int from = Math.min(offset, book.routes().size());
        List<Row> rows = new ArrayList<>();
        int index = 0;
        for (WorkbenchRoute route : book.routes().values()) {
            if (index++ < from) continue;
            if (rows.size() == limit) break;
            rows.add(describe(catalog.byId(route.recipeId()), route, catalog.generation()));
        }
        return new Page(catalog.generation(), book.revision(), book.routes().size(), from, Collections.unmodifiableList(rows));
    }

    public static Row describe(RecipeDeclaration recipe, WorkbenchRoute route) {
        return describe(recipe, route, null);
    }

    public static Row describe(RecipeDeclaration recipe, WorkbenchRoute route, String catalogGeneration) {
        Status status;
        if (recipe == null) status = Status.MISSING;
        else if (route != null && !route.matches(recipe)) status = Status.CHANGED;
        else if (!recipe.hasDeclaredCandidates()) status = Status.INCOMPLETE;
        else if (route != null && !route.validChoices(recipe)) status = Status.INVALID_CHOICES;
        else if (route != null && route.hasPatterns() && route.patterns().values().stream().noneMatch(pattern ->
                pattern.declarationSignature().equals(recipe.signature())
                        && (catalogGeneration == null || pattern.catalogGeneration().equals(catalogGeneration))))
            status = Status.PATTERN_STALE;
        else status = route == null ? Status.AVAILABLE : Status.IMPORTED;
        return new Row(recipe == null ? route.recipeId() : recipe.id(), recipe, status, route == null || route.enabled(), route != null);
    }

    private static void bounds(int offset, int limit) {
        if (offset < 0 || limit <= 0) throw new IllegalArgumentException("Invalid page bounds");
    }

    public static final class Row {
        public final String recipeId;
        public final RecipeDeclaration declaration;
        public final Status status;
        public final boolean enabled;
        public final boolean imported;

        private Row(String id, RecipeDeclaration declaration, Status status, boolean enabled, boolean imported) {
            recipeId = id;
            this.declaration = declaration;
            this.status = status;
            this.enabled = enabled;
            this.imported = imported;
        }
    }

    public static final class Page {
        public final String generation;
        public final long revision;
        public final int total, offset;
        public final List<Row> rows;

        private Page(String generation, long revision, int total, int offset, List<Row> rows) {
            this.generation = generation;
            this.revision = revision;
            this.total = total;
            this.offset = offset;
            this.rows = rows;
        }

        public boolean hasMore() {
            return (long) offset + rows.size() < total;
        }
    }
}

