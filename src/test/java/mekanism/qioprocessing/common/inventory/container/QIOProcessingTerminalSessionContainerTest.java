package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QIOProcessingTerminalSessionContainerTest {

    @Test
    void resolvesWindowIdWhenPacketIsSent() {
        TestContainer container = new TestContainer();

        container.windowId = 7;
        assertEquals(7, container.getTerminalWindowId());

        container.windowId = 42;
        assertEquals(42, container.getTerminalWindowId());
    }

    private static final class TestContainer extends Container
          implements QIOProcessingTerminalSessionContainer {

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public QIOProcessingTerminalSession getTerminalSession() {
            return null;
        }
    }
}
