/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.core.api.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ThreadUtil {

    private ThreadUtil() {
        super();
    }

    /**
     * Creates an Executor that uses a single worker thread operating off an unbounded queue, and uses the provided
     * ThreadFactory to create a new thread when needed. Unlike the otherwise equivalent
     * {@code newFixedThreadPool(1, threadFactory)} the returned executor is guaranteed not to be reconfigurable to use
     * additional threads.
     *
     * @param name
     *            the name of the new thread
     *
     * @return the newly created single-threaded Executor
     * @throws NullPointerException
     *             if threadFactory is null
     */

    public static final ExecutorService buildNewSingleThreadExecutor(final String name) {
        return Executors.newSingleThreadExecutor(getThreadFactory(name));

    }

    /**
     * Creates a thread pool that reuses a fixed number of threads operating off a shared unbounded queue, using the
     * provided ThreadFactory to create new threads when needed. At any point, at most {@code nThreads} threads will be
     * active processing tasks. If additional tasks are submitted when all threads are active, they will wait in the
     * queue until a thread is available. If any thread terminates due to a failure during execution prior to shutdown,
     * a new one will take its place if needed to execute subsequent tasks. The threads in the pool will exist until it
     * is explicitly {@link ExecutorService#shutdown shutdown}.
     *
     * @param nThreads
     *            the number of threads in the pool
     * @param name
     *            the name of the new thread
     * @return the newly created thread pool
     * @throws NullPointerException
     *             if threadFactory is null
     * @throws IllegalArgumentException
     *             if {@code nThreads <= 0}
     */
    public static final ExecutorService buildNewFixedThreadExecutor(int nThreads, final String name) {
        return Executors.newFixedThreadPool(nThreads, getThreadFactory(name));

    }

    /**
     * Based on the default thread factory
     *
     * @param name
     *            the name prefix of the new thread
     * @return the factory to use when creating new threads
     */
    public static final ThreadFactory getThreadFactory(String name) {
        return r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName(name + "-" + t.getName()); //$NON-NLS-1$
            return t;
        };
    }
}
