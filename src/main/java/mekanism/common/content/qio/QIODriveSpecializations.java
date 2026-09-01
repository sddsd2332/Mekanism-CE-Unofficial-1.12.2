package mekanism.common.content.qio;

/** Built-in registered drive specializations. */
public final class QIODriveSpecializations {

    public static final QIODriveSpecialization MIXED = required(QIODriveSpecializationRegistry.MIXED_ID);
    public static final QIODriveSpecialization ITEM = required(QIODriveSpecializationRegistry.ITEM_ID);
    public static final QIODriveSpecialization FLUID = required(QIODriveSpecializationRegistry.FLUID_ID);
    public static final QIODriveSpecialization GAS = required(QIODriveSpecializationRegistry.GAS_ID);

    private QIODriveSpecializations() {
    }

    private static QIODriveSpecialization required(net.minecraft.util.ResourceLocation id) {
        QIODriveSpecialization specialization = QIODriveSpecializationRegistry.INSTANCE.get(id);
        if (specialization == null) {
            throw new IllegalStateException("Missing built-in QIO drive specialization: " + id);
        }
        return specialization;
    }
}
