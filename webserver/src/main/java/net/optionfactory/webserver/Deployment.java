package net.optionfactory.webserver;

import io.undertow.server.handlers.ProxyPeerAddressHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.api.ServletStackTraces;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.WebApplicationInitializer;

public interface Deployment {

    public static final String ROOT_PATH = "/";

    DeploymentInfo configuration();

    /**
     * Deployment configuration for a root web application, with resources
     * fetched from the classpath, and initialized by the given
     * {@link WebApplicationInitializer} implementations.
     *
     * @param webAppInitializers Spring initialization classes
     * @return a deployment configuration with sensible defaults
     */
    static Deployment springSelfContained(Class<? extends WebApplicationInitializer>... webAppInitializers) {
        return () -> new DeploymentInfo()
                .setDeploymentName("default")
                .setDisplayName("default")
                .setServletStackTraces(ServletStackTraces.ALL)
                .setContextPath(ROOT_PATH)
                .setClassLoader(Deployment.class.getClassLoader())
                .setResourceManager(new ClassPathResourceManager(Deployment.class.getClassLoader()))
                .addServletContainerInitializer(new ServletContainerInitializerInfo(SpringServletContainerInitializer.class, new HashSet<>(Arrays.asList(webAppInitializers))));
    }

    /**
     * Deployment configuration for a root web application, with resources
     * fetched from the given path on the local filesystem, and initialized by
     * the given {@link WebApplicationInitializer} implementations.
     *
     * @param localResources the root path from where the web application
     * resources are fetched
     * @param webAppInitializers Spring initialization classes
     * @return a deployment configuration with sensible defaults
     */
    static Deployment springWithExternalWebResources(Path localResources, Class<? extends WebApplicationInitializer>... webAppInitializers) {
        return () -> springSelfContained(webAppInitializers).configuration().setResourceManager(new FileResourceManager(localResources.toFile()));
    }

    /**
     * Application runs behind a reverse proxy and X-Forwarded-* HTTP headers
     * should be read. It is insecure to run this configuration when not behind
     * a proxy that sets such header, because a peer address can be forged.
     *
     * @return a modified copy of the deployment configuration
     */
    default Deployment behindProxy() {
        return () -> configuration().addInitialHandlerChainWrapper(new ProxyPeerAddressHandler.Builder().build(Collections.emptyMap()));
    }

    /**
     * Adds overrides to web application properties.
     *
     * @param properties the overriding properties
     * @return a modified copy of the deployment configuration
     */
    default Deployment withProperties(Map<String, String> properties) {
        return () -> {
            final DeploymentInfo info = configuration();
            info.getInitParameters().putAll(properties);
            return info;
        };
    }

    /**
     * Adds an additional Spring property source, e.g.
     * {@link org.springframework.core.env.SimpleCommandLinePropertySource SimpleCommandLinePropertySource}
     * for overriding application properties from the command line.
     *
     * @param propertySource the overriding properties
     * @return a modified copy of the deployment configuration
     */
    default Deployment withProperties(EnumerablePropertySource propertySource) {
        final Map<String, String> properties = Arrays.stream(propertySource.getPropertyNames()).collect(Collectors.toMap(Function.identity(), name -> Objects.toString(propertySource.getProperty(name))));
        return withProperties(properties);
    }
}
