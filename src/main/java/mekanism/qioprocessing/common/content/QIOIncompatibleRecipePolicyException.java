package mekanism.qioprocessing.common.content;

/**
 * An unsupported policy format must not trigger damaged-network reset or backup rollback.
 */
public final class QIOIncompatibleRecipePolicyException extends QIOProcessingDataException {
    private static final long serialVersionUID = 1L;

    public QIOIncompatibleRecipePolicyException(String message) {
        super(message);
    }
}
