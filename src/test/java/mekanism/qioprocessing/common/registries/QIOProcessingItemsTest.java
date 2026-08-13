package mekanism.qioprocessing.common.registries;

import mekanism.common.TestBootstrap;
import mekanism.api.EnumColor;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.item.interfaces.IColoredItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingItemsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void registryContainerDoesNotExposeCreativeTabAsAnObjectHolderField() {
        assertNull(QIOProcessingItems.class.getAnnotation(ObjectHolder.class));
    }

    @Test
    void everyPortableResponsibilityHasADistinctFixedItem() {
        ItemPortableQIOProcessingTerminal management =
              (ItemPortableQIOProcessingTerminal) QIOProcessingItems.PortableQIOManagementTerminal;
        ItemPortableQIOProcessingTerminal smart =
              (ItemPortableQIOProcessingTerminal) QIOProcessingItems.PortableQIOSmartProcessingTerminal;
        ItemPortableQIOProcessingTerminal maintenance =
              (ItemPortableQIOProcessingTerminal) QIOProcessingItems.PortableQIOMaintenanceTerminal;
        ItemPortableQIOProcessingTerminal monitor =
              (ItemPortableQIOProcessingTerminal) QIOProcessingItems.PortableQIOCraftingMonitor;

        assertEquals(QIOProcessingTerminalType.MANAGEMENT, management.getTerminalType());
        assertEquals(QIOProcessingTerminalType.SMART_PROCESSING, smart.getTerminalType());
        assertEquals(QIOProcessingTerminalType.MAINTENANCE, maintenance.getTerminalType());
        assertEquals(QIOProcessingTerminalType.CRAFTING_MONITOR, monitor.getTerminalType());
        assertNotSame(management, smart);
        assertNotSame(smart, maintenance);
        assertNotSame(maintenance, monitor);
    }

    @Test
    void everyStandaloneItemUsesTheQioProcessingCreativeTab() {
        for (net.minecraft.item.Item item : new net.minecraft.item.Item[]{
              QIOProcessingItems.QIOStackingUpgrade,
              QIOProcessingItems.QIOAutoCraftingUpgrade,
              QIOProcessingItems.QIOAutoProcessingUpgrade,
              QIOProcessingItems.QIOAutoOutputUpgrade,
              QIOProcessingItems.PortableQIOManagementTerminal,
              QIOProcessingItems.PortableQIOSmartProcessingTerminal,
              QIOProcessingItems.PortableQIOMaintenanceTerminal,
              QIOProcessingItems.PortableQIOCraftingMonitor}) {
            assertSame(mekanism.qioprocessing.common.MekanismQIOProcessing.TAB,
                  item.getCreativeTab());
        }
    }

    @Test
    void portableTerminalCachesAndClearsItsBoundFrequencyColor() {
        ItemPortableQIOProcessingTerminal terminal =
              (ItemPortableQIOProcessingTerminal)
                    QIOProcessingItems.PortableQIOManagementTerminal;
        ItemStack stack = new ItemStack(terminal);
        java.util.UUID owner = java.util.UUID.randomUUID();
        PortableQIOProcessingTerminalData.create(owner).writeTo(stack);
        QIOFrequency frequency = new QIOFrequency("colored", owner,
              SecurityMode.PRIVATE);
        frequency.setColor(EnumColor.ORANGE);

        assertTrue(terminal.applyAuthorizedFrequency(stack, frequency, owner));
        assertEquals(EnumColor.ORANGE, ((IColoredItem) terminal).getColor(stack));
        assertTrue(terminal.applyAuthorizedFrequency(stack, null, owner));
        assertNull(((IColoredItem) terminal).getColor(stack));
    }
}
