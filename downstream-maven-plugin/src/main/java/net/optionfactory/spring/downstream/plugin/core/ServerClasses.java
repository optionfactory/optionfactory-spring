package net.optionfactory.spring.downstream.plugin.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// The class loader the server classes are read and loaded from.
///
/// A server packaged as a war keeps its classes under `WEB-INF/classes`, a layout that neither the
/// scan nor a plain class loader reads, and Maven resolves none of a war's dependencies: put on
/// the plugin's classpath as is, a war contributes no class at all, and the types its signatures
/// use are missing. Each [Server] is read in place instead, its classes from the war's
/// `WEB-INF/classes` (or from the directory of a server not packaged yet), alongside its
/// dependencies, by a class loader that is a child of the given one.
///
/// The child loads what it holds before asking its parent: a server not packaged yet is on the
/// parent as well, and loaded there its classes would not see the dependencies the child holds.
/// The JDK and the `net.optionfactory.spring.downstream` package are the exceptions, always
/// loaded by the parent: the plugin recognizes `@Downstream` annotations by class, so the server
/// must use the plugin's own copies.
///
/// Without servers, the given class loader is used as is.
public final class ServerClasses implements AutoCloseable {

    /// A server to read the classes of.
    ///
    /// @param location the war, or the directory of its classes when it is not packaged yet
    /// @param dependencies the jars or directories the server classes need, its `provided`
    /// dependencies included
    public record Server(Path location, List<Path> dependencies) {

    }

    private final ClassLoader classLoader;
    private final URLClassLoader owned;

    private ServerClasses(ClassLoader classLoader, URLClassLoader owned) {
        this.classLoader = classLoader;
        this.owned = owned;
    }

    /// @param parent the class loader the plugin runs in
    /// @param servers the servers to read, empty when the server classes are already on `parent`
    /// @return the server classes, over `parent` when there are servers to read
    public static ServerClasses of(ClassLoader parent, List<Server> servers) {
        if (servers.isEmpty()) {
            return new ServerClasses(parent, null);
        }
        final var urls = new ArrayList<URL>();
        for (final var server : servers) {
            urls.add(classes(server.location()));
            for (final var dependency : server.dependencies()) {
                urls.add(url(dependency.toUri()));
            }
        }
        final var loader = new ServerFirstClassLoader(urls.toArray(URL[]::new), parent);
        return new ServerClasses(loader, loader);
    }

    /// @return the class loader to scan and to load the server classes with
    public ClassLoader classLoader() {
        return classLoader;
    }

    /// Closes the class loader built over the servers, if any.
    ///
    /// @throws UncheckedIOException when the class loader cannot be closed
    @Override
    public void close() {
        if (owned == null) {
            return;
        }
        try {
            owned.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /// Loads the classes it holds before asking its parent, except the ones the plugin shares
    /// with the server.
    private static final class ServerFirstClassLoader extends URLClassLoader {

        static {
            registerAsParallelCapable();
        }

        private static final String SHARED_PACKAGE = "net.optionfactory.spring.downstream.";

        private ServerFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("java.") || name.startsWith(SHARED_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                var loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notHeld) {
                        return super.loadClass(name, resolve);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        @Override
        public URL getResource(String name) {
            final var held = findResource(name);
            return held != null ? held : super.getResource(name);
        }
    }

    private static URL classes(Path location) {
        if (Files.isDirectory(location)) {
            return url(location.toUri());
        }
        return url(URI.create("jar:" + location.toUri() + "!/WEB-INF/classes/"));
    }

    private static URL url(URI uri) {
        try {
            return uri.toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
