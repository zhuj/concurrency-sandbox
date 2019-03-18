package org.test.sendbox.concurrency;

/**
 * Simplified version of ExecutorService.
 * It returns boolean flag as a result (instead of returning a Future object).
 * It's guaranteed that successfully submitted task (submit returns true) will be executed (even if shutdown method is called),
 * as well as unsuccessfully submitted task (submit returns false) will never been executed.
 */
public interface SimpleExecutorService {

    /**
     * Enqueue task to executor
     * If method returns true it's guaranteed that the task will be executed once,
     * if method returns false the task will never been executed
     * @param task task to be enqueued and executed
     * @return true if and only if the task has been enqueued and will be executed
     */
    boolean submit(Runnable task);

    /**
     * Shutdown the executor
     * After shutdown method is called, all new tasks are prevented from being enqueued to the executor,
     * all enqueued tasks are guaranteed to be executed.
     * @return true if current thread has just shutdown the executor
     */
    boolean shutdown();

}
