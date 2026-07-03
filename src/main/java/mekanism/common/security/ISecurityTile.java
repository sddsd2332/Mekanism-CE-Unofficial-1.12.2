package mekanism.common.security;

import mekanism.api.EnumColor;
import mekanism.api.IIncrementalEnum;
import mekanism.api.math.MathUtils;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.util.LangUtils;

import javax.annotation.Nonnull;

public interface ISecurityTile {

    TileComponentSecurity getSecurity();

    enum SecurityMode implements IIncrementalEnum<SecurityMode> {
        PUBLIC("security.public", EnumColor.BRIGHT_GREEN),
        PRIVATE("security.private", EnumColor.RED),
        TRUSTED("security.trusted", EnumColor.AQUA);

        private static final SecurityMode[] MODES = values();
        private String display;
        private EnumColor color;

        SecurityMode(String s, EnumColor c) {
            display = s;
            color = c;
        }

        public String getDisplay() {
            return color + LangUtils.localize(display);
        }

        @Nonnull
        @Override
        public SecurityMode byIndex(int index) {
            return byIndexStatic(index);
        }

        public static SecurityMode byIndexStatic(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }
    }
}
