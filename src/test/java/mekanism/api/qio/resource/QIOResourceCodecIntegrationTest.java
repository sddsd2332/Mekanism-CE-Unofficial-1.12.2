package mekanism.api.qio.resource;

import mekanism.api.Action;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.qio.client.QIOResourceRenderer;
import mekanism.api.qio.client.QIOResourceRendererRegistry;
import mekanism.api.qio.external.QIOResourceExternalConverter;
import mekanism.api.qio.external.QIOResourceExternalConverterRegistry;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOMatcherFilter;
import mekanism.common.content.qio.filter.QIOResourceFilter;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.planning.QIOPlanningSnapshotFactory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOResourceCodecIntegrationTest {

    private static final StringCodec FIRST = new StringCodec("first", "shared.resource", 37);
    private static final StringCodec SECOND = new StringCodec("second", "shared.resource", 11);
    private static final CaseInsensitiveCodec SEMANTIC = new CaseInsensitiveCodec();

    @BeforeAll
    static void registerCodecs() {
        QIOResourceCodecRegistry.INSTANCE.register(FIRST);
        QIOResourceCodecRegistry.INSTANCE.register(SECOND);
        QIOResourceCodecRegistry.INSTANCE.register(SEMANTIC);
    }

    @AfterEach
    void resetWorldResourceRegistry() {
        QIOResourceTypeRegistry.INSTANCE.reset();
    }

    @Test
    void codecIdsRemainUniqueWhileFamiliesMayBeShared() {
        QIOResourceDescriptor first = QIOResourceDescriptor.of(FIRST, "alpha");
        QIOResourceDescriptor second = QIOResourceDescriptor.of(SECOND, "alpha");

        assertEquals(first.getFamily(), second.getFamily());
        assertFalse(first.equals(second));
        assertTrue(QIOResourceFamilyMatcher.family("shared.resource").accepts(first));
        assertTrue(QIOResourceFamilyMatcher.family("shared.resource").accepts(second));
        assertTrue(QIOResourceFamilyMatcher.codec(FIRST.getCodecId()).accepts(first));
        assertFalse(QIOResourceFamilyMatcher.codec(FIRST.getCodecId()).accepts(second));
        assertThrows(IllegalArgumentException.class, () ->
              QIOResourceCodecRegistry.INSTANCE.register(
                    new StringCodec("first", "another.family", 1)));
    }

    @Test
    void descriptorAndMachineStateRoundTripWithoutAResourceEnumOrdinal() {
        QIOResourceDescriptor descriptor = QIOResourceDescriptor.of(FIRST, "alpha");
        MachineResourceStack stack = MachineResourceStack.resource("input", descriptor, 42)
              .withOrder(3);

        MachineResourceStack restored = MachineResourceStack.read(
              stack.write(new NBTTagCompound()));

        assertEquals(stack, restored);
        assertEquals(MachineResourceKind.CUSTOM, restored.kind());
        assertEquals(descriptor, restored.descriptor());
        assertEquals("alpha", restored.resolve(FIRST));
    }

    @Test
    void unknownCodecDescriptorsRemainPortableButUnresolved() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("value", "retained");
        QIOResourceDescriptor unknown = QIOResourceDescriptor.persisted(
              new ResourceLocation("qio_test", "missing_codec"), "shared.resource", 4, 29,
              payload);

        QIOResourceDescriptor restored = QIOResourceDescriptor.read(unknown.write());

        assertEquals(unknown, restored);
        assertFalse(restored.isResolved());
        assertNull(restored.resolve());
        assertEquals(29, restored.getStorageUnitsPerUnit());
    }

    @Test
    void customPortAndBaselineUseTheExactMatcher() {
        MachineResourceStack accepted = MachineResourceStack.resource("custom",
              QIOResourceDescriptor.of(FIRST, "alpha"), 5);
        MachineResourceStack rejected = MachineResourceStack.resource("custom",
              QIOResourceDescriptor.of(SECOND, "alpha"), 5);
        TestPort port = new TestPort(QIOResourceFamilyMatcher.codec(FIRST.getCodecId()));

        assertTrue(port.acceptsResource(accepted));
        assertFalse(port.acceptsResource(rejected));
        assertTrue(port.insert(accepted));
        MachinePortBaseline baseline = MachinePortBaseline.capture(port);
        MachinePortBaseline restored = MachinePortBaseline.read(baseline.write());

        assertEquals(baseline, restored);
        assertTrue(restored.matches(port));
        assertTrue(restored.acceptsResource(accepted));
        assertFalse(restored.acceptsResource(rejected));
    }

    @Test
    void matcherAndExactResourceFiltersRoundTripForCustomCodecs() {
        QIOResourceDescriptor first = QIOResourceDescriptor.of(FIRST, "alpha");
        QIOResourceDescriptor second = QIOResourceDescriptor.of(SECOND, "alpha");
        QIOResourceEntry firstEntry = QIOResourceEntry.of(UUID.randomUUID(), first, QIOAmount.of(4));
        QIOResourceEntry secondEntry = QIOResourceEntry.of(UUID.randomUUID(), second, QIOAmount.of(4));

        QIOFilter family = QIOFilter.read(new QIOMatcherFilter(
              QIOResourceFamilyMatcher.family("shared.resource")).write());
        QIOFilter exact = QIOFilter.read(new QIOResourceFilter(first).write());

        assertNotNull(family);
        assertTrue(family.test(firstEntry));
        assertTrue(family.test(secondEntry));
        assertNotNull(exact);
        assertTrue(exact.test(firstEntry));
        assertFalse(exact.test(secondEntry));
    }

    @Test
    void semanticEqualityReusesAWorldUuidWhenCanonicalPayloadsDiffer(@TempDir Path worldDirectory) {
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory.toFile());
        QIOResourceDescriptor upper = QIOResourceDescriptor.of(SEMANTIC, "Alpha");
        QIOResourceDescriptor lower = QIOResourceDescriptor.of(SEMANTIC, "alpha");

        assertNotEquals(upper.getPayload(), lower.getPayload());
        assertEquals(upper, lower);
        assertEquals(upper.hashCode(), lower.hashCode());
        UUID firstUuid = QIOResourceTypeRegistry.INSTANCE.getOrTrack(upper);
        assertEquals(firstUuid, QIOResourceTypeRegistry.INSTANCE.getOrTrack(lower));
    }

    @Test
    void unresolvedResourcesAreExcludedFromPlanningSnapshots() {
        QIOResourceDescriptor resolved = QIOResourceDescriptor.of(FIRST, "available");
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("value", "orphaned");
        QIOResourceDescriptor unresolved = QIOResourceDescriptor.persisted(
              new ResourceLocation("qio_test", "missing_planning_codec"), "shared.resource", 1, 37,
              payload);
        QIOStorageSnapshot snapshot = new QIOStorageSnapshot(UUID.randomUUID(), "test", 1, 2, 3,
              Arrays.asList(QIOStorageEntry.resource(UUID.randomUUID(), BigInteger.valueOf(9), resolved),
                    QIOStorageEntry.resource(UUID.randomUUID(), BigInteger.valueOf(12), unresolved)),
              BigInteger.valueOf(100), BigInteger.TEN, 0, 0);

        Map<PortableResourceDescriptor, Long> available =
              QIOPlanningSnapshotFactory.captureAvailableResources(snapshot);

        assertEquals(Collections.singletonMap(PortableResourceDescriptor.fromDescriptor(resolved), 9L),
              available);
    }

    @Test
    void clientRendererRegistryIsCodecOwnedAndRejectsDuplicates() {
        TestRenderer renderer = new TestRenderer();

        assertSame(renderer, QIOResourceRendererRegistry.INSTANCE.register(renderer));
        assertSame(renderer, QIOResourceRendererRegistry.INSTANCE.get(FIRST.getCodecId()));
        assertEquals("Rendered alpha", renderer.getDisplayName("alpha"));
        assertThrows(IllegalArgumentException.class, () ->
              QIOResourceRendererRegistry.INSTANCE.register(new TestRenderer()));
    }

    @Test
    void externalConvertersAreExplicitAndRejectDuplicateOrAmbiguousOwnership() {
        ResourceLocation externalSystem = new ResourceLocation("qio_test", "external_storage");
        TestExternalConverter first = new TestExternalConverter(externalSystem, FIRST);
        QIOResourceExternalConverterRegistry.INSTANCE.register(first);
        QIOResourceDescriptor descriptor = QIOResourceDescriptor.of(FIRST, "alpha");

        assertEquals(new ExternalValue("alpha"),
              QIOResourceExternalConverterRegistry.INSTANCE.toExternal(externalSystem, descriptor));
        assertEquals(descriptor, QIOResourceExternalConverterRegistry.INSTANCE.fromExternal(externalSystem,
              new ExternalValue("alpha")));
        assertThrows(IllegalArgumentException.class, () ->
              QIOResourceExternalConverterRegistry.INSTANCE.register(
                    new TestExternalConverter(externalSystem, FIRST)));
        assertThrows(IllegalArgumentException.class, () ->
              QIOResourceExternalConverterRegistry.INSTANCE.register(
                    new TestExternalConverter(externalSystem, SECOND)));
    }

    private static final class StringCodec implements QIOResourceCodec<String> {

        private final ResourceLocation id;
        private final String family;
        private final long storageUnits;

        private StringCodec(String path, String family, long storageUnits) {
            id = new ResourceLocation("qio_test", path);
            this.family = family;
            this.storageUnits = storageUnits;
        }

        @Override
        public ResourceLocation getCodecId() {
            return id;
        }

        @Override
        public String getFamily() {
            return family;
        }

        @Override
        public Class<String> getValueClass() {
            return String.class;
        }

        @Override
        public String normalize(String value) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Test resource cannot be empty");
            }
            return normalized;
        }

        @Override
        public boolean sameType(String first, String second) {
            return normalize(first).equals(normalize(second));
        }

        @Override
        public int typeHash(String value) {
            return normalize(value).hashCode();
        }

        @Override
        public NBTTagCompound writeTemplate(String value) {
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("value", normalize(value));
            return payload;
        }

        @Override
        public String readTemplate(NBTTagCompound payload, int codecVersion) {
            if (codecVersion != 1) {
                throw new IllegalArgumentException("Unsupported test codec version");
            }
            return normalize(payload.getString("value"));
        }

        @Override
        public long getStorageUnitsPerUnit() {
            return storageUnits;
        }
    }

    private static final class CaseInsensitiveCodec implements QIOResourceCodec<String> {

        private static final ResourceLocation ID = new ResourceLocation("qio_test", "semantic_string");

        @Override
        public ResourceLocation getCodecId() {
            return ID;
        }

        @Override
        public String getFamily() {
            return "shared.resource";
        }

        @Override
        public Class<String> getValueClass() {
            return String.class;
        }

        @Override
        public String normalize(String value) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Semantic test value cannot be empty");
            }
            return normalized;
        }

        @Override
        public boolean sameType(String first, String second) {
            return normalize(first).equalsIgnoreCase(normalize(second));
        }

        @Override
        public int typeHash(String value) {
            return normalize(value).toLowerCase(Locale.ROOT).hashCode();
        }

        @Override
        public NBTTagCompound writeTemplate(String value) {
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("value", normalize(value));
            return payload;
        }

        @Override
        public String readTemplate(NBTTagCompound payload, int codecVersion) {
            if (codecVersion != 1) {
                throw new IllegalArgumentException("Unsupported semantic test codec version");
            }
            return normalize(payload.getString("value"));
        }

        @Override
        public long getStorageUnitsPerUnit() {
            return 13;
        }
    }

    private static final class TestRenderer implements QIOResourceRenderer<String> {

        @Override
        public ResourceLocation getCodecId() {
            return FIRST.getCodecId();
        }

        @Override
        public Class<String> getValueClass() {
            return String.class;
        }

        @Override
        public void render(String resource, int x, int y) {
        }

        @Override
        public String getDisplayName(String resource) {
            return "Rendered " + resource;
        }

        @Override
        public Object getIngredient(String resource) {
            return resource;
        }
    }

    private static final class ExternalValue {

        private final String value;

        private ExternalValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof ExternalValue other && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static final class TestExternalConverter implements QIOResourceExternalConverter<ExternalValue> {

        private final ResourceLocation externalSystem;
        private final QIOResourceCodec<String> codec;

        private TestExternalConverter(ResourceLocation externalSystem, QIOResourceCodec<String> codec) {
            this.externalSystem = externalSystem;
            this.codec = codec;
        }

        @Override
        public ResourceLocation getExternalSystemId() {
            return externalSystem;
        }

        @Override
        public ResourceLocation getCodecId() {
            return codec.getCodecId();
        }

        @Override
        public Class<ExternalValue> getExternalType() {
            return ExternalValue.class;
        }

        @Nullable
        @Override
        public ExternalValue toExternal(QIOResourceDescriptor descriptor) {
            String value = descriptor.resolve(codec);
            return value == null ? null : new ExternalValue(value);
        }

        @Nullable
        @Override
        public QIOResourceDescriptor fromExternal(ExternalValue externalResource) {
            return externalResource == null ? null : QIOResourceDescriptor.of(codec, externalResource.value);
        }
    }

    private static final class TestPort extends MachinePort {

        @Nullable
        private MachineResourceStack stored;

        private TestPort(QIOResourceFamilyMatcher matcher) {
            super(matcher, "custom", Role.BOTH, Purpose.PROCESSING, "custom_group", 0);
        }

        @Override
        public long getAvailableCapacity(@Nullable MachineResourceStack stack) {
            return acceptsResource(stack) && stored == null ? Long.MAX_VALUE : 0;
        }

        @Nullable
        @Override
        public MachineResourceStack peek() {
            return stored;
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            if (!acceptsResource(stack) || stored != null) {
                return false;
            }
            if (action.execute()) {
                stored = stack.withPort(portId());
            }
            return true;
        }

        @Nullable
        @Override
        protected MachineResourceStack extract(@Nullable MachineResourceStack expected,
              Action action) {
            if (!acceptsResource(expected) || stored == null || !stored.equals(expected)) {
                return null;
            }
            MachineResourceStack extracted = stored;
            if (action.execute()) {
                stored = null;
            }
            return extracted;
        }

        @Override
        public Object container() {
            return this;
        }

        @Override
        protected NBTTagCompound serializeContainer() {
            NBTTagCompound data = new NBTTagCompound();
            if (stored != null) {
                data.setTag("stored", stored.write(new NBTTagCompound()));
            }
            return data;
        }

        @Override
        protected void deserializeContainer(NBTTagCompound nbt) {
            stored = nbt.hasKey("stored", 10) ?
                  MachineResourceStack.read(nbt.getCompoundTag("stored")) : null;
        }
    }
}
