package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIODriveDefinition;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIOAmount;
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

    private final QIODriveDefinition definition;
    private final QIODriveType driveType;

    public ItemQIODrive(QIODriveTier tier) {
        this(tier, QIODriveType.MIXED);
    }

    public ItemQIODrive(QIODriveTier tier, QIODriveType driveType) {
        this(tier.getDefinition(), driveType);
    }

    public ItemQIODrive(QIODriveDefinition definition) {
        this(definition, QIODriveType.MIXED);
    }

    public ItemQIODrive(QIODriveDefinition definition, QIODriveType driveType) {
        if (!QIODriveDefinition.isRegistered(definition)) {
            throw new IllegalArgumentException("QIO drive definition must be registered before creating its item");
        }
        this.definition = definition;
        this.driveType = java.util.Objects.requireNonNull(driveType, "QIO drive type cannot be null");
        // Validate the definition against the selected mixed/specialized multiplier now.
        driveType.getExactStorageCapacity(definition);
        setMaxStackSize(1);
    }

    @Override
    @Deprecated
    public QIODriveTier getDriveTier() {
        return QIODriveTier.byDefinition(definition);
    }

    @Override
    public QIODriveDefinition getDriveDefinition(ItemStack stack) {
        return definition;
    }

    @Override
    public QIODriveType getDriveType() {
        return driveType;
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        DriveMetadata metadata = getDriveMetadata(stack);
        QIODriveType stackDriveType = getDriveType(stack);
        MekanismLang countEntry = switch (stackDriveType) {
            case ITEM -> MekanismLang.QIO_ITEMS_DETAIL;
            case FLUID -> MekanismLang.QIO_FLUIDS_DETAIL;
            case GAS -> MekanismLang.QIO_GASES_DETAIL;
            default -> MekanismLang.QIO_RESOURCES_DETAIL;
        };
        boolean nativeUnitCapacity = stackDriveType == QIODriveType.FLUID || stackDriveType == QIODriveType.GAS;
        long stored = nativeUnitCapacity ? metadata.getStorageUnits() : metadata.getCount();
        QIOAmount capacity = nativeUnitCapacity ? getExactStorageCapacity(stack) : getExactCountCapacity(stack);
        ITextComponent itemDetails = countEntry.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(stored), hasUnlimitedCountCapacity(stack) ? LangUtils.localize("gui.infinite") :
                    TextUtils.format(capacity.toBigInteger()));
        ITextComponent typeDetails = MekanismLang.QIO_TYPES_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(metadata.getTypes()), hasUnlimitedTypeCapacity(stack) ? LangUtils.localize("gui.infinite") :
                    TextUtils.format(getTypeCapacity(stack)));
        tooltip.add(MekanismLang.QIO_DRIVE_TYPE_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              LangUtils.localize(stackDriveType.getTranslationKey())).getFormattedText());
        tooltip.add(itemDetails.getFormattedText());
        tooltip.add(typeDetails.getFormattedText());
    }

    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return definition.getBaseTier().getColor() + super.getItemStackDisplayName(stack);
    }
}
