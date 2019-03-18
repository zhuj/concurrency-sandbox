package org.test.sendbox.concurrency;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.AutoCloseableSoftAssertions;
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
        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            assertions.assertThat(service.isInRunningState()).isTrue();
            assertions.assertThat(service.shutdown()).isTrue();
            assertions.assertThat(service.isInRunningState()).isFalse();
            assertions.assertThat(service.shutdown()).isFalse();
            assertions.assertThat(service.isInRunningState()).isFalse();
            assertions.assertThat(service.join()).isTrue();
        } finally {
            service.shutdown();
        }
    }

    @Test(timeout = 1000)
    public void testDoubleShutdownMultipleCores() throws Throwable {
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(4);
        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            assertions.assertThat(service.isInRunningState()).isTrue();
            assertions.assertThat(service.shutdown()).isTrue();
            assertions.assertThat(service.isInRunningState()).isFalse();
            assertions.assertThat(service.shutdown()).isFalse();
            assertions.assertThat(service.isInRunningState()).isFalse();
            assertions.assertThat(service.join()).isTrue();
        } finally {
            service.shutdown();
        }
    }

    @Test(timeout = 1000)
    public void testSubmitAndShutdown() throws Throwable {
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1);
        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
            assertions.assertThat(service.submit(() -> queue.offer(MAGIC))).isTrue();
            assertions.assertThat(service.shutdown()).isTrue();
            assertions.assertThat(service.join()).isTrue();
            assertions.assertThat(queue.isEmpty()).isFalse();
        } finally {
            service.shutdown();
        }
    }

    @Test(timeout = 1000)
    public void testShutdownAndSubmit() throws Throwable {
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1);
        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
            assertions.assertThat(service.shutdown()).isTrue();
            assertions.assertThat(service.submit(() -> queue.offer(MAGIC))).isFalse();
            assertions.assertThat(service.join()).isTrue();
            assertions.assertThat(queue.isEmpty()).isTrue();
        } finally {
            service.shutdown();
        }
    }

}