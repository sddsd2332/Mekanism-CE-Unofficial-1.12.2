package mekanism.api.radial.mode;

import com.github.bsideup.jabel.Desugar;
import mekanism.api.EnumColor;
import mekanism.api.radial.RadialData;
import mekanism.api.text.ILangEntry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Record providing a basic implementation for nested radial types.
 *
 * @since 10.3.2
 */
@Desugar
public record NestedRadialMode(@NotNull RadialData<?> nestedData, @NotNull ITextComponent sliceName,
                               @NotNull ResourceLocation icon, @Nullable EnumColor color)
        implements INestedRadialMode {

    /**
     * @param nestedData Nested Radial Data.
     * @param sliceName  Name to display in this mode's slice of the radial menu.
     * @param icon       Asset location of the icon to draw in this mode's slice of the radial menu.
     */
    public NestedRadialMode(@NotNull RadialData<?> nestedData, @NotNull ITextComponent sliceName, @NotNull ResourceLocation icon) {
        this(nestedData, sliceName, icon, null);
    }

    /**
     * @param nestedData Nested Radial Data.
     * @param sliceName  Lang entry for the name to display in this mode's slice of the radial menu.
     * @param icon       Asset location of the icon to draw in this mode's slice of the radial menu.
     * @param color      Selection color of this mode's slice.
     * @implNote {@code sliceName} is colored using the given color.
     */
    public NestedRadialMode(@NotNull RadialData<?> nestedData, @NotNull ILangEntry sliceName, @NotNull ResourceLocation icon, @NotNull EnumColor color) {
        this(nestedData, sliceName.translateColored(color), icon, color);
    }

    public NestedRadialMode {
        Objects.requireNonNull(nestedData, "Nested data is required and cannot be null.");
        Objects.requireNonNull(sliceName, "Radial modes must have a slice name.");
        Objects.requireNonNull(icon, "Radial modes must have an icon to display.");
    }

    @Override
    public boolean hasNestedData() {
        return true;
    }
}