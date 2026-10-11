package mekanism.common.content.gear;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mekanism.api.NBTConstants;
import mekanism.api.annotations.NonNullSupplier;
import mekanism.api.gear.*;
import mekanism.api.gear.ModuleData.ModuleDataBuilder;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

public class ModuleHelper implements IModuleHelper {

    public static final ModuleHelper INSTANCE = new ModuleHelper();

    public static ModuleHelper get() {
        return INSTANCE;
    }

    private static final Map<String, ModuleData<?>> nameModuleLookup = new Object2ObjectOpenHashMap<>();
    private final Map<Item, Set<ModuleData<?>>> supportedModules = new Object2ObjectOpenHashMap<>();
    private final Map<ModuleData<?>, Set<Item>> supportedContainers = new Object2ObjectOpenHashMap<>();
    private final Map<ModuleData<?>, Set<ModuleData<?>>> conflictingModules = new IdentityHashMap<>();

    public static ModuleData<?> registerMarker(String name, UnaryOperator<ModuleData.ModuleDataBuilder<?>> builderModifier) {
        return register(name, builderModifier.apply(ModuleData.ModuleDataBuilder.marker()).name(name));
    }

    public <MODULE extends ICustomModule<MODULE>> ModuleData<MODULE> register(String name, NonNullSupplier<MODULE> supplier) {
        return register(name, supplier, UnaryOperator.identity());
    }

    public static <MODULE extends ICustomModule<MODULE>> ModuleData<MODULE> register(String name, NonNullSupplier<MODULE> supplier, UnaryOperator<ModuleDataBuilder<MODULE>> builderModifier) {
        return register(name, builderModifier.apply(ModuleDataBuilder.custom(supplier)).name(name));
    }

    public static ModuleData<?> registerEnchantBased(String name, NonNullSupplier<Enchantment> enchantment, UnaryOperator<ModuleDataBuilder<?>> builderModifier) {
        return register(name, builderModifier.apply(ModuleDataBuilder.custom(() -> new EnchantmentBasedModule() {
            @NotNull
            @Override
            public Enchantment getEnchantment() {
                return enchantment.get();
            }
        })).name(name));
    }

    public static <MODULE extends ICustomModule<MODULE>> ModuleData<MODULE> register(String name, ModuleDataBuilder<MODULE> builder) {
        ModuleData<MODULE> data = new ModuleData<>(builder);
        nameModuleLookup.put(name, data);
        return data;
    }

    public void setSupported(Item ontainerItem, ModuleData<?>... types) {
        for (ModuleData<?> module : types) {
            supportedModules.computeIfAbsent(ontainerItem, item -> new HashSet<>()).add(module);
        }
    }

    public ModuleData<?> getModuleTypeFromName(String name) {
        return nameModuleLookup.get(name);
    }


    public Collection<ModuleData<?>> getAll() {
        return nameModuleLookup.values();
    }

    public void processSupportedContainers() {
        for (Map.Entry<Item, Set<ModuleData<?>>> entry : supportedModules.entrySet()) {
            for (ModuleData<?> data : entry.getValue()) {
                supportedContainers.computeIfAbsent(data, d -> new HashSet<>()).add(entry.getKey());
            }
        }
    }

    /**
     * 关闭清除支持类型，因为我们是固定加载，不像高版本那样靠通信后通知服务器需要加载哪些，
     *
     * @author sddsd2332
     */
    /*
    public void resetSupportedContainers() {
        supportedContainers.clear();
    }
     */
    @Override
    public Set<ModuleData<?>> getSupported(ItemStack container) {
        return getSupported(container.getItem());
    }

    private Set<ModuleData<?>> getSupported(Item item) {
        return supportedModules.getOrDefault(item, Collections.emptySet());
    }

    @Override
    public Set<Item> getSupported(ModuleData<?> typeProvider) {
        return supportedContainers.getOrDefault(typeProvider.getModuleData(), Collections.emptySet());
    }

    @Override
    public Set<ModuleData<?>> getConflicting(ModuleData<?> typeProvider) {
        return conflictingModules.computeIfAbsent(typeProvider.getModuleData(), moduleType -> {
            Set<ModuleData<?>> conflicting = new ReferenceOpenHashSet<>();
            for (Item item : getSupported(moduleType)) {
                for (ModuleData<?> other : getSupported(item)) {
                    if (moduleType != other && moduleType.isExclusive(other.getExclusiveFlags())) {
                        conflicting.add(other);
                    }
                }
            }
            return conflicting;
        });
    }

