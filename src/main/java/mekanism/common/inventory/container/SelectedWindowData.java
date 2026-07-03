package mekanism.common.inventory.container;

import mekanism.common.Mekanism;
import mekanism.common.config.BaseConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.IntOption;
import net.minecraftforge.common.config.Configuration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SelectedWindowData {

    public static final SelectedWindowData UNSPECIFIED = new SelectedWindowData(WindowType.UNSPECIFIED);

    @Nonnull
    public final WindowType type;
    public final byte extraData;

    public SelectedWindowData(@Nonnull WindowType type) {
        this(type, (byte) 0);
    }

    public SelectedWindowData(@Nonnull WindowType type, byte extraData) {
        this.type = Objects.requireNonNull(type);
        this.extraData = this.type.isValid(extraData) ? extraData : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SelectedWindowData other = (SelectedWindowData) o;
        return extraData == other.extraData && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, extraData);
    }

    public void updateLastPosition(int x, int y, boolean pinned) {
        String saveName = type.getSaveName(extraData);
        if (saveName != null) {
            CachedWindowPosition cachedPosition = MekanismConfig.local().client.lastWindowPositions.get(saveName);
            if (cachedPosition != null && cachedPosition.update(x, y, type.canPin() && pinned)) {
                cachedPosition.save(Mekanism.configuration);
            }
        }
    }

    public boolean wasPinned() {
        return getLastPosition().pinned;
    }

    public WindowPosition getLastPosition() {
        String saveName = type.getSaveName(extraData);
        if (saveName != null) {
            CachedWindowPosition cachedPosition = MekanismConfig.local().client.lastWindowPositions.get(saveName);
            if (cachedPosition != null) {
                return cachedPosition.asWindowPosition();
            }
        }
        return new WindowPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, false);
    }

    public static class CachedWindowPosition {

        private final IntOption x;
        private final IntOption y;
        @Nullable
        private final BooleanOption pinned;

        public CachedWindowPosition(BaseConfig owner, String savePath, boolean canPin) {
            String category = owner.getCategory() + ".window." + savePath;
            this.x = new IntOption(owner, category, "x", Integer.MAX_VALUE, "The last x position the " + savePath + " window was in when it was closed.");
            this.y = new IntOption(owner, category, "y", Integer.MAX_VALUE, "The last y position the " + savePath + " window was in when it was closed.");
            this.pinned = canPin ? new BooleanOption(owner, category, "pinned", false, "Whether the " + savePath + " window was pinned when it was closed.") : null;
        }

        private boolean update(int x, int y, boolean pinned) {
            boolean changed = false;
            if (this.x.val() != x) {
                this.x.set(x);
                changed = true;
            }
            if (this.y.val() != y) {
                this.y.set(y);
                changed = true;
            }
            if (this.pinned != null && this.pinned.val() != pinned) {
                this.pinned.set(pinned);
                changed = true;
            }
            return changed;
        }

        private WindowPosition asWindowPosition() {
            return new WindowPosition(x.val(), y.val(), pinned != null && pinned.val());
        }

        private void save(Configuration config) {
            config.get(x.category(), x.key(), Integer.MAX_VALUE, x.comment()).set(x.val());
            config.get(y.category(), y.key(), Integer.MAX_VALUE, y.comment()).set(y.val());
            if (pinned != null) {
                config.get(pinned.category(), pinned.key(), false, pinned.comment()).set(pinned.val());
            }
            config.save();
        }
    }

    public static class WindowPosition {

        public final int x;
        public final int y;
        public final boolean pinned;

        public WindowPosition(int x, int y, boolean pinned) {
            this.x = x;
            this.y = y;
            this.pinned = pinned;
        }
    }

    public enum WindowType {
        COLOR("color", false),
        CONFIRMATION("confirmation", false),
        MEKA_SUIT_HELMET("mekasuit_helmet", false),
        RENAME("rename", false),
        SKIN_SELECT("skin_select", false),
        SIDE_CONFIG("side_config", true),
        TRANSPORTER_CONFIG("transporter_config", true),
        UPGRADE("upgrade", true),
        UNSPECIFIED(null, false);

        @Nullable
        private final String saveName;
        private final boolean canPin;
        private final byte maxData;

        WindowType(@Nullable String saveName, boolean canPin) {
            this(saveName, canPin, (byte) 1);
        }

        WindowType(@Nullable String saveName, boolean canPin, byte maxData) {
            this.saveName = saveName;
            this.canPin = canPin;
            this.maxData = maxData;
        }

        @Nullable
        String getSaveName(byte extraData) {
            return maxData == 1 ? saveName : saveName + extraData;
        }

        public List<String> getSavePaths() {
            if (saveName == null) {
                return Collections.emptyList();
            } else if (maxData == 1) {
                return Collections.singletonList(saveName);
            }
            List<String> savePaths = new ArrayList<>();
            for (int i = 0; i < maxData; i++) {
                savePaths.add(saveName + i);
            }
            return savePaths;
        }

        public boolean isValid(byte extraData) {
            return extraData >= 0 && extraData < maxData;
        }

        public boolean canPin() {
            return canPin;
        }
    }
}
