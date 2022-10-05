package net.optionfactory.spring.thymeleaf.dialects;

import java.net.URI;

public class CdnResources {

    private final URI baseUri;

    public CdnResources(URI baseUri) {
        final String s = baseUri.toString();
        this.baseUri = s.endsWith("/") ? baseUri : URI.create(s + "/");
    }

    public String url(String resource) {
        return baseUri.resolve(resource).toString();
    }
}
