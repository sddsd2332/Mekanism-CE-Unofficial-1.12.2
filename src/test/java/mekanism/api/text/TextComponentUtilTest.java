package mekanism.api.text;

import mekanism.api.EnumColor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextComponentUtilTest {

    @Test
    void formattingTokensDoNotConsumeTranslationArguments() {
        ITextComponent component = TestLang.DETAIL.translate(EnumColor.INDIGO, "12", "34");
        assertTrue(component instanceof TextComponentTranslation);
        TextComponentTranslation translation = (TextComponentTranslation) component;

        assertEquals("Items: 12 / 34", translation.getUnformattedText());
        Object[] args = translation.getFormatArgs();
        assertEquals(2, args.length);
        assertTrue(args[0] instanceof ITextComponent);
        ITextComponent first = (ITextComponent) args[0];
        assertEquals("12", first.getUnformattedComponentText());
        assertEquals(TextFormatting.BLUE, first.getStyle().getColor());
        assertEquals("34", args[1]);
    }

    @Test
    void outerAndArgumentColorsRemainIndependent() {
        ITextComponent translated = TestLang.DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO, "12", "34");
        assertEquals(TextFormatting.GRAY, translated.getStyle().getColor());

        assertTrue(translated.getSiblings().get(0) instanceof TextComponentTranslation);
        TextComponentTranslation translation = (TextComponentTranslation) translated.getSiblings().get(0);
        assertTrue(translation.getFormatArgs()[0] instanceof ITextComponent);
        ITextComponent first = (ITextComponent) translation.getFormatArgs()[0];
        assertEquals(TextFormatting.BLUE, first.getStyle().getColor());
    }

    private enum TestLang implements ILangEntry {
        DETAIL;

        @Override
        public String getTranslationKey() {
            return "Items: %1$s / %2$s";
        }
    }
}
