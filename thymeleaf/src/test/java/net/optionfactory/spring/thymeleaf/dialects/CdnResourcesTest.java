package net.optionfactory.spring.thymeleaf.dialects;

import java.net.URI;
import org.junit.Assert;
import org.junit.Test;

public class CdnResourcesTest {

    @Test
    public void canCreateAnUrl() {
        CdnResources cdn = new CdnResources(URI.create("https://cdn.test.com/"));
        String url = cdn.url("folder/id.png");
        Assert.assertEquals("https://cdn.test.com/folder/id.png", url);
    }

    @Test
    public void handlesDoubleSlashes() {
        CdnResources cdn = new CdnResources(URI.create("https://cdn.test.com/"));
        String url = cdn.url("/folder/id.png");
        Assert.assertEquals("https://cdn.test.com/folder/id.png", url);
    }

    @Test
    public void handlesMissingSlashes() {
        CdnResources cdn = new CdnResources(URI.create("https://cdn.test.com"));
        String url = cdn.url("folder/id.png");
        Assert.assertEquals("https://cdn.test.com/folder/id.png", url);
    }

    @Test
    public void handlesPathInBaseUri() {
        CdnResources cdn = new CdnResources(URI.create("https://cdn.test.com/test"));
        String url = cdn.url("folder/id.png");
        Assert.assertEquals("https://cdn.test.com/test/folder/id.png", url);
    }

    @Test
    public void handlesPathInBaseUriWithDoubleSlashes() {
        CdnResources cdn = new CdnResources(URI.create("https://cdn.test.com/test/"));
        String url = cdn.url("/folder/id.png");
        Assert.assertEquals("https://cdn.test.com/folder/id.png", url);
    }

    @Test
    public void handlesPathInBaseUriWithMissingSlashes() {
        CdnResources cdn = new CdnResources(URI.create("https://cdn.test.com/test"));
        String url = cdn.url("folder/id.png");
        Assert.assertEquals("https://cdn.test.com/test/folder/id.png", url);
    }
}
