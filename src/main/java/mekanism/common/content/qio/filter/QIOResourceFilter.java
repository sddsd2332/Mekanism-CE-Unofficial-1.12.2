package mekanism.common.content.qio.filter;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import mekanism.common.content.qio.QIOResourceEntry;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Exact codec-defined filter for built-in or addon resources. */
public class QIOResourceFilter extends QIOFilter {

    public static final String TYPE = "resource";

    private QIOResourceDescriptor descriptor;

    public QIOResourceFilter() {
    }

    public QIOResourceFilter(@Nonnull QIOResourceDescriptor descriptor) {
        this.descriptor = java.util.Objects.requireNonNull(descriptor, "descriptor");
    }

    @Nullable
    public QIOResourceDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    @Nonnull
    public QIOResourceFamilyMatcher getMatcher() {
        if (descriptor == null) {
            throw new IllegalStateException("QIO resource filter has not been initialized");
        }
        return QIOResourceFamilyMatcher.codec(descriptor.getCodecId());
    }

    @Override
    public boolean matches(QIOResourceEntry entry) {
        return descriptor != null && descriptor.equals(entry.getDescriptor());
    }

    @Override
    public boolean matches(QIOResourceDescriptor candidate) {
        return descriptor != null && descriptor.equals(candidate);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean hasFilter() {
        return descriptor != null;
    }

    @Override
    public void writePayload(NBTTagCompound data) {
        if (descriptor != null) {
            data.setTag("descriptor", descriptor.write());
        }
    }

    @Override
    protected void readPayload(NBTTagCompound data) {
        descriptor = data.hasKey("descriptor", 10) ?
              QIOResourceDescriptor.read(data.getCompoundTag("descriptor")) : null;
    }
}
