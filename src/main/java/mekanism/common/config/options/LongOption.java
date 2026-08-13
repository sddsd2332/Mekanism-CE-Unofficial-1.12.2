package mekanism.common.config.options;

import io.netty.buffer.ByteBuf;
import mekanism.common.config.BaseConfig;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Long-valued Mekanism config option. Forge 1.12 stores it as a validated decimal string. */
@ParametersAreNonnullByDefault
public class LongOption extends Option<LongOption> implements LongSupplier {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?[0-9]+");

    private long value;
    private final long defaultValue;
    private boolean hasRange;
    private long min;
    private long max;

    public LongOption(BaseConfig owner, String key, long defaultValue, @Nullable String comment) {
        super(owner, key, comment);
        this.defaultValue = defaultValue;
        value = defaultValue;
    }

    public LongOption(BaseConfig owner, String key, long defaultValue) {
        this(owner, key, defaultValue, null);
    }

    public LongOption(BaseConfig owner, String key, long defaultValue, @Nullable String comment,
          long min, long max) {
        this(owner, key, defaultValue, comment);
        setRange(min, max);
    }

    public LongOption(BaseConfig owner, String category, String key, long defaultValue,
          @Nullable String comment, long min, long max) {
        super(owner, category, key, comment);
        this.defaultValue = defaultValue;
        value = defaultValue;
        setRange(min, max);
    }

    public long val() {
        return value;
    }

    public void set(long value) {
        this.value = clamp(value);
    }

    @Override
    public void load(Configuration config) {
        if (category.isEmpty()) {
            return;
        }
        Property property = config.get(category, key, Long.toString(defaultValue), comment,
              DECIMAL_PATTERN);
        property.setRequiresMcRestart(requiresGameRestart);
        property.setRequiresWorldRestart(requiresWorldRestart);
        long parsed;
        try {
            parsed = Long.parseLong(property.getString().trim());
        } catch (RuntimeException e) {
            parsed = defaultValue;
        }
        value = clamp(parsed);
        String normalized = Long.toString(value);
        if (!normalized.equals(property.getString())) {
            property.set(normalized);
        }
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(value);
    }

    @Override
    public void read(ByteBuf buf) {
        value = clamp(buf.readLong());
    }

    @Override
    public long getAsLong() {
        return value;
    }

    private void setRange(long min, long max) {
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Invalid long config option range");
        }
        hasRange = true;
        this.min = min;
        this.max = max;
    }

    private long clamp(long value) {
        return hasRange ? Math.max(min, Math.min(max, value)) : value;
    }
}
