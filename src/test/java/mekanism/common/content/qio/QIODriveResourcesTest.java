package mekanism.common.content.qio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QIODriveResourcesTest {

    private static final List<String> TIERS = Arrays.asList(
          "base", "hyper_dense", "time_dilating", "supermassive");
    private static final Map<String, String> TYPE_DIRECTORIES = new LinkedHashMap<>();

    static {
        TYPE_DIRECTORIES.put("", "all");
        TYPE_DIRECTORIES.put("_item", "item");
        TYPE_DIRECTORIES.put("_fluid", "fluid");
        TYPE_DIRECTORIES.put("_gas", "gas");
    }

    @Test
    void everyDriveModelUsesItsResourceTypeDirectory() throws Exception {
        for (Map.Entry<String, String> type : TYPE_DIRECTORIES.entrySet()) {
            for (String tier : TIERS) {
                String texture = "qio_drive/" + type.getValue() + "/qio_drive_" + tier;
                String model = "qio_drive_" + tier + type.getKey();
                JsonObject json = json("models/item/" + model + ".json");
                assertEquals("mekanism:items/" + texture,
                      json.getAsJsonObject("textures").get("layer0").getAsString());
                BufferedImage image = texture("textures/items/" + texture + ".png");
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
            }
        }
    }

    private static JsonObject json(String path) throws Exception {
        try (InputStream stream = resource(path);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static BufferedImage texture(String path) throws Exception {
        try (InputStream stream = resource(path)) {
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "Invalid image /assets/mekanism/" + path);
            return image;
        }
    }

    private static InputStream resource(String path) {
        String full = "/assets/mekanism/" + path;
        InputStream stream = QIODriveResourcesTest.class.getResourceAsStream(full);
        assertNotNull(stream, "Missing resource " + full);
        return stream;
    }
}
