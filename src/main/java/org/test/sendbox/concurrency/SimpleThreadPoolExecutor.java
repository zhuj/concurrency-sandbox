package org.test.sendbox.concurrency;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleThreadPoolExecutor implements SimpleExecutorService {

    private static final Runnable POISON = () -> {};

    enum State {
        RUNNING, SHUTDOWN
    }

    private final Thread[] threads;
    private final AtomicReference<State> state;
    private final BlockingQueue<Runnable> queue;

    public SimpleThreadPoolExecutor(int threads) {
        if (threads < 1) { throw new IllegalArgumentException(""); }

        this.state = new AtomicReference<>(State.RUNNING);
        this.queue = new LinkedBlockingQueue<>();
        this.threads = start(threads);
    }

    @Override
    public boolean submit(Runnable task) {
        if (!isInRunningState()) {
            return false;
        }
        if (!queue.offer(task)) {
            return false;
        }
        if (!isInRunningState()) {
            if (queue.remove(task)) {
                return false;
            }
        }
        return true;
    }

    boolean isInRunningState() {
        return State.RUNNING.equals(state.get());
    }

    @Override
    public boolean shutdown() {
        if (!state.compareAndSet(State.RUNNING, State.SHUTDOWN)) {
            return false;
        }

        for (int i=0, l=threads.length; i<l; i++) {
            this.queue.add(POISON);
        }
        return true;
    }

    private void core() {
        try {
            Runnable task;
            while (POISON != (task = queue.take())) {
                try {
                    task.run();
                } catch (Exception e) {
                    // ignore task execution error
                }
            }
        } catch (InterruptedException e) {
            // just exit (someone asks us to stop)
        }
    }

    private Thread[] start(int size) {
        Thread[] threads = new Thread[size];
        for (int i=0; i<size; i++) {
            Thread t = new Thread(this::core);
            t.start();
            threads[i] = t;
        }
        return threads;
    }

    boolean isQueueEmpty() {
        return queue.isEmpty();
    }

}
