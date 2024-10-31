package net.optionfactory.spring.upstream.alerts.spooler;

import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.rendering.BodyRendering;

public class AlertBodiesFunctions {

    public String abbreviated(byte[] in, int maxSize) {
        return BodyRendering.abbreviated(BodySource.of(in), "✂️", maxSize);
    }

    public String abbreviated(BodySource in, int maxSize) {
        return BodyRendering.abbreviated(in, "✂️", maxSize);
    }
}
