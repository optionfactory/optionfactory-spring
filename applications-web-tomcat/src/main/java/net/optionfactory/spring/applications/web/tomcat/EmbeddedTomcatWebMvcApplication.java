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
import net.optionfactory.spring.context.fieldaccess.EnableCustomWebMvc.CustomizableDelegatingWebMvcConfiguration;
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

/// Turns the annotated class into an embedded Tomcat spring-web-mvc application, importing a
/// fixed set of configurations instead of enabling spring boot's auto-configuration:
/// - spring boot devtools, when on the classpath (see `DevToolsImportSelector`);
/// - the `DispatcherServlet` and embedded Tomcat auto-configurations of spring boot;
/// - `@EnableCustomWebMvc`'s configuration (direct field access binding, custom locale
///   resolvers): records cannot be bound from request parameters, see its documentation;
/// - `ApplicationPropertiesConfig`, unless [#useApplicationProperties()] is `false`;
/// - a `WebServerFactoryCustomizer` ([TomcatDefaultsCustomizer]) applying [#port()],
///   [#sameSite()], [#remoteIpValve()] and [#defaultServlet()], and serving requests on virtual
///   threads;
/// - a `StandardServletMultipartResolver`, limited by [#multipartMaxFileSize()] and
///   [#multipartMaxRequestSize()];
/// - the `@Controller`s and `@ControllerAdvice`s (rest ones included) in the package of the
///   annotated class and its subpackages, unless [#scanForControllers()] is `false`. Other
///   components are not scanned.
///
/// Every attribute is a string resolved against the `Environment`, so it can be a `${...}`
/// placeholder; a placeholder that cannot be resolved fails the startup, so give it a default
/// (`${port:8080}`) when the property may be missing. Boolean attributes are `true` only when
/// they resolve to `true`, ignoring case.
///
/// ```java
/// @EmbeddedTomcatWebMvcApplication(port = "${http.port:8080}", remoteIpValve = "true")
/// public class Application {
///
///     public static void main(String[] args) {
///         new SpringApplication(Application.class).run(args);
///     }
/// }
/// ```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
    DevToolsImportSelector.class,
    DispatcherServletAutoConfiguration.class,
    TomcatServletWebServerAutoConfiguration.class,
    CustomizableDelegatingWebMvcConfiguration.class,
    ApplicationPropertiesImportSelector.class,
    TomcatDefaultsBeanRegistrar.class,
    ScanForControllersBeanRegistrar.class
})
public @interface EmbeddedTomcatWebMvcApplication {

    /// The `SameSite` attribute of every cookie set by the application.
    ///
    /// @return one of `NONE`, `LAX`, `STRICT`, `OMITTED` (no attribute), upper case; another
    /// value fails the startup
    String sameSite() default "LAX";

    /// @return the port Tomcat listens on, `0` for a random free port
    String port() default "8080";

    /// Whether Tomcat's `RemoteIpValve` is added, with its defaults: the client address and scheme
    /// of a request are taken from its `X-Forwarded-For` and `X-Forwarded-Proto` headers when it
    /// comes from a loopback, link-local or private network address, i.e. from a reverse proxy
    /// on the internal network. Enable it only behind such a proxy, which must overwrite the
    /// headers sent by the client.
    ///
    /// @return `true` to add the valve
    String remoteIpValve() default "false";

    /// Whether Tomcat's default servlet, which serves static resources of the web application, is
    /// registered.
    ///
    /// @return `true` to register it, otherwise the factory default (not registered) applies
    String defaultServlet() default "true";

    /// Whether `ApplicationPropertiesConfig` is imported, registering the application property
    /// sources and the `${...}` placeholder resolution of `@Value`s.
    ///
    /// Resolved while the configuration is parsed, so it cannot come from the application
    /// properties themselves.
    ///
    /// @return `true` to import it
    String useApplicationProperties() default "true";

    /// @return `true` to register the `@Controller`s and `@ControllerAdvice`s found in the package
    /// of the annotated class and its subpackages
    String scanForControllers() default "true";

