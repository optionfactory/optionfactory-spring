package net.optionfactory.spring.thymeleaf;


import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

public class VersionedResourceDialectTest {
    public TemplateEngine createEngine() {
        final TemplateEngine engine = new SpringTemplateEngine();
        engine.addDialect(new VersionedResourceDialect(() -> "1"));
        return engine;
    }

    @Test
    public void canUseDataSyntax() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<script src=\"/path/to/resource.js\" data-version-append></script>";
        final String expected = "<script src=\"/path/to/resource.js?version=1\"></script>";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "the data- attribute syntax marks the element too");
    }

    @Test
    public void appendsVersionToSrcAsQueryParameter() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<script src=\"/path/to/resource.js\" version:append></script>";
        final String expected = "<script src=\"/path/to/resource.js?version=1\"></script>";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "the version is appended to src as a query parameter");
    }

    @Test
    public void appendsVersionToSrcAsAnotherQueryParameterIfAnyExists() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<script src=\"/path/to/resource.js?param=a\" version:append></script>";
        final String expected = "<script src=\"/path/to/resource.js?param=a&version=1\"></script>";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "an existing query on src is extended");
    }

    @Test
    public void appendsVersionToHrefAsQueryParameter() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<link href=\"/path/to/resource.js\" version:append>";
        final String expected = "<link href=\"/path/to/resource.js?version=1\">";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "the version is appended to href as a query parameter");
    }

    @Test
    public void appendsVersionToHrefAsAnotherQueryParameterIfAnyExists() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<link href=\"/path/to/resource.js?param=a\" version:append>";
        final String expected = "<link href=\"/path/to/resource.js?param=a&version=1\">";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "an existing query on href is extended");
    }

    @Test
    public void ifNoHrefOrSrcIsPresentDoNothing() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<div version:append></div>";
        final String expected = "<div></div>";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "without src nor href only the marker is removed");
    }

    @Test
    public void ifSrcIsEmptyDoNothing() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<script src=\"\" version:append></script>";
        final String expected = "<script src=\"\"></script>";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "an empty src is left as it is");
    }

    @Test
    public void ifHrefIsEmptyDoNothing() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<link href=\"\" version:append>";
        final String expected = "<link href=\"\">";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "an empty href is left as it is");
    }

    @Test
    public void ifVersionAlreadyPresentDoNothing() {
        final TemplateEngine engine = createEngine();
        final IContext context = new Context();
        final String template = "<script src=\"/path/to/resource.js?version=2\" version:append></script>";
        final String expected = "<script src=\"/path/to/resource.js?version=2\"></script>";
        final String got = engine.process(template, context);
        Assertions.assertEquals(expected, got, "an existing version parameter is not overridden");
    }

    @Test
    public void versionsTheValueThymeleafAttributesEvaluateTo() {
        final TemplateEngine engine = createEngine();
        final Context context = new Context();
        context.setVariable("path", "/path/to/resource.js");
        final String template = "<script th:src=\"${path}\" version:append></script>";
        final String expected = "<script src=\"/path/to/resource.js?version=1\"></script>";
        Assertions.assertEquals(expected, engine.process(template, context), "the marker runs after th:src has been evaluated");
    }

    @Test
    public void onlySrcIsVersionedWhenBothArePresent() {
        final TemplateEngine engine = createEngine();
        final String template = "<img src=\"/a.png\" href=\"/b.html\" version:append>";
        final String expected = "<img src=\"/a.png?version=1\" href=\"/b.html\">";
        Assertions.assertEquals(expected, engine.process(template, new Context()), "src takes precedence over href");
    }

    @Test
    public void theVersionGoesBeforeTheFragment() {
        final TemplateEngine engine = createEngine();
        final String template = "<a href=\"/page?x=1#section\" version:append></a>";
        final String expected = "<a href=\"/page?x=1&version=1#section\"></a>";
        Assertions.assertEquals(expected, engine.process(template, new Context()), "the version is part of the query, not of the fragment");
    }

    @Test
    public void absoluteUrlsAreVersionedToo() {
        final TemplateEngine engine = createEngine();
        final String template = "<script src=\"https://cdn.example.com/lib.js\" version:append></script>";
        final String expected = "<script src=\"https://cdn.example.com/lib.js?version=1\"></script>";
        Assertions.assertEquals(expected, engine.process(template, new Context()), "scheme and host are kept");
    }

    @Test
    public void theVersionIsSuppliedOnEveryElement() {
        final AtomicInteger calls = new AtomicInteger();
        final TemplateEngine engine = new SpringTemplateEngine();
        engine.addDialect(new VersionedResourceDialect(() -> String.valueOf(calls.incrementAndGet())));
        final String template = "<script src=\"/a.js\" version:append></script><script src=\"/b.js\" version:append></script>";
        final String expected = "<script src=\"/a.js?version=1\"></script><script src=\"/b.js?version=2\"></script>";
        Assertions.assertEquals(expected, engine.process(template, new Context()), "the supplier is asked for each element, so the version can change at runtime");
    }
}
