package net.optionfactory.spring.downstream.plugin;

import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.downstream.plugin.core.ServerClasses.Server;
import org.apache.maven.artifact.Artifact;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

/// Finds the wars among the plugin's dependencies and resolves their dependencies.
///
/// Maven considers a war self-contained and resolves none of its dependencies, so the plugin gets
/// the war alone. The dependencies its classes need are the ones its pom declares: they are
/// resolved here as Maven would for the war's own build, through the build's repository session
/// (offline mode, mirrors and reactor included), `provided` ones included since the server
/// classes reference them as well, `test` ones excluded.
final class WarServers {

    private WarServers() {
    }

    /// @param pluginArtifacts the plugin's resolved dependencies, `null` outside of a build
    /// @param system the repository system of the build
    /// @param session the repository session of the build
    /// @param repositories the repositories the war's dependencies are resolved from
    /// @return a server per war among `pluginArtifacts`, empty when there is none
    /// @throws IllegalStateException when the dependencies of a war cannot be resolved
    static List<Server> resolve(List<Artifact> pluginArtifacts, RepositorySystem system, RepositorySystemSession session, List<RemoteRepository> repositories) {
        if (pluginArtifacts == null) {
            return List.of();
        }
        final var servers = new ArrayList<Server>();
        for (final var artifact : pluginArtifacts) {
            if (!"war".equals(artifact.getType())) {
                continue;
            }
            final var war = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), "pom", artifact.getBaseVersion());
            try {
                final var descriptor = system.readArtifactDescriptor(session, new ArtifactDescriptorRequest(war, repositories, "downstream"));
                final var collect = new CollectRequest();
                collect.setRepositories(repositories);
                collect.setManagedDependencies(descriptor.getManagedDependencies());
                descriptor.getDependencies().stream()
                        .filter(d -> !"test".equals(d.getScope()))
                        .forEach(collect::addDependency);
                final var resolved = system.resolveDependencies(session, new DependencyRequest(collect, null));
                final var dependencies = resolved.getArtifactResults().stream()
                        .map(r -> r.getArtifact().getFile().toPath())
                        .toList();
                servers.add(new Server(artifact.getFile().toPath(), dependencies));
            } catch (ArtifactDescriptorException | DependencyResolutionException ex) {
                throw new IllegalStateException("cannot resolve the dependencies of war %s".formatted(artifact.getId()), ex);
            }
        }
        return servers;
    }
}
