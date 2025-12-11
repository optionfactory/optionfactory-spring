package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

public class XsltRedactorTest {

    @Test
    public void xml() throws Exception {
        final var redactor = XsltRedactor.Factory.INSTANCE.create(Map.of(), Map.of("@password", "redacted"), Map.of("//password", "redacted"));
        final var input = """
        <request>
            <a password="secret">a</a>
            <password>secret</password>
        </request>
        """;
        final var output = redactor.redact(new ByteArrayResource(input.getBytes(StandardCharsets.UTF_8)));
        System.out.println(output);
    }
}
