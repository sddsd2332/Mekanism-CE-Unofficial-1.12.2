package mekanism.common.config.options;

import io.netty.buffer.ByteBuf;
import mekanism.common.config.BaseConfig;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import javax.annotation.Nullable;

public class StringListOption extends Option<StringListOption> {

    private String[] value;
    private final String[] defaultValue;


    public StringListOption(BaseConfig owner, String key, String[] defaultValue, @Nullable String comment) {
        super(owner, key, comment);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public StringListOption(BaseConfig owner, String category, String key, String[] defaultValue, @Nullable String comment) {
        super(owner, category, key, comment);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String[] get() {
        return value;
    }

    public void set(String[] value) {
        this.value = value;
    }

    @SuppressWarnings("Duplicates")//types are different
    @Override
    public void load(Configuration config) {
        if (category.isEmpty()) {
            return;
        }
        Property prop;
        prop = config.get(this.category, this.key, this.defaultValue, this.comment);
        prop.setRequiresMcRestart(this.requiresGameRestart);
        prop.setRequiresWorldRestart(this.requiresWorldRestart);
        value = prop.getStringList();
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(this.value.length);
        for (String i : value) {
            ByteBufUtils.writeUTF8String(buf, i);
        }
    }

    @Override
    public void read(ByteBuf buf) {
        int size = buf.readInt();
        this.value = new String[size];
        for (int i = 0; i < size; i++) {
            this.value[i] = ByteBufUtils.readUTF8String(buf);
        }
    }
}
