package net.optionfactory.spring.downstream;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface Downstream {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Method {

        String[] value() default {};
    }

    @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Ignore {
    }
}
