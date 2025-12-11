package net.optionfactory.spring.email.inliner;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CssInlinerTest {

    @Test
    public void canInlineStyles() {
        final var src = """
                    <style data-inlined>a { color: black; }</style>
                    <a>test</a> 
                    """;
        final var expected = """
                    <html>
                      <head></head>
                      <body>
                        <a style="color:black;">test</a>
                      </body>
                    </html>""";

        final var got = new CssInliner().postprocess(src);
        System.out.println(got);
        Assertions.assertEquals(expected, got);
    }
}
