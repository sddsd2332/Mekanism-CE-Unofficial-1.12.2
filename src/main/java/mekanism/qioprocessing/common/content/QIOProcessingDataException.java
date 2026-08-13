package mekanism.qioprocessing.common.content;

/** Indicates that persisted QIO Processing data cannot be trusted or loaded. */
public class QIOProcessingDataException extends Exception {

    public QIOProcessingDataException(String message) {
        super(message);
    }

    public QIOProcessingDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
