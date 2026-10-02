package net.optionfactory.spring.email.spooling;

import java.nio.file.Path;
import java.util.List;

/// Turns a buffered payload into one or more spooled artifacts (e.g. files on disk).
///
/// Failure handling is the implementer's responsibility. The surrounding
/// [BufferedScheduledSpooler] drains its buffer before invoking [#spool] and
/// treats a normal return as success: it does **not** retain or retry a batch on failure. An
/// implementation that wants best-effort, loss-tolerant semantics should therefore swallow its own
/// failures (log and return an empty list); one that lets an exception propagate will cause the
/// batch to be dropped and the failure to surface to the scheduler. Choose deliberately based on
/// whether the expected failures are transient (which favors throwing, so they can be observed) or
/// deterministic (which favors swallowing, since no retry will succeed).
///
/// @param <T> the payload type, a `List` of events when used by a [BufferedScheduledSpooler]
public interface Spooler<T> {

    /// @param value the payload to spool, never empty when called by a [BufferedScheduledSpooler]
    /// @return the spooled artifacts, possibly fewer than the payload's elements
    List<Path> spool(T value);

}
