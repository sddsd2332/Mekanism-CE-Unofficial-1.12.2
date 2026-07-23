package mekanism.common.base;

import mekanism.common.base.IFactory.RecipeType;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryRecipeTypeCodecTest {

    @Test
    void everyV10TypeRoundTripsByStableName() {
        for (RecipeType type : RecipeType.values()) {
            NBTTagCompound nbt = new NBTTagCompound();
            FactoryRecipeTypeCodec.write(nbt, type);

            assertEquals(type, FactoryRecipeTypeCodec.read(nbt));
            assertEquals(type.getName(), nbt.getString(FactoryRecipeTypeCodec.NAME_KEY));
            assertEquals(FactoryRecipeTypeCodec.FORMAT_VERSION, nbt.getInteger(FactoryRecipeTypeCodec.VERSION_KEY));
        }
    }

    @Test
    void stableNameTakesPrecedenceOverOrdinal() {
        NBTTagCompound nbt = new NBTTagCompound();
        FactoryRecipeTypeCodec.write(nbt, RecipeType.PRC);
        nbt.setInteger(FactoryRecipeTypeCodec.ORDINAL_KEY, RecipeType.SMELTING.ordinal());

        assertEquals(RecipeType.PRC, FactoryRecipeTypeCodec.read(nbt));
    }

    @Test
    void legacyV10OrdinalsRemainReadable() {
        NBTTagCompound prc = new NBTTagCompound();
        FactoryRecipeTypeCodec.writeLegacyOrdinal(prc, 18);
        NBTTagCompound nucleosynthesizer = new NBTTagCompound();
        FactoryRecipeTypeCodec.writeLegacyOrdinal(nucleosynthesizer, 19);

        assertEquals(RecipeType.PRC, FactoryRecipeTypeCodec.read(prc));
        assertEquals(RecipeType.NUCLEOSYNTHESIZER, FactoryRecipeTypeCodec.read(nucleosynthesizer));
    }

    @Test
    void removedV9OrdinalsAreNotMigrated() {
        NBTTagCompound oldPrc = new NBTTagCompound();
        FactoryRecipeTypeCodec.writeLegacyOrdinal(oldPrc, 20);
        NBTTagCompound oldNucleosynthesizer = new NBTTagCompound();
        FactoryRecipeTypeCodec.writeLegacyOrdinal(oldNucleosynthesizer, 22);

        assertNull(FactoryRecipeTypeCodec.read(oldPrc));
        assertNull(FactoryRecipeTypeCodec.read(oldNucleosynthesizer));
    }

    @Test
    void unknownStableNameDoesNotFallBackToPotentiallyWrongOrdinal() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(FactoryRecipeTypeCodec.NAME_KEY, "future_factory");
        nbt.setInteger(FactoryRecipeTypeCodec.ORDINAL_KEY, RecipeType.SMELTING.ordinal());

        assertTrue(FactoryRecipeTypeCodec.hasRecipeType(nbt));
        assertNull(FactoryRecipeTypeCodec.read(nbt));
    }
}
