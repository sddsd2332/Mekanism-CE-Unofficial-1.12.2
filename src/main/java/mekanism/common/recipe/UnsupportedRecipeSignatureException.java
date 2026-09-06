package mekanism.common.recipe;

/** A signature cannot be represented completely by the supported semantic value contract. */
public final class UnsupportedRecipeSignatureException extends IllegalArgumentException {

    private final String valueType;
    private final String valuePath;

    public UnsupportedRecipeSignatureException(Object value, String path, String reason) {
        super(reason + " at " + path + " (" + (value == null ? "null" : value.getClass().getName()) +
              "). Supply supported semantic data or implement IRecipeSignatureSource; arbitrary object reflection is disabled.");
        valueType = value == null ? "null" : value.getClass().getName();
        valuePath = path;
    }

    public String getValueType() { return valueType; }
    public String getValuePath() { return valuePath; }
}
