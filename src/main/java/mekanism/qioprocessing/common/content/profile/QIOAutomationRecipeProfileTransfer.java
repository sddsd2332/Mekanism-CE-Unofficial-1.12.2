package mekanism.qioprocessing.common.content.profile;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Portable configuration-card representation of one active automation recipe profile. */
/**
 * QIO 处理模块中的 QIOAutomationRecipeProfileTransfer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationRecipeProfileTransfer {

    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_PROFILE_SCOPE_ID_LENGTH = 1_536;

    private final QIOAutomationMode mode;
    private final String profileScopeId;
    private final boolean individual;
    private final int globalSlot;
    private final RouteFilterMode filterMode;
    private final QIOAutomationRecipeProfile profile;

    private QIOAutomationRecipeProfileTransfer(@Nonnull QIOAutomationMode mode,
          @Nonnull String profileScopeId, boolean individual, int globalSlot,
          @Nonnull RouteFilterMode filterMode,
          @Nonnull QIOAutomationRecipeProfile profile) {
        this.mode = requireConfigurableMode(mode);
        this.profileScopeId = checkedProfileScopeId(profileScopeId);
        if (globalSlot < QIOAutomationRecipeProfileCatalog.MIN_GLOBAL_SLOT ||
            globalSlot > QIOAutomationRecipeProfileCatalog.MAX_GLOBAL_SLOT) {
            throw new IllegalArgumentException("Invalid QIO automation global profile slot");
        }
        this.filterMode = Objects.requireNonNull(filterMode, "filterMode");
        if (mode == QIOAutomationMode.PASSIVE && filterMode != RouteFilterMode.WHITELIST) {
            throw new IllegalArgumentException("Passive QIO automation requires a whitelist profile");
        }
        this.profile = Objects.requireNonNull(profile, "profile").copy();
        if (this.profile.getRouteFilterMode() != filterMode) {
            throw new IllegalArgumentException("QIO automation profile filter identity mismatch");
        }
        this.individual = individual;
        this.globalSlot = globalSlot;
    }

    @Nonnull
    public static QIOAutomationRecipeProfileTransfer capture(
          @Nonnull QIOAutomationRecipeProfileCatalog catalog, @Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId) {
        Objects.requireNonNull(catalog, "catalog");
        QIOAutomationRecipeProfileCatalog.Selection selection = catalog.getSelection(
              deviceUUID, mode, profileScopeId);
        return new QIOAutomationRecipeProfileTransfer(mode, profileScopeId,
              selection.isIndividual(), selection.getGlobalSlot(), selection.getFilterMode(),
              catalog.getActiveProfile(deviceUUID, mode, profileScopeId));
    }

    public boolean applyTo(@Nonnull QIOAutomationRecipeProfileCatalog catalog,
          @Nonnull UUID deviceUUID, @Nonnull QIOAutomationMode expectedMode,
          @Nonnull String expectedProfileScopeId,
          @Nonnull QIOAutomationRecipeProfileLayout layout) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(layout, "layout");
        if (mode != expectedMode || !profileScopeId.equals(
              checkedProfileScopeId(expectedProfileScopeId))) {
            return false;
        }
        QIOAutomationRecipeProfile imported = profile.copy();
        imported.prune(layout);
        int targetGlobalSlot = individual ? catalog.getSelection(deviceUUID, mode,
              profileScopeId).getGlobalSlot() : globalSlot;
        return catalog.replaceSelectionAndActiveProfile(deviceUUID, mode, profileScopeId,
              individual, targetGlobalSlot, filterMode, imported);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("mode", mode.name());
        data.setString("profileScopeId", profileScopeId);
        data.setBoolean("individual", individual);
        data.setInteger("globalSlot", globalSlot);
        data.setString("filterMode", filterMode.name());
        data.setTag("profile", profile.write());
        return data;
    }

    @Nonnull
    public static QIOAutomationRecipeProfileTransfer read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        Objects.requireNonNull(data, "data");
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("mode", NBT.TAG_STRING) ||
                !data.hasKey("profileScopeId", NBT.TAG_STRING) ||
                !data.hasKey("individual", NBT.TAG_BYTE) ||
                !data.hasKey("globalSlot", NBT.TAG_INT) ||
                !data.hasKey("filterMode", NBT.TAG_STRING) ||
                !data.hasKey("profile", NBT.TAG_COMPOUND)) {
                throw new QIOProcessingDataException("QIO automation profile transfer is incomplete");
            }
            QIOAutomationMode mode = QIOAutomationMode.valueOf(data.getString("mode"));
            RouteFilterMode filterMode = RouteFilterMode.valueOf(
                  data.getString("filterMode"));
            return new QIOAutomationRecipeProfileTransfer(mode,
                  data.getString("profileScopeId"), data.getBoolean("individual"),
                  data.getInteger("globalSlot"), filterMode,
                  QIOAutomationRecipeProfile.read(data.getCompoundTag("profile"),
                        filterMode));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO automation profile transfer", e);
        }
    }

    @Nonnull
    public QIOAutomationMode getMode() {
        return mode;
    }

    @Nonnull
    public String getProfileScopeId() {
        return profileScopeId;
    }

    public boolean isIndividual() {
        return individual;
    }

    public int getGlobalSlot() {
        return globalSlot;
    }

    @Nonnull
    public RouteFilterMode getFilterMode() {
        return filterMode;
    }

    @Nonnull
    public QIOAutomationRecipeProfile getProfile() {
        return profile.copy();
    }

    private static QIOAutomationMode requireConfigurableMode(QIOAutomationMode mode) {
        QIOAutomationMode checked = Objects.requireNonNull(mode, "mode");
        if (checked == QIOAutomationMode.OUTPUT_ONLY) {
            throw new IllegalArgumentException("Output-only automation has no recipe profile");
        }
        return checked;
    }

    private static String checkedProfileScopeId(String profileScopeId) {
        String checked = Objects.requireNonNull(profileScopeId, "profileScopeId").trim();
        if (checked.isEmpty() || checked.length() > MAX_PROFILE_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException("profileScopeId has an invalid length");
        }
        return checked;
    }
}
