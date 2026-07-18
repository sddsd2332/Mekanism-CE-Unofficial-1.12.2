package mekanism.common.integration.crafttweaker;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InfuseRegistrationTest {

    @Test
    void expandsWildcardMetadataAndPreservesTag() {
        Item item = new SubtypeItem();
        ItemStack wildcard = new ItemStack(item, 8, OreDictionary.WILDCARD_VALUE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", "registered");
        wildcard.setTagCompound(tag);

        ItemStack[] expanded = InfuseRegistration.expandMatchingStacks(new ItemStack[]{wildcard});

        assertNotNull(expanded);
        assertEquals(2, expanded.length);
        assertEquals(2, expanded[0].getMetadata());
        assertEquals(7, expanded[1].getMetadata());
        for (ItemStack stack : expanded) {
            assertEquals(1, stack.getCount());
            assertEquals(tag, stack.getTagCompound());
        }
    }

    @Test
    void rejectsWildcardWithoutConcreteSubItems() {
        ItemStack wildcard = new ItemStack(new Item(), 1, OreDictionary.WILDCARD_VALUE);

        assertNull(InfuseRegistration.expandMatchingStacks(new ItemStack[]{wildcard}));
    }

    private static class SubtypeItem extends Item {

        private SubtypeItem() {
            setHasSubtypes(true);
        }

        @Override
        public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
            items.add(new ItemStack(this, 3, 2));
            items.add(new ItemStack(this, 1, 7));
            items.add(new ItemStack(this, 5, 2));
        }
    }
}
