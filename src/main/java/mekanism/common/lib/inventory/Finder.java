package mekanism.common.lib.inventory;

import mekanism.common.OreDictCache;
import mekanism.common.lib.WildcardMatcher;
import mekanism.common.util.ItemRegistryUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

@FunctionalInterface
public interface Finder {

    Finder ANY = stack -> true;
    Finder NONE = stack -> false;

    static Finder item(Item itemType) {
        return itemType == Items.AIR ? NONE : stack -> stack.getItem() == itemType;
    }

    static Finder item(ItemStack itemType) {
        return item(itemType.getItem());
    }

    static Finder strict(ItemStack itemType) {
        return stack -> ItemHandlerHelper.canItemStacksStack(itemType, stack);
    }

    static Finder oreDict(String oreDictName) {
        return stack -> !stack.isEmpty() && OreDictCache.getOreDictName(stack).stream().anyMatch(oreKey -> WildcardMatcher.matches(oreDictName, oreKey));
    }

    static Finder material(Material materialType) {
        return stack -> !stack.isEmpty() && stack.getItem() instanceof ItemBlock &&
              Block.getBlockFromItem(stack.getItem()).getStateFromMeta(stack.getItemDamage()).getMaterial() == materialType;
    }

    static Finder modID(String modID) {
        return stack -> !stack.isEmpty() && WildcardMatcher.matches(modID, ItemRegistryUtils.getMod(stack));
    }

    static Finder wildcard(ItemStack itemType) {
        return stack -> StackUtils.equalsWildcard(itemType, stack);
    }

    boolean modifies(ItemStack stack);
}
