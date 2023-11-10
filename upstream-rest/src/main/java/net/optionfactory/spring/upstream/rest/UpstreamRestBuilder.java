package net.optionfactory.spring.upstream.rest;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.springframework.web.client.RestClient;

public class UpstreamRestBuilder extends UpstreamBuilder {

    @Override
    protected void configureRestClient(RestClient.Builder rcb) {

    }

    public static UpstreamRestBuilder create() {
        return new UpstreamRestBuilder();
    }
}
