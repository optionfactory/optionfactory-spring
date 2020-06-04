/**
 * Undertow embedded web server running a single web application.
 * 
 * <pre><code>
package net.optionfactory.archetype.grayskull;

import io.undertow.servlet.api.DeploymentInfo;
import java.nio.file.Path;
import java.util.Optional;
import javax.servlet.ServletException;
import net.optionfactory.context.propertysources.ApplicationPropertiesConfig;
import net.optionfactory.webserver.Deployment;
import net.optionfactory.webserver.WebServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.SimpleCommandLinePropertySource;

public class Main {

    public static void main(String[] args) throws ServletException {
        final AnnotationConfigApplicationContext mainContext = new AnnotationConfigApplicationContext();
        mainContext.getEnvironment().getPropertySources().addFirst(new SimpleCommandLinePropertySource(args));
        mainContext.register(Main.Config.class);
        mainContext.refresh();
        mainContext.registerShutdownHook();
    }

    @Configuration
    @Import(ApplicationPropertiesConfig.class)
    public static class Config {

        @Bean
        public WebServer webServer(
                AnnotationConfigApplicationContext applicationContext,
                @Value("${server.bind}") String listeningAddress,
                @Value("${server.port}") int listeningPort,
                @Value("${server.local.resources.path:#{null}}") Optional<String> localResourcesPath,
                @Value("${server.behind.proxy:false}") boolean behindProxy
        ) throws ServletException {
            final SimpleCommandLinePropertySource externalPropertySource = (SimpleCommandLinePropertySource) applicationContext.getEnvironment().getPropertySources().get(SimpleCommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME);
            final DeploymentInfo deployment = localResourcesPath
                    .map(String::trim)
                    .filter(path -> !path.isEmpty())
                    .map(path -> Deployment.springWithExternalWebResources(Path.of(path), WebApplication.class))
                    .orElse(Deployment.springSelfContained(WebApplication.class));
            return new WebServer(listeningAddress, listeningPort, (behindProxy ? deployment.behindProxy() : deployment).withProperties(externalPropertySource));
        }
    }
}
 * </code></pre>
 */
package net.optionfactory.spring.webserver;
