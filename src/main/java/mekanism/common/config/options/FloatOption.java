package mekanism.common.config.options;

import io.netty.buffer.ByteBuf;
import mekanism.api.functions.FloatSupplier;
import mekanism.common.config.BaseConfig;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Created by Thiakil on 15/03/2019.
 */
@ParametersAreNonnullByDefault
public class FloatOption extends Option<FloatOption> implements FloatSupplier {

    private float value;
    private final float defaultValue;
    private boolean hasRange = false;
    private float min;
    private float max;

    public FloatOption(BaseConfig owner, String key, float defaultValue, @Nullable String comment) {
        super(owner, key, comment);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }


    public FloatOption(BaseConfig owner, String key, float defaultValue) {
        this(owner, key, defaultValue, null);
    }

    public FloatOption(BaseConfig owner, String key) {
        this(owner, key, 0);
    }

    public FloatOption(BaseConfig owner, String key, float defaultValue, @Nullable String comment, float min, float max) {
        this(owner, key, defaultValue, comment);
        this.hasRange = true;
        this.min = min;
        this.max = max;
    }

    @Deprecated
    public FloatOption(BaseConfig owner, String category, String key, float defaultValue, @Nullable String comment) {
        super(owner, category, key, comment);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Deprecated
    public FloatOption(BaseConfig owner, String category, String key, float defaultValue) {
        this(owner, category, key, defaultValue, null);
    }

    @Deprecated
    public FloatOption(BaseConfig owner, String category, String key) {
        this(owner, category, key, 0, null);
    }

    @Deprecated
    public FloatOption(BaseConfig owner, String category, String key, float defaultValue, @Nullable String comment, float min, float max) {
        this(owner, category, key, defaultValue, comment);
        this.hasRange = true;
        this.min = min;
        this.max = max;
    }

    public float val() {
        return value;
    }

    public void set(float value) {
        this.value = value;
    }

    @SuppressWarnings("Duplicates")//types are different
    @Override
    public void load(Configuration config) {
        if (category.isEmpty()) {
            return;
        }
        Property prop;
        if (hasRange) {
            prop = config.get(this.category, this.key, this.defaultValue, this.comment, this.min, this.max);
        } else {
            prop = config.get(this.category, this.key, this.defaultValue, this.comment);
        }
        prop.setRequiresMcRestart(this.requiresGameRestart);
        prop.setRequiresWorldRestart(this.requiresWorldRestart);
        this.value = (float) prop.getDouble();
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeFloat(this.value);
    }

    @Override
    public void read(ByteBuf buf) {
        this.value = buf.readFloat();
    }

    @Override
    public float getAsFloat() {
        return value;
    }
}
