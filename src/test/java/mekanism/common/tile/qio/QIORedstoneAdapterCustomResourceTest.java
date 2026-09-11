package mekanism.common.tile.qio;

import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.filter.QIOResourceFilter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIORedstoneAdapterCustomResourceTest {

    private static final TestCodec CODEC = new TestCodec();

    @BeforeAll
    static void registerCodec() {
        QIOResourceCodecRegistry.INSTANCE.register(CODEC);
    }

    @Test
    void exactCustomDescriptorIsCountedWithoutALegacyResourceKind() {
        QIOResourceDescriptor selected = QIOResourceDescriptor.of(CODEC, "selected");
        QIOResourceDescriptor other = QIOResourceDescriptor.of(CODEC, "other");
        TestFrequency frequency = new TestFrequency(Arrays.asList(
              QIOResourceEntry.of(UUID.randomUUID(), selected, QIOAmount.of(7)),
              QIOResourceEntry.of(UUID.randomUUID(), other, QIOAmount.of(11))));
        TestAdapter adapter = new TestAdapter(frequency);
        adapter.setFilter(new QIOResourceFilter(selected));
        adapter.setThreshold(7);

        assertEquals(7, adapter.getStoredCount());
        assertTrue(adapter.isPowering());
    }

    private static final class TestAdapter extends TileEntityQIORedstoneAdapter {

        private final QIOFrequency frequency;

        private TestAdapter(QIOFrequency frequency) {
            this.frequency = frequency;
        }

        @Override
        public QIOFrequency getQIOFrequency() {
            return frequency;
        }

        @Override
        public boolean isRemote() {
            return false;
        }
    }

    private static final class TestFrequency extends QIOFrequency {

        private final List<QIOResourceEntry> entries;

        private TestFrequency(List<QIOResourceEntry> entries) {
            this.entries = entries;
        }

        @Nonnull
        @Override
        public synchronized List<QIOResourceEntry> getResourceEntries() {
            return entries;
        }
    }

    private static final class TestCodec implements QIOResourceCodec<String> {

        private static final ResourceLocation ID = new ResourceLocation(
              "qio_test", "redstone_custom_resource");

        @Nonnull
        @Override
        public ResourceLocation getCodecId() {
            return ID;
        }

        @Nonnull
        @Override
        public String getFamily() {
            return "qio_test.redstone";
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
                throw new IllegalArgumentException("Redstone test resource cannot be empty");
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
