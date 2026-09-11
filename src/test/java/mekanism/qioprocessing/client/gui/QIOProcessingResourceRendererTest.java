package mekanism.qioprocessing.client.gui;

import mekanism.api.qio.client.QIOResourceRenderer;
import mekanism.api.qio.client.QIOResourceRendererRegistry;
import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QIOProcessingResourceRendererTest {

    private static final TestCodec CODEC = new TestCodec();

    @BeforeAll
    static void registerPresentation() {
        QIOResourceCodecRegistry.INSTANCE.register(CODEC);
        QIOResourceRendererRegistry.INSTANCE.register(new TestRenderer());
    }

    @Test
    void processingPresentationUsesTheCodecRenderer() {
        PortableResourceDescriptor resource = PortableResourceDescriptor.fromDescriptor(
              QIOResourceDescriptor.of(CODEC, "alpha"));

        assertEquals("Display alpha", QIOGuiResourceRenderer.name(resource));
        assertEquals("qio_test:alpha", QIOGuiResourceRenderer.identity(resource));
        assertEquals(Arrays.asList("Display alpha", "Codec tooltip"),
              QIOGuiResourceRenderer.tooltip(resource));
        assertEquals("ingredient:alpha", QIOGuiResourceRenderer.ingredient(resource));
    }

    private static final class TestRenderer implements QIOResourceRenderer<String> {

        @Override
        public ResourceLocation getCodecId() {
            return CODEC.getCodecId();
        }

        @Override
        public Class<String> getValueClass() {
            return String.class;
        }

        @Override
        public void render(String resource, int x, int y) {
        }

        @Override
        public String getDisplayName(String resource) {
            return "Display " + resource;
        }

        @Override
        public String getRegistryName(String resource) {
            return "qio_test:" + resource;
        }

        @Override
        public List<String> getTooltip(String resource) {
            return Arrays.asList(getDisplayName(resource), "Codec tooltip");
        }

        @Override
        public Object getIngredient(String resource) {
            return "ingredient:" + resource;
        }
    }

    private static final class TestCodec implements QIOResourceCodec<String> {

        private static final ResourceLocation ID = new ResourceLocation(
              "qio_test", "processing_renderer_resource");

        @Override
        public ResourceLocation getCodecId() {
            return ID;
        }

        @Override
        public String getFamily() {
            return "qio_test.processing_renderer";
        }

        @Override
        public Class<String> getValueClass() {
            return String.class;
        }

        @Override
        public String normalize(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Renderer test resource cannot be empty");
            }
            return value;
        }

        @Override
        public boolean sameType(String first, String second) {
            return normalize(first).equals(normalize(second));
        }

        @Override
        public int typeHash(String value) {
            return normalize(value).hashCode();
        }

        @Override
        public NBTTagCompound writeTemplate(String value) {
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("value", normalize(value));
            return payload;
        }

        @Override
        public String readTemplate(NBTTagCompound payload, int codecVersion) {
            return normalize(payload.getString("value"));
        }

        @Override
        public long getStorageUnitsPerUnit() {
            return 1;
        }
    }
}
