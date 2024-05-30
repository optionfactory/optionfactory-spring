package net.optionfactory.spring.email;

public interface HtmlBodyPostprocessor {
    String postprocess(String body);
}
