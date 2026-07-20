package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIODriveType;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.util.LangUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemQIODrive extends ItemMekanism implements IQIODriveItem {

    private final QIODriveTier tier;
    private final QIODriveType driveType;

    public ItemQIODrive(QIODriveTier tier) {
        this(tier, QIODriveType.MIXED);
    }

    public ItemQIODrive(QIODriveTier tier, QIODriveType driveType) {
        this.tier = tier;
        this.driveType = driveType;
        setMaxStackSize(1);
    }

    @Override
    public QIODriveTier getDriveTier() {
        return tier;
    }

    @Override
    public QIODriveType getDriveType() {
        return driveType;
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        DriveMetadata metadata = getDriveMetadata(stack);
        MekanismLang countEntry = switch (driveType) {
            case ITEM -> MekanismLang.QIO_ITEMS_DETAIL;
            case FLUID -> MekanismLang.QIO_FLUIDS_DETAIL;
            case GAS -> MekanismLang.QIO_GASES_DETAIL;
            default -> MekanismLang.QIO_RESOURCES_DETAIL;
        };
        boolean nativeUnitCapacity = driveType == QIODriveType.FLUID || driveType == QIODriveType.GAS;
        long stored = nativeUnitCapacity ? metadata.getStorageUnits() : metadata.getCount();
        long capacity = nativeUnitCapacity ? getStorageCapacity() : getCountCapacity();
        ITextComponent itemDetails = countEntry.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(stored), TextUtils.format(capacity));
        ITextComponent typeDetails = MekanismLang.QIO_TYPES_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(metadata.getTypes()), TextUtils.format(getTypeCapacity()));
        tooltip.add(MekanismLang.QIO_DRIVE_TYPE_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              LangUtils.localize(driveType.getTranslationKey())).getFormattedText());
        tooltip.add(itemDetails.getFormattedText());
        tooltip.add(typeDetails.getFormattedText());
    }

    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return tier.getBaseTier().getColor() + super.getItemStackDisplayName(stack);
    }
}
