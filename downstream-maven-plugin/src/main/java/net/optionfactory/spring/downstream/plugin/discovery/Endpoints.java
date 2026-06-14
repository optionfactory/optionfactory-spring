package net.optionfactory.spring.downstream.plugin.discovery;

import io.github.classgraph.ScanResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.optionfactory.spring.downstream.Downstream;

public class Endpoints {

    private final String targetClientName;

    public Endpoints(String targetClientName) {
        this.targetClientName = targetClientName;
    }

    public List<Method> discover(ScanResult scanResult) {
        final var methods = new ArrayList<Method>();
        for (final var classInfo : scanResult.getClassesWithMethodAnnotation(Downstream.Method.class.getName())) {
            final var clazz = classInfo.loadClass();
            for (final var method : clazz.getDeclaredMethods()) {
                final var annotation = method.getAnnotation(Downstream.Method.class);
                if (annotation == null) {
                    continue;
                }
                final var clients = annotation.clients();
                if (clients.length != 0 && targetClientName != null && !Arrays.asList(clients).contains(targetClientName)) {
                    continue;
                }
                methods.add(method);
            }
        }
        return methods;
    }
}
