package org.test.sendbox.concurrency;


import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SimpleThreadPoolExecutorSingleThreadTest {

    private static final Integer MAGIC = 1;

    @Test
    public void testWrongConstructorArgument() {
        Assertions
                .assertThatThrownBy(() -> new SimpleThreadPoolExecutor(0))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test(timeout = 1000)
    public void testDoubleShutdownSingleCore() throws Throwable {
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1);
        Assertions.assertThat(service.isInRunningState()).isTrue();
        Assertions.assertThat(service.shutdown()).isTrue();
        Assertions.assertThat(service.isInRunningState()).isFalse();
        Assertions.assertThat(service.shutdown()).isFalse();
        Assertions.assertThat(service.isInRunningState()).isFalse();
        Assertions.assertThat(finished(service)).isTrue();
    }

    @Test(timeout = 1000)
    public void testDoubleShutdownMultipleCores() throws Throwable {
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(4);
        Assertions.assertThat(service.isInRunningState()).isTrue();
        Assertions.assertThat(service.shutdown()).isTrue();
        Assertions.assertThat(service.isInRunningState()).isFalse();
        Assertions.assertThat(service.shutdown()).isFalse();
        Assertions.assertThat(service.isInRunningState()).isFalse();
        Assertions.assertThat(finished(service)).isTrue();
    }

    @Test(timeout = 1000)
    public void testSubmitAndShutdown() throws Throwable {
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1);
        try {
            BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
            Assertions.assertThat(service.submit(() -> queue.offer(MAGIC))).isTrue();
            Assertions.assertThat(service.shutdown()).isTrue();
            Assertions.assertThat(queue.poll(500, TimeUnit.MILLISECONDS)).isEqualTo(MAGIC);
            Assertions.assertThat(finished(service) && queue.isEmpty()).isTrue();
        } finally {
            service.shutdown();
        }
    }

    @Test(timeout = 1000)
    public void testShutdownAndSubmit() throws Throwable {
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1);
        try {
            BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
            Assertions.assertThat(service.shutdown()).isTrue();
            Assertions.assertThat(service.submit(() -> queue.offer(MAGIC))).isFalse();
            Assertions.assertThat(queue.poll(500, TimeUnit.MILLISECONDS)).isNull();
            Assertions.assertThat(finished(service) && queue.isEmpty()).isTrue();
        } finally {
            service.shutdown();
        }
    }

    private boolean finished(SimpleThreadPoolExecutor service) throws Throwable {
        while (!service.isQueueEmpty()) {
            Thread.sleep(100);
        }
        return true;
    }

}