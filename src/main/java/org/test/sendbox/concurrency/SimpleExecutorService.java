package org.test.sendbox.concurrency;

/**
 *
 */
public interface SimpleExecutorService {

    /**
     *
     * @param task
     * @return
     */
    boolean submit(Runnable task);

    /**
     *
     * @return
     */
    boolean shutdown();

}
