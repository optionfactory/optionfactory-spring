package net.optionfactory.spring.upstream;

import java.nio.charset.StandardCharsets;

public class BodyRendering {

    public static String abbreviated(byte[] bodyBytes, String infix, int maxSize) {

        if (bodyBytes.length == 0) {
            return "";
        }
        if (bodyBytes.length < maxSize) {
            return new String(bodyBytes, StandardCharsets.UTF_8);
        }
        final int abbreviatedSize = maxSize / 2;
        final var prefix = new String(bodyBytes, 0, abbreviatedSize, StandardCharsets.UTF_8);
        final var suffix = new String(bodyBytes, bodyBytes.length - abbreviatedSize, abbreviatedSize, StandardCharsets.UTF_8);
        return prefix + infix + suffix;
    }

}
