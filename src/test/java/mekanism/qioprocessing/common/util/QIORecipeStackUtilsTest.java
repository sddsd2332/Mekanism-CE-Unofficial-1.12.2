package mekanism.qioprocessing.common.util;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIORecipeStackUtilsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void recipeBoundaryDropsForgeCapsButKeepsOrdinaryTag() {
        NBTTagCompound capabilities = new NBTTagCompound();
        capabilities.setString("oversized", "transient");
        ItemStack held = new ItemStack(Items.STICK, 1, 0, capabilities);
        NBTTagCompound ordinary = new NBTTagCompound();
        ordinary.setString("variant", "same");
        held.setTagCompound(ordinary.copy());

        NBTTagCompound stable = QIORecipeStackUtils.writeForRecipeSelection(held);
        assertFalse(stable.hasKey("ForgeCaps"));
        assertEquals("same", stable.getCompoundTag("tag").getString("variant"));

        ItemStack copy = QIORecipeStackUtils.readForRecipeSelection(stable);
        assertTrue(QIORecipeStackUtils.sameRecipeSelection(held, copy));
        assertEquals(1, copy.getCount());
    }

    @Test
    void capabilityNeutralIdentityIgnoresOnlyTransientCapabilities() {
        ItemStack first = new ItemStack(Items.STICK);
        ItemStack second = new ItemStack(Items.STICK, 1, 0, new NBTTagCompound());
        NBTTagCompound firstTag = new NBTTagCompound();
        firstTag.setInteger("variant", 2);
        first.setTagCompound(firstTag.copy());
        second.setTagCompound(firstTag.copy());

        assertTrue(QIORecipeStackUtils.sameRecipeSelection(first, second));
        assertEquals(QIORecipeStackUtils.writeForRecipeSelection(first),
              QIORecipeStackUtils.writeForRecipeSelection(second));
    }

    @Test
    void cacheReadSelectivelyDropsOversizedForgeCaps() {
        NBTTagCompound stored = QIORecipeStackUtils.writeForRecipeSelection(
              new ItemStack(Items.STICK));
        NBTTagCompound caps = new NBTTagCompound();
        StringBuilder payload = new StringBuilder(100_000);
        for (int index = 0; index < 100_000; index++) payload.append('x');
        caps.setString("payload", payload.toString());
        stored.setTag("ForgeCaps", caps);

        ItemStack restored = QIORecipeStackUtils.readForRecipeSelection(stored);
        assertFalse(restored.isEmpty());
        assertTrue(QIORecipeStackUtils.sameRecipeSelection(new ItemStack(Items.STICK), restored));
    }
}
