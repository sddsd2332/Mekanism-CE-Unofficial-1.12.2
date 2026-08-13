package mekanism.qioprocessing.common.registries;

import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.common.block.BlockQIOProcessingTerminal;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingBlocksTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void everyTerminalBlockCreatesItsFixedTileResponsibility() {
        QIOProcessingTerminal management = terminal(QIOProcessingBlocks.QIOManagementTerminal);
        QIOProcessingTerminal smart = terminal(QIOProcessingBlocks.QIOSmartProcessingTerminal);
        QIOProcessingTerminal maintenance = terminal(QIOProcessingBlocks.QIOMaintenanceTerminal);
        QIOProcessingTerminal monitor = terminal(QIOProcessingBlocks.QIOCraftingMonitor);

        assertEquals(QIOProcessingTerminalType.MANAGEMENT, management.getTerminalType());
        assertEquals(QIOProcessingTerminalType.SMART_PROCESSING, smart.getTerminalType());
        assertEquals(QIOProcessingTerminalType.MAINTENANCE, maintenance.getTerminalType());
        assertEquals(QIOProcessingTerminalType.CRAFTING_MONITOR, monitor.getTerminalType());
        assertNotSame(management, smart);
        assertNotSame(smart, maintenance);
        assertNotSame(maintenance, monitor);
    }

    private static QIOProcessingTerminal terminal(net.minecraft.block.Block block) {
        assertTrue(block instanceof BlockQIOProcessingTerminal);
        net.minecraft.tileentity.TileEntity tile =
              ((BlockQIOProcessingTerminal) block).createNewTileEntity((World) null, 0);
        assertTrue(tile instanceof QIOProcessingTerminal);
        return (QIOProcessingTerminal) tile;
    }
}
