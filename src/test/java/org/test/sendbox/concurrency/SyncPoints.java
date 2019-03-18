package org.test.sendbox.concurrency;

import org.assertj.core.api.Assertions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;

public class SyncPoints {

    private final Map<String, Semaphore> semaphoreMap = new HashMap<>();

    private Semaphore get(String phase) {
        synchronized (semaphoreMap) {
            return semaphoreMap.computeIfAbsent(phase, ($) -> new Semaphore(0));
        }
    }

    public String phase(String phase) {
        get(phase);
        return phase;
    }

    public void await(String phase) {
        await(get(phase));
    }

    public void release(String phase) {
        get(phase).release();
    }

    public void release(String ...phases) {
        for (String phase: phases) { release(phase); }
    }

    public void releaseAll() {
        synchronized (semaphoreMap) {
            for (Semaphore s: semaphoreMap.values()) {
                s.release();
            }
        }
    }

    public boolean isFree() {
        synchronized (semaphoreMap) {
            for (Semaphore s: semaphoreMap.values()) {
                if (s.hasQueuedThreads()) { return false; }
            }
            return true;
        }
    }


    static void await(Runnable o) {
        try {
            ForkJoinPool.commonPool().submit(o).get();
        } catch (ExecutionException ee) {
            Throwable e = ee.getCause();
            Assertions.fail(e.getMessage(), e);
        } catch (Exception e) {
            Assertions.fail(e.getMessage(), e);
        }
    }

    static void await(Semaphore execution) {
        try { execution.acquire(); }
        catch (InterruptedException e) { Assertions.fail(e.getMessage(), e); }
    }

}
