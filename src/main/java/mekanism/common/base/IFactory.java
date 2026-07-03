package mekanism.common.base;

import mekanism.common.Mekanism;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.util.LangUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Internal interface for managing various Factory types.
 *
 * @author AidanBrady
 */
public interface IFactory {

    /**
     * Gets the recipe type this Smelting Factory currently has.
     *
     * @param itemStack - stack to check
     * @return RecipeType ordinal
     */
    int getRecipeType(ItemStack itemStack);

    /**
     * Gets the recipe type this Factory currently has.
     *
     * @param itemStack - stack to check
     * @return RecipeType or null if it has invalid NBT
     */
    @Nullable
    RecipeType getRecipeTypeOrNull(ItemStack itemStack);

    /**
     * Sets the recipe type of this Smelting Factory to a new value.
     *
     * @param type      - RecipeType ordinal
     * @param itemStack - stack to set
     */
    void setRecipeType(int type, ItemStack itemStack);

    enum RecipeType implements IStringSerializable {
        SMELTING("Smelting", "smelter", MachineType.ENERGIZED_SMELTER),
        ENRICHING("Enriching", "enrichment", MachineType.ENRICHMENT_CHAMBER),
        CRUSHING("Crushing", "crusher", MachineType.CRUSHER),
        COMPRESSING("Compressing", "compressor", MachineType.OSMIUM_COMPRESSOR),
        COMBINING("Combining", "combiner", MachineType.COMBINER),
        PURIFYING("Purifying", "purifier", MachineType.PURIFICATION_CHAMBER),
        INJECTING("Injecting", "injection", MachineType.CHEMICAL_INJECTION_CHAMBER),
        INFUSING("Infusing", "metalinfuser", MachineType.METALLURGIC_INFUSER),
        SAWING("Sawing", "sawmill", MachineType.PRECISION_SAWMILL),
        STAMPING("Stamping", "stamping", MachineType.STAMPING),
        ROLLING("Rolling", "rolling", MachineType.ROLLING),
        BRUSHED("Brushed", "brushed", MachineType.BRUSHED),
        TURNING("Turning", "turning", MachineType.TURNING),
        AllOY("Alloy", "alloy", MachineType.ALLOY),
        EXTRACTOR("Extractor", "extractor", MachineType.CELL_EXTRACTOR),
        SEPARATOR("Separator", "separator", MachineType.CELL_SEPARATOR),
        FARM("Farm", "farm", MachineType.ORGANIC_FARM),
        RECYCLER("Recycler", "Recycler", MachineType.RECYCLER),
        PRC("PRC", "prc", MachineType.PRESSURIZED_REACTION_CHAMBER, false, false),
        NUCLEOSYNTHESIZER("Nucleosynthesizer", "nucleosynthesizer", MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, false, false);

        private String name;
        private SoundEvent sound;
        private MachineType type;
        public boolean isFullBlock;
        public boolean isOpaqueCube;


        RecipeType(String s, String s1, MachineType t) {
            this(s, s1, t, true, true);
        }

        RecipeType(String s, String s1, MachineType t, boolean fullBlock, boolean opaque) {
            name = s;
            sound = new SoundEvent(new ResourceLocation(Mekanism.MODID, "tile.machine." + s1));
            type = t;
            isFullBlock = fullBlock;
            isOpaqueCube = opaque;
        }

        public double getEnergyUsage() {
            return type.getUsage();
        }

        public double getEnergyStorage() {
            return type.getStorage();
        }

        public ItemStack getStack() {
            return type.getStack();
        }

        public String getTranslationKey() {
            return name;
        }

        public String getLocalizedName() {
            return LangUtils.localize("gui.factory." + name);
        }

        public SoundEvent getSound() {
            return sound;
        }

        @Override
        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public MachineType getType() {
            return type;
        }

    }
}
