package mekanism.client.recipe_viewer;

import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeViewerOptionalDependencyTest {

    @AfterEach
    void resetRuntime() {
        RecipeViewerUtils.setRuntime(null);
    }

    @Test
    void genericRuntimeRefreshUsesRegisteredAdapter() {
        AtomicBoolean refreshed = new AtomicBoolean();
        RecipeViewerUtils.setRuntime(new IRecipeViewerRuntime() {
            @Override
            public void refreshRecipeTransferButtons() {
                refreshed.set(true);
            }
        });

        RecipeViewerUtils.refreshRecipeTransferButtons();

        assertTrue(refreshed.get());
    }

    @Test
    void coreRecipeViewerClassesDoNotLinkJei() throws IOException {
        assertDoesNotReferenceJeiApi("/mekanism/client/ClientTickHandler.class");
        assertDoesNotReferenceJeiApi("/mekanism/client/recipe_viewer/RecipeViewerUtils.class");
        assertDoesNotReferenceJeiApi("/mekanism/client/recipe_viewer/type/RecipeViewerRecipeType.class");
        assertDoesNotReferenceJeiPlugin("/mekanism/client/ClientTickHandler.class");
        assertDoesNotReferenceJeiPlugin("/mekanism/client/recipe_viewer/RecipeViewerUtils.class");
    }

    private void assertDoesNotReferenceJeiApi(String classResource) throws IOException {
        String constantPool = new String(readClass(classResource), StandardCharsets.ISO_8859_1);
        assertFalse(constantPool.contains("mezz/jei"), classResource + " must not link JEI API classes");
    }

    private void assertDoesNotReferenceJeiPlugin(String classResource) throws IOException {
        String constantPool = new String(readClass(classResource), StandardCharsets.ISO_8859_1);
        assertFalse(constantPool.contains("mekanism/client/jei/MekanismJEI"),
              classResource + " must not link the JEI plugin class");
    }

    private byte[] readClass(String classResource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(classResource)) {
            assertNotNull(input, classResource);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
