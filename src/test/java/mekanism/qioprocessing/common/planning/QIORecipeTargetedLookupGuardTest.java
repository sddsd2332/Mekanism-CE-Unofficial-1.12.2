package mekanism.qioprocessing.common.planning;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIORecipeTargetedLookupGuardTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void cachesCapabilityNeutralSuccessesAndBoundsFullSearchesPerTick() {
        QIORecipeTargetedLookupGuard guard = new QIORecipeTargetedLookupGuard(8, 1, 3, 2);
        NBTTagCompound capabilities = new NBTTagCompound();
        capabilities.setString("test:transient", "held");
        ItemStack held = new ItemStack(Items.STICK, 1, 0, capabilities);
        ItemStack stored = new ItemStack(Items.STICK);
        NBTTagCompound ordinary = new NBTTagCompound();
        ordinary.setString("variant", "same");
        held.setTagCompound(ordinary.copy());
        stored.setTagCompound(ordinary.copy());
        ResourceLocation recipeId = new ResourceLocation("qio_test", "cached");

        assertTrue(guard.tryAcquireFullSearch());
        assertFalse(guard.tryAcquireFullSearch());
        guard.remember(0, grid(held), recipeId);

        QIORecipeTargetedLookupGuard.Lookup cached = guard.lookup(0, grid(stored));
        assertTrue(cached.isCached());
        assertEquals(recipeId, cached.getRecipeId());
        assertFalse(guard.lookup(1, grid(stored)).isCached());

        guard.beginTick();
        assertTrue(guard.tryAcquireFullSearch());
        guard.beginTick();
        assertTrue(guard.lookup(0, grid(stored)).isCached());
        guard.beginTick();
        assertFalse(guard.lookup(0, grid(stored)).isCached());
    }

    @Test
    void negativeResultExpiresAndCacheRemainsBounded() {
        QIORecipeTargetedLookupGuard guard = new QIORecipeTargetedLookupGuard(2, 1, 4, 2);
        List<ItemStack> missing = grid(new ItemStack(Items.STRING));
        guard.remember(0, missing, null);

        QIORecipeTargetedLookupGuard.Lookup cached = guard.lookup(0, missing);
        assertTrue(cached.isCached());
        assertNull(cached.getRecipeId());
        guard.beginTick();
        assertTrue(guard.lookup(0, missing).isCached());
        guard.beginTick();
        assertFalse(guard.lookup(0, missing).isCached());

        guard.remember(0, grid(new ItemStack(Items.STICK)),
              new ResourceLocation("qio_test", "first"));
        guard.remember(0, grid(new ItemStack(Items.DIAMOND)),
              new ResourceLocation("qio_test", "second"));
        guard.remember(0, grid(new ItemStack(Items.EMERALD)),
              new ResourceLocation("qio_test", "third"));
        assertEquals(2, guard.size());
    }

    private static List<ItemStack> grid(ItemStack first) {
        List<ItemStack> grid = new ArrayList<>(9);
        grid.add(first);
        for (int slot = 1; slot < 9; slot++) grid.add(ItemStack.EMPTY);
        return grid;
    }
}
