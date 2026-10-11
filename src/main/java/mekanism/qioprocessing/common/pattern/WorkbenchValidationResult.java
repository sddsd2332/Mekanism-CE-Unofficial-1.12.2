package mekanism.qioprocessing.common.pattern;

public final class WorkbenchValidationResult {
    public enum Status {VALID, UNKNOWN_RECIPE, EMPTY_GRID, INVALID_INPUT, NO_MATCH, EMPTY_OUTPUT, OUTPUT_CHANGED, INVALID_REMAINDERS, FAILED}

    private final Status status;
    private final VerifiedWorkbenchPattern pattern;
    private final String detail;

    private WorkbenchValidationResult(Status status, VerifiedWorkbenchPattern pattern, String detail) {
        this.status = status;
        this.pattern = pattern;
        this.detail = detail;
    }

    public static WorkbenchValidationResult valid(VerifiedWorkbenchPattern pattern) {
        return new WorkbenchValidationResult(Status.VALID, pattern, "");
    }

    public static WorkbenchValidationResult invalid(Status status, String detail) {
        return new WorkbenchValidationResult(status, null, detail);
    }

    public Status status() {
        return status;
    }

    public VerifiedWorkbenchPattern pattern() {
        return pattern;
    }

    public String detail() {
        return detail;
    }

    public boolean valid() {
        return status == Status.VALID && pattern != null;
    }
}

