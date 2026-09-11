package mekanism.client.gui.qio;

import mekanism.api.qio.client.QIOResourceSelectionAdapter;
import mekanism.api.qio.client.QIOResourceSelectionAdapterRegistry;
import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOFilterResourceHelper;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.content.qio.filter.QIOResourceFilter;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOResourceSelectionTest {

    private static final TestCodec FIRST = new TestCodec("selection_first");
    private static final TestCodec SECOND = new TestCodec("selection_second");
    private static final TestSelectionAdapter FIRST_ADAPTER =
          new TestSelectionAdapter(FIRST, "first");
    private static final TestSelectionAdapter SECOND_ADAPTER =
          new TestSelectionAdapter(SECOND, "second");

    @BeforeAll
    static void registerSelectionAdapters() {
        Bootstrap.register();
        QIOResourceCodecRegistry.INSTANCE.register(FIRST);
        QIOResourceCodecRegistry.INSTANCE.register(SECOND);
        QIOResourceSelectionAdapterRegistry.INSTANCE.register(FIRST_ADAPTER);
        QIOResourceSelectionAdapterRegistry.INSTANCE.register(SECOND_ADAPTER);
    }

    @Test
    void addonIngredientsRequireOneUnambiguousCodecOwner() {
        QIOResourceSelection.Resolution unique = QIOResourceSelection.fromIngredient(
              new SelectionIngredient(true, false, "alpha"));
        QIOResourceSelection.Resolution ambiguous = QIOResourceSelection.fromIngredient(
              new SelectionIngredient(true, true, "alpha"));

        assertTrue(unique.isUnique());
        assertEquals(FIRST.getCodecId(), unique.getDescriptor().getCodecId());
        assertFalse(ambiguous.isUnique());
        assertTrue(ambiguous.isAmbiguous());
        assertNull(ambiguous.getDescriptor());
        assertThrows(IllegalArgumentException.class, () ->
              QIOResourceSelectionAdapterRegistry.INSTANCE.register(FIRST_ADAPTER));
    }

    @Test
    void addonContainerCandidatesAlsoRejectAmbiguity() {
        ItemStack container = new ItemStack(Items.STICK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("first", true);
        container.setTagCompound(tag);

        QIOResourceSelection.Resolution unique = QIOResourceSelection.fromContainer(container);
        assertTrue(unique.isUnique());
        assertEquals(FIRST.getCodecId(), unique.getDescriptor().getCodecId());

        tag.setBoolean("second", true);
        container.setTagCompound(tag);
        QIOResourceSelection.Resolution ambiguous = QIOResourceSelection.fromContainer(container);
        assertFalse(ambiguous.isUnique());
        assertTrue(ambiguous.isAmbiguous());
    }

    @Test
    void itemIngredientsRemainItemsAndCustomDescriptorsCreateExactFilters() {
        QIOResourceSelection.Resolution item = QIOResourceSelection.fromIngredient(
              new ItemStack(Items.STICK));
        assertTrue(item.isUnique());
        assertEquals(QIOResourceCodecs.ITEM_STACK_ID, item.getDescriptor().getCodecId());
        assertTrue(QIOFilterResourceHelper.createFilter(item.getDescriptor()) instanceof
              QIOItemStackFilter);

        QIOResourceDescriptor custom = QIOResourceDescriptor.of(FIRST, "alpha");
        QIOFilter filter = QIOFilterResourceHelper.createFilter(custom);
        assertTrue(filter instanceof QIOResourceFilter);
        assertEquals(custom, QIOFilterResourceHelper.getDescriptor(filter));
    }

    @Test
    void oversizedAddonSelectionPayloadIsRejected() {
        String oversized = String.join("", Collections.nCopies(50_000, "x"));
        QIOResourceSelection.Resolution selection = QIOResourceSelection.fromIngredient(
              new SelectionIngredient(true, false, oversized));

        assertFalse(selection.isUnique());
        assertFalse(selection.isAmbiguous());
    }

    private static final class SelectionIngredient {

        private final boolean first;
        private final boolean second;
        private final String value;

        private SelectionIngredient(boolean first, boolean second, String value) {
            this.first = first;
            this.second = second;
            this.value = value;
        }
    }

    private static final class TestSelectionAdapter implements QIOResourceSelectionAdapter {

        private final TestCodec codec;
        private final String containerKey;

        private TestSelectionAdapter(TestCodec codec, String containerKey) {
            this.codec = codec;
            this.containerKey = containerKey;
        }

        @Nonnull
        @Override
        public ResourceLocation getCodecId() {
            return codec.getCodecId();
        }

        @Nullable
        @Override
        public QIOResourceDescriptor fromIngredient(@Nonnull Object ingredient) {
            if (!(ingredient instanceof SelectionIngredient)) {
                return null;
            }
            SelectionIngredient selection = (SelectionIngredient) ingredient;
            boolean selected = codec == FIRST ? selection.first : selection.second;
            return selected ? QIOResourceDescriptor.of(codec, selection.value) : null;
        }

        @Nonnull
        @Override
        public List<QIOResourceDescriptor> getContainedResources(@Nonnull ItemStack container) {
            NBTTagCompound tag = container.getTagCompound();
            return tag != null && tag.getBoolean(containerKey) ?
                  Collections.singletonList(QIOResourceDescriptor.of(codec, containerKey)) :
                  Collections.emptyList();
        }
    }

    private static final class TestCodec implements QIOResourceCodec<String> {

        private final ResourceLocation id;

        private TestCodec(String path) {
            id = new ResourceLocation("qio_test", path);
        }

        @Nonnull
        @Override
        public ResourceLocation getCodecId() {
            return id;
        }

        @Nonnull
        @Override
        public String getFamily() {
            return "qio_test.selection";
        }

        @Nonnull
        @Override
        public Class<String> getValueClass() {
            return String.class;
        }

        @Nonnull
        @Override
        public String normalize(@Nonnull String value) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Selection value cannot be empty");
            }
            return value;
        }

        @Override
        public boolean sameType(@Nonnull String first, @Nonnull String second) {
            return first.equals(second);
        }

        @Override
        public int typeHash(@Nonnull String value) {
            return value.hashCode();
        }

        @Nonnull
        @Override
        public NBTTagCompound writeTemplate(@Nonnull String value) {
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("value", normalize(value));
            return payload;
        }

        @Nonnull
        @Override
        public String readTemplate(@Nonnull NBTTagCompound payload, int codecVersion) {
            return normalize(payload.getString("value"));
        }

        @Override
        public long getStorageUnitsPerUnit() {
            return 1;
        }
    }

}
