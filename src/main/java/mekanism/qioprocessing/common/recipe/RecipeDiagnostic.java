package mekanism.qioprocessing.common.recipe;

/**
 * Per-recipe skip or incomplete declaration. A broken mod recipe does not discard the directory.
 */
public final class RecipeDiagnostic {
    public enum Reason {
        NULL_RECIPE, UNNAMED, DYNAMIC, EMPTY_OUTPUT, INVALID_LAYOUT, EMPTY_INPUTS,
        DUPLICATE_ID, CAPTURE_FAILED, UNENUMERATED_INGREDIENT
    }

    public final String recipeId;
    public final Reason reason;
    public final int slot;
    public final String detail;

    RecipeDiagnostic(String recipeId, Reason reason, int slot, String detail) {
        this.recipeId = recipeId;
        this.reason = reason;
        this.slot = slot;
        this.detail = detail;
    }
}

