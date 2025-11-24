package net.optionfactory.spring.thymeleaf.jackson3;

import java.util.function.Consumer;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.standard.StandardDialect;
import tools.jackson.databind.json.JsonMapper;

public class ThymeleafWithJackson3 {

    public static SpringTemplateEngine configure(SpringTemplateEngine te, Consumer<JsonMapper.Builder> customizer) {
        te.getDialects().stream()
                .filter(StandardDialect.class::isInstance)
                .map(StandardDialect.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Thymeleaf StandardDialect not found"))
                .setJavaScriptSerializer(new Jackson3JavascriptSerializer(customizer));
        return te;
    }

    public static SpringTemplateEngine configure(SpringTemplateEngine te) {
        return ThymeleafWithJackson3.configure(te, builder -> {
        });
    }

}
