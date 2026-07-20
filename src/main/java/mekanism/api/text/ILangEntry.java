package mekanism.api.text;


import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.EnumColor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

/**
 * Helper interface for creating formatted translations in our lang enums
 */
@MethodsReturnNonnullByDefault
public interface ILangEntry extends IHasTranslationKey {

    /**
     * Translates this {@link ILangEntry} using a "smart" replacement scheme to allow for automatic replacements, and coloring to take place.
     */

    default ITextComponent translate(Object... args) {
        return TextComponentUtil.smartTranslate(getTranslationKey(), args);
    }

    /**
     * Translates this {@link ILangEntry} and applies the {@link net.minecraft.network.chat.TextColor} of the given {@link EnumColor} to the {@link Component}.
     */

    default ITextComponent translateColored(EnumColor color, Object... args) {
        return translateColored(color.textFormatting, args);
    }

    /**
     * Translates this {@link ILangEntry} and applies the {@link net.minecraft.network.chat.TextColor} to the {@link Component}.
     *
     * @since 10.4.0
     */

    default ITextComponent translateColored(TextFormatting color, Object... args) {
        return new TextComponentGroup(color).component(translate(args));
    }
}
