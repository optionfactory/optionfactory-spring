package net.optionfactory.spring.upstream.faults.spooler;

import net.optionfactory.spring.upstream.rendering.BodyRendering;

public class FaultBodiesFunctions {

    public String abbreviated(byte[] in, int maxSize) {
        return BodyRendering.abbreviated(in, "✂️", maxSize);
    }
}
