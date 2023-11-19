package net.optionfactory.spring.email.spooling;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import net.optionfactory.spring.email.spooling.SpoolerTest.Conf;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = Conf.class)
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
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2),
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
    public void asd() throws InterruptedException {
        for (int i = 0; i != 100; i++) {
            publisher.publishEvent(new TestEvent(i));
        }
        boolean reachedZero = countDownLatch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(reachedZero);
    }

}
