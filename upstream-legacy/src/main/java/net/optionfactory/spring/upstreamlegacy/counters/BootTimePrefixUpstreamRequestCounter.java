package net.optionfactory.spring.upstreamlegacy.counters;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.client5.http.utils.Hex;

public class BootTimePrefixUpstreamRequestCounter implements UpstreamRequestCounter {

    private final AtomicLong counter = new AtomicLong(0);
    private final String prefix;

    public BootTimePrefixUpstreamRequestCounter(Instant bootTime) {
        final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt((int) bootTime.getEpochSecond());
        prefix = Hex.encodeHexString(buffer.array());
    }

    @Override
    public String next() {
        return String.format("%s.%s", prefix, counter.incrementAndGet());
    }

}
