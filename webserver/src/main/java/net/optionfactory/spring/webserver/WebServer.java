package net.optionfactory.spring.webserver;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.WebApplicationInitializer;

import javax.servlet.ServletException;
import java.io.Closeable;

/**
 * Undertow embedded web server running a single web application.
 * <p>
 * Given one or more classes that implement the Spring Web
 * {@link WebApplicationInitializer} interface, the embedded web server can be
 * instantiated with:
 * <br><br>
 * <pre><code>
 * new WebServer("0.0.0.0", 8080, Deployment.springSelfContained(OurWebApplication.class))
 * </code></pre>
 * <br><br>
 * If web resources have to be fetched from the filesystem (e.g. while
 * developing the web UI), the web server should be initialized with:
 * <br><br>
 * <pre><code>
 * new WebServer("0.0.0.0", 8080, Deployment.springWithExternalWebResources("src/main/webapp/", OurWebApplication.class))
 * </code></pre>
 *
 * @see <a href="http://undertow.io/">undertow.io</a>
 */
public class WebServer implements Closeable {

    public static final String ROOT_PATH = "/";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final DeploymentManager deploymentManager;
    private final Undertow server;

    /**
     * Starts the embedded web server. Deployment configuration can be built
     * using helper methods in {@link Deployment}.
     *
     * @param listeningAddress the address this server is bound to
     * @param listeningPort the port number where the server accepts connections
     * @param deployment deployment configuration
     * @throws ServletException
     */
    public WebServer(String listeningAddress, int listeningPort, Deployment deployment) throws ServletException {
        logger.info("Preparing Undertow for webapp deployment");
        deploymentManager = Servlets.defaultContainer().addDeployment(deployment.configuration());
        deploymentManager.deploy();
        final HttpHandler httpHandler = Handlers.path().addPrefixPath(ROOT_PATH, deploymentManager.start());
        server = Undertow.builder().addHttpListener(listeningPort, listeningAddress).setHandler(httpHandler).build();
        server.start();
        logger.info("Started Undertow, listening at {}:{}", listeningAddress, listeningPort);
    }

    /**
     * Gracefully undeploys the web application and stops the embedded web
     * server.
     * <p>
     * If the web server instance is a Spring-managed bean, this method will be
     * automatically called on context teardown. Otherwise a shutdown hook can
     * be configured with:
     * <br><br>
     * <pre><code>
     * WebServer webServer = ...
     * Runtime.getRuntime().addShutdownHook(new Thread(webServer::close));
     * </code></pre>
     */
    @Override
    public void close() {
        try {
            logger.info("Stopping Undertow...");
            server.stop();
            deploymentManager.stop();
            deploymentManager.undeploy();
            logger.info("Stopped Undertow.");
        } catch (ServletException exception) {
            logger.error("Error while shutting down web server", exception);
            throw new IllegalStateException(exception);
        }
    }
}
