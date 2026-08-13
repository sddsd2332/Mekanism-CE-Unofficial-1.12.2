package mekanism.qioprocessing.common.config;

import mekanism.api.processing.MachineResourceStack;
import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationRecipeConfigSnapshotTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void snapshotRoundTripPreservesProfileStateAndRouteBounds() throws Exception {
        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationRecipeConfigSnapshot.Route route =
              new QIOAutomationRecipeConfigSnapshot.Route("product", "5:smeltiron",
                    "smelt", "iron", Collections.singletonList(
                    MachineResourceStack.item("in", new ItemStack(Items.IRON_INGOT))),
                    Collections.singletonList(MachineResourceStack.item("out",
                          new ItemStack(Blocks.IRON_BLOCK))), true, false,
                    2, 3, 7, 32, true);
        QIOAutomationRecipeConfigSnapshot original = new QIOAutomationRecipeConfigSnapshot(
              QIOAutomationRecipeConfigType.SCHEDULED, UUID.randomUUID(), deviceUUID,
              "test:provider", 12, 9, 0, 1, "iron", true, 7,
              RouteFilterMode.WHITELIST, true, true, 4, 64,
              Collections.singletonList(route));

        QIOAutomationRecipeConfigSnapshot restored =
              QIOAutomationRecipeConfigSnapshot.read(original.write());

        assertEquals(original.getType(), restored.getType());
        assertEquals(12, restored.getProfileRevision());
        assertEquals(9, restored.getPolicyRevision());
        assertTrue(restored.isIndividualProfile());
        assertEquals(7, restored.getGlobalProfileSlot());
        assertEquals(RouteFilterMode.WHITELIST, restored.getRouteFilterMode());
        assertTrue(restored.isEditable());
        assertEquals(7, restored.getRoutes().get(0).getCraftAmount());
        assertTrue(restored.getRoutes().get(0).hasCraftAmountOverride());
        assertTrue(restored.getRoutes().get(0).isProfileEnabled());
    }

    @Test
    void passiveSnapshotRejectsMutableOrBlacklistShape() {
        assertThrows(IllegalArgumentException.class, () ->
              new QIOAutomationRecipeConfigSnapshot(
                    QIOAutomationRecipeConfigType.PASSIVE, UUID.randomUUID(),
                    UUID.randomUUID(), "test:provider", 0, 0, 0, 0, "", false,
                    1, RouteFilterMode.BLACKLIST, false, false, 1, 1,
                    Collections.emptyList()));
    }
}
