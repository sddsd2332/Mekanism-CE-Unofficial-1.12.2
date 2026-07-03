package mekanism.api.gear.config;

import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

/**
 * Integer implementation of {@link ModuleConfigData}.
 *
 * @apiNote Currently the only creatable version of this is via {@link ModuleConfigData} as there is currently no generic support for integers in the module screen.
 * @since 10.3.3
 */
@NothingNullByDefault
public  class ModuleIntegerData implements ModuleConfigData<Integer>  {

    private int value;

    /**
     * Creates a new {@link ModuleIntegerData} with the given default value.
     *
     * @param def Default value.
     */
    protected ModuleIntegerData(int def) {
        value = def;
    }

    /**
     * Sanitizes the given value.
     *
     * @param value Value to sanitize.
     *
     * @return Sanitized value.
     */
    protected int sanitizeValue(int value) {
        return value;
    }

    @Override
    public Integer get() {
        return value;
    }

    @Override
    public void set(Integer val) {
        Objects.requireNonNull(val, "Value cannot be null.");
        value = sanitizeValue(val);
    }

    @Override
    public void read(String name, NBTTagCompound tag) {
        Objects.requireNonNull(tag, "Tag cannot be null.");
        Objects.requireNonNull(name, "Name cannot be null.");
        value = sanitizeValue(tag.getInteger(name));
    }

    @Override
    public void write(String name, NBTTagCompound tag) {
        Objects.requireNonNull(tag, "Tag cannot be null.");
        Objects.requireNonNull(name, "Name cannot be null.");
        tag.setInteger(name, value);
    }
}
