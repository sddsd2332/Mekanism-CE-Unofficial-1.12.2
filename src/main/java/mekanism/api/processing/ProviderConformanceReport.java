package mekanism.api.processing;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of validating one bound provider for one QIO automation mode. */
public final class ProviderConformanceReport {

    private final QIOAutomationMode mode;
    private final List<String> errors;

    ProviderConformanceReport(QIOAutomationMode mode, List<String> errors) {
        this.mode = mode;
        this.errors = errors.isEmpty() ? Collections.emptyList() :
              Collections.unmodifiableList(new ArrayList<>(errors));
    }

    @Nonnull
    public QIOAutomationMode mode() {
        return mode;
    }

    public boolean isConformant() {
        return errors.isEmpty();
    }

    @Nonnull
    public List<String> errors() {
        return errors;
    }
}
