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

public class CssInliner implements HtmlBodyPostprocessor {

    private final CSSOMParser cssParser = new CSSOMParser(new SACParserCSS3());

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
            return new StyleTagsAndRules(styleEls, cssParser.parseStyleSheet(new InputSource(r), null, null).getCssRules());
        }

    }
}
