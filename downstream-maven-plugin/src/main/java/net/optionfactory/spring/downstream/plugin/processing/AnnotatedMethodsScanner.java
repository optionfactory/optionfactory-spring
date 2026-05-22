package net.optionfactory.spring.downstream.plugin.processing;

import io.github.classgraph.ScanResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.downstream.Downstream;

public class AnnotatedMethodsScanner {

    public record AnnotatedMethod(Method method, Downstream.Method annotation) {

    }

    public List<AnnotatedMethod> scan(ScanResult scanResult) {
        final var methods = new ArrayList<AnnotatedMethod>();
        final var classInfoList = scanResult.getClassesWithMethodAnnotation(Downstream.Method.class.getName());
        for (final var classInfo : classInfoList) {
            for (final var method : classInfo.loadClass().getDeclaredMethods()) {
                final var annotation = method.getAnnotation(Downstream.Method.class);
                if (annotation != null) {
                    methods.add(new AnnotatedMethod(method, annotation));
                }
            }
        }
        return methods;
    }
}
