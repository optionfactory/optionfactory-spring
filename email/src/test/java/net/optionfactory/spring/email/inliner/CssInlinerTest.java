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
        Assertions.assertEquals(expected, got, "the rule is moved into a style attribute and the style element is removed");
    }

    @Test
    public void styleElementsNotMarkedForInliningAreKept() {
        final var got = new CssInliner().postprocess("""
                    <style data-inlined>p { color: red; }</style>
                    <style>@media (max-width: 600px) { p { font-size: 12px; } }</style>
                    <p>test</p>
                    """);

        Assertions.assertTrue(got.contains("<style>@media (max-width: 600px) { p { font-size: 12px; } }</style>"), "a style element without data-inlined is left as is");
        Assertions.assertTrue(got.contains("<p style=\"color:red;\">test</p>"), "the data-inlined rules are still inlined");
        Assertions.assertFalse(got.contains("data-inlined"), "the inlined style element is removed");
    }

    @Test
    public void existingStyleAttributesComeAfterTheInlinedRulesSoTheyWin() {
        final var got = new CssInliner().postprocess("""
                    <style data-inlined>p { color: red; }</style>
                    <p style="color:blue">test</p>
                    """);

        Assertions.assertTrue(got.contains("<p style=\"color:red;color:blue\">test</p>"), "the declaration written on the element follows, and overrides, the inlined one");
    }

    @Test
    public void rulesFromSeveralStyleElementsAreAllInlined() {
        final var got = new CssInliner().postprocess("""
                    <style data-inlined>p { color: red; }</style>
                    <style data-inlined>p { margin: 0; }</style>
                    <p>test</p>
                    """);

        Assertions.assertTrue(got.contains("<p style=\"color:red;margin:0;\">test</p>"), "the rules of every data-inlined element are applied, in document order");
    }

    @Test
    public void elementsNotMatchedByAnyRuleAreUntouched() {
        final var got = new CssInliner().postprocess("""
                    <style data-inlined>.warning { color: red; }</style>
                    <p class="warning">a</p><p>b</p>
                    """);

        Assertions.assertTrue(got.contains("<p class=\"warning\" style=\"color:red;\">a</p>"), "the matching element gets the rule");
        Assertions.assertTrue(got.contains("<p>b</p>"), "a non matching element gets no style attribute");
    }
}
