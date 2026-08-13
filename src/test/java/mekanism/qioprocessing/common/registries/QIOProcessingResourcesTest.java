package mekanism.qioprocessing.common.registries;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingResourcesTest {

    private static final List<String> BLOCKS = Arrays.asList(
          "qio_crafting_processor", "basic_qio_crafting_processor",
          "advanced_qio_crafting_processor", "elite_qio_crafting_processor",
          "ultimate_qio_crafting_processor", "qio_management_terminal",
          "qio_smart_processing_terminal", "qio_maintenance_terminal",
          "qio_crafting_monitor");
    private static final List<String> PROCESSORS = BLOCKS.subList(0, 5);
    private static final List<String> TERMINALS = BLOCKS.subList(5, 9);
    private static final List<String> ITEMS = Arrays.asList(
          "portable_qio_management_terminal", "portable_qio_smart_processing_terminal",
          "portable_qio_maintenance_terminal", "portable_qio_crafting_monitor",
          "qio_stacking_upgrade", "qio_auto_crafting_upgrade",
          "qio_auto_processing_upgrade", "qio_auto_output_upgrade");
    private static final List<String> UPGRADES = Arrays.asList(
          "qio_stacking_upgrade", "qio_auto_crafting_upgrade",
          "qio_auto_processing_upgrade", "qio_auto_output_upgrade");
    private static final List<String> PORTABLE_TERMINALS = ITEMS.subList(0, 4);

    @Test
    void everyStableRegistrationHasItsOwnRecipeAndModel() throws Exception {
        Set<String> registrations = new LinkedHashSet<>();
        registrations.addAll(BLOCKS);
        registrations.addAll(ITEMS);
        assertEquals(17, registrations.size());
        for (String name : registrations) {
            JsonObject recipe = json("recipes/" + name + ".json");
            assertEquals("forge:ore_shaped", recipe.get("type").getAsString());
            assertEquals("mekanismqioprocessing:" + name,
                  recipe.getAsJsonObject("result").get("item").getAsString());
            assertNotNull(json("models/item/" + name + ".json"));
        }
        for (String name : PROCESSORS) {
            JsonObject model = json("models/block/" + name + ".json");
            JsonObject state = json("blockstates/" + name + ".json");
            JsonObject variants = state.getAsJsonObject("variants");
            assertEquals("mekanismqioprocessing:" + name + "_offline",
                  variants.getAsJsonObject("active").getAsJsonObject("false")
                        .get("model").getAsString());
            assertEquals("mekanismqioprocessing:" + name + "_working",
                  variants.getAsJsonObject("working").getAsJsonObject("true")
                        .get("model").getAsString());
            if ("qio_crafting_processor".equals(name)) {
                assertEquals("mekanismqioprocessing:blocks/crafting_processor/base/side",
                      model.getAsJsonObject("textures").get("5").getAsString());
                assertNotNull(json("models/block/qio_crafting_processor_offline.json"));
                assertNotNull(json("models/block/qio_crafting_processor_working.json"));
            } else {
                String tier = name.replace("_qio_crafting_processor", "");
                assertEquals("mekanismqioprocessing:block/qio_tier_crafting_processor",
                      model.get("parent").getAsString());
                assertEquals("mekanism:blocks/models/factory/led/active/" + tier,
                      model.getAsJsonObject("textures").get("4").getAsString());
                assertEquals("mekanismqioprocessing:block/qio_tier_crafting_processor_offline",
                      json("models/block/" + name + "_offline.json").get("parent")
                            .getAsString());
                assertEquals("mekanismqioprocessing:block/qio_tier_crafting_processor_working",
                      json("models/block/" + name + "_working.json").get("parent")
                            .getAsString());
            }
        }
        assertNotNull(json("models/block/qio_tier_crafting_processor.json"));
        assertNotNull(json("models/block/qio_tier_crafting_processor_offline.json"));
        assertNotNull(json("models/block/qio_tier_crafting_processor_working.json"));
        for (String name : TERMINALS) {
            JsonObject model = json("models/block/" + name + ".json");
            assertEquals("mekanismqioprocessing:block/qio_processing_terminal",
                  model.get("parent").getAsString());
            assertEquals("mekanismqioprocessing:blocks/terminals/" + name + "_base",
                  model.getAsJsonObject("textures").get("base").getAsString());
            assertEquals("mekanismqioprocessing:blocks/terminals/" + name + "_channel",
                  model.getAsJsonObject("textures").get("channel").getAsString());
            assertNotNull(json("models/block/" + name + "_offline.json"));
            assertNotNull(json("blockstates/" + name + ".json"));
        }
        for (String name : PORTABLE_TERMINALS) {
            JsonObject model = json("models/item/" + name + ".json");
            assertEquals("mekanismqioprocessing:items/terminals/" + name + "_base",
                  model.getAsJsonObject("textures").get("layer0").getAsString());
            assertEquals("mekanismqioprocessing:items/terminals/" + name + "_channel",
                  model.getAsJsonObject("textures").get("layer1").getAsString());
        }
        JsonObject coloredModel = json("models/block/qio_processing_terminal.json");
        assertEquals(1, coloredModel.getAsJsonArray("elements").get(1).getAsJsonObject()
              .getAsJsonObject("faces").getAsJsonObject("up")
              .get("tintindex").getAsInt());
    }

    @Test
    void everyReferencedTextureIsMinecraftSized() throws Exception {
        Set<String> texturePaths = new LinkedHashSet<>();
        for (String texture : Arrays.asList("bottom", "led_on", "side", "top_on", "top")) {
            texturePaths.add("blocks/crafting_processor/base/" + texture + ".png");
        }
        for (String texture : Arrays.asList("side", "top_on", "top")) {
            texturePaths.add("blocks/crafting_processor/tier/" + texture + ".png");
        }
        for (String name : TERMINALS) {
            texturePaths.add("blocks/terminals/" + name + "_base.png");
            texturePaths.add("blocks/terminals/" + name + "_channel.png");
        }
        for (String name : PORTABLE_TERMINALS) {
            texturePaths.add("items/terminals/" + name + "_base.png");
            texturePaths.add("items/terminals/" + name + "_channel.png");
        }
        for (String name : UPGRADES) texturePaths.add("items/" + name + ".png");
        assertEquals(28, texturePaths.size());
        for (String path : texturePaths) texture(path);
    }

    @Test
    void upgradeRuntimeTranslationKeysExistInEveryLocale() throws Exception {
        for (String locale : Arrays.asList("en_us", "zh_cn")) {
            Properties translations = new Properties();
            try (InputStream stream = resource("lang/" + locale + ".lang");
                 InputStreamReader reader = new InputStreamReader(stream,
                       StandardCharsets.UTF_8)) {
                translations.load(reader);
            }
            for (String upgrade : UPGRADES) {
                assertTrue(hasText(translations.getProperty("upgrade." + upgrade)),
                      "Missing upgrade name in " + locale + ": " + upgrade);
                assertTrue(hasText(translations.getProperty("upgrade." + upgrade + ".desc")),
                      "Missing upgrade description in " + locale + ": " + upgrade);
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static JsonObject json(String path) throws Exception {
        try (InputStream stream = resource(path)) {
            return new JsonParser().parse(new InputStreamReader(stream,
                  StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static int texture(String path) throws Exception {
        try (InputStream stream = resource("textures/" + path)) {
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            int hash = 1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    hash = 31 * hash + image.getRGB(x, y);
                }
            }
            return hash;
        }
    }

    private static InputStream resource(String path) {
        String full = "/assets/mekanismqioprocessing/" + path;
        InputStream stream = QIOProcessingResourcesTest.class.getResourceAsStream(full);
        assertNotNull(stream, "Missing resource " + full);
        return stream;
    }
}
