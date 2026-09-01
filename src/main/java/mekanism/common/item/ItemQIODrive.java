package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIODriveDefinition;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIODriveType;
import mekanism.common.content.qio.QIODriveSpecialization;
import mekanism.common.content.qio.QIODriveSpecializationRegistry;
import mekanism.common.content.qio.QIODriveSpecializations;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.util.LangUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemQIODrive extends ItemMekanism implements IQIODriveItem {

    private final QIODriveDefinition definition;
    private final QIODriveSpecialization specialization;

    public ItemQIODrive(QIODriveTier tier) {
        this(tier, QIODriveSpecializations.MIXED);
    }

    public ItemQIODrive(QIODriveTier tier, QIODriveType driveType) {
        this(tier.getDefinition(), java.util.Objects.requireNonNull(driveType, "driveType").getSpecialization());
    }

    public ItemQIODrive(QIODriveTier tier, QIODriveSpecialization specialization) {
        this(tier.getDefinition(), specialization);
    }

    public ItemQIODrive(QIODriveDefinition definition) {
        this(definition, QIODriveSpecializations.MIXED);
    }

    public ItemQIODrive(QIODriveDefinition definition, QIODriveType driveType) {
        this(definition, java.util.Objects.requireNonNull(driveType, "driveType").getSpecialization());
    }

    public ItemQIODrive(QIODriveDefinition definition, QIODriveSpecialization specialization) {
        if (!QIODriveDefinition.isRegistered(definition)) {
            throw new IllegalArgumentException("QIO drive definition must be registered before creating its item");
        }
        this.definition = definition;
        this.specialization = java.util.Objects.requireNonNull(specialization,
              "QIO drive specialization cannot be null");
        if (!QIODriveSpecializationRegistry.INSTANCE.isRegistered(specialization)) {
            throw new IllegalArgumentException("QIO drive specialization must be registered before creating its item");
        }
        if (!specialization.supports(definition)) {
            throw new IllegalArgumentException("QIO drive specialization " + specialization.getRegistryName() +
                  " has no capacity for definition " + definition.getRegistryName());
        }
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
    @Nullable
    public QIODriveType getDriveType() {
        return QIODriveType.fromSpecialization(specialization);
    }

    @Override
    public QIODriveSpecialization getDriveSpecialization() {
        return specialization;
    }

    @Override
    public QIODriveSpecialization getDriveSpecialization(ItemStack stack) {
        return specialization;
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        DriveMetadata metadata = getDriveMetadata(stack);
        QIODriveType stackDriveType = QIODriveType.fromSpecialization(getDriveSpecialization(stack));
        MekanismLang countEntry = stackDriveType == QIODriveType.ITEM ? MekanismLang.QIO_ITEMS_DETAIL :
              stackDriveType == QIODriveType.FLUID ? MekanismLang.QIO_FLUIDS_DETAIL :
                    stackDriveType == QIODriveType.GAS ? MekanismLang.QIO_GASES_DETAIL :
                          MekanismLang.QIO_RESOURCES_DETAIL;
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
              LangUtils.localize(specialization.getTranslationKey())).getFormattedText());
        tooltip.add(itemDetails.getFormattedText());
        tooltip.add(typeDetails.getFormattedText());
    }

    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return definition.getBaseTier().getColor() + super.getItemStackDisplayName(stack);
    }
}
