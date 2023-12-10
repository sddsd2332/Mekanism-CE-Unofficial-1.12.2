package mekanism.api;

import mekanism.api.math.MathUtils;
import mekanism.api.text.APILang;
import mekanism.api.text.EnumColor;
import mekanism.api.text.IHasTranslationKey;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants.*;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum Upgrade implements IHasTranslationKey {
    SPEED("speed", APILang.UPGRADE_SPEED, APILang.UPGRADE_SPEED_DESCRIPTION, 8, mekanism.api.text.EnumColor.RED),
    ENERGY("energy", APILang.UPGRADE_ENERGY, APILang.UPGRADE_ENERGY_DESCRIPTION, 8, mekanism.api.text.EnumColor.BRIGHT_GREEN),
    FILTER("filter", APILang.UPGRADE_FILTER, APILang.UPGRADE_FILTER_DESCRIPTION, 1, mekanism.api.text.EnumColor.DARK_AQUA),
    GAS("gas", APILang.UPGRADE_GAS, APILang.UPGRADE_GAS_DESCRIPTION, 8, mekanism.api.text.EnumColor.YELLOW),
    MUFFLING("muffling", APILang.UPGRADE_MUFFLING, APILang.UPGRADE_MUFFLING_DESCRIPTION, 4, mekanism.api.text.EnumColor.INDIGO),
    ANCHOR("anchor", APILang.UPGRADE_ANCHOR, APILang.UPGRADE_ANCHOR_DESCRIPTION, 1, mekanism.api.text.EnumColor.DARK_GREEN),
    STONE_GENERATOR("stone_generator", APILang.UPGRADE_STONE_GENERATOR, APILang.UPGRADE_STONE_GENERATOR_DESCRIPTION, 1, EnumColor.ORANGE);
    private static final Upgrade[] UPGRADES = values();


    private final String name;
    private final APILang langKey;
    private final APILang descLangKey;
    private final int maxStack;
    private final EnumColor color;



    Upgrade(String name, APILang langKey, APILang descLangKey, int maxStack, EnumColor color) {
        this.name = name;
        this.langKey = langKey;
        this.descLangKey = descLangKey;
        this.maxStack = maxStack;
        this.color = color;
    }

    /**
     * Reads and builds a map of upgrades to their amounts from NBT.
     *
     * @param nbtTags Stored upgrades.
     *
     * @return Installed upgrade map.
     *
     * @implNote Unmodifiable if empty.
     */
    public static Map<Upgrade, Integer> buildMap(@Nullable NBTTagCompound nbtTags) {
        Map<Upgrade, Integer> upgrades = null;
        if (nbtTags != null && nbtTags.hasKey(NBTConstants.UPGRADES)) {
            NBTTagList list = nbtTags.getTagList(NBTConstants.UPGRADES, NBT.TAG_COMPOUND);
            for (int tagCount = 0; tagCount < list.tagCount(); tagCount++) {
                NBTTagCompound compound = list.getCompoundTagAt(tagCount);
                Upgrade upgrade = byIndexStatic(compound.getInteger(NBTConstants.TYPE));
                //Validate the nbt isn't malformed with a negative or zero amount
                int installed = MathHelper.clamp(compound.getInteger(NBTConstants.AMOUNT), 0, upgrade.maxStack);
                if (installed > 0) {
                    if (upgrades == null) {
                        upgrades = new EnumMap<>(Upgrade.class);
                    }
                    upgrades.put(upgrade, installed);
                }
            }
        }
        return  upgrades == null ? Collections.emptyMap() : upgrades;
    }

    public static void saveMap(Map<Upgrade, Integer> upgrades, NBTTagCompound nbtTags) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Upgrade, Integer> entry : upgrades.entrySet()) {
            list.appendTag(entry.getKey().getTag(entry.getValue()));
        }
        nbtTags.setTag(NBTConstants.UPGRADES, list);
    }

    public NBTTagCompound getTag(int amount) {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger(NBTConstants.TYPE, ordinal());
        compound.setInteger(NBTConstants.AMOUNT, amount);
        return compound;
    }


    /**
     * Gets the "raw" name of this upgrade for use in registry names.
     */
    public String getRawName() {
        return name;
    }

    @Override
    public String getTranslationKey() {
        return langKey.getTranslationKey();
    }

    public String getDescription() {
        return descLangKey.translate();
    }

    /**
     * Gets the max number of upgrades of this type that can be installed.
     */
    public int getMax() {
        return maxStack;
    }

    /**
     * Gets the color to use when rendering various information related to this upgrade.
     */
    public EnumColor getColor() {
        return color;
    }

    /**
     * Gets an upgrade by index.
     *
     * @param index Index of the upgrade.
     */
    public static Upgrade byIndexStatic(int index) {
        return MathUtils.getByIndexMod(UPGRADES, index);
    }

    public interface IUpgradeInfoHandler {

        List<String> getInfo(Upgrade upgrade);
    }
}
