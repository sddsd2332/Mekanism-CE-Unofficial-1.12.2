package mekanism.common;

import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum Upgrade {
    SPEED("speed", MekanismConfig.current().mekce.MAXSpeedUpgrade.val(), MekanismConfig.current().mekce.MAXSpeedUpgradeSize.val(), EnumColor.RED),
    ENERGY("energy", MekanismConfig.current().mekce.MAXEnergyUpgrade.val(), MekanismConfig.current().mekce.MAXEnergyUpgradeSize.val(), EnumColor.BRIGHT_GREEN),
    FILTER("filter", 1, 1, EnumColor.DARK_AQUA),
    GAS("gas", MekanismConfig.current().mekce.MAXGasUpgrade.val(), MekanismConfig.current().mekce.MAXGasUpgradeSize.val(), EnumColor.YELLOW),
    MUFFLING("muffling", MekanismConfig.current().mekce.MAXMufflingUpgrade.val(), MekanismConfig.current().mekce.MAXMufflingUpgradeSize.val(), EnumColor.DARK_GREY),
    ANCHOR("anchor", 1, 1, EnumColor.DARK_GREEN),
    STONE_GENERATOR("stonegenerator", 1, 1, EnumColor.ORANGE),
    THREAD("thread", MekanismConfig.current().mekce.MAXThreadUpgrade.val(), MekanismConfig.current().mekce.MAXThreadUpgradeSize.val(), EnumColor.ORANGE);

    private String name;
    private int maxInstalled;
    private int maxItemStackSize;
    private EnumColor color;

    Upgrade(String name, int maxInstalled, int maxItemStackSize, EnumColor c) {
        this.name = name;
        this.maxInstalled = maxInstalled;
        this.maxItemStackSize = maxItemStackSize;
        color = c;
    }

    public static Map<Upgrade, Integer> buildMap(@Nullable NBTTagCompound nbtTags) {
        Map<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);
        if (nbtTags != null) {
            if (nbtTags.hasKey(NBTConstants.UPGRADES)) {
                NBTTagList list = nbtTags.getTagList(NBTConstants.UPGRADES, NBT.TAG_COMPOUND);
                for (int tagCount = 0; tagCount < list.tagCount(); tagCount++) {
                    NBTTagCompound compound = list.getCompoundTagAt(tagCount);
                    Upgrade upgrade = MekanismUtils.getByIndex(Upgrade.values(), compound.getInteger(NBTConstants.TYPE), null);
                    if (upgrade != null) {
                        upgrades.put(upgrade, compound.getInteger(NBTConstants.AMOUNT));
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
        upgrades.forEach((key, value) -> list.appendTag(getTagFor(key, value)));
        nbtTags.setTag(NBTConstants.UPGRADES, list);
    }

    public static NBTTagCompound getTagFor(Upgrade upgrade, int amount) {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger(NBTConstants.TYPE, upgrade.ordinal());
        compound.setInteger(NBTConstants.AMOUNT, amount);
        return compound;
    }

    public String getName() {
        return LangUtils.localize("upgrade." + name);
    }

    public String getDescription() {
        return LangUtils.localize("upgrade." + name + ".desc");
    }

    public int getMaxItemStackSize() {
        return maxItemStackSize;
    }

    public int getMaxInstalled() {
        return maxInstalled;
    }

    public EnumColor getColor() {
        return color;
    }

    public boolean canMultiply() {
        return getMaxInstalled() > 1;
    }

    public ItemStack getStack() {
        return switch (this) {
            case SPEED -> new ItemStack(MekanismItems.SpeedUpgrade);
            case ENERGY -> new ItemStack(MekanismItems.EnergyUpgrade);
            case FILTER -> new ItemStack(MekanismItems.FilterUpgrade);
            case MUFFLING -> new ItemStack(MekanismItems.MufflingUpgrade);
            case GAS -> new ItemStack(MekanismItems.GasUpgrade);
            case ANCHOR -> new ItemStack(MekanismItems.AnchorUpgrade);
            case STONE_GENERATOR -> new ItemStack(MekanismItems.StoneGeneratorUpgrade);
            case THREAD -> new ItemStack(MekanismItems.ThreadUpgrade);
        };
    }

    public List<String> getInfo(TileEntity tile) {
        List<String> ret = new ArrayList<>();
        if (tile instanceof IUpgradeTile upgradeTile) {
            if (tile instanceof IUpgradeInfoHandler handler) {
                return handler.getInfo(this);
            } else {
                ret = getMultScaledInfo(upgradeTile);
            }
        }
        return ret;
    }

    public List<String> getMultScaledInfo(IUpgradeTile tile) {
        List<String> ret = new ArrayList<>();
        Upgrade upgrade = this;
        if (canMultiply()) {
            if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val()) {
                double effect = upgrade == Upgrade.ENERGY ? MekanismUtils.capacity(tile) : upgrade == Upgrade.SPEED ? MekanismUtils.time(tile) : Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), (float) tile.getComponent().getUpgrades(upgrade) / (float) getMaxInstalled());
                ret.add(LangUtils.localize("gui.upgrades.effect") + ": " + MekanismUtils.exponential(effect) + "x");
            } else {
                double effect = Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), (float) tile.getComponent().getUpgrades(this) / (float) getMaxInstalled());
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
                double effect = Math.min(Math.pow(2, (float) tile.getComponent().getUpgrades(this)), MekanismConfig.current().mekce.MAXspeedmachines.val());
                ret.add(LangUtils.localize("gui.upgrades.effect") + ": " + effect + "x");
            }
        }
        return ret;

    }

    public interface IUpgradeInfoHandler {

        List<String> getInfo(Upgrade upgrade);
    }
}
