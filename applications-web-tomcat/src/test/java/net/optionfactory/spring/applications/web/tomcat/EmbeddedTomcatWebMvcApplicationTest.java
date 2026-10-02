package net.optionfactory.spring.applications.web.tomcat;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.ApplicationPropertiesImportSelector;
import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.ScanForControllersBeanRegistrar;
import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.TomcatDefaultsBeanRegistrar;
import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.TomcatDefaultsCustomizer;
import net.optionfactory.spring.applications.web.tomcat.scan.ScanRoot;
import net.optionfactory.spring.applications.web.tomcat.scan.sub.SubpackageController;
import net.optionfactory.spring.context.propertysources.ApplicationPropertiesConfig;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

public class EmbeddedTomcatWebMvcApplicationTest {

    @EmbeddedTomcatWebMvcApplication
    public static class Defaults {
    }

    @EmbeddedTomcatWebMvcApplication(
            port = "${http.port}",
            sameSite = "${same.site}",
            remoteIpValve = "${behind.proxy}",
            useApplicationProperties = "${use.properties}",
            multipartMaxFileSize = "${max.file}",
            multipartMaxRequestSize = "${max.request}"
    )
    public static class Placeholders {
    }

    @EmbeddedTomcatWebMvcApplication(sameSite = "lax")
    public static class LowercaseSameSite {
    }

    private static MockEnvironment placeholders() {
        return new MockEnvironment()
                .withProperty("http.port", "9123")
                .withProperty("same.site", "STRICT")
                .withProperty("behind.proxy", "true")
                .withProperty("use.properties", "false")
                .withProperty("max.file", "1KB")
                .withProperty("max.request", "2KB");
    }

    private static DefaultListableBeanFactory register(Class<?> config, MockEnvironment env) {
        final var registry = new DefaultListableBeanFactory();
        new TomcatDefaultsBeanRegistrar(env).registerBeanDefinitions(AnnotationMetadata.introspect(config), registry);
        return registry;
    }

    private static TomcatServletWebServerFactory customized(TomcatDefaultsCustomizer customizer) {
        final var factory = new TomcatServletWebServerFactory();
        customizer.customize(factory);
        return factory;
    }

    @Test
    public void defaultsRegisterTheTomcatCustomizerAndTheMultipartBeans() {
        final var registry = register(Defaults.class, new MockEnvironment());
        Assertions.assertEquals(
                Set.of("defaultTomcatCustomizer", "multipartResolver", "multipartConfigElement"),
                Set.of(registry.getBeanDefinitionNames()),
                "the registrar registers the tomcat customizer, the multipart resolver and its config");
        Assertions.assertInstanceOf(StandardServletMultipartResolver.class, registry.getBean("multipartResolver"), "multipart is handled by the servlet container");

        final var multipart = registry.getBean(MultipartConfigElement.class);
        Assertions.assertEquals(20L * 1024 * 1024, multipart.getMaxFileSize(), "files are limited to 20MB by default");
        Assertions.assertEquals(100L * 1024 * 1024, multipart.getMaxRequestSize(), "requests are limited to 100MB by default");

        final var factory = customized(registry.getBean(TomcatDefaultsCustomizer.class));
        Assertions.assertEquals(8080, factory.getPort(), "the default port is 8080");
        Assertions.assertTrue(factory.getEngineValves().isEmpty(), "the remote ip valve is off by default");
        Assertions.assertTrue(factory.getSettings().isRegisterDefaultServlet(), "the default servlet is registered by default");
        Assertions.assertEquals(SameSite.LAX, factory.getSettings().getCookieSameSiteSuppliers().get(0).getSameSite(new Cookie("session", "x")), "cookies are SameSite=Lax by default");
    }

    @Test
    public void attributesAreResolvedAgainstTheEnvironment() {
        final var registry = register(Placeholders.class, placeholders());

        final var multipart = registry.getBean(MultipartConfigElement.class);
        Assertions.assertEquals(1024L, multipart.getMaxFileSize(), "the max file size placeholder is resolved and parsed as a data size");
        Assertions.assertEquals(2048L, multipart.getMaxRequestSize(), "the max request size placeholder is resolved and parsed as a data size");

        final var factory = customized(registry.getBean(TomcatDefaultsCustomizer.class));
        Assertions.assertEquals(9123, factory.getPort(), "the port placeholder is resolved");
        Assertions.assertEquals(SameSite.STRICT, factory.getSettings().getCookieSameSiteSuppliers().get(0).getSameSite(new Cookie("session", "x")), "the same site placeholder is resolved");
        Assertions.assertTrue(factory.getEngineValves().stream().anyMatch(v -> v instanceof RemoteIpValve), "the remote ip valve is added when enabled");
    }

