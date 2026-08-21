package mekanism.qioprocessing.common.machine;

import mekanism.common.item.ConfigurationCardDataExtensionRegistry;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileTransfer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

/** Configuration-card bridge for the active scheduled or passive QIO recipe profile. */
/**
 * QIO 处理模块中的 QIOAutomationRecipeConfigCardData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationRecipeConfigCardData implements
      ConfigurationCardDataExtensionRegistry.Handler {

    public static final String SCHEDULED_TAG = "qioAutoCraftingRecipeProfile";
    public static final String PASSIVE_TAG = "qioAutoProcessingRecipeProfile";
    private static final ResourceLocation EXTENSION_ID = new ResourceLocation(
          MekanismQIOProcessing.MODID, "automation_recipe_config_card");
    private static final QIOAutomationRecipeConfigCardData INSTANCE =
          new QIOAutomationRecipeConfigCardData();
    private static final Map<QIOAutomationRecipeConfigType, String> TAGS =
          new EnumMap<>(QIOAutomationRecipeConfigType.class);
    private static boolean registered;

    static {
        TAGS.put(QIOAutomationRecipeConfigType.SCHEDULED, SCHEDULED_TAG);
        TAGS.put(QIOAutomationRecipeConfigType.PASSIVE, PASSIVE_TAG);
    }

    private QIOAutomationRecipeConfigCardData() {
    }

    public static synchronized void register() {
        if (!registered) {
            ConfigurationCardDataExtensionRegistry.register(EXTENSION_ID, INSTANCE);
            registered = true;
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound collect(@Nonnull TileEntity tile,
          @Nonnull EntityPlayer player, @Nonnull NBTTagCompound data) {
        TileEntity profileTile = profileTile(tile);
        for (Map.Entry<QIOAutomationRecipeConfigType, String> entry : TAGS.entrySet()) {
            QIOAutomationRecipeConfigService.Context context =
                  QIOAutomationRecipeConfigService.open(player, profileTile, entry.getKey());
            if (context == null || !context.isEditable()) {
                data.removeTag(entry.getValue());
                continue;
            }
            QIOAutomationRecipeProfileTransfer transfer =
                  QIOAutomationRecipeProfileTransfer.capture(
                        context.getNetwork().getAutomationRecipeProfiles(),
                        context.getHost().getPersistentDeviceUUID(),
                        context.getType().getMode(), context.getProfileScopeId());
            data.setTag(entry.getValue(), transfer.write());
        }
        return data;
    }

    @Nonnull
    @Override
    public NBTTagCompound filterForApply(@Nonnull TileEntity tile,
          @Nonnull EntityPlayer player, @Nonnull NBTTagCompound data) {
        TileEntity profileTile = profileTile(tile);
        NBTTagCompound filtered = data;
        for (Map.Entry<QIOAutomationRecipeConfigType, String> entry : TAGS.entrySet()) {
            if (!data.hasKey(entry.getValue())) {
                continue;
            }
            QIOAutomationRecipeConfigService.Context context =
                  QIOAutomationRecipeConfigService.open(player, profileTile, entry.getKey());
            if (context == null || !context.isEditable()) {
                if (filtered == data) {
                    filtered = data.copy();
                }
                filtered.removeTag(entry.getValue());
            }
        }
        return filtered;
    }

    @Override
    public void apply(@Nonnull TileEntity tile, @Nonnull EntityPlayer player,
          @Nonnull NBTTagCompound data) {
        TileEntity profileTile = profileTile(tile);
        for (Map.Entry<QIOAutomationRecipeConfigType, String> entry : TAGS.entrySet()) {
            if (!data.hasKey(entry.getValue(), NBT.TAG_COMPOUND)) {
                continue;
            }
            QIOAutomationRecipeConfigService.Context context =
                  QIOAutomationRecipeConfigService.open(player, profileTile, entry.getKey());
            if (context == null || !context.isEditable()) {
                continue;
            }
            try {
                QIOAutomationRecipeProfileTransfer transfer =
                      QIOAutomationRecipeProfileTransfer.read(
                            data.getCompoundTag(entry.getValue()));
                QIOAutomationRecipeProfileCatalog catalog =
                      context.getNetwork().getAutomationRecipeProfiles();
                long before = catalog.getRevision();
                transfer.applyTo(catalog, context.getHost().getPersistentDeviceUUID(),
                      context.getType().getMode(), context.getProfileScopeId(),
                      context.getLayout());
                context.getNetwork().markAutomationRecipeProfilesChanged(before);
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                // Invalid or incompatible card data cannot alter the target profile.
            }
        }
    }

    @Nonnull
    private static TileEntity profileTile(@Nonnull TileEntity tile) {
        if (tile instanceof TileEntityBoundingBlock bounding && bounding.getMainTile() != null) {
            return bounding.getMainTile();
        }
        return tile;
    }
}
