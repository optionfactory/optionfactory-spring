package net.optionfactory.spring.pem.der;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DerCursorTest {

    @Test
    public void rejectsLengthExceedingAvailableBytes() {
        // OCTET STRING (0x04) whose length field (0x82 0xFF 0xFF) claims 65535 bytes, but only 1 follows
        final byte[] malicious = new byte[]{0x04, (byte) 0x82, (byte) 0xFF, (byte) 0xFF, 0x41};
        Assertions.assertThrows(DerException.class, () -> DerCursor.of(malicious).flat().next());
    }
}
