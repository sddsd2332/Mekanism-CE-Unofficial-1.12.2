package mekanism.common.content.qio.filter;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import mekanism.common.content.qio.QIOResourceEntry;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Matches every resource accepted by a persisted family/codec matcher. */
public class QIOMatcherFilter extends QIOFilter {

    public static final String TYPE = "matcher";

    private QIOResourceFamilyMatcher matcher;

    public QIOMatcherFilter() {
    }

    public QIOMatcherFilter(@Nonnull QIOResourceFamilyMatcher matcher) {
        this.matcher = java.util.Objects.requireNonNull(matcher, "matcher");
    }

    @Override
    @Nonnull
    public QIOResourceFamilyMatcher getMatcher() {
        if (matcher == null) {
            throw new IllegalStateException("QIO matcher filter has not been initialized");
        }
        return matcher;
    }

    @Override
    public boolean matches(QIOResourceEntry entry) {
        return true;
    }

    @Override
    public boolean matches(QIOResourceDescriptor descriptor) {
        return true;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean hasFilter() {
        return matcher != null;
    }

    @Override
    public void writePayload(NBTTagCompound data) {
        if (matcher != null) {
            data.setTag("matcher", matcher.write());
        }
    }

    @Override
    protected void readPayload(NBTTagCompound data) {
        matcher = data.hasKey("matcher", 10) ? QIOResourceFamilyMatcher.read(data.getCompoundTag("matcher")) : null;
    }
}
