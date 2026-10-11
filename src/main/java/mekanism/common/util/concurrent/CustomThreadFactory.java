package mekanism.common.util.concurrent;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {
    private final String threadName;
    private final ThreadGroup group;
    private final AtomicInteger threadCount = new AtomicInteger(1);

    public CustomThreadFactory(String threadName, final ThreadGroup group) {
        this.threadName = threadName;
        this.group = group;
    }

    public CustomThreadFactory(String threadName) {
        this(threadName, Thread.currentThread().getThreadGroup());
    }

    @Override
    public Thread newThread(@Nonnull Runnable r) {
        return new Thread(group, r, String.format(threadName, threadCount.getAndIncrement()));
    }

    /**
     * 新建一个自定义线程工厂, 此工厂的线程名可自定义
     *
     * @param threadName 线程名, 如 "Thread-%s"; "CustomThread-%s", %s 为线程编号.
     */
    public static CustomThreadFactory create(String threadName) {
        return new CustomThreadFactory(threadName);
    }
}
