package net.optionfactory.spring.problems.web.upstream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.HttpStatus;
import net.optionfactory.spring.problems.web.upstream.UpstreamProblems.MapContext.MapContextList;

public interface UpstreamProblems {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Forward {

        String upstream() default "";

        String endpoint() default "";

        HttpStatus source() default HttpStatus.BAD_REQUEST;

        HttpStatus target();

        boolean problems() default true;
    }
    
    public enum MapMode {
        REGEX_FIRST, REGEX_ALL, STRING_ALL;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(MapContextList.class)
    public @interface MapContext {

        String upstream() default "";

        String endpoint() default "";

        MapMode mode() default MapMode.STRING_ALL;
        
        String source();

        String target() default "";

        @Documented
        @Target(value = ElementType.METHOD)
        @Retention(value = RetentionPolicy.RUNTIME)
        public static @interface MapContextList {

            MapContext[] value();
        }

    }
}
