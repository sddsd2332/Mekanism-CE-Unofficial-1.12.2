package mekanism.common.tile.qio;

import mekanism.api.Action;
import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.api.qio.resource.QIOResourceStack;
import mekanism.api.qio.resource.QIOResourceTransferAdapter;
import mekanism.api.qio.resource.QIOResourceTransferAdapterRegistry;
import mekanism.common.content.qio.QIOFrequency;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCustomImporterTypeBudgetTest {

    private static final TestCodec CODEC = new TestCodec();

    @BeforeAll
    static void registerAdapter() {
        QIOResourceCodecRegistry.INSTANCE.register(CODEC);
        QIOResourceTransferAdapterRegistry.INSTANCE.register(new DuplicateCandidateAdapter());
    }

    @Test
    void repeatedCandidatesOnlyConsumeOneTransitType() {
        QIOResourceDescriptor first = QIOResourceDescriptor.of(CODEC, "first");
        QIOResourceDescriptor second = QIOResourceDescriptor.of(CODEC, "second");
        RecordingFrequency frequency = new RecordingFrequency();

        assertTrue(new OneTypeImporter().importCustomResources(frequency, new TileEntityChest()));
        assertEquals(2, frequency.getInserted(first));
        assertEquals(0, frequency.getInserted(second));
    }

    private static final class OneTypeImporter extends TileEntityQIOImporter {

        @Override
        protected int getMaxTransitTypes() {
            return 1;
        }

        @Override
        protected int getMaxTransitCount() {
            return 8;
        }
    }

    private static final class RecordingFrequency extends QIOFrequency {

        private final Map<QIOResourceDescriptor, Long> inserted = new HashMap<>();

        @Override
        public synchronized long massInsert(@Nullable QIOResourceDescriptor descriptor, long amount,
              Action action) {
            if (descriptor == null || amount <= 0 || action == null) {
                return 0;
            }
            if (action.execute()) {
                inserted.merge(descriptor, amount, Long::sum);
            }
            return amount;
        }

        private long getInserted(QIOResourceDescriptor descriptor) {
            return inserted.getOrDefault(descriptor, 0L);
        }
    }

    private static final class DuplicateCandidateAdapter implements QIOResourceTransferAdapter {

        @Override
        public ResourceLocation getCodecId() {
            return CODEC.getCodecId();
        }

        @Override
        public boolean supports(@Nonnull TileEntity target, @Nonnull EnumFacing targetFace) {
            return true;
        }

        @Nonnull
        @Override
        public List<QIOResourceStack> getExtractable(@Nonnull TileEntity target,
              @Nonnull EnumFacing targetFace, int maximumTypes, long maximumAmount) {
            QIOResourceDescriptor first = QIOResourceDescriptor.of(CODEC, "first");
            return Arrays.asList(new QIOResourceStack(first, 1), new QIOResourceStack(first, 1),
                  new QIOResourceStack(QIOResourceDescriptor.of(CODEC, "second"), 1));
        }

        @Override
        public long extract(@Nonnull TileEntity target, @Nonnull EnumFacing targetFace,
              @Nonnull QIOResourceDescriptor descriptor, long amount, @Nonnull Action action) {
            return amount;
        }

        @Override
        public long insert(@Nonnull TileEntity target, @Nonnull EnumFacing targetFace,
              @Nonnull QIOResourceDescriptor descriptor, long amount, @Nonnull Action action) {
            return amount;
        }
    }

    private static final class TestCodec implements QIOResourceCodec<String> {

        private static final ResourceLocation ID = new ResourceLocation("qio_test", "importer_resource");

        @Nonnull
        @Override
        public ResourceLocation getCodecId() {
            return ID;
        }

        @Nonnull
        @Override
        public String getFamily() {
            return "qio_test.importer";
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
                throw new IllegalArgumentException("Importer test value cannot be empty");
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
