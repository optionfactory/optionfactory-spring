package net.optionfactory.spring.applications.web.tomcat.scan;

import net.optionfactory.spring.applications.web.tomcat.EmbeddedTomcatWebMvcApplication;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@EmbeddedTomcatWebMvcApplication(scanForControllers = "${scan:true}")
public class ScanRoot {

    @Controller
    public static class PlainController {
    }

    @RestController
    public static class ARestController {
    }

    @ControllerAdvice
    public static class PlainAdvice {
    }

    @RestControllerAdvice
    public static class ARestAdvice {
    }

    @Service
    public static class NotAController {
    }
}
