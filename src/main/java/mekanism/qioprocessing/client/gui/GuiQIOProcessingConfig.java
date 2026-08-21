package mekanism.qioprocessing.client.gui;

import mekanism.common.Mekanism;
import mekanism.common.util.LangUtils;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.GuiConfigEntries.CategoryEntry;
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
        super(parent, getConfigElements(), MekanismQIOProcessing.MODID, false, false,
              "Mekanism QIO Processing");
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<>();
        elements.add(category("planning", PlanningEntry.class));
        elements.add(category("scheduling", SchedulingEntry.class));
        elements.add(category("claims", ClaimsEntry.class));
        elements.add(category("processors", ProcessorsEntry.class));
        elements.add(category("maintenance", MaintenanceEntry.class));
        elements.add(category("devices", DevicesEntry.class));
        elements.add(category("providers", ProvidersEntry.class));
        elements.add(category("terminals", TerminalsEntry.class));
        elements.add(category("recipe_catalog", RecipeCatalogEntry.class));
        return elements;
    }

    private static DummyCategoryElement category(String category,
          Class<? extends CategoryEntry> entryClass) {
        String translationKey = TRANSLATION_PREFIX + category;
        return new DummyCategoryElement(LangUtils.localize(translationKey), translationKey,
              entryClass);
    }

    private abstract static class QIOCategoryEntry extends CategoryEntry {

        private final String category;

        protected QIOCategoryEntry(GuiConfig owningScreen,
              GuiConfigEntries owningEntryList, IConfigElement configElement,
              String category) {
            super(owningScreen, owningEntryList, configElement);
            this.category = category;
        }

        @Override
        protected GuiScreen buildChildScreen() {
            return new GuiConfig(owningScreen,
                  new ConfigElement(Mekanism.configurationQIOProcessing.getCategory(category))
                        .getChildElements(),
                  owningScreen.modID, Configuration.CATEGORY_GENERAL,
                  configElement.requiresWorldRestart() || owningScreen.allRequireWorldRestart,
                  configElement.requiresMcRestart() || owningScreen.allRequireMcRestart,
                  GuiConfig.getAbridgedConfigPath(
                        Mekanism.configurationQIOProcessing.toString()));
        }
    }

    public static class PlanningEntry extends QIOCategoryEntry {

        public PlanningEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "planning");
        }
    }

    public static class SchedulingEntry extends QIOCategoryEntry {

        public SchedulingEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "scheduling");
        }
    }

    public static class ClaimsEntry extends QIOCategoryEntry {

        public ClaimsEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "claims");
        }
    }

    public static class ProcessorsEntry extends QIOCategoryEntry {

        public ProcessorsEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "processors");
        }
    }

    public static class MaintenanceEntry extends QIOCategoryEntry {

        public MaintenanceEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "maintenance");
        }
    }

    public static class DevicesEntry extends QIOCategoryEntry {

        public DevicesEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "devices");
        }
    }

    public static class ProvidersEntry extends QIOCategoryEntry {

        public ProvidersEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "providers");
        }
    }

    public static class TerminalsEntry extends QIOCategoryEntry {

        public TerminalsEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "terminals");
        }
    }

    public static class RecipeCatalogEntry extends QIOCategoryEntry {

        public RecipeCatalogEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "recipe_catalog");
        }
    }
}
