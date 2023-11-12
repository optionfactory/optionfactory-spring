package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.MediaType;

public class BodyRenderingTest {

    @Test
    public void canAbbreviateString() {
        Assert.assertEquals(".", BodyRendering.abbreviated("ab", ".", 1));
        Assert.assertEquals("ab", BodyRendering.abbreviated("ab", ".", 2));

        Assert.assertEquals(".", BodyRendering.abbreviated("abc", ".", 1));
        Assert.assertEquals("a.c", BodyRendering.abbreviated("abc", ".", 2));
        Assert.assertEquals("abc", BodyRendering.abbreviated("abc", ".", 3));

    }

    @Test
    public void canCompactXml() {
        final var source = """
                           <?xml version="1.0" encoding="iso-8859-1"?>
                           <a>
                           <b>c</b>
                           </a>
                           """;
        final var got = BodyRendering.xsltCompact(source.getBytes(StandardCharsets.ISO_8859_1));
        Assert.assertEquals("<a><b>c</b></a>", got);

    }

    @Test
    public void canCompactJson() {
        final var source = """
                           {
                                "a": "b",
                                "c": 1
                           }
                           """;
        final var got = BodyRendering.jsonCompact(source.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals("{\"a\":\"b\",\"c\":1}", got);

    }

    @Test
    public void canRenderAbbreviatedCompactJsonMediaTypes() {
        final var source = "[1, 2]".getBytes(StandardCharsets.UTF_8);

        Assert.assertEquals("[1,2]", BodyRendering.render(BodyRendering.Strategy.ABBREVIATED_COMPACT, MediaType.APPLICATION_JSON, source, ".", 2048));
        Assert.assertEquals("[1,2]", BodyRendering.render(BodyRendering.Strategy.ABBREVIATED_COMPACT, MediaType.APPLICATION_PROBLEM_JSON, source, ".", 2048));
        Assert.assertEquals("[1, 2]", BodyRendering.render(BodyRendering.Strategy.ABBREVIATED_COMPACT, MediaType.parseMediaType("application/unsupported"), source, ".", 2048));

    }
    @Test
    public void canRenderAbbreviatedCompactXMLMediaTypes() {
        final var source = "<a> <b/> </a>".getBytes(StandardCharsets.UTF_8);

        Assert.assertEquals("<a><b/></a>", BodyRendering.render(BodyRendering.Strategy.ABBREVIATED_COMPACT, MediaType.APPLICATION_XML, source, ".", 2048));
        Assert.assertEquals("<a><b/></a>", BodyRendering.render(BodyRendering.Strategy.ABBREVIATED_COMPACT, MediaType.parseMediaType("application/soap+xml"), source, ".", 2048));
        Assert.assertEquals("<a> <b/> </a>", BodyRendering.render(BodyRendering.Strategy.ABBREVIATED_COMPACT, MediaType.parseMediaType("application/unsupported"), source, ".", 2048));

    }
}
