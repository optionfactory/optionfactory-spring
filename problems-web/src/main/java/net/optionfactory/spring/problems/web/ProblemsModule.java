package net.optionfactory.spring.problems.web;

import java.util.List;

/// Every [ExceptionClassifier] and [FailureTransformer] a library contributes to the rest resolver,
/// registered in one go with `RestExceptionResolver.Builder#withModule`: an application wires a
/// library once, and the library decides — and may later change — what that registers.
///
/// A module contributes classifiers and transformers and nothing else. In particular, it cannot
/// include details in a production response: detail omission is applied after every transformer,
/// a module's included.
public interface ProblemsModule {

    /// @return the classifiers to register, in the order they should be consulted
    default List<ExceptionClassifier> classifiers() {
        return List.of();
    }

    /// @return the transformers to register, in the order they should run
    default List<FailureTransformer> transformers() {
        return List.of();
    }
}
