package net.optionfactory.spring.applications.web.tomcat;

import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.TomcatDefaultsCustomizer;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Cookie.SameSite;

/// Logs, at `INFO`, the Tomcat version, the operating system, the JVM, the `catalina.base` and
/// `catalina.home` directories and the service settings when the server is about to initialize, so
/// that every startup records what it ran on.
///
/// Registered on the server by [EmbeddedTomcatWebMvcApplication.TomcatDefaultsCustomizer]. It logs
/// through the logger of `TomcatDefaultsCustomizer`, not its own.
public class TomcatStartupListener implements LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(TomcatDefaultsCustomizer.class);
    private final boolean useRemoteIpValve;
    private final boolean registerDefaultServlet;
    private final int port;
    private final SameSite sameSite;

    /// The settings to report: logged as given, not read back from Tomcat.
    ///
    /// @param useRemoteIpValve whether the remote ip valve is in use
    /// @param registerDefaultServlet whether the default servlet is registered
    /// @param port the service port
    /// @param sameSite the `SameSite` cookie policy
    public TomcatStartupListener(boolean useRemoteIpValve, boolean registerDefaultServlet, int port, SameSite sameSite) {
        this.useRemoteIpValve = useRemoteIpValve;
        this.registerDefaultServlet = registerDefaultServlet;
        this.port = port;
        this.sameSite = sameSite;
    }

    /// Logs on `before_init`, ignoring every other event.
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (!Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            return;
        }
        logger.info("Server version name:            {}", ServerInfo.getServerInfo());
        logger.info("Server build time:              {}", ServerInfo.getServerBuilt());
        logger.info("Server version number:          {}", ServerInfo.getServerNumber());
        logger.info("OS name:                        {}", System.getProperty("os.name"));
        logger.info("OS version:                     {}", System.getProperty("os.version"));
        logger.info("OS architecture:                {}", System.getProperty("os.arch"));
        logger.info("Java home:                      {}", System.getProperty("java.home"));
        logger.info("JVM Version:                    {}", System.getProperty("java.runtime.version"));
        logger.info("JVM Vendor:                     {}", System.getProperty("java.vm.vendor"));
        logger.info("CATALINA_BASE:                  {}", System.getProperty("catalina.base"));
        logger.info("CATALINA_HOME:                  {}", System.getProperty("catalina.home"));
        logger.info("Service port:                   {}", port);
        logger.info("Service SameSite cookie policy: {}", sameSite);
        logger.info("Service using remote ip valve:  {}", useRemoteIpValve);
        logger.info("Service using default servlet:  {}", registerDefaultServlet);
    }

}
