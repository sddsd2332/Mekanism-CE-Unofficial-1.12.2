package mekanism.qioprocessing.common.terminal;

import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration.EncodedPattern;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QIOWorkbenchCopyServiceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void uuidImportCopiesOnlyTheSourceConfigurationData() {
        UUID player = UUID.randomUUID();
        QIOProcessingNetworkData source = network("source", player);
        QIOProcessingNetworkData target = network("target", player);
        ResourceLocation recipeId = new ResourceLocation("qio_test", "copied_recipe");
        EncodedPattern pattern = new EncodedPattern(UUID.randomUUID(), recipeId,
              QIOHashing.sha256("copied-recipe"),
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)), 1,
              patternGrid());
        source.getWorkbenchConfiguration().putEncodedPattern(pattern);

        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(player,
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 0,
              target.getFrequencyUUID(), 0);
        QIOWorkbenchCopyService.PreviewResult preview = QIOWorkbenchCopyService.preview(
              target, true, session, player,
              source.getWorkbenchConfiguration().getConfigUUID(), 100,
              ignored -> source, (ignored, ignoredPlayer) -> true);
        assertEquals(QIOWorkbenchCopyService.Status.READY, preview.getStatus());
        assertNotNull(preview.getPreview());

        QIOWorkbenchCopyService.ConfirmResult result = QIOWorkbenchCopyService.confirm(
              target, true, session, player,
              preview.getPreview().getConfirmationNonce(), 100,
              ignored -> source, (ignored, ignoredPlayer) -> true);

        assertEquals(QIOWorkbenchCopyService.Status.APPLIED, result.getStatus());
        QIOWorkbenchConfiguration copied = target.getWorkbenchConfiguration();
        assertEquals(1, copied.getEncodedPatternCount());
        assertNotNull(copied.getEncodedPattern(recipeId.toString()));
    }

    private static QIOProcessingNetworkData network(String name, UUID owner) {
        return new QIOProcessingNetworkData(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot(name, owner, SecurityMode.PUBLIC));
    }

    private static List<ItemStack> patternGrid() {
        List<ItemStack> grid = new ArrayList<>(9);
        grid.add(new ItemStack(Items.STICK));
        for (int slot = 1; slot < 9; slot++) grid.add(ItemStack.EMPTY);
        return grid;
    }
}
