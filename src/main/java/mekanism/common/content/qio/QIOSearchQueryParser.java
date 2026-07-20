package mekanism.common.content.qio;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/** Parser for the QIO viewer's name, mod, tooltip and ore-dictionary queries. */
public final class QIOSearchQueryParser {

    private static final ISearchQuery INVALID = entry -> false;
    private static final Set<Character> TERMINATORS = new HashSet<>();

    static {
        TERMINATORS.add('|');
        TERMINATORS.add('(');
        TERMINATORS.add('"');
        TERMINATORS.add('\'');
    }

    private QIOSearchQueryParser() {
    }

    public static ISearchQuery parse(@Nullable String query) {
        List<SearchQuery> queries = new ArrayList<>();
        SearchQuery current = new SearchQuery();
        String text = query == null ? "" : query;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '|') {
                if (!current.isEmpty()) {
                    queries.add(current);
                }
                current = new SearchQuery();
                continue;
            } else if (c == ' ') {
                continue;
            }
            QueryType type = QueryType.get(c);
            if (type != null) {
                i++;
            } else {
                type = QueryType.NAME;
            }
            KeyListResult result = readKeyList(text, i, type, current);
            if (!result.valid) {
                return INVALID;
            }
            i = result.index;
        }
        if (!current.isEmpty()) {
            queries.add(current);
        }
        return new SearchQueryList(queries);
    }

    private static KeyListResult readKeyList(String query, int start, QueryType type, SearchQuery target) {
        if (start >= query.length()) {
            return new KeyListResult(true, start);
        }
        char first = query.charAt(start);
        List<String> keys;
        int index;
        if (first == '(') {
            ListResult result = readList(query, start);
            if (result == null) {
                return new KeyListResult(false, -1);
            }
            keys = result.values;
            index = result.index;
        } else if (first == '"' || first == '\'') {
            Result result = readQuote(query, start);
            if (result == null) {
                return new KeyListResult(false, -1);
            }
            keys = Collections.singletonList(result.value);
            index = result.index;
        } else {
            Result result = readUntilTermination(query, start, type != QueryType.NAME);
            keys = Collections.singletonList(result.value);
            index = result.index;
        }
        if (!keys.isEmpty()) {
            target.queryStrings.put(type, keys);
        }
        return new KeyListResult(true, index);
    }

    @Nullable
    private static ListResult readList(String query, int start) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = start + 1; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == ')') {
                addTrimmed(result, current);
                return new ListResult(result, i);
            } else if (c == '|') {
                addTrimmed(result, current);
                current = new StringBuilder();
            } else if (c == '"' || c == '\'') {
                Result quoted = readQuote(query, i);
                if (quoted == null) {
                    return null;
                }
                result.add(quoted.value);
                i = quoted.index;
            } else {
                current.append(c);
            }
        }
        return null;
    }

    private static void addTrimmed(List<String> values, StringBuilder value) {
        String trimmed = value.toString().trim();
        if (!trimmed.isEmpty()) {
            values.add(trimmed);
        }
    }

    @Nullable
    private static Result readQuote(String text, int start) {
        char quote = text.charAt(start);
        StringBuilder result = new StringBuilder();
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == quote) {
                return new Result(result.toString(), i);
            }
            result.append(c);
        }
        return null;
    }

    private static Result readUntilTermination(String text, int start, boolean terminateAtSpace) {
        StringBuilder result = new StringBuilder();
        int index = start;
        for (; index < text.length(); index++) {
            char c = text.charAt(index);
            if (TERMINATORS.contains(c) || QueryType.get(c) != null || terminateAtSpace && c == ' ') {
                index--;
                break;
            }
            result.append(c);
        }
        return new Result(result.toString().trim(), index);
    }

    public enum QueryType {
        NAME('~', (key, entry) -> resourceName(entry).toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT))),
        MOD_ID('@', (key, entry) -> resourceIdentifier(entry).toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT))),
        TOOLTIP('$', (key, entry) -> resourceTooltip(entry).toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT))),
        TAG('#', (key, entry) -> resourceTags(entry).stream().anyMatch(tag -> tag.contains(key.toLowerCase(Locale.ROOT))));

        private static final Map<Character, QueryType> LOOKUP = new HashMap<>();

        static {
            for (QueryType type : values()) {
                LOOKUP.put(type.prefix, type);
            }
        }

        private final char prefix;
        private final BiPredicate<String, QIOResourceEntry> checker;

        QueryType(char prefix, BiPredicate<String, QIOResourceEntry> checker) {
            this.prefix = prefix;
            this.checker = checker;
        }

        @Nullable
        public static QueryType get(char prefix) {
            return LOOKUP.get(prefix);
        }

        private boolean matches(String key, QIOResourceEntry entry) {
            return checker.test(key, entry);
        }
    }

    public interface ISearchQuery {

        boolean matches(QIOResourceEntry entry);

        default boolean isInvalid() {
            return this == INVALID;
        }
    }

    private static class SearchQuery implements ISearchQuery {

        private final Map<QueryType, List<String>> queryStrings = new EnumMap<>(QueryType.class);

        @Override
        public boolean matches(QIOResourceEntry entry) {
            for (Map.Entry<QueryType, List<String>> query : queryStrings.entrySet()) {
                boolean matched = false;
                for (String key : query.getValue()) {
                    if (query.getKey().matches(key, entry)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            return true;
        }

        private boolean isEmpty() {
            return queryStrings.isEmpty();
        }
    }

    private static class SearchQueryList implements ISearchQuery {

        private final List<SearchQuery> queries;

        private SearchQueryList(List<SearchQuery> queries) {
            this.queries = queries;
        }

        @Override
        public boolean matches(QIOResourceEntry entry) {
            if (queries.isEmpty()) {
                return true;
            }
            for (SearchQuery query : queries) {
                if (query.matches(entry)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String resourceName(QIOResourceEntry entry) {
        switch (entry.getKind()) {
            case ITEM:
                return entry.getItem().getDisplayName();
            case FLUID:
                return entry.getFluid() == null ? "" : entry.getFluid().getLocalizedName();
            case GAS:
                return entry.getGas() == null || entry.getGas().getGas() == null ? "" : entry.getGas().getGas().getLocalizedName();
            default:
                return "";
        }
    }

    private static String resourceIdentifier(QIOResourceEntry entry) {
        switch (entry.getKind()) {
            case ITEM:
                return entry.getItem().getItem().getRegistryName() == null ? "" : entry.getItem().getItem().getRegistryName().toString();
            case FLUID:
                return entry.getFluid() == null || entry.getFluid().getFluid() == null ? "" : entry.getFluid().getFluid().getName();
            case GAS:
                return entry.getGas() == null || entry.getGas().getGas() == null ? "" : entry.getGas().getGas().getName();
            default:
                return "";
        }
    }

    private static String resourceTooltip(QIOResourceEntry entry) {
        if (entry.getKind() == QIOResourceKind.ITEM) {
            // Tooltip rendering is client-only in 1.12. Keep the common
            // parser side-safe by indexing the stable display/NBT text; the
            // client still presents the full tooltip when hovering a result.
            ItemStack stack = entry.getItem();
            return stack.getDisplayName() + " " + (stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
        }
        return resourceName(entry);
    }

    private static Set<String> resourceTags(QIOResourceEntry entry) {
        Set<String> tags = new HashSet<>();
        if (entry.getKind() == QIOResourceKind.ITEM) {
            for (int id : OreDictionary.getOreIDs(entry.getItem())) {
                tags.add(OreDictionary.getOreName(id).toLowerCase(Locale.ROOT));
            }
        } else {
            tags.add(resourceIdentifier(entry).toLowerCase(Locale.ROOT));
        }
        return tags;
    }

    private static class KeyListResult {

        private final boolean valid;
        private final int index;

        private KeyListResult(boolean valid, int index) {
            this.valid = valid;
            this.index = index;
        }
    }

    private static class Result {

        private final String value;
        private final int index;

        private Result(String value, int index) {
            this.value = value;
            this.index = index;
        }
    }

    private static class ListResult {

        private final List<String> values;
        private final int index;

        private ListResult(List<String> values, int index) {
            this.values = values;
            this.index = index;
        }
    }
}
