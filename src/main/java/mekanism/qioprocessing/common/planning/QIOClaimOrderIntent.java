package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Durable identity and exact request bytes for one not-yet-executed QIO reservation.
 */
public final class QIOClaimOrderIntent {
    private static final String FORMAT = "mekanismqioprocessing:claim_order_intent";
    private static final int WORKBENCH_VERSION = 3;
    private static final int PROVIDER_VERSION = 4;
    private static final int MAX_RESOURCES = 4096;

    private final UUID orderId, frequencyId, requestId, claimId;
    private final long bookRevision, contentsRevision, claimRevision;
    private final String generation;
    private final Map<UUID, Long> amounts;
    private final QIOFrequencyReference frequencyReference;
    private final QIOWorkbenchExecutionManifest manifest;
    private final QIOProviderExecutionManifest providerManifest;
    private final Map<UUID, QIOResourceDescriptor> providerResources;
    private final int version;

    private QIOClaimOrderIntent(UUID orderId, UUID frequencyId, long bookRevision, String generation,
                                UUID requestId, UUID claimId, long contentsRevision, long claimRevision, Map<UUID, Long> amounts,
                                QIOFrequencyReference frequencyReference, QIOWorkbenchExecutionManifest manifest,
                                QIOProviderExecutionManifest providerManifest,
                                Map<UUID, QIOResourceDescriptor> providerResources, int version) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.frequencyId = Objects.requireNonNull(frequencyId, "frequencyId");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.claimId = Objects.requireNonNull(claimId, "claimId");
        if (frequencyReference != null && !frequencyId.equals(frequencyReference.getFrequencyUUID())) {
            throw new IllegalArgumentException("Claim frequency reference mismatch");
        }
        this.frequencyReference = frequencyReference;
        if (version != 1 && version != 2 && version != WORKBENCH_VERSION && version != PROVIDER_VERSION) {
            throw new IllegalArgumentException("Unknown claim order version");
        }
        if (version == WORKBENCH_VERSION && (manifest == null || providerManifest != null || frequencyReference == null
                || frequencyReference.getBindingPlayerUUID() == null)) {
            throw new IllegalArgumentException("Executable claim order requires a bound manifest");
        }
        if (version == PROVIDER_VERSION && (providerManifest == null || manifest != null || frequencyReference == null
                || frequencyReference.getBindingPlayerUUID() == null)) {
            throw new IllegalArgumentException("Provider claim order requires a bound manifest");
        }
        if (version != WORKBENCH_VERSION && version != PROVIDER_VERSION && (manifest != null || providerManifest != null)) {
            throw new IllegalArgumentException("Legacy order cannot have an execution manifest");
        }
        this.version = version;
        this.manifest = manifest;
        this.providerManifest = providerManifest;
        this.providerResources = providerBindings(providerResources, amounts, providerManifest, version);
        if (bookRevision < 0 || contentsRevision < 0 || claimRevision < 0)
            throw new IllegalArgumentException("Negative claim order revision");
        this.bookRevision = bookRevision;
        this.contentsRevision = contentsRevision;
        this.claimRevision = claimRevision;
        if (generation == null || !generation.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid catalog generation");
        this.generation = generation;
        if (amounts == null || amounts.isEmpty() || amounts.size() > MAX_RESOURCES)
            throw new IllegalArgumentException("Invalid claim resource count");
        Map<UUID, Long> copy = new LinkedHashMap<>();
        amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            UUID resource = entry.getKey();
            Long amount = entry.getValue();
            if (resource == null || amount == null || amount <= 0)
                throw new IllegalArgumentException("Invalid claim resource amount");
            copy.put(resource, amount);
        });
        this.amounts = Collections.unmodifiableMap(copy);
        if (manifest != null && (manifest.revision() != bookRevision || !manifest.generation().equals(generation)
                || !manifest.claimAmounts().equals(this.amounts))) {
            throw new IllegalArgumentException("Manifest does not match claim order identity or amounts");
        }
        if (providerManifest != null && (!generation.equals(providerGeneration(providerManifest))
                || providerManifest.catalogRevision() != bookRevision
                || !providerAmountsMatch(providerManifest, this.providerResources, this.amounts))) {
            throw new IllegalArgumentException("Provider manifest does not match claim order identity or amounts");
        }
    }

    public static Optional<QIOClaimOrderIntent> prepare(QIOPatternPlanner.Result plan, QIOStorageSnapshot storage,
                                                        WorkbenchSnapshot book, UUID orderId, UUID requestId, UUID claimId) {
        return prepare(plan, storage, book, orderId, requestId, claimId, null);
    }

    public static Optional<QIOClaimOrderIntent> prepare(QIOPatternPlanner.Result plan, QIOStorageSnapshot storage,
                                                        WorkbenchSnapshot book, UUID orderId, UUID requestId, UUID claimId, QIOFrequencyReference reference) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(book, "book");
        if (!book.id().equals(storage.getFrequencyUUID()) || plan.revision() != book.revision()) {
            throw new IllegalArgumentException("Plan configuration or QIO frequency changed");
        }
        Optional<QIOClaimRequest> request = QIOPlanClaimIntent.prepare(plan, storage, orderId, requestId, claimId);
        return request.map(value -> new QIOClaimOrderIntent(orderId, book.id(), book.revision(), plan.generation(),
                requestId, claimId, value.getExpectedContentsRevision(), value.getExpectedClaimRevision(), value.getResourceAmounts(),
                reference, null, null, Collections.emptyMap(), 2));
    }

    public static Optional<QIOClaimOrderIntent> prepareExecutable(QIOPatternPlanner.Result plan, QIOStorageSnapshot storage,
                                                                  WorkbenchSnapshot book, UUID orderId, UUID requestId, UUID claimId, QIOFrequencyReference reference,
                                                                  mekanism.qioprocessing.common.pattern.WorkbenchPatternIndex index) {
        return prepareExecutable(plan, storage, book, orderId, requestId, claimId, reference, index, 0);
    }

    public static Optional<QIOClaimOrderIntent> prepareExecutable(QIOPatternPlanner.Result plan, QIOStorageSnapshot storage,
                                                                  WorkbenchSnapshot book, UUID orderId, UUID requestId, UUID claimId, QIOFrequencyReference reference,
                                                                  mekanism.qioprocessing.common.pattern.WorkbenchPatternIndex index, int dimension) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(storage);
        Objects.requireNonNull(book);
        if (!book.id().equals(storage.getFrequencyUUID()) || plan.revision() != book.revision()) {
            throw new IllegalArgumentException("Plan configuration or QIO frequency changed");
        }
        if (reference == null || !book.id().equals(reference.getFrequencyUUID())
                || reference.getBindingPlayerUUID() == null)
            throw new IllegalArgumentException("Missing bound frequency reference");
        Optional<QIOClaimRequest> request = QIOPlanClaimIntent.prepare(plan, storage, orderId, requestId, claimId);
        return request.map(value -> new QIOClaimOrderIntent(orderId, book.id(), book.revision(), plan.generation(),
                requestId, claimId, value.getExpectedContentsRevision(), value.getExpectedClaimRevision(), value.getResourceAmounts(),
                reference, QIOWorkbenchExecutionManifest.capture(plan, index, storage, dimension), null, Collections.emptyMap(), WORKBENCH_VERSION));
    }

    public static Optional<QIOClaimOrderIntent> prepareProviderExecutable(
            QIOProviderRoutePlanner.Result plan, QIOStorageSnapshot storage, UUID orderId,
            UUID requestId, UUID claimId, QIOFrequencyReference reference) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(storage);
        if (!plan.successful() || reference == null || !storage.getFrequencyUUID().equals(reference.getFrequencyUUID())
                || reference.getBindingPlayerUUID() == null) {
            throw new IllegalArgumentException("Invalid Provider claim order binding");
        }
        QIOProviderExecutionManifest providerManifest = QIOProviderExecutionManifest.capture(plan);
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        Map<UUID, QIOResourceDescriptor> bindings = new LinkedHashMap<>();
        for (Map.Entry<QIOResourceDescriptor, Long> needed : providerManifest.initialWithdrawals().entrySet()) {
            QIOStorageEntry found = null;
            for (QIOStorageEntry entry : storage.getEntries()) {
                if (!needed.getKey().equals(entry.getDescriptor())) continue;
                if (found != null) throw new IllegalArgumentException("Ambiguous Provider resource descriptor");
                found = entry;
            }
            if (found == null || found.getExactAvailableAmount().compareTo(java.math.BigInteger.valueOf(needed.getValue())) < 0) {
                return Optional.empty();
            }
            amounts.put(found.getResourceUUID(), needed.getValue());
            bindings.put(found.getResourceUUID(), needed.getKey());
        }
        if (amounts.isEmpty()) return Optional.empty();
        QIOClaimRequest request = QIOClaimRequest.createOrAdjust(requestId, claimId,
                QIOPlanClaimIntent.OWNER_NAMESPACE, orderId.toString(), 0, 0,
                storage.getContentsRevision(), storage.getClaimRevision(), amounts);
        return Optional.of(new QIOClaimOrderIntent(orderId, storage.getFrequencyUUID(), providerManifest.catalogRevision(),
                providerGeneration(providerManifest), requestId, claimId, request.getExpectedContentsRevision(),
                request.getExpectedClaimRevision(), amounts, reference, null, providerManifest, bindings, PROVIDER_VERSION));
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID frequencyId() {
        return frequencyId;
    }

    public long bookRevision() {
        return bookRevision;
    }

    public long contentsRevision() {
        return contentsRevision;
    }

    public long claimRevision() {
        return claimRevision;
    }

    public String generation() {
        return generation;
    }

    public UUID requestId() {
        return requestId;
    }

    public UUID claimId() {
        return claimId;
    }

    public Map<UUID, Long> amounts() {
        return amounts;
    }

    public QIOFrequencyReference frequencyReference() {
        return frequencyReference;
    }

    public QIOWorkbenchExecutionManifest manifest() {
        return manifest;
    }

    public QIOProviderExecutionManifest providerManifest() {
        return providerManifest;
    }

    public Map<UUID, QIOResourceDescriptor> providerResources() {
        return providerResources;
    }

    public boolean isExecutable() {
        return manifest != null || providerManifest != null;
    }

    public QIOClaimRequest request() {
        return QIOClaimRequest.createOrAdjust(requestId, claimId, QIOPlanClaimIntent.OWNER_NAMESPACE,
                orderId.toString(), 0, 0, contentsRevision, claimRevision, amounts);
    }

    public QIOClaimRequest releaseRequest() {
        UUID releaseId = UUID.nameUUIDFromBytes(("mekanismqioprocessing:release:" + orderId + ":" + claimId).getBytes(StandardCharsets.UTF_8));
        return QIOClaimRequest.release(releaseId, claimId, QIOPlanClaimIntent.OWNER_NAMESPACE,
                orderId.toString(), QIOClaimRequest.ANY_REVISION, Collections.emptyMap());
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("format", FORMAT);
        tag.setInteger("version", version);
        tag.setUniqueId("order", orderId);
        tag.setUniqueId("frequency", frequencyId);
        tag.setUniqueId("request", requestId);
        tag.setUniqueId("claim", claimId);
        tag.setLong("bookRevision", bookRevision);
        tag.setString("generation", generation);
        tag.setLong("contentsRevision", contentsRevision);
        tag.setLong("claimRevision", claimRevision);
        if (frequencyReference != null) tag.setTag("frequencyReference", frequencyReference.write());
        if (manifest != null) tag.setTag("manifest", manifest.write());
        if (providerManifest != null) tag.setTag("providerManifest", providerManifest.write());
        if (!providerResources.isEmpty()) {
            NBTTagList providerEntries = new NBTTagList();
            providerResources.forEach((resource, descriptor) -> {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setUniqueId("resource", resource);
                entry.setTag("descriptor", descriptor.write());
                providerEntries.appendTag(entry);
            });
            tag.setTag("providerResources", providerEntries);
        }
        NBTTagList entries = new NBTTagList();
        amounts.forEach((resource, amount) -> {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId("resource", resource);
            entry.setLong("amount", amount);
            entries.appendTag(entry);
        });
        tag.setTag("amounts", entries);
        return tag;
    }

    public static QIOClaimOrderIntent read(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("format", 8) || !FORMAT.equals(tag.getString("format"))
                || !tag.hasKey("version", 3) || (tag.getInteger("version") != 1 && tag.getInteger("version") != 2
                && tag.getInteger("version") != WORKBENCH_VERSION && tag.getInteger("version") != PROVIDER_VERSION)
                || !tag.hasUniqueId("order") || !tag.hasUniqueId("frequency")
                || !tag.hasUniqueId("request") || !tag.hasUniqueId("claim")
                || !tag.hasKey("bookRevision", 4) || !tag.hasKey("generation", 8)
                || !tag.hasKey("contentsRevision", 4) || !tag.hasKey("claimRevision", 4)
                || !tag.hasKey("amounts", 9) || (tag.hasKey("frequencyReference") &&
                (tag.getInteger("version") == 1 || !tag.hasKey("frequencyReference", 10)))
                || (tag.getInteger("version") == WORKBENCH_VERSION && (!tag.hasKey("frequencyReference", 10)
                || !tag.hasKey("manifest", 10) || tag.hasKey("providerManifest")))
                || (tag.getInteger("version") == PROVIDER_VERSION && (!tag.hasKey("frequencyReference", 10)
                || !tag.hasKey("providerManifest", 10) || !tag.hasKey("providerResources", 9)
                || tag.hasKey("manifest")))
                || (tag.getInteger("version") != WORKBENCH_VERSION && tag.getInteger("version") != PROVIDER_VERSION
                && (tag.hasKey("manifest") || tag.hasKey("providerManifest")))) {
            throw new IllegalArgumentException("Invalid claim order intent");
        }
        QIOFrequencyReference reference = tag.hasKey("frequencyReference", 10)
                ? QIOFrequencyReference.read(tag.getCompoundTag("frequencyReference")) : null;
        QIOWorkbenchExecutionManifest manifest = tag.getInteger("version") == WORKBENCH_VERSION
                ? QIOWorkbenchExecutionManifest.read(tag.getCompoundTag("manifest")) : null;
        QIOProviderExecutionManifest providerManifest = tag.getInteger("version") == PROVIDER_VERSION
                ? QIOProviderExecutionManifest.read(tag.getCompoundTag("providerManifest")) : null;
        Map<UUID, QIOResourceDescriptor> providerResources = new LinkedHashMap<>();
        if (providerManifest != null) {
            NBTTagList providerEntries = tag.getTagList("providerResources", 10);
            for (int index = 0; index < providerEntries.tagCount(); index++) {
                NBTTagCompound entry = providerEntries.getCompoundTagAt(index);
                if (!entry.hasUniqueId("resource") || !entry.hasKey("descriptor", 10)
                        || providerResources.putIfAbsent(entry.getUniqueId("resource"),
                        QIOResourceDescriptor.read(entry.getCompoundTag("descriptor"))) != null) {
                    throw new IllegalArgumentException("Invalid Provider resource binding");
                }
            }
        }
        NBTTagList entries = tag.getTagList("amounts", 10);
        if (entries.tagCount() == 0 || entries.tagCount() > MAX_RESOURCES)
            throw new IllegalArgumentException("Invalid claim resource count");
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            if (!entry.hasUniqueId("resource") || !entry.hasKey("amount", 4) || entry.getLong("amount") <= 0
                    || amounts.putIfAbsent(entry.getUniqueId("resource"), entry.getLong("amount")) != null) {
                throw new IllegalArgumentException("Invalid or duplicate claim resource");
            }
        }
        return new QIOClaimOrderIntent(tag.getUniqueId("order"), tag.getUniqueId("frequency"),
                tag.getLong("bookRevision"), tag.getString("generation"), tag.getUniqueId("request"),
                tag.getUniqueId("claim"), tag.getLong("contentsRevision"), tag.getLong("claimRevision"), amounts,
                reference, manifest, providerManifest, providerResources, tag.getInteger("version"));
    }

    private static Map<UUID, QIOResourceDescriptor> providerBindings(Map<UUID, QIOResourceDescriptor> values,
                                                                     Map<UUID, Long> amounts, QIOProviderExecutionManifest manifest, int version) {
        if (version != PROVIDER_VERSION) return Collections.emptyMap();
        if (values == null || values.isEmpty() || !values.keySet().equals(amounts.keySet())) {
            throw new IllegalArgumentException("Provider resource bindings do not match claim amounts");
        }
        Map<UUID, QIOResourceDescriptor> copy = new LinkedHashMap<>();
        values.forEach((resource, descriptor) -> {
            if (resource == null || descriptor == null || !manifest.initialWithdrawals().containsKey(descriptor)) {
                throw new IllegalArgumentException("Provider resource binding is not in the manifest");
            }
            copy.put(resource, descriptor);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static boolean providerAmountsMatch(QIOProviderExecutionManifest manifest,
                                                Map<UUID, QIOResourceDescriptor> bindings, Map<UUID, Long> amounts) {
        Map<QIOResourceDescriptor, Long> actual = new LinkedHashMap<>();
        bindings.forEach((resource, descriptor) -> actual.merge(descriptor, amounts.get(resource), Math::addExact));
        return actual.equals(manifest.initialWithdrawals());
    }

    private static String providerGeneration(QIOProviderExecutionManifest manifest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(manifest.write().toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format("%02x", value & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
