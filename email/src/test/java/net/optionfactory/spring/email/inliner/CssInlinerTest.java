package net.optionfactory.spring.email.inliner;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
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

    @Test
    public void oneInstanceCanBeSharedBetweenThreads() throws Exception {
        final var inliner = new CssInliner();
        final var threads = 8;
        final var rounds = 200;
        final var barrier = new CyclicBarrier(threads);
        try (final var executor = Executors.newFixedThreadPool(threads)) {
            final var tasks = new ArrayList<Callable<Integer>>();
            for (int t = 0; t != threads; t++) {
                final var color = "c" + t;
                tasks.add(() -> {
                    var wrong = 0;
                    for (int r = 0; r != rounds; r++) {
                        barrier.await();
                        try {
                            final var got = inliner.postprocess("<style data-inlined>p { color: %s; }</style><p>test</p>".formatted(color));
                            if (!got.contains("<p style=\"color:%s;\">test</p>".formatted(color))) {
                                wrong++;
                            }
                        } catch (RuntimeException | Error ex) {
                            wrong++;
                        }
                    }
                    return wrong;
                });
            }
            var wrong = 0;
            for (final var f : executor.invokeAll(tasks)) {
                wrong += f.get();
            }
            Assertions.assertEquals(0, wrong, "every concurrent postprocessing inlines its own rules");
        }
    }
}
