package mekanism.common;

import mekanism.api.util.ItemInfo;
import mekanism.common.lib.WildcardMatcher;
import mekanism.common.util.ItemRegistryUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OreDictCache {

    public static Map<ItemInfo, List<String>> cachedKeys = new ConcurrentHashMap<>();
    public static Map<String, List<ItemStack>> oreDictStacks = new ConcurrentHashMap<>();
    public static Map<String, List<ItemStack>> oreDictBlockStacks = new ConcurrentHashMap<>();
    public static Map<String, List<ItemStack>> modIDStacks = new ConcurrentHashMap<>();
    public static Map<String, List<ItemStack>> modIDBlockStacks = new ConcurrentHashMap<>();
    private static final Map<String, List<ItemStack>> qioModIDStacks = new ConcurrentHashMap<>();

    public static List<String> getOreDictName(ItemStack check) {
        if (check.isEmpty()) {
            return new ArrayList<>();
        }
        ItemInfo info = ItemInfo.get(check);
        List<String> cached = cachedKeys.get(info);
        if (cached != null) {
            return cached;
        }

        int[] idsFound = OreDictionary.getOreIDs(check);
        List<String> ret = new ArrayList<>();
        for (Integer id : idsFound) {
            ret.add(OreDictionary.getOreName(id));
        }
        cachedKeys.put(info, ret);
        return ret;
    }

    public static List<ItemStack> getOreDictStacks(String oreName, boolean forceBlock) {
        Map<String, List<ItemStack>> stackCache = forceBlock ? oreDictBlockStacks : oreDictStacks;
        if (stackCache.get(oreName) != null) {
            return stackCache.get(oreName);
        }

        List<String> keys = new ArrayList<>();
        for (String s : OreDictionary.getOreNames()) {
            if (s == null) {
                continue;
            }
            if (oreName.equals(s) || oreName.equals("*")) {
                keys.add(s);
            } else if (oreName.endsWith("*") && !oreName.startsWith("*")) {
                if (s.startsWith(oreName.substring(0, oreName.length() - 1))) {
                    keys.add(s);
                }
            } else if (oreName.startsWith("*") && !oreName.endsWith("*")) {
                if (s.endsWith(oreName.substring(1))) {
                    keys.add(s);
                }
            } else if (oreName.startsWith("*") && oreName.endsWith("*")) {
                if (s.contains(oreName.substring(1, oreName.length() - 1))) {
                    keys.add(s);
                }
            }
        }

        List<ItemStack> stacks = new ArrayList<>();
        keys.forEach(key -> {
            OreDictionary.getOres(key, false).forEach(stack -> {
                ItemStack toAdd = stack.copy();
                if (!stacks.contains(stack) && (!forceBlock || toAdd.getItem() instanceof ItemBlock)) {
                    stacks.add(stack.copy());
                }
            });
        });
        stackCache.put(oreName, stacks);
        return stacks;
    }

    public static List<ItemStack> getModIDStacks(String modName, boolean forceBlock) {
        Map<String, List<ItemStack>> stackCache = forceBlock ? modIDBlockStacks : modIDStacks;
        if (stackCache.get(modName) != null) {
            return stackCache.get(modName);
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (String key : OreDictionary.getOreNames()) {
            for (ItemStack stack : OreDictionary.getOres(key, false)) {
                ItemStack toAdd = stack.copy();
                String s = ItemRegistryUtils.getMod(toAdd);
                if (!stacks.contains(stack) && (!forceBlock || toAdd.getItem() instanceof ItemBlock)) {
                    if (modName.equals(s) || modName.equals("*")) {
                        stacks.add(stack.copy());
                    } else if (modName.endsWith("*") && !modName.startsWith("*")) {
                        if (s.startsWith(modName.substring(0, modName.length() - 1))) {
                            stacks.add(stack.copy());
                        }
                    } else if (modName.startsWith("*") && !modName.endsWith("*")) {
                        if (s.endsWith(modName.substring(1))) {
                            stacks.add(stack.copy());
                        }
                    } else if (modName.startsWith("*") && modName.endsWith("*")) {
                        if (s.contains(modName.substring(1, modName.length() - 1))) {
                            stacks.add(stack.copy());
                        }
                    }
                }
            }
        }
        stackCache.put(modName, stacks);
        return stacks;
    }

    /** QIO follows modern Mekanism and matches the registry namespace, not the localized mod name. */
    public static List<ItemStack> getQIOModIDStacks(String modID) {
        return qioModIDStacks.computeIfAbsent(modID, id -> {
            List<ItemStack> stacks = new ArrayList<>();
            for (Item item : Item.REGISTRY) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty() && WildcardMatcher.matches(id, MekanismUtils.getModId(stack))) {
                    stacks.add(stack);
                }
            }
            return stacks;
        });
    }
}
