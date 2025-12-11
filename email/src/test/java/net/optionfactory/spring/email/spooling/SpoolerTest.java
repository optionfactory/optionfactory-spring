package net.optionfactory.spring.email.spooling;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import net.optionfactory.spring.email.spooling.SpoolerTest.Conf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(Conf.class)
public class SpoolerTest {

    @EnableScheduling
    @EnableAsync
    public static class Conf {

        @Bean
        public TaskScheduler taskScheduler() {
            final var ts = new SimpleAsyncTaskScheduler();
            ts.setVirtualThreads(true);
            ts.setThreadNamePrefix("tasks-vt-");
            return ts;
        }

        @Bean
        public BufferedScheduledSpooler<TestEvent> spooler(CountDownLatch counter, TaskScheduler ts, ConfigurableApplicationContext publisher) {
            return new BufferedScheduledSpooler<>(
                    TestEvent.class,
                    publisher,
                    ts,
                    Duration.ofSeconds(0),
                    Duration.ofMillis(50),
                    Duration.ofMillis(500),
                    (List<TestEvent> events) -> {
                        System.out.format("spooled: %s events: %s%n", events.size(), events);
                        for (TestEvent event : events) {
                            counter.countDown();
                        }
                        return IntStream.rangeClosed(1, events.size())
                                .mapToObj(i -> Path.of(String.valueOf(i)))
                                .toList();
                    });
        }

        @Bean
        public CountDownLatch counter() {
            return new CountDownLatch(100);
        }
    }

    public record TestEvent(int id) {

    }

    @Autowired
    public ApplicationEventPublisher publisher;
    @Autowired
    public CountDownLatch countDownLatch;

    @Test
    public void allEventsAreConsumed() throws InterruptedException {
        for (int i = 0; i != 100; i++) {
            publisher.publishEvent(new TestEvent(i));
        }
        boolean reachedZero = countDownLatch.await(1, TimeUnit.SECONDS);
        Assertions.assertTrue(reachedZero);
    }

}
