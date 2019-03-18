package org.test.sendbox.concurrency;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleThreadPoolExecutor implements SimpleExecutorService {

    static class TaskWrapper {
        private final Runnable task;
        private TaskWrapper(Runnable task) {
            this.task = task;
        }
    }

    private static final TaskWrapper POISON = new TaskWrapper(() -> {
    });

    enum State {
        RUNNING, SHUTDOWN
    }

    private final Thread[] threads;
    private final AtomicReference<State> state;
    private final BlockingQueue<TaskWrapper> queue;

    public SimpleThreadPoolExecutor(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("");
        }

        this.state = new AtomicReference<>(State.RUNNING);
        this.queue = new LinkedBlockingQueue<>();
        this.threads = startThreads(threads, this::executionThreadCore);
    }

    @Override
    public boolean submit(Runnable task) {
        if (!isInRunningState()) {
            // trivial case: has already been in non-running state.
            // the task should be added to queue, just return false
            return false;
        }

        // it's required to use wrappers over tasks here
        // so that it will be unique even if tasks are the same objects
        TaskWrapper wrapper = new TaskWrapper(task);

        if (!doOfferTask(wrapper)) {
            // something went wrong (the task hasn't been added), return false
            return false;
        }

        if (!isInRunningState()) {
            // hard case: the task has been added and state has changed to non-running state.
            // queue is poisoning or has been poisoned, so there is no guarantee that the task will be executed,
            // so we need to try to remove it (if it hasn't been executed yet)

            if (!doRemoveTask(wrapper)) {
                // this means that the task has already been executed by non-poisoned thread, return true (according to the contract)
                return true;
            }

            // the task has successfully been removed, and will not been executed, so return false (according to the contract)
            return false;
        }
        return true;
    }

    @Override
    public boolean shutdown() {
        if (!state.compareAndSet(State.RUNNING, State.SHUTDOWN)) {
            return false;
        }

        doPoisonThreads();
        return true;
    }

    private void executionThreadCore() {
        doEnterExecutionThread();
        try {
            TaskWrapper wrapper;
            while (POISON != (wrapper = queue.take())) {
                doExecuteTask(wrapper);
            }
        } catch (InterruptedException e) {
            // just exit (someone asks us to stop)
        } finally {
            doExitExecutionThread();
        }
    }

    static Thread[] startThreads(int size, Runnable executionCore) {
        Thread[] threads = new Thread[size];
        for (int i = 0; i < size; i++) {
            Thread t = new Thread(executionCore);
            t.start();
            threads[i] = t;
        }
        return threads;
    }

    /**
     * Internal method: check status of the executor
     * @return true if the status is {@link State#RUNNING}
     */
    @TestExtensionPoint
    boolean isInRunningState() {
        return State.RUNNING.equals(state.get());
    }

    /**
     * Internal method: check status of the task queue
     * @return true if there are no enqueued/unprocessed tasks (either real tasks or poison pills)
     */
    @TestExtensionPoint
    boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    /**
     * Internal method: enqueues task wrapper to the queue
     * @param wrapper task to be enqueued
     * @return true if queue accepts the offer
     */
    @TestExtensionPoint
    boolean doOfferTask(TaskWrapper wrapper) {
        return queue.offer(wrapper);
    }

    /**
     * Internal method: remove task wrapper from the queue
     * @param wrapper
     * @return true if success (task haven't been processed yet)
     */
    @TestExtensionPoint
    boolean doRemoveTask(TaskWrapper wrapper) {
        return queue.remove(wrapper);
    }

    /**
     * Internal method: executes the task logic by an executor thread (masquerade all exceptions)
     * Method will call either {@link #doFinishTask(TaskWrapper)}
     * or {@link #doFailTask(TaskWrapper, Exception)} depends on the task execution result
     * WARNING: method should not throw exceptions
     * @param wrapper task wrapper to be executed
     */
    @TestExtensionPoint
    void doExecuteTask(TaskWrapper wrapper) {
        try {
            wrapper.task.run();
            doFinishTask(wrapper);
        } catch (Exception e) {
            doFailTask(wrapper, e);
        }
    }

    /**
     * Internal method: called by {@link #doExecuteTask(TaskWrapper)} if the task completed successfully
     * WARNING: method should not throw exceptions
     * @param wrapper
     */
    @TestExtensionPoint
    void doFinishTask(TaskWrapper wrapper) {
        // do nothing
    }

    /**
     * Internal method: called by {@link #doExecuteTask(TaskWrapper)} if the task completed with an exception
     * WARNING: method should not throw exceptions
     * @param wrapper
     * @param exception
     */
    @TestExtensionPoint
    void doFailTask(TaskWrapper wrapper, Exception exception) {
        // do nothing
    }

    /**
     * Internal method: is called just after an execution thread starts
     */
    @TestExtensionPoint
    void doEnterExecutionThread() {
        // do nothing
    }

    /**
     * Internal method: is called just before an execution thread stops
     */
    @TestExtensionPoint
    void doExitExecutionThread() {
        // do nothing
    }

    /**
     * Internal method: fills queue with poison pills so that all the threads will stop execution.
     * Since queue guarantee FIFO all the previous sent task will be executed before poison pills.
     * WARNING: should be called only once.
     */
    @TestExtensionPoint
    void doPoisonThreads() {
        for (int i = 0, l = threads.length; i < l; i++) {
            this.queue.add(POISON);
        }
    }

    /**
     * Internal method: Await for all executor threads finish execution and check if queue is empty
     * @return true if the queue is empty at the moment when all the threads are finished
     * @throws InterruptedException
     */
    @TestExtensionPoint
    boolean join() throws InterruptedException {
        for (Thread t: threads) {
            t.join();
        }
        return isQueueEmpty();
    }

}
