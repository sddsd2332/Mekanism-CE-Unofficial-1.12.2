package mekanism.common.util.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * One producer wake per armed sleep, with an obligatory queue recheck before parking.
 */
public final class CoalescingWakeup {
    private final AtomicBoolean armed = new AtomicBoolean();

    public void arm() {
        armed.set(true);
    }

    public void cancel() {
        armed.set(false);
    }

    public void signal(Thread thread) {
        if (thread != null && armed.get() && armed.compareAndSet(true, false)) LockSupport.unpark(thread);
    }

}
