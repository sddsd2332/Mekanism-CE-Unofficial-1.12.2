package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable result returned before a QIO frequency is removed. */
public final class QIOFrequencyDeleteCheck {

    private static final QIOFrequencyDeleteCheck ALLOWED =
          new QIOFrequencyDeleteCheck(Collections.emptyList());

    private final List<String> blockers;

    private QIOFrequencyDeleteCheck(List<String> blockers) {
        this.blockers = Collections.unmodifiableList(new ArrayList<>(blockers));
    }

    @Nonnull
    public static QIOFrequencyDeleteCheck allowed() {
        return ALLOWED;
    }

    @Nonnull
    public static QIOFrequencyDeleteCheck blocked(@Nonnull String... blockers) {
        Objects.requireNonNull(blockers, "blockers");
        List<String> checked = new ArrayList<>(Arrays.asList(blockers));
        if (checked.isEmpty() || checked.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("A blocked QIO deletion requires blocker identifiers");
        }
        return new QIOFrequencyDeleteCheck(checked);
    }

    @Nonnull
    public static QIOFrequencyDeleteCheck blocked(@Nonnull List<String> blockers) {
        Objects.requireNonNull(blockers, "blockers");
        return blocked(blockers.toArray(new String[0]));
    }

    public boolean isAllowed() {
        return blockers.isEmpty();
    }

    @Nonnull
    public List<String> getBlockers() {
        return blockers;
    }
}
