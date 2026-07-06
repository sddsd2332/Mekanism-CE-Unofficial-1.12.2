package mekanism.common;

import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.common.base.IUpgradeItem;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Upgrade {

    private static final Map<ResourceLocation, Upgrade> REGISTRY = new LinkedHashMap<>();
    private static final List<Upgrade> REGISTERED_UPGRADES = new ArrayList<>();
    private static final Map<ResourceLocation, Upgrade> REGISTRY_VIEW = Collections.unmodifiableMap(REGISTRY);
    private static final List<Upgrade> REGISTERED_UPGRADES_VIEW = Collections.unmodifiableList(REGISTERED_UPGRADES);
    private static final InfoProvider DEFAULT_INFO_PROVIDER = (upgrade, tile) -> upgrade.getMultScaledInfo(tile);
    private static final ChangeHandler NOOP_CHANGE_HANDLER = (upgrade, tile, previousAmount, amount) -> {
    };

    public static final Upgrade SPEED = builder("speed")
            .maxInstalled(() -> MekanismConfig.current().mekce.MAXSpeedUpgrade.val())
            .maxItemStackSize(() -> MekanismConfig.current().mekce.MAXSpeedUpgradeSize.val())
            .color(EnumColor.RED)
            .stack(count -> new ItemStack(MekanismItems.SpeedUpgrade, count))
            .register();
    public static final Upgrade ENERGY = builder("energy")
            .maxInstalled(() -> MekanismConfig.current().mekce.MAXEnergyUpgrade.val())
            .maxItemStackSize(() -> MekanismConfig.current().mekce.MAXEnergyUpgradeSize.val())
            .color(EnumColor.BRIGHT_GREEN)
            .stack(count -> new ItemStack(MekanismItems.EnergyUpgrade, count))
            .register();
    public static final Upgrade FILTER = builder("filter")
            .maxInstalled(1)
            .maxItemStackSize(1)
            .color(EnumColor.DARK_AQUA)
            .stack(count -> new ItemStack(MekanismItems.FilterUpgrade, count))
            .register();
    public static final Upgrade GAS = builder("gas")
            .maxInstalled(() -> MekanismConfig.current().mekce.MAXGasUpgrade.val())
            .maxItemStackSize(() -> MekanismConfig.current().mekce.MAXGasUpgradeSize.val())
            .color(EnumColor.YELLOW)
            .stack(count -> new ItemStack(MekanismItems.GasUpgrade, count))
            .register();
    public static final Upgrade MUFFLING = builder("muffling")
            .maxInstalled(() -> MekanismConfig.current().mekce.MAXMufflingUpgrade.val())
            .maxItemStackSize(() -> MekanismConfig.current().mekce.MAXMufflingUpgradeSize.val())
            .color(EnumColor.DARK_GREY)
            .stack(count -> new ItemStack(MekanismItems.MufflingUpgrade, count))
            .register();
    public static final Upgrade ANCHOR = builder("anchor")
            .maxInstalled(1)
            .maxItemStackSize(1)
            .color(EnumColor.DARK_GREEN)
            .stack(count -> new ItemStack(MekanismItems.AnchorUpgrade, count))
            .register();
    public static final Upgrade STONE_GENERATOR = builder("stonegenerator")
            .maxInstalled(1)
            .maxItemStackSize(1)
            .color(EnumColor.ORANGE)
            .stack(count -> new ItemStack(MekanismItems.StoneGeneratorUpgrade, count))
            .register();
    private final ResourceLocation registryName;
    private final String translationKey;
    private final IntSupplier maxInstalled;
    private final IntSupplier maxItemStackSize;
    private final EnumColor color;
    private final IntFunction<ItemStack> stackCreator;
    private final InfoProvider infoProvider;
    private final ChangeHandler changeHandler;

    private Upgrade(Builder builder) {
        registryName = builder.registryName;
        translationKey = builder.translationKey;
        maxInstalled = builder.maxInstalled;
        maxItemStackSize = builder.maxItemStackSize;
        color = builder.color;
        stackCreator = builder.stackCreator;
        infoProvider = builder.infoProvider;
        changeHandler = builder.changeHandler;
    }

    public static Builder builder(String name) {
        return builder(Mekanism.MODID, name);
    }

    public static Builder builder(String modid, String name) {
        return builder(new ResourceLocation(modid, name), name);
    }

    public static Builder builder(ResourceLocation registryName) {
        return builder(registryName, registryName.getNamespace() + "." + registryName.getPath().replace('/', '.'));
    }

    public static Builder builder(ResourceLocation registryName, String translationKey) {
        return new Builder(registryName, translationKey);
    }

    public static Upgrade register(Builder builder) {
        Upgrade upgrade = new Upgrade(Objects.requireNonNull(builder, "Upgrade builder cannot be null"));
        Upgrade previous = REGISTRY.putIfAbsent(upgrade.registryName, upgrade);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate upgrade registered: " + upgrade.registryName);
        }
        REGISTERED_UPGRADES.add(upgrade);
        return upgrade;
    }

    @Nullable
    public static Upgrade byName(@Nullable ResourceLocation registryName) {
        return registryName == null ? null : REGISTRY.get(registryName);
    }

    @Nullable
    public static Upgrade byName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            ResourceLocation registryName = name.indexOf(':') == -1 ? new ResourceLocation(Mekanism.MODID, name) : new ResourceLocation(name);
            return byName(registryName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static List<Upgrade> getRegisteredUpgrades() {
        return REGISTERED_UPGRADES_VIEW;
    }

    public static Map<ResourceLocation, Upgrade> getRegistry() {
        return REGISTRY_VIEW;
    }

    public static boolean isRegistered(@Nullable Upgrade upgrade) {
        return upgrade != null && REGISTRY.get(upgrade.registryName) == upgrade;
    }

    @Nullable
    public static Upgrade byStack(@Nonnull ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IUpgradeItem upgradeItem) {
            Upgrade upgrade = upgradeItem.getUpgradeType(stack);
            return isRegistered(upgrade) ? upgrade : null;
        }
        return null;
    }

    public static boolean isUpgrade(@Nonnull ItemStack stack) {
        return byStack(stack) != null;
    }

    public static Map<Upgrade, Integer> buildMap(@Nullable NBTTagCompound nbtTags) {
        Map<Upgrade, Integer> upgrades = new LinkedHashMap<>();
        if (nbtTags != null && nbtTags.hasKey(NBTConstants.UPGRADES)) {
            NBTTagList list = nbtTags.getTagList(NBTConstants.UPGRADES, NBT.TAG_COMPOUND);
            for (int tagCount = 0; tagCount < list.tagCount(); tagCount++) {
                NBTTagCompound compound = list.getCompoundTagAt(tagCount);
                Upgrade upgrade = byName(compound.getString(NBTConstants.TYPE));
                if (upgrade != null) {
                    int amount = Math.min(compound.getInteger(NBTConstants.AMOUNT), upgrade.getMaxInstalled());
                    if (amount > 0) {
                        upgrades.compute(upgrade, (key, existing) -> (int) Math.min(upgrade.getMaxInstalled(), amount + (long) (existing == null ? 0 : existing)));
                    }
                }
            }
        }
        return upgrades;
    }

    public static boolean hasUpgradeData(@Nullable NBTTagCompound nbtTags) {
        return nbtTags != null && nbtTags.hasKey(NBTConstants.COMPONENT_UPGRADE, NBT.TAG_COMPOUND) &&
                nbtTags.getCompoundTag(NBTConstants.COMPONENT_UPGRADE).hasKey(NBTConstants.UPGRADES, NBT.TAG_LIST);
    }

    public static Map<Upgrade, Integer> buildComponentMap(@Nullable NBTTagCompound nbtTags) {
        return buildMap(nbtTags == null ? null : nbtTags.getCompoundTag(NBTConstants.COMPONENT_UPGRADE));
    }

    public static void saveComponentMap(Map<Upgrade, Integer> upgrades, NBTTagCompound nbtTags) {
        NBTTagCompound upgradeNBT = nbtTags.getCompoundTag(NBTConstants.COMPONENT_UPGRADE);
        saveMap(upgrades, upgradeNBT);
        nbtTags.setTag(NBTConstants.COMPONENT_UPGRADE, upgradeNBT);
    }

    public static void saveMap(Map<Upgrade, Integer> upgrades, NBTTagCompound nbtTags) {
        NBTTagList list = new NBTTagList();
        upgrades.forEach((key, value) -> {
            if (key != null && value != null) {
                int amount = Math.min(value, key.getMaxInstalled());
                if (amount > 0) {
                    list.appendTag(getTagFor(key, amount));
                }
            }
        });
        nbtTags.setTag(NBTConstants.UPGRADES, list);
    }

    public static NBTTagCompound getTagFor(Upgrade upgrade, int amount) {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString(NBTConstants.TYPE, upgrade.getRegistryNameString());
        compound.setInteger(NBTConstants.AMOUNT, Math.max(0, Math.min(amount, upgrade.getMaxInstalled())));
        return compound;
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public String getRegistryNameString() {
        return registryName.toString();
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getNameTranslationKey() {
        return "upgrade." + translationKey;
    }

    public String getDescriptionTranslationKey() {
        return getNameTranslationKey() + ".desc";
    }

    public String getName() {
        return LangUtils.localize(getNameTranslationKey());
    }

    public String getDescription() {
        return LangUtils.localize(getDescriptionTranslationKey());
    }

    public int getMaxItemStackSize() {
        return Math.max(1, Math.min(64, maxItemStackSize.getAsInt()));
    }

    public int getMaxInstalled() {
        return Math.max(0, maxInstalled.getAsInt());
    }

    public EnumColor getColor() {
        return color;
    }

    public boolean canMultiply() {
        return getMaxInstalled() > 1;
    }

    public boolean isInstallable() {
        return getMaxInstalled() > 0;
    }

    public int getInstalled(IUpgradeTile tile) {
        return tile == null ? 0 : tile.getInstalledUpgrades(this);
    }

    public double getInstalledFraction(IUpgradeTile tile) {
        int maxInstalled = getMaxInstalled();
        return maxInstalled <= 0 ? 0 : getInstalled(tile) / (double) maxInstalled;
    }

    public boolean isSupportedBy(@Nullable IUpgradeTile tile) {
        return tile != null && tile.supportsUpgrade(this);
    }

    public boolean isInstalledIn(@Nullable IUpgradeTile tile) {
        return tile != null && tile.isUpgradeInstalled(this);
    }

    public ItemStack getStack() {
        return getStack(1);
    }

    public ItemStack getStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = stackCreator.apply(count);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public List<String> getInfo(TileEntity tile) {
        if (tile instanceof IUpgradeTile upgradeTile) {
            if (tile instanceof IUpgradeInfoHandler handler) {
                List<String> info = handler.getInfo(this);
                return info == null ? Collections.emptyList() : info;
            }
            return getInfo(upgradeTile);
        }
        return Collections.emptyList();
    }

    public List<String> getInfo(IUpgradeTile tile) {
        List<String> info = infoProvider.getInfo(this, tile);
        return info == null ? Collections.emptyList() : info;
    }

    public void onChanged(TileEntityContainerBlock tile, int previousAmount, int amount) {
        changeHandler.onChanged(this, tile, previousAmount, amount);
    }

    public List<String> getMultScaledInfo(IUpgradeTile tile) {
        List<String> ret = new ArrayList<>();
        if (canMultiply()) {
            if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val()) {
                double effect = this == ENERGY ? MekanismUtils.capacity(tile) : this == SPEED ? MekanismUtils.time(tile) :
                        Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), MekanismUtils.fractionUpgrades(tile, this));
                ret.add(LangUtils.localize("gui.upgrades.effect") + ": " + MekanismUtils.exponential(effect) + "x");
            } else {
                double effect = Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), MekanismUtils.fractionUpgrades(tile, this));
                ret.add(LangUtils.localize("gui.upgrades.effect") + ": " + (Math.round(effect * 100) / 100F) + "x");
            }
        }
        return ret;
    }

    public List<String> getExpScaledInfo(IUpgradeTile tile) {
        List<String> ret = new ArrayList<>();
        if (canMultiply()) {
            if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val()) {
                ret.add(LangUtils.localize("gui.upgrades.effect") + ": " + MekanismUtils.time(tile) + "x");
            } else {
                double effect = Math.min(Math.pow(2, (float) tile.getInstalledUpgrades(this)), MekanismConfig.current().mekce.MAXspeedmachines.val());
                ret.add(LangUtils.localize("gui.upgrades.effect") + ": " + effect + "x");
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        return getRegistryNameString();
    }

    public static class Builder {

        private final ResourceLocation registryName;
        private final String translationKey;
        private IntSupplier maxInstalled = () -> 1;
        private IntSupplier maxItemStackSize = () -> 1;
        private EnumColor color = EnumColor.GREY;
        private IntFunction<ItemStack> stackCreator = count -> ItemStack.EMPTY;
        private InfoProvider infoProvider = DEFAULT_INFO_PROVIDER;
        private ChangeHandler changeHandler = NOOP_CHANGE_HANDLER;

        private Builder(ResourceLocation registryName, String translationKey) {
            this.registryName = Objects.requireNonNull(registryName, "Upgrade registry name cannot be null");
            this.translationKey = Objects.requireNonNull(translationKey, "Upgrade translation key cannot be null");
        }

        public Builder maxInstalled(int maxInstalled) {
            return maxInstalled(() -> maxInstalled);
        }

        public Builder maxInstalled(@Nonnull IntSupplier maxInstalled) {
            this.maxInstalled = Objects.requireNonNull(maxInstalled, "Max installed supplier cannot be null");
            return this;
        }

        public Builder maxItemStackSize(int maxItemStackSize) {
            return maxItemStackSize(() -> maxItemStackSize);
        }

        public Builder maxItemStackSize(@Nonnull IntSupplier maxItemStackSize) {
            this.maxItemStackSize = Objects.requireNonNull(maxItemStackSize, "Max stack size supplier cannot be null");
            return this;
        }

        public Builder color(@Nonnull EnumColor color) {
            this.color = Objects.requireNonNull(color, "Upgrade color cannot be null");
            return this;
        }

        public Builder stack(@Nonnull IntFunction<ItemStack> stackCreator) {
            this.stackCreator = Objects.requireNonNull(stackCreator, "Upgrade stack creator cannot be null");
            return this;
        }

        public Builder stack(@Nonnull Item item) {
            Objects.requireNonNull(item, "Upgrade item cannot be null");
            return stack(count -> new ItemStack(item, count));
        }

        public Builder stack(@Nonnull Supplier<? extends Item> itemSupplier) {
            Objects.requireNonNull(itemSupplier, "Upgrade item supplier cannot be null");
            return stack(count -> {
                Item item = itemSupplier.get();
                return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
            });
        }

        public Builder info(@Nonnull InfoProvider infoProvider) {
            this.infoProvider = Objects.requireNonNull(infoProvider, "Upgrade info provider cannot be null");
            return this;
        }

        public Builder onChanged(@Nonnull ChangeHandler changeHandler) {
            this.changeHandler = Objects.requireNonNull(changeHandler, "Upgrade change handler cannot be null");
            return this;
        }

        public Upgrade register() {
            return Upgrade.register(this);
        }
    }

    @FunctionalInterface
    public interface InfoProvider {

        List<String> getInfo(Upgrade upgrade, IUpgradeTile tile);
    }

    @FunctionalInterface
    public interface ChangeHandler {

        void onChanged(Upgrade upgrade, TileEntityContainerBlock tile, int previousAmount, int amount);
    }

    public interface IUpgradeInfoHandler {

        List<String> getInfo(Upgrade upgrade);
    }
}
