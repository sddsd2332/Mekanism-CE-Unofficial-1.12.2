package mekanism.common.content.qio;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOFluidFilter;
import mekanism.common.content.qio.filter.QIOGasFilter;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.content.qio.filter.QIOModIDFilter;
import mekanism.common.content.qio.filter.QIOOreDictFilter;
import mekanism.common.tile.qio.TileEntityQIOImporter;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOFilterTest {

    private static Fluid fluid;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        fluid = new Fluid("qio_filter_test_fluid", new net.minecraft.util.ResourceLocation("minecraft", "blocks/water"),
              new net.minecraft.util.ResourceLocation("minecraft", "blocks/water"));
        FluidRegistry.registerFluid(fluid);
    }

    @Test
    void filtersRoundTripAndRemainKindAware() {
        ItemStack item = new ItemStack(Item.getItemFromBlock(Blocks.STONE));
        QIOItemStackFilter itemFilter = new QIOItemStackFilter(item);
        assertTrue(itemFilter.test(item));
        assertTrue(QIOFilter.read(itemFilter.write()).test(item));

        FluidStack fluidStack = new FluidStack(fluid, 100);
        QIOFluidFilter fluidFilter = new QIOFluidFilter(fluidStack);
        assertTrue(fluidFilter.test(fluidStack));
        assertNotNull(QIOFilter.read(fluidFilter.write()));

        Gas gas = new Gas("qio_filter_test_gas", 0x123456);
        GasStack gasStack = new GasStack(gas, 100);
        QIOGasFilter gasFilter = new QIOGasFilter(gasStack);
        assertTrue(gasFilter.test(gasStack));
        assertTrue(!gasFilter.test(fluidStack));
    }

    @Test
    void filterCollectionsAreDeduplicatedAndDefensivelyCopied() {
        ItemStack item = new ItemStack(Item.getItemFromBlock(Blocks.STONE));
        QIOItemStackFilter first = new QIOItemStackFilter(item);
        QIOItemStackFilter duplicate = new QIOItemStackFilter(item);
        duplicate.setEnabled(false);
        TileEntityQIOImporter importer = new TileEntityQIOImporter();

        importer.setFilters(Arrays.asList(first, duplicate));
        assertEquals(1, importer.getFilters().size());
        first.setEnabled(false);
        assertTrue(importer.getFilters().get(0).isEnabled());

        QIOFilter returned = importer.getFilters().get(0);
        returned.setEnabled(false);
        assertTrue(importer.getFilters().get(0).isEnabled());
    }

    @Test
    void itemFiltersIgnoreQuantityButPreserveNbtIdentity() {
        ItemStack template = new ItemStack(Item.getItemFromBlock(Blocks.STONE), 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", "a");
        template.setTagCompound(tag);
        QIOItemStackFilter filter = new QIOItemStackFilter(template);

        ItemStack matching = template.copy();
        matching.setCount(48);
        assertTrue(filter.test(matching));

        ItemStack different = matching.copy();
        different.getTagCompound().setString("variant", "b");
        assertTrue(!filter.test(different));

        TestImporter importer = new TestImporter();
        importer.setLegacyFilter(template);
        assertTrue(importer.matchesItem(matching));
        assertTrue(!importer.matchesItem(different));
    }

    @Test
    void invalidOrEmptyFilterPayloadsAreRejected() {
        NBTTagCompound emptyItem = new NBTTagCompound();
        emptyItem.setInteger("version", 1);
        emptyItem.setString("type", QIOItemStackFilter.TYPE);
        emptyItem.setTag("payload", new NBTTagCompound());
        assertNull(QIOFilter.read(emptyItem));

        NBTTagCompound unknown = emptyItem.copy();
        unknown.setString("type", "unknown");
        assertNull(QIOFilter.read(unknown));
    }

    @Test
    void itemFuzzyModeRoundTripsAndIgnoresMetadataAndNbt() {
        ItemStack template = new ItemStack(Item.getItemFromBlock(Blocks.STONE), 1, 0);
        NBTTagCompound templateTag = new NBTTagCompound();
        templateTag.setString("variant", "a");
        template.setTagCompound(templateTag);
        QIOItemStackFilter filter = new QIOItemStackFilter(template);
        filter.setFuzzyMode(true);

        ItemStack different = new ItemStack(template.getItem(), 32, 1);
        NBTTagCompound differentTag = new NBTTagCompound();
        differentTag.setString("variant", "b");
        different.setTagCompound(differentTag);
        assertTrue(filter.test(different));

        QIOFilter decoded = QIOFilter.read(filter.write());
        assertTrue(decoded instanceof QIOItemStackFilter);
        assertTrue(((QIOItemStackFilter) decoded).isFuzzyMode());
        assertTrue(decoded.test(different));
    }

    @Test
    void textItemFiltersRoundTripAndMatchTheirConfiguredDomain() {
        ItemStack stone = new ItemStack(Item.getItemFromBlock(Blocks.STONE));

        QIOOreDictFilter oreFilter = new QIOOreDictFilter("*");
        assertTrue(oreFilter.test(stone));
        assertTrue(QIOFilter.read(oreFilter.write()) instanceof QIOOreDictFilter);

        QIOModIDFilter modFilter = new QIOModIDFilter("minecraft");
        assertTrue(modFilter.test(stone));
        assertTrue(QIOFilter.read(modFilter.write()) instanceof QIOModIDFilter);

        QIOModIDFilter wildcardModFilter = new QIOModIDFilter("mine*");
        assertTrue(wildcardModFilter.test(stone));
        assertEquals("mine*", wildcardModFilter.getModID());
    }

    private static final class TestImporter extends TileEntityQIOImporter {

        private void setLegacyFilter(ItemStack stack) {
            filterSlot.setStackUnchecked(stack.copy());
        }
    }
}