    /// @return the maximum size of an uploaded file, as a spring `DataSize` (`512KB`, `20MB`; a
    /// bare number is in bytes)
    String multipartMaxFileSize() default "20MB";

    /// @return the maximum size of a multipart request, all its parts included, as a spring
    /// `DataSize`
    String multipartMaxRequestSize() default "100MB";

    /// Imports `ApplicationPropertiesConfig` according to [#useApplicationProperties()].
    public static class ApplicationPropertiesImportSelector implements ImportSelector {

        private final Environment environment;

        /// @param environment resolves the attribute placeholders
        public ApplicationPropertiesImportSelector(Environment environment) {
            this.environment = environment;
        }

        /// @param importingClass the class annotated with [EmbeddedTomcatWebMvcApplication]
        /// @return `ApplicationPropertiesConfig`, or nothing when the attribute is not `true`
        /// @throws IllegalArgumentException when the attribute has an unresolvable placeholder
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

    /// Registers the `defaultTomcatCustomizer` ([TomcatDefaultsCustomizer]), `multipartResolver`
    /// and `multipartConfigElement` beans from the annotation attributes.
    public static class TomcatDefaultsBeanRegistrar implements ImportBeanDefinitionRegistrar {

        private final Environment environment;

        /// @param environment resolves the attribute placeholders
        public TomcatDefaultsBeanRegistrar(Environment environment) {
            this.environment = environment;
        }

        /// @param importingClass the class annotated with [EmbeddedTomcatWebMvcApplication]
        /// @param registry where the beans are registered
        /// @throws IllegalArgumentException when an attribute has an unresolvable placeholder or
        /// does not parse: a port that is not an integer, an unknown `sameSite`, a malformed size
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

    /// Registers the controllers and controller advices of the application according to
    /// [#scanForControllers()].
    public static class ScanForControllersBeanRegistrar implements ImportBeanDefinitionRegistrar {

        private final Environment environment;

        /// @param environment resolves the attribute placeholders and evaluates the conditions
        /// (e.g. `@Profile`) of the scanned classes
        public ScanForControllersBeanRegistrar(Environment environment) {
            this.environment = environment;
        }

        /// Scans the package of the importing class and its subpackages for classes annotated,
        /// directly or through a meta-annotation, with `@Controller` or `@ControllerAdvice`. The
        /// annotation config processors are registered too, as with any classpath scan.
        ///
        /// @param importingClass the class annotated with [EmbeddedTomcatWebMvcApplication]
        /// @param registry where the beans are registered
        /// @throws IllegalArgumentException when the attribute has an unresolvable placeholder
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClass, BeanDefinitionRegistry registry) {
            final var attrs = AnnotationAttributes.fromMap(importingClass.getAnnotationAttributes(EmbeddedTomcatWebMvcApplication.class.getName()));
            if (!Boolean.parseBoolean(environment.resolveRequiredPlaceholders(attrs.getString("scanForControllers")))) {
                return;
            }

            final var scanner = new ClassPathBeanDefinitionScanner(registry, false, environment);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
            scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));
            scanner.scan(ClassUtils.getPackageName(importingClass.getClassName()));
        }

    }

    /// Applies the [EmbeddedTomcatWebMvcApplication] settings to the Tomcat factory.
    ///
    /// Besides the attributes, it makes the protocol handler run requests on virtual threads
    /// (`tomcat-handler-` named), and adds a [TomcatStartupListener] to the Tomcat server, once
    /// however many contexts are customized, to log the server and service settings at startup.
    ///
    /// Ordered at `1`, so customizers with a higher order value run after it and can override its
    /// settings.
    @Order(1)
    public static class TomcatDefaultsCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

        private final boolean useRemoteIpValve;
        private final boolean registerDefaultServlet;
        private final int port;
        private final SameSite sameSite;

        /// @param useRemoteIpValve whether to add a `RemoteIpValve` to the engine
        /// @param registerDefaultServlet whether to register the default servlet; `false` leaves
        /// the factory setting untouched
        /// @param port the port to listen on
        /// @param sameSite the `SameSite` attribute of every cookie, `null` to leave cookies alone
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
