package mekanism.common.content.miner;

import mekanism.api.MekanismAPI;
import mekanism.api.util.BlockInfo;
import mekanism.common.OreDictCache;
import mekanism.common.lib.WildcardMatcher;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

final class MinerBlacklistHelper {

    private MinerBlacklistHelper() {
    }

    static boolean hasBlacklistedItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        return !MekanismAPI.isBlockCompatible(block, stack.getItemDamage());
    }

    static boolean hasBlacklistedMaterial(Material material) {
        for (BlockInfo info : MekanismAPI.getBoxIgnore()) {
            if (info.block != null && (info.meta == OreDictionary.WILDCARD_VALUE ?
                  info.block.getBlockState().getValidStates().stream().anyMatch(state -> state.getMaterial() == material) :
                  info.block.getStateFromMeta(info.meta).getMaterial() == material)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasBlacklistedOreDict(String oreDictName) {
        return OreDictCache.getOreDictStacks(oreDictName, true).stream().anyMatch(MinerBlacklistHelper::hasBlacklistedItem);
    }

    static boolean hasBlacklistedModID(String modID) {
        if (modID == null) {
            return false;
        }
        for (String blacklistedMod : MekanismAPI.getBoxModIgnore()) {
            if (WildcardMatcher.matches(modID, blacklistedMod)) {
                return true;
            }
        }
        for (BlockInfo info : MekanismAPI.getBoxIgnore()) {
            if (info.block != null) {
                ResourceLocation name = info.block.getRegistryName();
                if (name != null && WildcardMatcher.matches(modID, name.getNamespace())) {
                    return true;
                }
            }
        }
        return false;
    }
}
