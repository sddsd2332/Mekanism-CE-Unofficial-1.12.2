package mekanism.common.inventory.container;

import mekanism.common.Mekanism;
import mekanism.common.config.BaseConfig;
import mekanism.common.config.ClientConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.IntOption;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return extraData == other.extraData && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, extraData);
    }

    public void updateLastPosition(int x, int y, boolean pinned) {
        String saveName = type.getSaveName(extraData);
        if (saveName != null) {
            CachedWindowPosition cachedPosition = getCachedPosition(saveName);
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
            CachedWindowPosition cachedPosition = getCachedPosition(saveName);
            if (cachedPosition != null) {
                return cachedPosition.asWindowPosition();
            }
        }
        return new WindowPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, false);
    }

    @Nullable
    private CachedWindowPosition getCachedPosition(String saveName) {
        ClientConfig client = MekanismConfig.local().client;
        client.registerWindowType(type);
        return client.lastWindowPositions.get(saveName);
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

    public static final class WindowType {

        private static final Map<ResourceLocation, WindowType> REGISTRY = new LinkedHashMap<>();

        public static final WindowType COLOR = register("color", "color", false);
        public static final WindowType CONFIRMATION = register("confirmation", "confirmation", false);
        public static final WindowType MEKA_SUIT_HELMET = register("mekasuit_helmet", "mekasuit_helmet", false);
        public static final WindowType RENAME = register("rename", "rename", false);
        public static final WindowType SKIN_SELECT = register("skin_select", "skin_select", false);
        public static final WindowType SIDE_CONFIG = register("side_config", "side_config", true);
        public static final WindowType TRANSPORTER_CONFIG = register("transporter_config", "transporter_config", true);
        public static final WindowType UPGRADE = register("upgrade", "upgrade", true);
        public static final WindowType UNSPECIFIED = register("unspecified", null, false);

        @Nullable
        private final String saveName;
        private final boolean canPin;
        private final byte maxData;
        private final ResourceLocation registryName;

        public static WindowType register(String name, @Nullable String saveName, boolean canPin) {
            return register(new ResourceLocation("mekanism", name), saveName, canPin);
        }

        public static WindowType register(ResourceLocation registryName, @Nullable String saveName, boolean canPin) {
            return register(registryName, saveName, canPin, (byte) 1);
        }

        public static WindowType register(ResourceLocation registryName, @Nullable String saveName, boolean canPin, byte maxData) {
            if (REGISTRY.containsKey(registryName)) {
                throw new IllegalArgumentException("Duplicate window type registration: " + registryName);
            }
            WindowType type = new WindowType(registryName, saveName, canPin, maxData);
            REGISTRY.put(registryName, type);
            return type;
        }

        @Nullable
        public static WindowType byName(String name) {
            return REGISTRY.get(new ResourceLocation(name));
        }

        public static List<WindowType> getRegisteredWindowTypes() {
            return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
        }

        private WindowType(ResourceLocation registryName, @Nullable String saveName, boolean canPin, byte maxData) {
            this.registryName = registryName;
            this.saveName = saveName;
            this.canPin = canPin;
            this.maxData = maxData;
        }

        public String getRegistryNameString() {
            return registryName.toString();
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

        @Override
        public boolean equals(Object obj) {
            return obj instanceof WindowType other && registryName.equals(other.registryName);
        }

        @Override
        public int hashCode() {
            return registryName.hashCode();
        }
    }
}
