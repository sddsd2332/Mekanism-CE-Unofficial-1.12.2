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
        SMELTING(0, "Smelting", "smelter", MachineType.ENERGIZED_SMELTER),
        ENRICHING(1, "Enriching", "enrichment", MachineType.ENRICHMENT_CHAMBER),
        CRUSHING(2, "Crushing", "crusher", MachineType.CRUSHER),
        COMPRESSING(3, "Compressing", "compressor", MachineType.OSMIUM_COMPRESSOR),
        COMBINING(4, "Combining", "combiner", MachineType.COMBINER),
        PURIFYING(5, "Purifying", "purifier", MachineType.PURIFICATION_CHAMBER),
        INJECTING(6, "Injecting", "injection", MachineType.CHEMICAL_INJECTION_CHAMBER),
        INFUSING(7, "Infusing", "metalinfuser", MachineType.METALLURGIC_INFUSER),
        SAWING(8, "Sawing", "sawmill", MachineType.PRECISION_SAWMILL),
        STAMPING(9, "Stamping", "stamping", MachineType.STAMPING),
        ROLLING(10, "Rolling", "rolling", MachineType.ROLLING),
        BRUSHED(11, "Brushed", "brushed", MachineType.BRUSHED),
        TURNING(12, "Turning", "turning", MachineType.TURNING),
        AllOY(13, "Alloy", "alloy", MachineType.ALLOY),
        EXTRACTOR(14, "Extractor", "extractor", MachineType.CELL_EXTRACTOR),
        SEPARATOR(15, "Separator", "separator", MachineType.CELL_SEPARATOR),
        RECYCLER(17, "Recycler", "Recycler", MachineType.RECYCLER),
        PRC(18, "PRC", "prc", MachineType.PRESSURIZED_REACTION_CHAMBER, false, false),
        NUCLEOSYNTHESIZER(19, "Nucleosynthesizer", "nucleosynthesizer", MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, false, false);

        private final int persistedId;
        private String name;
        private SoundEvent sound;
        private MachineType type;
        public boolean isFullBlock;
        public boolean isOpaqueCube;


        RecipeType(int persistedId, String s, String s1, MachineType t) {
            this(persistedId, s, s1, t, true, true);
        }

        RecipeType(int persistedId, String s, String s1, MachineType t, boolean fullBlock, boolean opaque) {
            this.persistedId = persistedId;
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

        public int getPersistedId() {
            return persistedId;
        }

        @Nullable
        public static RecipeType byPersistedId(int persistedId) {
            for (RecipeType type : values()) {
                if (type.persistedId == persistedId) {
                    return type;
                }
            }
            return null;
        }

    }
}