    @Override
    public boolean isEnabled(ItemStack container, ModuleData<?> typeProvider) {
        IModule<?> m = load(container, typeProvider);
        return m != null && m.isEnabled();
    }

    @Nullable
    @Override
    public <MODULE extends ICustomModule<MODULE>> Module<MODULE> load(ItemStack container, ModuleData<MODULE> typeProvider) {
        if (container.getItem() instanceof IModuleContainerItem) {
            NBTTagCompound modulesTag = ItemDataUtils.getCompound(container, NBTConstants.MODULES);
            return load(container, typeProvider.getModuleData(), modulesTag, null);
        }
        return null;
    }

    @Override
    public List<Module<?>> loadAll(ItemStack container) {
        if (container.getItem() instanceof IModuleContainerItem) {
            List<Module<?>> modules = new ArrayList<>();
            NBTTagCompound modulesTag = ItemDataUtils.getCompound(container, NBTConstants.MODULES);
            for (ModuleData<?> moduleType : loadAllTypes(modulesTag)) {
                Module<?> module = load(container, moduleType, modulesTag, null);
                if (module != null) {
                    modules.add(module);
                }
            }
            return modules;
        }
        return Collections.emptyList();
    }

    @Override
    public <MODULE extends ICustomModule<?>> List<Module<? extends MODULE>> loadAll(ItemStack container, Class<MODULE> moduleClass) {
        if (container.getItem() instanceof IModuleContainerItem) {
            List<Module<? extends MODULE>> modules = new ArrayList<>();
            NBTTagCompound modulesTag = ItemDataUtils.getCompound(container, NBTConstants.MODULES);
            for (ModuleData<?> moduleType : loadAllTypes(modulesTag)) {
                Module<?> module = load(container, moduleType, modulesTag, moduleClass);
                if (module != null) {
                    modules.add((Module<? extends MODULE>) module);
                }
            }
            return modules;
        }
        return Collections.emptyList();
    }

    @Override
    public Collection<ModuleData<?>> loadAllTypes(ItemStack container) {
        if (container.getItem() instanceof IModuleContainerItem) {
            return loadAllTypes(ItemDataUtils.getCompound(container, NBTConstants.MODULES));
        }
        return Collections.emptyList();
    }

    private Set<ModuleData<?>> loadAllTypes(NBTTagCompound modulesTag) {
        Set<ModuleData<?>> moduleTypes = new HashSet<>();
        for (String name : modulesTag.getKeySet()) {
            ModuleData<?> moduleType = getModuleTypeFromName(name);
            if (moduleType != null) {
                moduleTypes.add(moduleType);
            }
        }
        return moduleTypes;
    }

    @Nullable
    private <MODULE extends ICustomModule<MODULE>> Module<MODULE> load(ItemStack container, ModuleData<MODULE> type, NBTTagCompound modulesTag, @Nullable Class<? extends ICustomModule<?>> typeFilter) {
        String name = type.getName();
        if (modulesTag.hasKey(name, Constants.NBT.TAG_COMPOUND)) {
            Module<MODULE> module = new Module<>(type, container);
            if (typeFilter == null || typeFilter.isInstance(module.getCustomInstance())) {
                module.read(modulesTag.getCompoundTag(name));
                return module;
            }
        }
        return null;
    }


    @Override
    public IHUDElement hudElement(ResourceLocation icon, String text, IHUDElement.HUDColor color) {
        return HUDElement.of(icon, text, HUDElement.HUDColor.from(color));
    }

    @Override
    public IHUDElement hudElementEnabled(ResourceLocation icon, boolean enabled) {
        return hudElement(icon, LangUtils.transOnOff(enabled), enabled ? IHUDElement.HUDColor.REGULAR : IHUDElement.HUDColor.FADED);
    }

    @Override
    public IHUDElement hudElementPercent(ResourceLocation icon, double ratio) {
        return hudElement(icon, TextUtils.getPercent(ratio), ratio > 0.2 ? IHUDElement.HUDColor.REGULAR : (ratio > 0.1 ? IHUDElement.HUDColor.WARNING : IHUDElement.HUDColor.DANGER));
    }
}
