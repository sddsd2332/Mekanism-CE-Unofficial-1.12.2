package mekanism.common.command;

import mekanism.common.MekanismLang;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadiationCommandTranslationTest {

    private static final String[] USAGE_KEYS = {
          "cmd.mek.radiation.usage",
          "cmd.mek.radiation.add.usage",
          "cmd.mek.radiation.addEntity.usage",
          "cmd.mek.radiation.get.usage",
          "cmd.mek.radiation.heal.usage",
          "cmd.mek.radiation.reduce.usage",
          "cmd.mek.radiation.removeAll.usage"
    };

    private static final MekanismLang[] MESSAGE_KEYS = {
          MekanismLang.COMMAND_RADIATION_ADD,
          MekanismLang.COMMAND_RADIATION_ADD_ENTITY,
          MekanismLang.COMMAND_RADIATION_ADD_ENTITY_TARGET,
          MekanismLang.COMMAND_RADIATION_GET,
          MekanismLang.COMMAND_RADIATION_CLEAR,
          MekanismLang.COMMAND_RADIATION_CLEAR_ENTITY,
          MekanismLang.COMMAND_RADIATION_REDUCE,
          MekanismLang.COMMAND_RADIATION_REDUCE_TARGET,
          MekanismLang.COMMAND_RADIATION_REMOVE_ALL,
          MekanismLang.RADIATION_EXPOSURE,
          MekanismLang.RADIATION_EXPOSURE_ENTITY,
          MekanismLang.RADIATION_DOSE,
          MekanismLang.RADIATION_DECAY_TIME,
          MekanismLang.GENERIC_BLOCK_POS
    };

    @Test
    void englishAndChineseContainEveryRadiationMessageAndUsage() throws IOException {
        assertComplete("en_us");
        assertComplete("zh_cn");
    }

    private static void assertComplete(String locale) throws IOException {
        Map<String, String> translations = loadTranslations(locale);
        for (MekanismLang entry : MESSAGE_KEYS) {
            String key = entry.getTranslationKey();
            assertTrue(translations.containsKey(key), () -> locale + " is missing " + key);
            assertTrue(!translations.get(key).trim().isEmpty(), () -> locale + " has an empty value for " + key);
        }
        for (String key : USAGE_KEYS) {
            assertTrue(translations.containsKey(key), () -> locale + " is missing " + key);
            assertTrue(!translations.get(key).trim().isEmpty(), () -> locale + " has an empty value for " + key);
        }
    }

    private static Map<String, String> loadTranslations(String locale) throws IOException {
        String path = "/assets/mekanism/lang/" + locale + ".lang";
        InputStream stream = RadiationCommandTranslationTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing language resource " + path);
        Map<String, String> translations = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator > 0) {
                    translations.put(line.substring(0, separator).trim(), line.substring(separator + 1));
                }
            }
        }
        return translations;
    }
}
