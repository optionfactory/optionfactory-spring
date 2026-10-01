package net.optionfactory.spring.upstream.hc5;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.buffering.Buffering;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

public class HcRequestFactoriesTimeoutTest {

    @Test
    public void subSecondSocketTimeoutIsNotTruncatedToInfinity() throws Exception {
        try (final var server = new ServerSocket(0)) {
            final var factory = HcRequestFactories.builder()
                    .socketTimeout(Duration.ofMillis(150))
                    .build(Buffering.UNBUFFERED);
            final var client = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getLocalPort())
                    .requestFactory(factory)
                    .build();

            final var startedAt = System.nanoTime();
            final var failure = new AtomicReference<Exception>();
            final var request = Thread.ofPlatform().daemon().start(() -> {
                try {
                    client.get().retrieve().body(String.class);
                } catch (Exception ex) {
                    failure.set(ex);
                }
            });
            request.join(2000);
            Assertions.assertFalse(request.isAlive(), "request still running: sub-second socket timeout was truncated to 0, which means no timeout");
            Assertions.assertNotNull(failure.get(), "the stalling server should have produced an i/o failure");
            final var elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            Assertions.assertTrue(elapsedMs < 1500, "socket timeout should fire at ~150ms, took " + elapsedMs + "ms");
        }
    }
}
