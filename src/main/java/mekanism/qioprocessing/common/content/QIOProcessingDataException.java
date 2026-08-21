package mekanism.qioprocessing.common.content;

/** Indicates that persisted QIO Processing data cannot be trusted or loaded. */
/**
 * QIO 处理模块中的 QIOProcessingDataException 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class QIOProcessingDataException extends Exception {

    public QIOProcessingDataException(String message) {
        super(message);
    }

    public QIOProcessingDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
