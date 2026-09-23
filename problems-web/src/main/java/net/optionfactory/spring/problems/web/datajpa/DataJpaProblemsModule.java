package net.optionfactory.spring.problems.web.datajpa;

import java.util.List;
import net.optionfactory.spring.problems.web.ExceptionClassifier;
import net.optionfactory.spring.problems.web.ProblemsModule;

/// How `problems-web` answers `data-jpa`'s exceptions: the filter and sort requests a repository
/// rejects are answered as bad requests rather than as unexpected errors (see
/// [FilteringExceptionClassifier]).
///
/// Register it where the rest resolver is configured:
///
/// ```java
/// ExceptionResolvers.configurer(resolvers)
///         .rest(jsonMapper, rest -> rest.withModule(new DataJpaProblemsModule()))
///         .configure();
/// ```
///
/// `data-jpa` is an optional dependency of `problems-web`: applications not using it never load this
/// class.
public class DataJpaProblemsModule implements ProblemsModule {

    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(new FilteringExceptionClassifier());
    }
}
