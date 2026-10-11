package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.util.QIONbtUtils;
import net.minecraft.nbt.NBTBase;

import java.util.Collection;
import java.util.Map;

/**
 * Conservative retained-data weights, not a JVM heap measurement. Never resolves resources.
 */
public final class QIOWorkbenchMemory {
    public static final long MIB = 1024L * 1024;
    public static final long STACK_LIMIT = MIB;
    public static final long REQUEST_LIMIT = 8 * MIB;
    public static final long RESULT_LIMIT = 16 * MIB;
    public static final long TASK_LIMIT = 64 * MIB;

    private QIOWorkbenchMemory() {
    }

    public static long text(String value) {
        return value == null ? 0 : 64L + 2L * value.length();
    }

    public static long nbt(NBTBase value) {
        long size = QIONbtUtils.boundedSize(value, RESULT_LIMIT);
        return Math.min(size, RESULT_LIMIT + 1);
    }

    public static long resource(PortableResourceDescriptor value) {
        return value == null ? 0 : 192 + nbt(value.write()) + 2 * text(value.toString());
    }

    public static long stacks(Collection<QIOWorkbenchDeclarations.Stack> values) {
        long bytes = 64;
        for (QIOWorkbenchDeclarations.Stack value : values) bytes += 8 + value.getRetainedBytes();
        return bytes;
    }

    public static long resources(Map<PortableResourceDescriptor, Long> values) {
        long bytes = 64;
        for (PortableResourceDescriptor value : values.keySet()) bytes += 96 + resource(value);
        return bytes;
    }

    public static final class LimitException extends IllegalArgumentException {
        public LimitException(String message) {
            super(message);
        }
    }
}
