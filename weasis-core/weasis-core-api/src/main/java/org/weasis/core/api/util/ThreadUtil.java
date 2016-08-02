package org.weasis.core.api.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadUtil {

    private ThreadUtil() {
        super();
    }

    public static final ExecutorService buildNewSingleThreadExecutor(final String name) {
        return buildNewFixedThreadExecutor(1, name);
    }

    public static final ExecutorService buildNewFixedThreadExecutor(int nThreads, final String name) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), getThreadFactory(name));
    }

    public static final ThreadFactory getThreadFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        };
    }
}
