package net.optionfactory.spring.applications.web.tomcat;

import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication.TomcatDefaultsCustomizer;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Cookie.SameSite;

public class TomcatStartupListener implements LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(TomcatDefaultsCustomizer.class);
    private final boolean useRemoteIpValve;
    private final boolean registerDefaultServlet;
    private final int port;
    private final SameSite sameSite;

    public TomcatStartupListener(boolean useRemoteIpValve, boolean registerDefaultServlet, int port, SameSite sameSite) {
        this.useRemoteIpValve = useRemoteIpValve;
        this.registerDefaultServlet = registerDefaultServlet;
        this.port = port;
        this.sameSite = sameSite;
    }

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
