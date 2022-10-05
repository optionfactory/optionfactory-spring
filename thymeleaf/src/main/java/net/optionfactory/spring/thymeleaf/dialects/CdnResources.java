package net.optionfactory.spring.thymeleaf.dialects;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;

public class CdnResources {

    private final URI baseUri;

    public CdnResources(URI baseUri) {
        final String s = baseUri.toString();
        this.baseUri = s.endsWith("/") ? baseUri : URI.create(s + "/");
    }

    public String url(String... parts) {
        final var path = Arrays.stream(parts).collect(Collectors.joining("/"));
        return baseUri.resolve(path).toString();
    }
}
