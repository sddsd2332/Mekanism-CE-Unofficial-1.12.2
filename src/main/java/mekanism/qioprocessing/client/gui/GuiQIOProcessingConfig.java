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
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.GuiConfigEntries.CategoryEntry;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
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
        protected QIOCategoryEntry(GuiConfig owningScreen,
              GuiConfigEntries owningEntryList, IConfigElement configElement,
              String ignoredCategory) {
            super(owningScreen, owningEntryList, configElement);
        }

        /**
         * CategoryEntry builds the child screen from its superclass constructor.  Do not store
         * the category in an instance field: subclass fields are still uninitialized at that
         * point and Forge would call Configuration#getCategory(null).
         */
        protected abstract String categoryName();

        @Override
        protected GuiScreen buildChildScreen() {
            Configuration configuration = Mekanism.getQIOProcessingConfiguration();
            // Catalogue can open this screen before QIO common pre-init.  Populate the same
            // shared Configuration instance before asking Forge for a category.
            MekanismConfig.local().qioProcessing.load(configuration);
            if (configuration.hasChanged()) {
                configuration.save();
            }
            ConfigCategory configCategory = configuration.getCategory(categoryName());
            List<IConfigElement> children = configCategory == null ? Collections.emptyList() :
                  new ConfigElement(configCategory).getChildElements();
            return new GuiConfig(owningScreen,
                  children,
                  owningScreen.modID, Configuration.CATEGORY_GENERAL,
                  configElement.requiresWorldRestart() || owningScreen.allRequireWorldRestart,
                  configElement.requiresMcRestart() || owningScreen.allRequireMcRestart,
                  GuiConfig.getAbridgedConfigPath(
                        configuration.toString()));
        }
    }

    public static class PlanningEntry extends QIOCategoryEntry {

        public PlanningEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "planning");
        }

        @Override
        protected String categoryName() { return "planning"; }
    }

    public static class SchedulingEntry extends QIOCategoryEntry {

        public SchedulingEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "scheduling");
        }

        @Override
        protected String categoryName() { return "scheduling"; }
    }

    public static class ClaimsEntry extends QIOCategoryEntry {

        public ClaimsEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "claims");
        }

        @Override
        protected String categoryName() { return "claims"; }
    }

    public static class ProcessorsEntry extends QIOCategoryEntry {

        public ProcessorsEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "processors");
        }

        @Override
        protected String categoryName() { return "processors"; }
    }

    public static class MaintenanceEntry extends QIOCategoryEntry {

        public MaintenanceEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "maintenance");
        }

        @Override
        protected String categoryName() { return "maintenance"; }
    }

    public static class DevicesEntry extends QIOCategoryEntry {

        public DevicesEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "devices");
        }

        @Override
        protected String categoryName() { return "devices"; }
    }

    public static class ProvidersEntry extends QIOCategoryEntry {

        public ProvidersEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "providers");
        }

        @Override
        protected String categoryName() { return "providers"; }
    }

    public static class TerminalsEntry extends QIOCategoryEntry {

        public TerminalsEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "terminals");
        }

        @Override
        protected String categoryName() { return "terminals"; }
    }

    public static class RecipeCatalogEntry extends QIOCategoryEntry {

        public RecipeCatalogEntry(GuiConfig screen, GuiConfigEntries entries,
              IConfigElement element) {
            super(screen, entries, element, "recipe_catalog");
        }

        @Override
        protected String categoryName() { return "recipe_catalog"; }
    }
}
