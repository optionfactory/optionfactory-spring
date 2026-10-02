package net.optionfactory.spring.problems.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.HttpStatus;

/// Marks a handler method as a download for [BinaryResponseExceptionResolver], and sets the status
/// its failures are answered with, whatever the exception.
///
/// Only the annotation on the method is read: although the annotation can be placed on a type,
/// the resolver does not look for it there.
///
/// ```java
/// @GetMapping("/reports/{id}.csv")
/// @BinaryResponseErrorStatus(HttpStatus.SERVICE_UNAVAILABLE)
/// public void report(@PathVariable long id, HttpServletResponse response) throws IOException {
///     ...
/// }
/// ```
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface BinaryResponseErrorStatus {

    /// @return the status to answer a failed download with
    HttpStatus value();
}
