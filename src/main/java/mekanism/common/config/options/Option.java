package mekanism.common.config.options;

import io.netty.buffer.ByteBuf;
import mekanism.api.annotations.FieldsAreNonnullByDefault;
import mekanism.common.config.BaseConfig;
import net.minecraftforge.common.config.Configuration;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Created by Thiakil on 15/03/2019.
 */
@ParametersAreNonnullByDefault
@FieldsAreNonnullByDefault
public abstract class Option<THISTYPE extends Option> {

    protected final String key;

    @Nullable
    protected final String comment;

    protected final String category;

    protected boolean requiresGameRestart = false;

    protected boolean requiresWorldRestart = false;


    public Option(BaseConfig owner, String key, @Nullable String comment) {
        this(owner, owner.getCategory(), key, comment);
    }

    public Option(BaseConfig owner, String category, String key, @Nullable String comment) {
        this.category = category;
        this.key = key;
        this.comment = comment;
        owner.registerOption(this);
    }

    public String category() {
        return category;
    }

    public String key() {
        return key;
    }

    @Nullable
    public String comment() {
        return comment;
    }

    /**
     * Loads this option from the config file NB: saving back is handled by the config system / GUIs, load will be re-called
     *
     * @param config where to load from
     */
    public abstract void load(Configuration config);

    /**
     * Serialise value to network buffer Must write the same way {@link #read(ByteBuf)} reads
     *
     * @param buf where to write to.
     */
    public abstract void write(ByteBuf buf);

    /**
     * Deserialise from network buffer Must read the same way {@link #write(ByteBuf)} writes
     *
     * @param buf where to read from
     */
    public abstract void read(ByteBuf buf);

    @Deprecated
    public THISTYPE setRequiresWorldRestart(boolean requiresWorldRestart) {
        this.requiresWorldRestart = requiresWorldRestart;
        //noinspection unchecked
        return (THISTYPE) this;
    }

    @Deprecated
    public THISTYPE setRequiresGameRestart(boolean requiresGameRestart) {
        this.requiresGameRestart = requiresGameRestart;
        //noinspection unchecked
        return (THISTYPE) this;
    }

    public THISTYPE setRequiresWorldRestart() {
        this.requiresWorldRestart = true;
        //noinspection unchecked
        return (THISTYPE) this;
    }


    public THISTYPE setRequiresGameRestart() {
        this.requiresGameRestart = true;
        //noinspection unchecked
        return (THISTYPE) this;
    }
}
