package mekanism.common.content.filter;

public interface IFilter {

    default boolean isEnabled() {
        return true;
    }

    default void setEnabled(boolean enabled) {
    }
}
