package mekanism.common.content.qio;

import javax.annotation.Nullable;

public interface IQIOFrequencyHolder {

    @Nullable
    default QIOFrequency getQIOFrequency() {
        return null;
    }
}
