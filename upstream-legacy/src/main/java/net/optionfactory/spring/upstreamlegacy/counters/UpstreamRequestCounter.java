package net.optionfactory.spring.upstreamlegacy.counters;

import java.time.Instant;

public interface UpstreamRequestCounter {

    public String next();

    public static UpstreamRequestCounter bootTime(Instant bootTime) {
        return new BootTimePrefixUpstreamRequestCounter(bootTime);
    }

}
