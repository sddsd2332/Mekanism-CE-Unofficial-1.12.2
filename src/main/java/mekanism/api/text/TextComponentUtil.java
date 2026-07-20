package mekanism.api.text;

import mekanism.api.EnumColor;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** Backport of the modern smart translation argument handling. */
public final class TextComponentUtil {

    private TextComponentUtil() {
    }

    public static ITextComponent smartTranslate(String key, Object... components) {
        if (components.length == 0) {
            return new TextComponentTranslation(key);
        }
        List<Object> args = new ArrayList<>();
        Style cachedStyle = new Style();
        boolean hasCachedStyle = false;
        for (Object component : components) {
            if (component == null) {
                args.add(null);
                cachedStyle = new Style();
                hasCachedStyle = false;
                continue;
            }
            ITextComponent current = null;
            if (component instanceof IHasTextComponent) {
                current = ((IHasTextComponent) component).getTextComponent().createCopy();
            } else if (component instanceof IHasTranslationKey) {
                current = new TextComponentTranslation(((IHasTranslationKey) component).getTranslationKey());
            } else if (component instanceof Block) {
                current = new TextComponentTranslation(((Block) component).getTranslationKey());
            } else if (component instanceof Item) {
                current = new TextComponentTranslation(((Item) component).getTranslationKey());
            } else if (component instanceof ItemStack) {
                current = new TextComponentString(((ItemStack) component).getDisplayName());
            } else if (component instanceof FluidStack) {
                current = new TextComponentString(((FluidStack) component).getLocalizedName());
            } else if (component instanceof Fluid) {
                current = new TextComponentTranslation(((Fluid) component).getUnlocalizedName());
            } else if (component instanceof ITextComponent) {
                current = ((ITextComponent) component).createCopy();
            } else if (component instanceof EnumColor && cachedStyle.getColor() == null) {
                cachedStyle.setColor(((EnumColor) component).textFormatting);
                hasCachedStyle = true;
                continue;
            } else if (component instanceof TextFormatting && !hasFormatting(cachedStyle, (TextFormatting) component)) {
                applyFormatting(cachedStyle, (TextFormatting) component);
                hasCachedStyle = true;
                continue;
            } else if (component instanceof ClickEvent && cachedStyle.getClickEvent() == null) {
                cachedStyle.setClickEvent((ClickEvent) component);
                hasCachedStyle = true;
                continue;
            } else if (component instanceof HoverEvent && cachedStyle.getHoverEvent() == null) {
                cachedStyle.setHoverEvent((HoverEvent) component);
                hasCachedStyle = true;
                continue;
            } else if (hasCachedStyle) {
                if (component instanceof EnumColor) {
                    current = new TextComponentTranslation(((EnumColor) component).getTranslationKey());
                } else {
                    current = getString(component.toString());
                }
            } else if (component instanceof String) {
                component = cleanString((String) component);
            }
            if (hasCachedStyle) {
                if (current == null) {
                    args.add(component);
                } else {
                    current.setStyle(cachedStyle);
                    args.add(current);
                }
                cachedStyle = new Style();
                hasCachedStyle = false;
            } else if (current == null) {
                args.add(component);
            } else {
                args.add(current);
            }
        }
        if (hasCachedStyle) {
            Object lastComponent = components[components.length - 1];
            args.add(lastComponent instanceof EnumColor ?
                  new TextComponentTranslation(((EnumColor) lastComponent).getTranslationKey()) : lastComponent);
        }
        return new TextComponentTranslation(key, args.toArray());
    }

    private static TextComponentString getString(String value) {
        return new TextComponentString(cleanString(value));
    }

    private static String cleanString(String value) {
        return value.replace('\u00A0', ' ').replace('\u202F', ' ');
    }

    private static boolean hasFormatting(Style style, TextFormatting formatting) {
        switch (formatting) {
            case OBFUSCATED:
                return style.getObfuscated();
            case BOLD:
                return style.getBold();
            case STRIKETHROUGH:
                return style.getStrikethrough();
            case UNDERLINE:
                return style.getUnderlined();
            case ITALIC:
                return style.getItalic();
            case RESET:
                return false;
            default:
                return style.getColor() != null;
        }
    }

    private static void applyFormatting(Style style, TextFormatting formatting) {
        if (formatting.isColor()) {
            style.setColor(formatting);
            return;
        }
        switch (formatting) {
            case OBFUSCATED:
                style.setObfuscated(true);
                break;
            case BOLD:
                style.setBold(true);
                break;
            case STRIKETHROUGH:
                style.setStrikethrough(true);
                break;
            case UNDERLINE:
                style.setUnderlined(true);
                break;
            case ITALIC:
                style.setItalic(true);
                break;
            default:
                break;
        }
    }
}
