package net.optionfactory.spring.upstream.counters;

import java.time.Instant;

public interface UpstreamRequestCounter {

    public String next();

    public static UpstreamRequestCounter bootTime(Instant bootTime) {
        return new BootTimePrefixUpstreamRequestCounter(bootTime);
    }

}
