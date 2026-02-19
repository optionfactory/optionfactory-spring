package net.optionfactory.spring.applications.web.tomcat;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EmbeddedTomcatWebMvcApplication(port = "${port:9010}")
public class EmbeddedTomcatExample implements WebMvcConfigurer {

    public static void main(String[] args) {
        final var app = new SpringApplication(EmbeddedTomcatExample.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }
}
