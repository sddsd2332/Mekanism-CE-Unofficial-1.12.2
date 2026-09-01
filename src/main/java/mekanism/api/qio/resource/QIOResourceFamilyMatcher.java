package mekanism.api.qio.resource;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable exact matcher for a bounded set of resource families and codec ids. */
public final class QIOResourceFamilyMatcher {

    private static final int MAX_ENTRIES = 64;
    private static final QIOResourceFamilyMatcher ANY = new QIOResourceFamilyMatcher(true,
          Collections.emptySet(), Collections.emptySet());

    private final boolean any;
    private final Set<String> families;
    private final Set<ResourceLocation> codecIds;

    private QIOResourceFamilyMatcher(boolean any, Collection<String> families,
          Collection<ResourceLocation> codecIds) {
        this.any = any;
        LinkedHashSet<String> checkedFamilies = new LinkedHashSet<>();
        for (String family : Objects.requireNonNull(families, "families")) {
            checkedFamilies.add(QIOResourceFamily.requireValid(family));
        }
        LinkedHashSet<ResourceLocation> checkedCodecs = new LinkedHashSet<>();
        for (ResourceLocation codecId : Objects.requireNonNull(codecIds, "codecIds")) {
            checkedCodecs.add(Objects.requireNonNull(codecId, "codecId"));
        }
        if (checkedFamilies.size() + checkedCodecs.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("A QIO resource matcher may contain at most " + MAX_ENTRIES + " entries");
        }
        if (!any && checkedFamilies.isEmpty() && checkedCodecs.isEmpty()) {
            throw new IllegalArgumentException("A QIO resource matcher cannot be empty");
        }
        this.families = Collections.unmodifiableSet(checkedFamilies);
        this.codecIds = Collections.unmodifiableSet(checkedCodecs);
    }

    @Nonnull
    public static QIOResourceFamilyMatcher any() {
        return ANY;
    }

    @Nonnull
    public static QIOResourceFamilyMatcher family(@Nonnull String family) {
        return new QIOResourceFamilyMatcher(false, Collections.singleton(family), Collections.emptySet());
    }

    @Nonnull
    public static QIOResourceFamilyMatcher codec(@Nonnull ResourceLocation codecId) {
        return new QIOResourceFamilyMatcher(false, Collections.emptySet(), Collections.singleton(codecId));
    }

    @Nonnull
    public static QIOResourceFamilyMatcher of(@Nonnull Collection<String> families,
          @Nonnull Collection<ResourceLocation> codecIds) {
        return new QIOResourceFamilyMatcher(false, families, codecIds);
    }

    public boolean accepts(@Nonnull QIOResourceDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return accepts(descriptor.getFamily(), descriptor.getCodecId());
    }

    /** Matches already persisted metadata without requiring its codec to be installed. */
    public boolean accepts(@Nullable String family, @Nullable ResourceLocation codecId) {
        return any || codecId != null && codecIds.contains(codecId) || family != null && families.contains(family);
    }

    public boolean isAny() {
        return any;
    }

    @Nonnull
    public Set<String> getFamilies() {
        return families;
    }

    @Nonnull
    public Set<ResourceLocation> getCodecIds() {
        return codecIds;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("any", any);
        NBTTagList storedFamilies = new NBTTagList();
        families.stream().sorted().forEach(family -> storedFamilies.appendTag(new NBTTagString(family)));
        data.setTag("families", storedFamilies);
        NBTTagList storedCodecs = new NBTTagList();
        codecIds.stream().map(ResourceLocation::toString).sorted()
              .forEach(codec -> storedCodecs.appendTag(new NBTTagString(codec)));
        data.setTag("codecs", storedCodecs);
        return data;
    }

    @Nonnull
    public static QIOResourceFamilyMatcher read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "matcher");
        boolean any = data.getBoolean("any");
        Set<String> families = new LinkedHashSet<>();
        NBTTagList storedFamilies = data.getTagList("families", NBT.TAG_STRING);
        for (int index = 0; index < storedFamilies.tagCount(); index++) {
            families.add(storedFamilies.getStringTagAt(index));
        }
        Set<ResourceLocation> codecs = new LinkedHashSet<>();
        NBTTagList storedCodecs = data.getTagList("codecs", NBT.TAG_STRING);
        for (int index = 0; index < storedCodecs.tagCount(); index++) {
            codecs.add(new ResourceLocation(storedCodecs.getStringTagAt(index)));
        }
        return any ? ANY : new QIOResourceFamilyMatcher(false, families, codecs);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof QIOResourceFamilyMatcher other)) {
            return false;
        }
        return any == other.any && families.equals(other.families) && codecIds.equals(other.codecIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(any, families, codecIds);
    }
}
