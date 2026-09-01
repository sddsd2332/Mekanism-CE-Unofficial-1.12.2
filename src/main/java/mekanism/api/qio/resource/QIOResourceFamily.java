package mekanism.api.qio.resource;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validation for the shared semantic family attached to a QIO resource codec. */
public final class QIOResourceFamily {

    public static final int MAX_LENGTH = 64;
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+");

    private QIOResourceFamily() {
    }

    /**
     * Validates a family without changing its identity.
     *
     * <p>Families are deliberately not namespaced registry keys. Multiple mods may use the same
     * family when they agree on its semantic meaning. Matching is always exact.</p>
     */
    @Nonnull
    public static String requireValid(@Nonnull String family) {
        Objects.requireNonNull(family, "Resource family cannot be null");
        if (family.isEmpty() || family.length() > MAX_LENGTH ||
              !family.equals(family.toLowerCase(Locale.ROOT)) || !VALID.matcher(family).matches()) {
            throw new IllegalArgumentException("QIO resource family must contain 1.." + MAX_LENGTH +
                  " lowercase ASCII letters, digits, '.', '_' or '-': " + family);
        }
        return family;
    }
}
