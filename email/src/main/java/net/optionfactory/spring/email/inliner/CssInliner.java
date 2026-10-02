package net.optionfactory.spring.email.inliner;

import com.steadystate.css.parser.CSSOMParser;
import com.steadystate.css.parser.SACParserCSS3;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.optionfactory.spring.email.HtmlBodyPostprocessor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.css.CSSRuleList;
import org.w3c.dom.css.CSSStyleRule;

/// Moves css rules into `style` attributes, for the many mail clients that ignore `<style>` elements.
///
/// Only `<style data-inlined>` elements are inlined, and removed from the document; other `<style>`
/// elements are kept as they are, which is where rules that cannot be inlined belong, such as media
/// queries:
///
/// ```html
/// <style data-inlined>.warning { color: red; }</style>
/// <style>@media (max-width: 600px) { .warning { font-size: 12px; } }</style>
/// <p class="warning">careful</p>
/// ```
///
/// becomes `<p class="warning" style="color:red;">careful</p>`, the second style element staying
/// in place. The inlining is deliberately simple:
///
/// - selectors are matched with jsoup, so a selector jsoup cannot parse, such as a pseudo-class
///   like `:hover`, fails the whole postprocessing with a jsoup `SelectorParseException`;
/// - specificity is ignored: when several rules set the same property on an element, the last
///   one in the stylesheet wins;
/// - `!important` is dropped;
/// - rules other than style rules (e.g. `@media`, `@font-face`) in a `data-inlined` element are
///   discarded;
/// - declarations already in an element's `style` attribute are kept after the inlined ones, so
///   they win.
///
/// The result is a whole html document (`<html>`, `<head>` and `<body>` are added when missing),
/// pretty-printed.
///
/// An instance is thread-safe: each postprocessing uses its own css parser, so one inliner can be
/// shared, e.g. by a [net.optionfactory.spring.email.EmailMessage.Prototype], between threads
/// rendering emails concurrently.
public class CssInliner implements HtmlBodyPostprocessor {

    /// @param html the html to inline, never `null`
    /// @return the html document with the inlined styles
    /// @throws NullPointerException when `html` is `null`
    @Override
    public String postprocess(String html) {
        try {
            final var document = Jsoup.parse(html);
            final var tagsAndRules = styleTagsAndRules(document);
            final var elToStyles = calcStyles(tagsAndRules.rules(), document);
            applyStyles(elToStyles, tagsAndRules);
            document.outputSettings().indentAmount(2);
            return document.html();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void applyStyles(final Map<Element, Map<String, String>> elToStyles, final StyleTagsAndRules tagsAndRules) {
        for (final var elAndStyles : elToStyles.entrySet()) {
            final var el = elAndStyles.getKey();
            final var builder = new StringBuilder();
            for (final var style : elAndStyles.getValue().entrySet()) {
                builder.append(style.getKey()).append(":").append(style.getValue()).append(";");
            }
            builder.append(el.attr("style"));
            el.attr("style", builder.toString());
        }
        tagsAndRules.tags().remove();
    }

    private Map<Element, Map<String, String>> calcStyles(CSSRuleList cssRules, Document document) {
        final var result = new HashMap<Element, Map<String, String>>();
        for (int ri = 0; ri != cssRules.getLength(); ri++) {
            final var rule = cssRules.item(ri);
            if (rule instanceof CSSStyleRule styleRule) {
                for (final var el : document.select(styleRule.getSelectorText())) {
                    final var elStyles = result.computeIfAbsent(el, k -> new LinkedHashMap<>());
                    final var style = styleRule.getStyle();
                    for (int pi = 0; pi != style.getLength(); pi++) {
                        final var k = style.item(pi);
                        String v = style.getPropertyValue(k);
                        elStyles.put(k, v);
                    }
                }
            }
        }
        return result;
    }

    /// The `data-inlined` style elements of a document and the css rules they contain.
    ///
    /// @param tags the style elements, removed from the document once inlined
    /// @param rules the rules of all the style elements, in document order
    public record StyleTagsAndRules(Elements tags, CSSRuleList rules) {

    }

    private StyleTagsAndRules styleTagsAndRules(final Document document) throws IOException {
        final var styleEls = document.getElementsByTag("style")
                .stream()
                .filter(e -> e.hasAttr("data-inlined"))
                .collect(Collectors.toCollection(Elements::new));
        final var stylesTexts = styleEls.stream()
                .map(n -> n.data())
                .collect(Collectors.joining("\r\n"));
        styleEls.remove();
        try (final var r = new StringReader(stylesTexts)) {
            return new StyleTagsAndRules(styleEls, new CSSOMParser(new SACParserCSS3()).parseStyleSheet(new InputSource(r), null, null).getCssRules());
        }

    }
}
