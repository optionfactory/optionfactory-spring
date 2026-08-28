package net.optionfactory.spring.data.jpa.test.containers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.platform.commons.support.AnnotationSupport;

/// Starts the containers declared with [SharedContainer] before the first test of an annotated class and
/// registers them in the *root* [ExtensionContext.Store], so they are stopped when the JUnit engine finishes
/// executing the whole test plan, i.e. after the last test has run.
///
/// Registered automatically via `@ExtendWith` on [SharedContainer]; never register it directly.
public final class SharedContainersExtension implements BeforeAllCallback {

    private static final Namespace NAMESPACE = Namespace.create(SharedContainersExtension.class);

    /// Starts the containers declared on the test class and its enclosing classes, registering each in the root
    /// store on first use so that it is stopped when the engine is done.
    @Override
    public void beforeAll(ExtensionContext context) {
        final var store = context.getRoot().getStore(NAMESPACE);
        for (final var definition : definitions(context)) {
            // the Holder is AutoCloseable: the root store closes it when the engine is done
            store.computeIfAbsent(definition, SharedContainerRegistry::holder, SharedContainerRegistry.Holder.class).container();
        }
    }

    private static Set<Class<? extends ContainerDefinition<?>>> definitions(ExtensionContext context) {
        final var classes = new ArrayList<Class<?>>(context.getEnclosingTestClasses());
        classes.add(context.getRequiredTestClass());
        final var definitions = new LinkedHashSet<Class<? extends ContainerDefinition<?>>>();
        for (final var type : classes) {
            for (final var annotation : AnnotationSupport.findRepeatableAnnotations(type, SharedContainer.class)) {
                definitions.add(annotation.value());
            }
        }
        return definitions;
    }
}
