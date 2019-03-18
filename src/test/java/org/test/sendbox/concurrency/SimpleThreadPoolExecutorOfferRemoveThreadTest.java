package org.test.sendbox.concurrency;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Special tests for the "check-offer-check-remove" sequence in {@link SimpleThreadPoolExecutor#submit(Runnable)}
 */
public class SimpleThreadPoolExecutorOfferRemoveThreadTest {

    private static final Integer MAGIC = 1;

    /**
     * Check that the following flow returns false and the task never executed
     *
     * |        t1                  |         t2
     * | service.submit(task) {     |
     * |  doOfferTask() {           |
     * |   state check -> running   |
     * |   offer {                  |
     * |    await {                 |
     * |     .                     -->   service.shutdown()
     * |     .                     <--
     * |    }                       |
     * |    queue.offer             |
     * |   }                        |
     * |   state check -> shutdown  |
     * |   queue.remove             |
     * |   return false             |
     * | }                          |
     */
    @Test(timeout = 1000)
    public void testShutdownInOffer() throws Throwable {

        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        Runnable runnable = () -> queue.add(MAGIC);

        Semaphore semaphore = new Semaphore(0);
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1) {
            @Override void doEnterExecutionThread() {
                SyncPoints.await(semaphore);
                super.doEnterExecutionThread();
            }
            @Override boolean doOfferTask(TaskWrapper wrapper) {
                SyncPoints.await(this::shutdown);
                return super.doOfferTask(wrapper);
            }
        };

        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            assertions.assertThat(service.isInRunningState()).isTrue();

            assertions.assertThat(service.submit(runnable)).isFalse();
            assertions.assertThat(service.isInRunningState()).isFalse();

            semaphore.release();
            assertions.assertThat(service.join()).isTrue();

            assertions.assertThat(queue.isEmpty()).isTrue();
        } finally {
            semaphore.release();
            service.shutdown();
        }
    }

    /**
     * Check that the following flow returns true and executes task
     *
     * |        t1                  |         t2              |      t3 (execution)
     * | service.submit(task) {     |                         |
     * |  doOfferTask() {           |                         |
     * |   state check -> running   |                         |
     * |   offer {                  |                         |
     * |    queue.offer             |                         |
     * |    await {                 |                         |
     * |     .                     ---------------------------->  pick & execute task
     * |     .                     <----------------------------
     * |     .                     -->   service.shutdown()   |
     * |     .                     <--                        |
     * |    }                       |                         |
     * |   }                        |                         |
     * |   state check -> shutdown  |                         |
     * |   queue.remove (false)     |                         |
     * |   return true              |                         |
     * | }                          |                         |
     */
    @Test(timeout = 1000)
    public void testShutdownInRemoveOption1() throws Throwable {

        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        Runnable runnable = () -> queue.add(MAGIC);

        SyncPoints phases = new SyncPoints();
        String phase_remove = phases.phase("remove");
        String phase_execution = phases.phase("execution");

        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1) {
            @Override boolean doOfferTask(TaskWrapper wrapper) {
                try {
                    return super.doOfferTask(wrapper);
                } finally {
                    phases.release(phase_execution);
                    phases.await(phase_remove);
                    SyncPoints.await(this::shutdown);
                }
            }
            @Override void doExecuteTask(TaskWrapper wrapper) {
                phases.await(phase_execution);
                super.doExecuteTask(wrapper);
                phases.release(phase_remove);
            }
        };

        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            assertions.assertThat(service.isInRunningState()).as("1").isTrue();
            assertions.assertThat(service.submit(runnable)).as("2").isTrue();
            assertions.assertThat(service.isInRunningState()).as("3").isFalse();

            assertions.assertThat(service.join()).isTrue();
            assertions.assertThat(phases.isFree()).isTrue();

            assertions.assertThat(queue.size()).isEqualTo(1);
        } finally {
            phases.releaseAll();
            service.shutdown();
        }
    }

    /**
     * Check that the following flow returns true and executes task
     *
     * |        t1                  |         t2              |      t3 (execution)
     * | service.submit(task) {     |                         |
     * |  doOfferTask() {           |                         |
     * |   state check -> running   |                         |
     * |   offer {                  |                         |
     * |    queue.offer             |                         |
     * |    await {                 |                         |
     * |     .                     -->   service.shutdown()   |
     * |     .                     <--                        |
     * |     .                     ---------------------------->  pick & execute task
     * |     .                     <----------------------------
     * |    }                       |                         |
     * |   }                        |                         |
     * |   state check -> shutdown  |                         |
     * |   queue.remove (false)     |                         |
     * |   return true              |                         |
     * | }                          |                         |
     */
    @Test(timeout = 1000)
    public void testShutdownInRemoveOption2() throws Throwable {

        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        Runnable runnable = () -> queue.add(MAGIC);

        SyncPoints phases = new SyncPoints();
        String phase_remove = phases.phase("remove");
        String phase_execution = phases.phase("execution");

        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1) {
            @Override boolean doOfferTask(TaskWrapper wrapper) {
                try {
                    return super.doOfferTask(wrapper);
                } finally {
                    SyncPoints.await(this::shutdown);
                    phases.release(phase_execution);
                    phases.await(phase_remove);
                }
            }
            @Override void doExecuteTask(TaskWrapper wrapper) {
                phases.await(phase_execution);
                super.doExecuteTask(wrapper);
                phases.release(phase_remove);
            }
        };

        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            assertions.assertThat(service.isInRunningState()).as("1").isTrue();
            assertions.assertThat(service.submit(runnable)).as("2").isTrue();
            assertions.assertThat(service.isInRunningState()).as("3").isFalse();

            assertions.assertThat(service.join()).isTrue();
            assertions.assertThat(phases.isFree()).isTrue();

            assertions.assertThat(queue.size()).isEqualTo(1);
        } finally {
            phases.releaseAll();
            service.shutdown();
        }
    }

    /**
     * Check that the following flow executes task (the same task) exactly two times:
     * first two submit should be successful (and should be executed),
     * the the third one should both fail and avoid execution,
     * the last one should fail too
     *
     *
     * |        t1                  |         t2
     * | service.submit(task) {     |
     * |   ... normally ...         |
     * | }                          |
     * | service.submit(task) {     |
     * |   ... normally ...         |
     * | }                          |
     * | service.submit(task) {     |
     * |  doOfferTask() {           |
     * |   state check -> running   |
     * |   offer {                  |
     * |    await {                 |
     * |     .                     -->   service.shutdown()
     * |     .                     <--
     * |    }                       |
     * |    queue.offer             |
     * |   }                        |
     * |   state check -> shutdown  |
     * |   queue.remove             |
     * |   return false             |
     * | }                          |
     * | service.submit(task) {     |
     * |   ... normal ...           |
     * | }                          |
     */
    @Test(timeout = 1000)
    public void testSameTaskDoubleSend() throws Throwable {

        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        Runnable runnable = () -> queue.add(MAGIC);

        Semaphore semaphore = new Semaphore(0);
        SyncPoints sync = new SyncPoints();
        AtomicInteger counter = new AtomicInteger(3);
        SimpleThreadPoolExecutor service = new SimpleThreadPoolExecutor(1) {
            @Override void doEnterExecutionThread() {
                SyncPoints.await(semaphore);
                super.doEnterExecutionThread();
            }
            @Override boolean doOfferTask(TaskWrapper wrapper) {
                if (0 == counter.decrementAndGet()) {
                    SyncPoints.await(this::shutdown);
                }
                return super.doOfferTask(wrapper);
            }

        };

        try (AutoCloseableSoftAssertions assertions = new AutoCloseableSoftAssertions()) {
            assertions.assertThat(service.isInRunningState()).isTrue();
            assertions.assertThat(service.submit(runnable)).isTrue();

            assertions.assertThat(service.isInRunningState()).isTrue();
            assertions.assertThat(service.submit(runnable)).isTrue();

            assertions.assertThat(service.isInRunningState()).isTrue();
            assertions.assertThat(service.submit(runnable)).isFalse();

            assertions.assertThat(service.isInRunningState()).isFalse();
            assertions.assertThat(service.submit(runnable)).isFalse();

            assertions.assertThat(service.isInRunningState()).isFalse();

            semaphore.release();
            assertions.assertThat(service.join()).isTrue();

            assertions.assertThat(queue.size()).isEqualTo(2);
        } finally {
            semaphore.release();
            service.shutdown();
        }
    }

}