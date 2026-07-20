package mekanism.client.gui;

import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiMekanismTooltipTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void keepsNormalTooltipOffset() {
        assertEquals(450, GuiMekanism.getTooltipZOffset(450));
    }

    @Test
    void clampsTooltipOffsetAccumulatedByMultipleWindows() {
        assertEquals(500, GuiMekanism.getTooltipZOffset(1_400));
    }

    @Test
    void leavesDepthRoomForVanillaFloatingItems() {
        // RenderItem adds 100 + 200 + 50 before drawing a carried stack.
        assertTrue(GuiMekanism.getTooltipZOffset(Integer.MAX_VALUE) + 350 < 1_000);
    }
}
