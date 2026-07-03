package mekanism.common.advancements;

import com.google.gson.JsonObject;
import mekanism.api.gear.ModuleData;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.ModuleHelper;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

public class MaxedModuleContainerItemPredicate extends ItemPredicate {

    private final Item item;

    public MaxedModuleContainerItemPredicate(JsonObject json) {
        if (json.has("item")) {
            item = Item.REGISTRY.getObject(new ResourceLocation(JsonUtils.getString(json, "item")));
        } else {
            item = null;
        }
    }

    @Override
    public boolean test(ItemStack stack) {
        if (item != null && stack.getItem() != item) {
            return false;
        }
        if (!(stack.getItem() instanceof IModuleContainerItem)) {
            return false;
        }
        if (ModuleHelper.get().getSupported(stack).isEmpty()) {
            return false;
        }
        for (ModuleData<?> moduleData : ModuleHelper.get().getSupported(stack)) {
            Module<?> module = ModuleHelper.get().load(stack, moduleData);
            if (module == null || module.getInstalledCount() < moduleData.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }
}
