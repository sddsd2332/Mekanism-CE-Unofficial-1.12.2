package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import java.math.BigInteger;
import java.util.*;

/**
 * Immutable, frequency-scoped maintenance rule book.
 */
public final class QIOProcessingMaintenanceBook {
    public static final int VERSION = 1;
    public static final int MAX_RULES = 4096;

    public static final class Rule {
        private final UUID id;
        private final QIOResourceDescriptor descriptor;
        private final BigInteger target;
        private final long batch;
        private final boolean enabled;

        public Rule(UUID id, QIOResourceDescriptor descriptor, BigInteger target, long batch, boolean enabled) {
            this.id = Objects.requireNonNull(id, "rule id");
            this.descriptor = Objects.requireNonNull(descriptor, "rule descriptor");
            if (target == null || target.signum() < 0 || batch <= 0) {
                throw new IllegalArgumentException("Invalid maintenance rule quantities");
            }
            this.target = target;
            this.batch = batch;
            this.enabled = enabled;
        }

        public UUID id() {
            return id;
        }

        public QIOResourceDescriptor descriptor() {
            return descriptor;
        }

        public BigInteger target() {
            return target;
        }

        public long batch() {
            return batch;
        }

        public boolean enabled() {
            return enabled;
        }

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("id", id);
            tag.setTag("descriptor", descriptor.write());
            tag.setString("target", target.toString());
            tag.setLong("batch", batch);
            tag.setBoolean("enabled", enabled);
            return tag;
        }

        public NBTTagCompound writeForNetwork() {
            return write();
        }

        private static Rule read(NBTTagCompound tag) {
            if (tag == null || !tag.hasUniqueId("id") || !tag.hasKey("descriptor", NBT.TAG_COMPOUND)
                    || !tag.hasKey("target", NBT.TAG_STRING) || !tag.hasKey("batch", NBT.TAG_LONG)
                    || !tag.hasKey("enabled", NBT.TAG_BYTE)) {
                throw new IllegalArgumentException("Invalid maintenance rule");
            }
            BigInteger target;
            try {
                target = new BigInteger(tag.getString("target"));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Invalid maintenance target", error);
            }
            return new Rule(tag.getUniqueId("id"), QIOResourceDescriptor.read(tag.getCompoundTag("descriptor")),
                    target, tag.getLong("batch"), tag.getBoolean("enabled"));
        }
    }

    private final UUID frequency;
    private final long revision;
    private final Map<UUID, Rule> rules;

    private QIOProcessingMaintenanceBook(UUID frequency, long revision, Map<UUID, Rule> rules) {
        this.frequency = Objects.requireNonNull(frequency, "frequency");
        if (revision < 0 || rules.size() > MAX_RULES) throw new IllegalArgumentException("Invalid maintenance book");
        this.revision = revision;
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    public static QIOProcessingMaintenanceBook empty(UUID frequency) {
        return new QIOProcessingMaintenanceBook(frequency, 0, Collections.emptyMap());
    }

    public UUID frequency() {
        return frequency;
    }

    public long revision() {
        return revision;
    }

    public Map<UUID, Rule> rules() {
        return rules;
    }

    public QIOProcessingMaintenanceBook upsert(long expectedRevision, Rule rule) {
        checkRevision(expectedRevision);
        Map<UUID, Rule> next = new LinkedHashMap<>(rules);
        if (!rules.containsKey(rule.id()) && next.size() >= MAX_RULES) {
            throw new IllegalStateException("Maintenance rule quota exceeded");
        }
        next.put(rule.id(), rule);
        return new QIOProcessingMaintenanceBook(frequency, revision + 1, next);
    }

    public QIOProcessingMaintenanceBook remove(long expectedRevision, UUID id) {
        checkRevision(expectedRevision);
        if (!rules.containsKey(id)) return this;
        Map<UUID, Rule> next = new LinkedHashMap<>(rules);
        next.remove(id);
        return new QIOProcessingMaintenanceBook(frequency, revision + 1, next);
    }

    public QIOProcessingMaintenanceBook toggle(long expectedRevision, UUID id, boolean enabled) {
        checkRevision(expectedRevision);
        Rule current = rules.get(id);
        if (current == null) throw new IllegalArgumentException("Unknown maintenance rule");
        return upsert(revision, new Rule(current.id(), current.descriptor(), current.target(), current.batch(), enabled));
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("format", "mekanismqioprocessing:maintenance_book");
        tag.setInteger("version", VERSION);
        tag.setUniqueId("frequency", frequency);
        tag.setLong("revision", revision);
        NBTTagList stored = new NBTTagList();
        rules.values().forEach(rule -> stored.appendTag(rule.write()));
        tag.setTag("rules", stored);
        return tag;
    }

    public static QIOProcessingMaintenanceBook read(NBTTagCompound tag, UUID expectedFrequency) {
        if (tag == null || !"mekanismqioprocessing:maintenance_book".equals(tag.getString("format"))
                || tag.getInteger("version") != VERSION || !tag.hasUniqueId("frequency")
                || !expectedFrequency.equals(tag.getUniqueId("frequency")) || !tag.hasKey("rules", NBT.TAG_LIST)) {
            throw new IllegalArgumentException("Invalid maintenance book header");
        }
        NBTTagList stored = tag.getTagList("rules", NBT.TAG_COMPOUND);
        if (stored.tagCount() > MAX_RULES) throw new IllegalArgumentException("Too many maintenance rules");
        Map<UUID, Rule> rules = new LinkedHashMap<>();
        for (int index = 0; index < stored.tagCount(); index++) {
            Rule rule = Rule.read(stored.getCompoundTagAt(index));
            if (rules.putIfAbsent(rule.id(), rule) != null)
                throw new IllegalArgumentException("Duplicate maintenance rule");
        }
        return new QIOProcessingMaintenanceBook(expectedFrequency, tag.getLong("revision"), rules);
    }

    private void checkRevision(long expectedRevision) {
        if (expectedRevision != revision) throw new IllegalStateException("Maintenance revision changed");
    }
}

