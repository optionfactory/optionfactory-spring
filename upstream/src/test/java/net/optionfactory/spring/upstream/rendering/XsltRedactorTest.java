package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;

public class XsltRedactorTest {

    @Test
    public void xml() throws Exception {
        XsltRedactor redactor = XsltRedactor.Factory.INSTANCE.create(Map.of(), List.of("@password"), List.of("//password"));
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
