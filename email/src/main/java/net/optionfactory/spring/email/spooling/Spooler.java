package net.optionfactory.spring.email.spooling;

import java.nio.file.Path;
import java.util.List;

/**
 * Turns a buffered payload into one or more spooled artifacts (e.g. files on disk).
 * <p>
 * Failure handling is the implementer's responsibility. The surrounding
 * {@link BufferedScheduledSpooler} drains its buffer before invoking
 * {@link #spool(java.util.List)} and treats a normal return as success: it does
 * <strong>not</strong> retain or retry a batch on failure. An implementation that
 * wants best-effort, loss-tolerant semantics should therefore swallow its own
 * failures (log and return an empty list); one that lets an exception propagate
 * will cause the batch to be dropped and the failure to surface to the scheduler.
 * Choose deliberately based on whether the expected failures are transient (which
 * favors throwing, so they can be observed) or deterministic (which favors
 * swallowing, since no retry will succeed).
 */
public interface Spooler<T> {

    List<Path> spool(T value);
    
}
