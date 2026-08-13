package mekanism.qioprocessing.common.terminal;

import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyService.ConfirmResult;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyService.PreviewResult;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyService.Status;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOWorkbenchCopyServiceTest {

    @Test
    void previewAndOneTimeConfirmationBindBothRevisionsAndKeepTargetIdentity() {
        UUID player = UUID.randomUUID();
        QIOProcessingNetworkData source = network("source", player);
        QIOProcessingNetworkData target = network("target", player);
        String signature = String.format("%064x", 1);
        source.getWorkbenchConfiguration().setRecipeEnabled("test:source", signature,
              false);
        UUID targetConfigUUID = target.getWorkbenchConfiguration().getConfigUUID();
        QIOProcessingTerminalSession session = session(player, target.getFrequencyUUID());
        QIOWorkbenchCopyService.NetworkResolver resolver = configUUID ->
              configUUID.equals(source.getWorkbenchConfiguration().getConfigUUID()) ?
                    source : null;

        PreviewResult sourcePreview = QIOWorkbenchCopyService.preview(target, true,
              session, player, source.getWorkbenchConfiguration().getConfigUUID(), 10,
              resolver, (network, requester) -> true);
        assertEquals(Status.READY, sourcePreview.getStatus());
        assertNotNull(sourcePreview.getPreview());
        source.getWorkbenchConfiguration().setRecipeEnabled("test:changed", signature,
              false);
        UUID sourceNonce = sourcePreview.getPreview().getConfirmationNonce();
        assertEquals(Status.SOURCE_CHANGED, QIOWorkbenchCopyService.confirm(target, true,
              session, player, sourceNonce, 11, resolver,
              (network, requester) -> true).getStatus());
        assertEquals(Status.EXPIRED, QIOWorkbenchCopyService.confirm(target, true,
              session, player, sourceNonce, 11, resolver,
              (network, requester) -> true).getStatus());

        PreviewResult targetPreview = QIOWorkbenchCopyService.preview(target, true,
              session, player, source.getWorkbenchConfiguration().getConfigUUID(), 20,
              resolver, (network, requester) -> true);
        assertNotNull(targetPreview.getPreview());
        target.getWorkbenchConfiguration().setRecipeEnabled("test:target", signature,
              false);
        assertEquals(Status.TARGET_CHANGED, QIOWorkbenchCopyService.confirm(target, true,
              session, player, targetPreview.getPreview().getConfirmationNonce(), 21,
              resolver, (network, requester) -> true).getStatus());

        PreviewResult appliedPreview = QIOWorkbenchCopyService.preview(target, true,
              session, player, source.getWorkbenchConfiguration().getConfigUUID(), 30,
              resolver, (network, requester) -> true);
        assertNotNull(appliedPreview.getPreview());
        ConfirmResult applied = QIOWorkbenchCopyService.confirm(target, true, session,
              player, appliedPreview.getPreview().getConfirmationNonce(), 31, resolver,
              (network, requester) -> true);
        assertEquals(Status.APPLIED, applied.getStatus());
        assertEquals(targetConfigUUID, target.getWorkbenchConfiguration().getConfigUUID());
        assertEquals(source.getWorkbenchConfiguration().getOriginUUID(),
              target.getWorkbenchConfiguration().getOriginUUID());
        assertEquals(source.getWorkbenchConfiguration().contentDigest(),
              target.getWorkbenchConfiguration().contentDigest());
        assertFalse(target.getWorkbenchConfiguration().isRecipeEnabled("test:source",
              signature));

        assertEquals(Status.ALREADY_IMPORTED, QIOWorkbenchCopyService.preview(target,
              true, session, player, source.getWorkbenchConfiguration().getConfigUUID(),
              32, resolver, (network, requester) -> true).getStatus());
    }

    @Test
    void previewHonorsAccessAndConfirmationExpires() {
        UUID player = UUID.randomUUID();
        QIOProcessingNetworkData source = network("source", player);
        QIOProcessingNetworkData target = network("target", player);
        QIOProcessingTerminalSession session = session(player, target.getFrequencyUUID());
        QIOWorkbenchCopyService.NetworkResolver resolver = configUUID -> source;

        assertEquals(Status.ACCESS_DENIED, QIOWorkbenchCopyService.preview(target, true,
              session, player, source.getWorkbenchConfiguration().getConfigUUID(), 0,
              resolver, (network, requester) -> false).getStatus());
        assertEquals(Status.INVALID_TARGET, QIOWorkbenchCopyService.preview(target, true,
              session, player, target.getWorkbenchConfiguration().getConfigUUID(), 0,
              resolver, (network, requester) -> true).getStatus());

        PreviewResult preview = QIOWorkbenchCopyService.preview(target, true, session,
              player, source.getWorkbenchConfiguration().getConfigUUID(), 0, resolver,
              (network, requester) -> true);
        assertEquals(Status.READY, preview.getStatus());
        assertNotNull(preview.getPreview());
        assertEquals(Status.EXPIRED, QIOWorkbenchCopyService.confirm(target, true,
              session, player, preview.getPreview().getConfirmationNonce(), 1_201,
              resolver, (network, requester) -> true).getStatus());
    }

    private static QIOProcessingNetworkData network(String name, UUID owner) {
        return new QIOProcessingNetworkData(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot(name, owner, SecurityMode.PRIVATE));
    }

    private static QIOProcessingTerminalSession session(UUID player, UUID frequency) {
        return new QIOProcessingTerminalSession(player,
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 0,
              frequency, 1);
    }
}
