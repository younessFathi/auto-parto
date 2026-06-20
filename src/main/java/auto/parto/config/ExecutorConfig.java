package auto.parto.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExecutorConfig.class);

    @Value("${auto.parto.executor.retry.pool-size:3}")
    private int retryPoolSize;

    @Value("${auto.parto.executor.notification-retry.pool-size:2}")
    private int notificationRetryPoolSize;

    @Bean
    @Qualifier("retryExecutor")
    public ScheduledExecutorService retryExecutor() {
        return Executors.newScheduledThreadPool(
                retryPoolSize,
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("push-retry-" + counter.getAndIncrement());
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
    }

    @Bean
    @Qualifier("notificationRetryExecutor")
    public ScheduledExecutorService notificationRetryExecutor() {
        return Executors.newScheduledThreadPool(
                notificationRetryPoolSize,
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("notification-retry-" + counter.getAndIncrement());
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
    }

    @Bean
    public RejectedExecutionHandler loggingRejectedExecutionHandler() {
        return (r, executor) -> log.warn("Task {} rejected from executor {}", r, executor);
    }
}