    @Test
    public void unresolvablePlaceholdersFailTheRegistration() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> register(Placeholders.class, new MockEnvironment()), "attributes are resolved as required placeholders, without implicit defaults");
    }

    @Test
    public void sameSiteMustBeAnUppercaseConstantName() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> register(LowercaseSameSite.class, new MockEnvironment()), "sameSite is parsed with SameSite.valueOf, which is case sensitive");
    }

    @Test
    public void everyCustomizedTomcatServesRequestsOnVirtualThreads() {
        final var factory = customized(new TomcatDefaultsCustomizer(false, false, 0, SameSite.LAX));
        Assertions.assertEquals(1, factory.getProtocolHandlerCustomizers().size(), "a protocol handler customizer installs the virtual thread executor");
        Assertions.assertFalse(factory.getSettings().isRegisterDefaultServlet(), "the default servlet is left to the factory default (off) when disabled");
    }

    @Test
    public void theStartupListenerIsAddedToTheServerOnce() {
        final var server = new StandardServer();
        final var service = new StandardService();
        server.addService(service);
        final var engine = new StandardEngine();
        service.setContainer(engine);
        final var host = new StandardHost();
        host.setName("localhost");
        engine.addChild(host);
        final var context = new StandardContext();
        context.setName("");
        context.setPath("");
        host.addChild(context);

        final var factory = customized(new TomcatDefaultsCustomizer(false, true, 0, SameSite.LAX));
        factory.getContextCustomizers().forEach(c -> c.customize(context));
        factory.getContextCustomizers().forEach(c -> c.customize(context));

        final var listeners = Stream.of(server.findLifecycleListeners()).filter(l -> l instanceof TomcatStartupListener).count();
        Assertions.assertEquals(1, listeners, "customizing more than one context registers a single startup listener on the server");
    }

    @Test
    public void applicationPropertiesAreImportedByDefault() {
        final var imports = new ApplicationPropertiesImportSelector(new MockEnvironment()).selectImports(AnnotationMetadata.introspect(Defaults.class));
        Assertions.assertArrayEquals(new String[]{ApplicationPropertiesConfig.class.getName()}, imports, "ApplicationPropertiesConfig is imported by default");
    }

    @Test
    public void applicationPropertiesCanBeTurnedOffThroughAPlaceholder() {
        final var imports = new ApplicationPropertiesImportSelector(placeholders()).selectImports(AnnotationMetadata.introspect(Placeholders.class));
        Assertions.assertEquals(0, imports.length, "nothing is imported when useApplicationProperties resolves to false");
    }

    private static Set<String> scanned(MockEnvironment env) {
        final var registry = new DefaultListableBeanFactory();
        new ScanForControllersBeanRegistrar(env).registerBeanDefinitions(AnnotationMetadata.introspect(ScanRoot.class), registry);
        return Stream.of(registry.getBeanDefinitionNames())
                .map(name -> registry.getBeanDefinition(name).getBeanClassName())
                .filter(name -> name.startsWith(ScanRoot.class.getPackageName()))
                .collect(Collectors.toSet());
    }

    @Test
    public void controllersAndAdvicesAreScannedFromThePackageOfTheAnnotatedClassDown() {
        Assertions.assertEquals(Set.of(
                ScanRoot.PlainController.class.getName(),
                ScanRoot.ARestController.class.getName(),
                ScanRoot.PlainAdvice.class.getName(),
                ScanRoot.ARestAdvice.class.getName(),
                SubpackageController.class.getName()
        ), scanned(new MockEnvironment()), "controllers and advices, rest ones and subpackages included, are registered; other components are not");
    }

    @Test
    public void scanningCanBeTurnedOffThroughAPlaceholder() {
        Assertions.assertEquals(Set.of(), scanned(new MockEnvironment().withProperty("scan", "false")), "nothing is scanned when scanForControllers resolves to false");
    }
}
