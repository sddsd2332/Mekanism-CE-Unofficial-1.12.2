package mekanism.qioprocessing.client.gui;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiQIOProcessingConfig extends GuiConfig {

    private static final String TRANSLATION_PREFIX =
          "mekanism.configgui.ctgy.qio_processing.";

    public GuiQIOProcessingConfig(GuiScreen parent) {
        super(parent, getConfigElements(), MekanismQIOProcessing.MODID,
              MekanismQIOProcessing.MODID, false, false, "Mekanism QIO Processing");
    }

    private static List<IConfigElement> getConfigElements() {
        Configuration configuration = Mekanism.getQIOProcessingConfiguration();
        MekanismConfig.local().qioProcessing.load(configuration);
        if (configuration.hasChanged()) {
            configuration.save();
        }
        List<IConfigElement> elements = new ArrayList<>();
        elements.add(category(configuration, "planning"));
        elements.add(category(configuration, "scheduling"));
        elements.add(category(configuration, "claims"));
        elements.add(category(configuration, "processors"));
        elements.add(category(configuration, "maintenance"));
        elements.add(category(configuration, "devices"));
        elements.add(category(configuration, "providers"));
        elements.add(category(configuration, "terminals"));
        elements.add(category(configuration, "recipe_catalog"));
        return elements;
    }

    private static DummyCategoryElement category(Configuration configuration,
          String categoryName) {
        String translationKey = TRANSLATION_PREFIX + categoryName;
        ConfigCategory category = configuration.getCategory(categoryName);
        return new DummyCategoryElement(LangUtils.localize(translationKey), translationKey,
              new ConfigElement(category).getChildElements());
    }
}
