package auto.parto.otp;

import auto.parto.config.BoundedProviderScheduler;
import auto.parto.config.CorrelationIdWebFilter;
import auto.parto.config.ProviderCallTelemetry;
import auto.parto.exception.EmailSendException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.io.ByteArrayResource;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class EmailServiceIsolationTest {

    private EmailProvider emailProvider;
    private BoundedProviderScheduler scheduler;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        Class.forName(CorrelationIdWebFilter.class.getName());
        emailProvider = mock(EmailProvider.class);
        scheduler = new BoundedProviderScheduler("resend-test", 1, 1, Duration.ofMillis(25));
        registry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        registry.close();
    }

    @Test
    void sendsOnDedicatedSchedulerAndPropagatesRequestCorrelation() throws Exception {
        AtomicReference<String> requestId = new AtomicReference<>();
        doAnswer(invocation -> {
            requestId.set(MDC.get(CorrelationIdWebFilter.MDC_KEY));
            return null;
        }).when(emailProvider).send(any());

        StepVerifier.create(service(Duration.ofSeconds(1)).sendOTPEmail("person@example.test", "123456")
                        .contextWrite(context -> context.put(CorrelationIdWebFilter.MDC_KEY, "request-42")))
                .expectNext("123456")
                .verifyComplete();

        assertThat(requestId).hasValue("request-42");
        assertThat(registry.get("auto.parto.provider.calls").tag("provider", "resend")
                .tag("outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void timeoutCancelsBlockingSdkWorkAndMapsToSafeError() throws Exception {
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch interruptedProvider = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredProvider.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interrupted) {
                interruptedProvider.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(emailProvider).send(any());

        StepVerifier.create(service(Duration.ofMillis(50)).sendOTPEmail("person@example.test", "123456"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(EmailSendException.class);
                    assertThat(error.getMessage()).isEqualTo("Email delivery failed");
                    assertThat(error.getMessage()).doesNotContain("person@example.test", "123456");
                })
                .verify();

        assertThat(enteredProvider.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(interruptedProvider.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.get("auto.parto.provider.calls").tag("provider", "resend")
                .tag("outcome", "timeout").timer().count()).isEqualTo(1);
    }

    @Test
    void providerExceptionContentIsNotPropagated() throws Exception {
        doAnswer(invocation -> {
            throw new IllegalStateException("provider returned private response detail");
        }).when(emailProvider).send(any());

        StepVerifier.create(service(Duration.ofSeconds(1)).sendOTPEmail("person@example.test", "123456"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(EmailSendException.class);
                    assertThat(error.getMessage()).doesNotContain("private response detail");
                    assertThat(error.getCause()).isNull();
                })
                .verify();

        assertThat(registry.get("auto.parto.provider.calls").tag("provider", "resend")
                .tag("outcome", "failure").timer().count()).isEqualTo(1);
    }

    @Test
    void fullEmailQueueRejectsImmediatelyWithSafeFailure() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        CountDownLatch providerCallsFinished = new CountDownLatch(2);
        doAnswer(invocation -> {
            firstStarted.countDown();
            try {
                releaseProvider.await();
            } finally {
                providerCallsFinished.countDown();
            }
            return null;
        }).when(emailProvider).send(any());
        EmailService emailService = service(Duration.ofSeconds(5));

        emailService.sendOTPEmail("one@example.test", "100001").subscribe();
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
        emailService.sendOTPEmail("two@example.test", "100002").subscribe();
        StepVerifier.create(emailService.sendOTPEmail("three@example.test", "100003"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(EmailSendException.class);
                    assertThat(error.getMessage()).doesNotContain("three@example.test", "100003");
                })
                .verify();
        assertThat(registry.get("auto.parto.provider.calls").tag("provider", "resend")
                .tag("outcome", "saturated").timer().count()).isEqualTo(1);

        releaseProvider.countDown();
        assertThat(providerCallsFinished.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void emailDisabledKeepsExistingNoProviderBehavior() throws Exception {
        EmailService disabled = new EmailService(emailProvider, scheduler, new ProviderCallTelemetry(registry),
                "sender@example.test", template(), "OTP", Duration.ofSeconds(1), false);

        StepVerifier.create(disabled.sendOTPEmail("person@example.test", "123456"))
                .expectNext("123456")
                .verifyComplete();
        assertThat(registry.getMeters()).isEmpty();
    }

    private EmailService service(Duration timeout) {
        return new EmailService(emailProvider, scheduler, new ProviderCallTelemetry(registry),
                "sender@example.test", template(), "OTP", timeout, true);
    }

    private ByteArrayResource template() {
        return new ByteArrayResource("Code {{otp_code}}".getBytes(StandardCharsets.UTF_8));
    }
}
