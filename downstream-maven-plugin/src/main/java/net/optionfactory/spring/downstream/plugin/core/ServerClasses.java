package net.optionfactory.spring.downstream.plugin.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// The class loader the server classes are read and loaded from.
///
/// A server packaged as a war keeps its classes under `WEB-INF/classes`, a layout that neither the
/// scan nor a plain class loader reads, and Maven resolves none of a war's dependencies: put on
/// the plugin's classpath as is, a war contributes no class at all, and the types its signatures
/// use are missing. Each [Server] is read instead, its classes from the war's `WEB-INF/classes`
/// (or from the directory of a server not packaged yet), alongside its dependencies, by a class
/// loader that is a child of the given one.
///
/// The war's `WEB-INF/classes` is copied to a directory of its own, removed on [#close()]: read
/// through a `jar:` URL, the war would be opened by a `JarFile` the JDK caches and shares across
/// the whole JVM, and closing a class loader closes it, under any other execution reading the same
/// war in a parallel build.
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
    private final List<Path> copies;

    private ServerClasses(ClassLoader classLoader, URLClassLoader owned, List<Path> copies) {
        this.classLoader = classLoader;
        this.owned = owned;
        this.copies = copies;
    }

    /// @param parent the class loader the plugin runs in
    /// @param servers the servers to read, empty when the server classes are already on `parent`
    /// @return the server classes, over `parent` when there are servers to read
    public static ServerClasses of(ClassLoader parent, List<Server> servers) {
        if (servers.isEmpty()) {
            return new ServerClasses(parent, null, List.of());
        }
        final var copies = new ArrayList<Path>();
        try {
            final var urls = new ArrayList<URL>();
            for (final var server : servers) {
                urls.add(url(classes(server.location(), copies).toUri()));
                for (final var dependency : server.dependencies()) {
                    urls.add(url(dependency.toUri()));
                }
            }
            final var loader = new ServerFirstClassLoader(urls.toArray(URL[]::new), parent);
            return new ServerClasses(loader, loader, copies);
        } catch (RuntimeException ex) {
            copies.forEach(ServerClasses::delete);
            throw ex;
        }
    }

    /// @return the class loader to scan and to load the server classes with
    public ClassLoader classLoader() {
        return classLoader;
    }

    /// Closes the class loader built over the servers, if any, and removes the copies of the
    /// wars' classes.
    ///
    /// @throws UncheckedIOException when the class loader cannot be closed or a copy removed
    @Override
    public void close() {
        if (owned == null) {
            return;
        }
        try {
            owned.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            copies.forEach(ServerClasses::delete);
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

    private static Path classes(Path location, List<Path> copies) {
        if (Files.isDirectory(location)) {
            return location;
        }
        try (final var war = FileSystems.newFileSystem(location)) {
            final var copy = Files.createTempDirectory("downstream-server-classes");
            copies.add(copy);
            final var classes = war.getPath("/WEB-INF/classes");
            if (!Files.isDirectory(classes)) {
                return copy;
            }
            try (final var paths = Files.walk(classes)) {
                for (final var path : paths.toList()) {
                    final var target = copy.resolve(classes.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(path, target);
                    }
                }
            }
            return copy;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void delete(Path copy) {
        try (final var paths = Files.walk(copy)) {
            for (final var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static URL url(URI uri) {
        try {
            return uri.toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
