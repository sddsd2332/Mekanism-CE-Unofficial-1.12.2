package mekanism.generators.common.util;

import mekanism.generators.common.MekanismGenerators;
import net.minecraft.util.ResourceLocation;

public class MekanismGeneratorUtils {


    public static ResourceLocation getResource(ResourceType type, String name) {
        return new ResourceLocation(MekanismGenerators.MODID, type.getPrefix() + name);
    }


    public enum ResourceType {

        GUI("gui"),
        GUI_BUTTON("gui/button"),
        SOUND("sound"),
        RENDER("render"),
        TEXTURE_BLOCKS("textures/blocks"),
        TEXTURE_ITEMS("textures/items"),
        MODEL("models");

        private String prefix;

        ResourceType(String s) {
            prefix = s;
        }

        public String getPrefix() {
            return prefix + "/";
        }
    }
}
