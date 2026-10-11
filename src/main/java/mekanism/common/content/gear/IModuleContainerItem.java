package mekanism.common.content.gear;

import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.gear.*;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface IModuleContainerItem extends IItemHUDProvider {

    default List<Module<?>> getModules(ItemStack stack) {
        return ModuleHelper.get().loadAll(stack);
    }

    @Nullable
    default <MODULE extends ICustomModule<MODULE>> IModule<MODULE> getModule(ItemStack stack, ModuleData<MODULE> typeProvider) {
        return ModuleHelper.get().load(stack, typeProvider);
    }

    default boolean supportsModule(ItemStack stack, ModuleData<?> typeProvider) {
        return ModuleHelper.get().getSupported(stack).contains(typeProvider.getModuleData());
    }

    default void addModuleDetails(ItemStack stack, List<String> tooltip) {
        for (Module<?> module : getModules(stack)) {
            ModuleData<?> data = module.getData();
            if (module.getInstalledCount() > 1) {
                tooltip.add(EnumColor.GREY + LangUtils.localize(data.getTranslationKey()) + " " + "(" + module.getInstalledCount() + "/" + data.getMaxStackSize() + ")");
            } else {
                tooltip.add(EnumColor.GREY + LangUtils.localize(data.getTranslationKey()));
            }
        }
    }

    default boolean hasModule(ItemStack stack, ModuleData<?> type) {
        NBTTagCompound modules = ItemDataUtils.getCompound(stack, NBTConstants.MODULES);
        return modules.hasKey(type.getName().toString(), NBT.TAG_COMPOUND);
    }

    default boolean isModuleEnabled(ItemStack stack, ModuleData<?> type) {
        IModule<?> module = getModule(stack, type);
        return module != null && module.isEnabled();
    }

    default void removeModule(ItemStack stack, ModuleData<?> type) {
        Module<?> module = ModuleHelper.get().load(stack, type);
        if (module != null) {
            if (module.getInstalledCount() > 1) {
                module.setInstalledCount(module.getInstalledCount() - 1);
                module.save(null);
                module.onRemoved(false);
            } else {
                NBTTagCompound modules = ItemDataUtils.getCompound(stack, NBTConstants.MODULES);
                modules.removeTag(type.getName().toString());
                module.onRemoved(true);
            }
        }
    }

    default void addModule(ItemStack stack, ModuleData<?> type) {
        Module<?> module = ModuleHelper.get().load(stack, type);
        if (module == null) {
            ItemDataUtils.getOrAddCompound(stack, NBTConstants.MODULES).setTag(type.getName().toString(), new NBTTagCompound());
            ModuleHelper.get().load(stack, type).onAdded(true);
        } else {
            module.setInstalledCount(module.getInstalledCount() + 1);
            module.save(null);
            module.onAdded(false);
        }
    }

    default void setAllModule(ItemStack stack) {
        for (ModuleData<?> module : ModuleHelper.get().getAll()) {
            if (ModuleHelper.get().getSupported(stack).contains(module)) {
                setModule(stack, module);
            }
        }
    }


    default void setModule(ItemStack stack, ModuleData<?> type) {
        if (!ItemDataUtils.hasData(stack, NBTConstants.MODULES, NBT.TAG_COMPOUND)) {
            ItemDataUtils.setCompound(stack, NBTConstants.MODULES, new NBTTagCompound());
        }
        ItemDataUtils.getCompound(stack, NBTConstants.MODULES).setTag(type.getName(), new NBTTagCompound());
        ItemDataUtils.getCompound(stack, NBTConstants.MODULES).getCompoundTag(type.getName()).setInteger(NBTConstants.AMOUNT, type.getMaxStackSize());
        ModuleHelper.get().load(stack, type).save(null);
        ModuleHelper.get().load(stack, type).onAdded(false);

    }

    @Override
    default void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        for (Module<?> module : getModules(stack)) {
            if (module.renderHUD()) {
                module.addHUDStrings(player, list);
            }
        }
    }

    default List<IHUDElement> getHUDElements(EntityPlayer player, ItemStack stack) {
        List<IHUDElement> ret = new ArrayList<>();
        for (Module<?> module : getModules(stack)) {
            if (module.renderHUD()) {
                module.addHUDElements(player, ret);
            }
        }
        return ret;
    }

    static boolean hasOtherEnchants(ItemStack stack) {
        MatchedEnchants enchants = new MatchedEnchants(stack);
        IModuleContainerItem.forMatchingEnchants(stack, enchants, (e, module) -> e.matchedCount++);
        return enchants.enchantments == null || enchants.matchedCount < enchants.enchantments.size();
    }

    default void filterTooltips(ItemStack stack, List<String> tooltips) {
        List<String> enchantsToRemove = new ArrayList<>();
        IModuleContainerItem.forMatchingEnchants(stack, new MatchedEnchants(stack), (e, module) -> enchantsToRemove.add(module.getCustomInstance().getEnchantment().getTranslatedName(module.getInstalledCount())));
        tooltips.removeAll(enchantsToRemove);
    }

    static void forMatchingEnchants(ItemStack stack, MatchedEnchants enchants, BiConsumer<MatchedEnchants, IModule<? extends EnchantmentBasedModule<?>>> consumer) {
        for (IModule<? extends EnchantmentBasedModule> module : ModuleHelper.get().loadAll(stack, EnchantmentBasedModule.class)) {
            if (module.isEnabled() && enchants.getEnchantments().getOrDefault(module.getCustomInstance().getEnchantment(), 0) == module.getInstalledCount()) {
                consumer.accept(enchants, (IModule<? extends EnchantmentBasedModule<?>>) module);
            }
        }
    }

    class MatchedEnchants {

        private final ItemStack stack;
        private Map<Enchantment, Integer> enchantments;
        private int matchedCount;

        public MatchedEnchants(ItemStack stack) {
            this.stack = stack;
        }

        public Map<Enchantment, Integer> getEnchantments() {
            if (enchantments == null) {
                enchantments = EnchantmentHelper.getEnchantments(stack);
            }
            return enchantments;
        }
    }
}