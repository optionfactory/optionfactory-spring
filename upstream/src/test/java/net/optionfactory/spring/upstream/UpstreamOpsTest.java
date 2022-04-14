package net.optionfactory.spring.upstream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author rferranti
 */
public class UpstreamOpsTest {

    @Test
    public void empty() throws IOException {
        final var buffer = new byte[16 * 1024 - 1];
        Arrays.fill(buffer, (byte) 'A');
        final var bais = new ByteArrayInputStream(buffer);
        String got = UpstreamOps.copyToString(bais, StandardCharsets.UTF_8, 0);
        Assert.assertEquals("", got);
    }

    @Test
    public void limited() throws IOException {
        final var buffer = new byte[16 * 1024 - 1];
        Arrays.fill(buffer, (byte) 'A');
        final var bais = new ByteArrayInputStream(buffer);
        String got = UpstreamOps.copyToString(bais, StandardCharsets.UTF_8, 2);
        Assert.assertEquals("AA", got);
    }

    @Test
    public void limitedButBig() throws IOException {
        final var buffer = new byte[16 * 1024 - 1];
        Arrays.fill(buffer, (byte) 'A');
        final var bais = new ByteArrayInputStream(buffer);
        String got = UpstreamOps.copyToString(bais, StandardCharsets.UTF_8, 4097);
        Assert.assertEquals(4097, got.length());
    }

    @Test
    public void unlimited() throws IOException {
        final var buffer = new byte[16 * 1024 - 1];
        Arrays.fill(buffer, (byte) 'A');
        final var bais = new ByteArrayInputStream(buffer);
        String got = UpstreamOps.copyToString(bais, StandardCharsets.UTF_8, buffer.length + 100);
        Assert.assertEquals(buffer.length, got.length());
    }
}
