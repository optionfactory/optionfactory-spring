package net.optionfactory.spring.applications.web.tomcat;

import jakarta.servlet.MultipartConfigElement;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.stream.Stream;
import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.ApplicationPropertiesImportSelector;
import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.ScanForControllersBeanRegistrar;
import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.TomcatDefaultsBeanRegistrar;
import net.optionfactory.spring.context.devtools.DevToolsImportSelector;
import net.optionfactory.spring.context.propertysources.ApplicationPropertiesConfig;
import org.apache.catalina.Engine;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

/**
 * Configuration for an embedded Tomcat spring-web-mvc application. This
 * annotation enables and configures:
 * <ul>
 * <li>Spring dev tools (autodetected)
 * <li>The spring dispatcher servlet
 * <li>The embedded tomcat
 * <li>{@code @EnableWebMvc}
 * <li>{@code @ApplicationProperties} when {@code useApplicationProperties()} is
 * true (default: true)
 * <li>The RemoteIpValve if {@code remoteIpValve()} is true (default: false)
 * <li>The Tomcat CookieSameSiteSupplier via {@code sameSite()} (default:
 * SameSite.LAX)
 * <li>A StandardServletMultipartResolver bean with
 * {@code multipartMaxFileSize()} (default: 20MB) and
 * {@code multpartMaxRequestSize()} (default: 100MB)
 * <li>The service listening {@code port()} (default: 8080)
 * <li>Classpath scan for {@code @Controller}s and {@code @ControllerAdvice}s
 * when {@code scanForControllers()} is true (default: true).
 * </ul>
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
    DevToolsImportSelector.class,
    DispatcherServletAutoConfiguration.class,
    TomcatServletWebServerAutoConfiguration.class,
    DelegatingWebMvcConfiguration.class,
    ApplicationPropertiesImportSelector.class,
    TomcatDefaultsBeanRegistrar.class,
    ScanForControllersBeanRegistrar.class
})
public @interface EmbeddedTomcatWebMvcApplication {

    String sameSite() default "LAX";

    String port() default "8080";

    String remoteIpValve() default "false";

    String defaultServlet() default "true";

    String useApplicationProperties() default "true";

    String scanForControllers() default "true";

    String multipartMaxFileSize() default "20MB";

    String multipartMaxRequestSize() default "100MB";

    public static class ApplicationPropertiesImportSelector implements ImportSelector {

        private final Environment environment;

        public ApplicationPropertiesImportSelector(Environment environment) {
            this.environment = environment;
        }

        @Override
        public String[] selectImports(AnnotationMetadata importingClass) {
            final var attrs = AnnotationAttributes.fromMap(importingClass.getAnnotationAttributes(EmbeddedTomcatWebMvcApplication.class.getName()));
            final var toBeImported = new ArrayList<String>();

            if (Boolean.parseBoolean(environment.resolveRequiredPlaceholders(attrs.getString("useApplicationProperties")))) {
                toBeImported.add(ApplicationPropertiesConfig.class.getName());
            }
            return toBeImported.toArray(i -> new String[i]);
        }

    }

    public static class TomcatDefaultsBeanRegistrar implements ImportBeanDefinitionRegistrar {

        private final Environment environment;

        public TomcatDefaultsBeanRegistrar(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClass, BeanDefinitionRegistry registry) {

            final var attrs = AnnotationAttributes.fromMap(importingClass.getAnnotationAttributes(EmbeddedTomcatWebMvcApplication.class.getName()));

            final var useRemoteIpValve = Boolean.parseBoolean(environment.resolveRequiredPlaceholders(attrs.getString("remoteIpValve")));
            final var registerDefaultServlet = Boolean.parseBoolean(environment.resolveRequiredPlaceholders(attrs.getString("defaultServlet")));
            final var port = Integer.parseInt(environment.resolveRequiredPlaceholders(attrs.getString("port")));
            final var sameSite = SameSite.valueOf(environment.resolveRequiredPlaceholders(attrs.getString("sameSite")));
            final var multipartMaxFileSize = DataSize.parse(environment.resolveRequiredPlaceholders(attrs.getString("multipartMaxFileSize")));
            final var multipartMaxRequestSize = DataSize.parse(environment.resolveRequiredPlaceholders(attrs.getString("multipartMaxRequestSize")));

            registry.registerBeanDefinition("defaultTomcatCustomizer", BeanDefinitionBuilder
                    .genericBeanDefinition(TomcatDefaultsCustomizer.class, () -> {
                        return new TomcatDefaultsCustomizer(useRemoteIpValve, registerDefaultServlet, port, sameSite);
                    })
                    .getBeanDefinition()
            );

            registry.registerBeanDefinition("multipartResolver", BeanDefinitionBuilder
                    .genericBeanDefinition(StandardServletMultipartResolver.class, StandardServletMultipartResolver::new).getBeanDefinition()
            );
            registry.registerBeanDefinition("multipartConfigElement", BeanDefinitionBuilder
                    .genericBeanDefinition(MultipartConfigElement.class, () -> {
                        final var factory = new MultipartConfigFactory();
                        factory.setMaxFileSize(multipartMaxFileSize);
                        factory.setMaxRequestSize(multipartMaxRequestSize);
                        return factory.createMultipartConfig();
                    }).getBeanDefinition()
            );

        }

    }

    public static class ScanForControllersBeanRegistrar implements ImportBeanDefinitionRegistrar {

        private final Environment environment;

        public ScanForControllersBeanRegistrar(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClass, BeanDefinitionRegistry registry) {
            final var attrs = AnnotationAttributes.fromMap(importingClass.getAnnotationAttributes(EmbeddedTomcatWebMvcApplication.class.getName()));
            if (!Boolean.parseBoolean(environment.resolveRequiredPlaceholders(attrs.getString("scanForControllers")))) {
                return;
            }

            final var scanner = new ClassPathBeanDefinitionScanner(registry, false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
            scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));
            scanner.scan(ClassUtils.getPackageName(importingClass.getClassName()));
        }

    }

    @Order(1)
    public static class TomcatDefaultsCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

        private final boolean useRemoteIpValve;
        private final boolean registerDefaultServlet;
        private final int port;
        private final SameSite sameSite;

        public TomcatDefaultsCustomizer(boolean useRemoteIpValve, boolean registerDefaultServlet, int port, SameSite sameSite) {
            this.useRemoteIpValve = useRemoteIpValve;
            this.registerDefaultServlet = registerDefaultServlet;
            this.port = port;
            this.sameSite = sameSite;
        }

        @Override
        public void customize(TomcatServletWebServerFactory factory) {
            factory.addContextCustomizers(context -> {
                if (context.getParent().getParent() instanceof Engine e) {
                    final var server = e.getService().getServer();
                    if (!Stream.of(server.findLifecycleListeners()).anyMatch(l -> l instanceof TomcatStartupListener)) {
                        server.addLifecycleListener(new TomcatStartupListener(useRemoteIpValve, registerDefaultServlet, port, sameSite));
                    }
                }
            });
            factory.setPort(port);
            if (sameSite != null) {
                factory.addCookieSameSiteSuppliers(CookieSameSiteSupplier.of(sameSite));
            }
            if (useRemoteIpValve) {
                factory.addEngineValves(new RemoteIpValve());
            }
            if (registerDefaultServlet) {
                factory.setRegisterDefaultServlet(true);
            }
            factory.addProtocolHandlerCustomizers(phc -> {
                phc.setExecutor(new VirtualThreadExecutor("tomcat-handler-"));
            });
        }

    }
}
