package net.optionfactory.spring.pem.der;

import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DerWriterTest {

    @Test
    public void oidWithLargeFirstSubidentifierIsBase128Encoded() throws IOException {
        final byte[] got = DerWriter.oid("2.100.3");
        Assertions.assertArrayEquals(new byte[]{0x06, 0x03, (byte) 0x81, 0x34, 0x03}, got);
    }
}
