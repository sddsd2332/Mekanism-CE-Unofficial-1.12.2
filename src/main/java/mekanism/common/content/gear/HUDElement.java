package mekanism.common.content.gear;

import mekanism.api.gear.IHUDElement;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.Color;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;

public class HUDElement implements IHUDElement {

    private final ResourceLocation icon;
    private final String text;
    private final HUDColor color;

    private HUDElement(ResourceLocation icon, String text, HUDColor color) {
        this.icon = icon;
        this.text = text;
        this.color = color;
    }

    @NotNull
    @Override
    public ResourceLocation getIcon() {
        return icon;
    }

    @NotNull
    @Override
    public String getText() {
        return text;
    }

    @Override
    public int getColor() {
        return color.getColorARGB();
    }


    public static HUDElement of(ResourceLocation icon, String text, HUDColor color) {
        return new HUDElement(icon, text, color);
    }

    public enum HUDColor {
        REGULAR(MekanismConfig.current().client.hudColor),
        FADED(() -> REGULAR.getColor().darken(0.5).rgb()),
        WARNING(MekanismConfig.current().client.hudWarningColor),
        DANGER(MekanismConfig.current().client.hudDangerColor);

        private final IntSupplier color;

        HUDColor(IntSupplier color) {
            this.color = color;
        }

        public Color getColor() {
            return Color.rgb(color.getAsInt()).alpha(MekanismConfig.current().client.hudOpacity.val());
        }

        public int getColorARGB() {
            return getColor().argb();
        }

        public static HUDColor from(IHUDElement.HUDColor apiColor) {
            return switch (apiColor) {
                case REGULAR -> REGULAR;
                case FADED -> FADED;
                case WARNING -> WARNING;
                case DANGER -> DANGER;
            };
        }
    }
}