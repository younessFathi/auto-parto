package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedProviderSchedulerTest {

    @Test
    void boundedQueueRejectsExcessWorkAndGracefullyStops() throws Exception {
        BoundedProviderScheduler scheduler = new BoundedProviderScheduler("bounded-test", 1, 1,
                Duration.ofMillis(30));
        CountDownLatch activeTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveTask = new CountDownLatch(1);
        CountDownLatch queuedTaskRan = new CountDownLatch(1);
        Disposable active = Mono.fromRunnable(() -> {
            activeTaskStarted.countDown();
            try {
                releaseActiveTask.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }).subscribeOn(scheduler.scheduler()).subscribe();
        assertThat(activeTaskStarted.await(1, TimeUnit.SECONDS)).isTrue();

        Mono.fromRunnable(queuedTaskRan::countDown).subscribeOn(scheduler.scheduler()).subscribe();
        assertThat(scheduler.queuedTasks()).isEqualTo(1);
        StepVerifier.create(Mono.fromRunnable(() -> { }).subscribeOn(scheduler.scheduler()))
                .expectError(RejectedExecutionException.class)
                .verify();

        releaseActiveTask.countDown();
        assertThat(queuedTaskRan.await(1, TimeUnit.SECONDS)).isTrue();
        active.dispose();
        scheduler.close();
        assertThat(scheduler.isShutdown()).isTrue();
    }

    @Test
    void timedOutScheduledWorkIsInterrupted() throws Exception {
        try (BoundedProviderScheduler scheduler = new BoundedProviderScheduler("cancel-test", 1, 1,
                Duration.ofMillis(25))) {
            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch interrupted = new CountDownLatch(1);
            StepVerifier.create(Mono.fromCallable(() -> {
                        taskStarted.countDown();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException cancellation) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                        }
                        return "done";
                    }).subscribeOn(scheduler.scheduler()).timeout(Duration.ofMillis(40)))
                    .expectError(TimeoutException.class)
                    .verify();

            assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void reactorContextIsRestoredOnProviderThread() throws Exception {
        Class.forName(CorrelationIdWebFilter.class.getName());
        try (BoundedProviderScheduler scheduler = new BoundedProviderScheduler("context-test", 1, 1,
                Duration.ofMillis(25))) {
            AtomicReference<String> requestId = new AtomicReference<>();
            AtomicReference<String> threadName = new AtomicReference<>();
            Mono.fromCallable(() -> {
                        requestId.set(MDC.get(CorrelationIdWebFilter.MDC_KEY));
                        threadName.set(Thread.currentThread().getName());
                        return "done";
                    }).subscribeOn(scheduler.scheduler())
                    .contextWrite(context -> context.put(CorrelationIdWebFilter.MDC_KEY, "request-99"))
                    .block(Duration.ofSeconds(1));

            assertThat(requestId).hasValue("request-99");
            assertThat(threadName.get()).startsWith("context-test-");
        }
    }

    @Test
    void shutdownInterruptsWorkAfterConfiguredGracePeriod() throws Exception {
        BoundedProviderScheduler scheduler = new BoundedProviderScheduler("shutdown-test", 1, 1,
                Duration.ofMillis(20));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Mono.fromRunnable(() -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException cancellation) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }).subscribeOn(scheduler.scheduler()).subscribe();
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        scheduler.close();

        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.isShutdown()).isTrue();
    }
}
