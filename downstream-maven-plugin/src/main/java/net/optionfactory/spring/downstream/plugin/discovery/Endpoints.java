package net.optionfactory.spring.downstream.plugin.discovery;

import io.github.classgraph.ScanResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.optionfactory.spring.downstream.Downstream;

/// Finds the methods annotated with `@Downstream.Method` that are generated for a client.
public class Endpoints {

    private final String targetClientName;

    /// @param targetClientName the client to generate for; a method is selected when it lists no
    /// client or lists this one, and every method is selected when this is `null`
    public Endpoints(String targetClientName) {
        this.targetClientName = targetClientName;
    }

    /// Loads the classes declaring an annotated method and returns their selected methods.
    /// Only methods declared by the scanned classes are considered, inherited ones being found on
    /// the class that declares them.
    ///
    /// @param scanResult a scan with method and annotation info enabled
    /// @return the selected methods, empty when there is none
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
