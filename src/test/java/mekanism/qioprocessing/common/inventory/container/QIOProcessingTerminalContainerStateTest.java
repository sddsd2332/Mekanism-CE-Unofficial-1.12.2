package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.inventory.container.MekanismContainer;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingTerminalContainerStateTest {

    @Test
    void boundedMirrorRoundTripsTheCapabilityHeader() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.CRAFTING_MONITOR, UUID.randomUUID(),
              12, frequencyUUID, 8);
        QIOProcessingTerminalContainerState state =
              new QIOProcessingTerminalContainerState(new TestContainer(), () -> null);
        NBTTagCompound stored = QIOProcessingTerminalContainerState.write(session);
        stored.setLong("automationRecipeProfileRevision", 17);
        state.read(stored);

        assertTrue(state.isValid());
        assertEquals(session.getSessionNonce(), state.getSessionNonce());
        assertEquals(session.getTerminalUUID(), state.getTerminalUUID());
        assertEquals(frequencyUUID, state.getFrequencyUUID());
        assertEquals(12, state.getTargetRevision());
        assertEquals(8, state.getAccessRevision());
        assertEquals(17, state.getAutomationRecipeProfileRevision());
        assertTrue(state.matches(session.getSessionNonce(), session.getTerminalUUID(),
              12, frequencyUUID, 8));
        assertFalse(state.matches(session.getSessionNonce(), session.getTerminalUUID(),
              13, frequencyUUID, 8));
        assertFalse(state.matches(session.getSessionNonce(), session.getTerminalUUID(),
              12, UUID.randomUUID(), 8));
    }

    @Test
    void incompleteAndInconsistentHeadersClearTheClientCapability() {
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
              QIOProcessingTerminalType.MAINTENANCE, UUID.randomUUID(), 2,
              null, -1);
        QIOProcessingTerminalContainerState state =
              new QIOProcessingTerminalContainerState(new TestContainer(), () -> null);
        NBTTagCompound missingIdentity =
              QIOProcessingTerminalContainerState.write(session);
        missingIdentity.removeTag("terminalUUID");
        state.read(missingIdentity);
        assertFalse(state.isValid());

        NBTTagCompound badAccess = QIOProcessingTerminalContainerState.write(session);
        badAccess.setLong("accessRevision", 0);
        state.read(badAccess);
        assertFalse(state.isValid());
    }

    private static final class TestContainer extends MekanismContainer {

        private TestContainer() {
            super(null);
        }
    }
}
