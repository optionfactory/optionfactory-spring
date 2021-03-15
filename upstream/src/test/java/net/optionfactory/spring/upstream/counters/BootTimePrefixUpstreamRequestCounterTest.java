package net.optionfactory.spring.upstream.counters;

import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

public class BootTimePrefixUpstreamRequestCounterTest {

    @Test
    public void counterIsPrefixedWithBootTime() {
        final Instant t = Instant.ofEpochSecond(0);
        final BootTimePrefixUpstreamRequestCounter counter = new BootTimePrefixUpstreamRequestCounter(t);
        final String got = counter.next();
        Assert.assertEquals("00000000.1", got);
    }

    @Test
    public void bootTimeIsSerializedInNetworkOrder() {
        final Instant t = Instant.ofEpochSecond(0xaabbccdd);
        final BootTimePrefixUpstreamRequestCounter counter = new BootTimePrefixUpstreamRequestCounter(t);
        final String got = counter.next();
        Assert.assertEquals("aabbccdd.1", got);
    }

}
