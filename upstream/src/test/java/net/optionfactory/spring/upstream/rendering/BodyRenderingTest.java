package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.rendering.BodyRendering.Strategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class BodyRenderingTest {

    private final BodyRendering br = BodyRendering.builder().build();

    @Test
    public void canAbbreviateString() {

        Assertions.assertEquals(".", br.render(Strategy.ABBREVIATED, 0, MediaType.TEXT_PLAIN, BodySource.of("ab", StandardCharsets.UTF_8), ".", 1));
        Assertions.assertEquals("ab", br.render(Strategy.ABBREVIATED, 0, MediaType.TEXT_PLAIN, BodySource.of("ab", StandardCharsets.UTF_8), ".", 2));
        Assertions.assertEquals(".", br.render(Strategy.ABBREVIATED, 0, MediaType.TEXT_PLAIN, BodySource.of("abc", StandardCharsets.UTF_8), ".", 1));
        Assertions.assertEquals("a.c", br.render(Strategy.ABBREVIATED, 0, MediaType.TEXT_PLAIN, BodySource.of("abc", StandardCharsets.UTF_8), ".", 2));
        Assertions.assertEquals("abc", br.render(Strategy.ABBREVIATED, 0, MediaType.TEXT_PLAIN, BodySource.of("abc", StandardCharsets.UTF_8), ".", 3));

    }

    @Test
    public void xmlPreambleIsRemoved() {
        final var source = """
                           <?xml version="1.0" encoding="utf-8"?>
                           <a/>
                           """;
        final var got = br.render(Strategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("<a/>", got);
    }

    @Test
    public void canCompactXml() {
        final var source = """
                           <?xml version="1.0" encoding="iso-8859-1"?>
                           <a>
                           <b>c</b>
                           </a>
                           """;
        final var got = br.render(Strategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.ISO_8859_1), "✂", 100_000);
        Assertions.assertEquals("<a><b>c</b></a>", got);

    }

    @Test
    public void textNodesSpacesAreNormalized() {
        final var source = """
                           <a> a 
                                b    
                                    c 
                           </a>
                           """;
        final var got = br.render(Strategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("<a>a b c</a>", got);

    }

    @Test
    public void emptyElementsAreCollapsed() {
        final var source = """
            <a></a>
        """;
        final var got = br.render(Strategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("<a/>", got);

    }

    @Test
    public void canCompactJson() {
        final var source = """
        {
             "a": "b",
             "c": 1
        }
        """;
        final var got = br.render(Strategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_JSON, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("{\"a\":\"b\",\"c\":1}", got);

    }

    @Test
    public void canRenderAbbreviatedCompactJsonMediaTypes() {
        final var source = BodySource.of("[1, 2]", StandardCharsets.UTF_8);

        Assertions.assertEquals("[1,2]", br.render(BodyRendering.Strategy.ABBREVIATED_REDACTED, 6, MediaType.APPLICATION_JSON, source, ".", 2048));
        Assertions.assertEquals("[1,2]", br.render(BodyRendering.Strategy.ABBREVIATED_REDACTED, 6, MediaType.APPLICATION_PROBLEM_JSON, source, ".", 2048));
        Assertions.assertEquals("[1, 2]", br.render(BodyRendering.Strategy.ABBREVIATED_REDACTED, 6, MediaType.parseMediaType("application/unsupported"), source, ".", 2048));

    }

    @Test
    public void canRenderAbbreviatedCompactXMLMediaTypes() {
        final var source = BodySource.of("<a> <b/> </a>", StandardCharsets.UTF_8);

        Assertions.assertEquals("<a><b/></a>", br.render(BodyRendering.Strategy.ABBREVIATED_REDACTED, 6, MediaType.APPLICATION_XML, source, ".", 2048));
        Assertions.assertEquals("<a><b/></a>", br.render(BodyRendering.Strategy.ABBREVIATED_REDACTED, 6, MediaType.parseMediaType("application/soap+xml"), source, ".", 2048));
        Assertions.assertEquals("<a> <b/> </a>", br.render(BodyRendering.Strategy.ABBREVIATED_REDACTED, 6, MediaType.parseMediaType("application/unsupported"), source, ".", 2048));

    }

}
