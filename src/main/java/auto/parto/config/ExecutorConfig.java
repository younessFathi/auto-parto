package auto.parto.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ExecutorConfig {

    @Bean
    public ScheduledExecutorService retryExecutor() {
        return Executors.newScheduledThreadPool(
                3,  // Number of threads
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("push-retry-" + counter.getAndIncrement());
                        thread.setDaemon(true);  // Daemon threads won't prevent JVM shutdown
                        return thread;
                    }
                }
        );
    }

    // Optional: For more control
    @Bean(name = "notificationRetryExecutor")
    public ScheduledExecutorService notificationRetryExecutor() {
        return Executors.newScheduledThreadPool(
                2,
                new ThreadFactoryBuilder()
                        .setNameFormat("notification-retry-%d")
                        .setDaemon(true)
                        .setPriority(Thread.NORM_PRIORITY)
                        .build()
        );
    }
}