package net.optionfactory.spring.upstream.contexts;

import java.time.Instant;

/// Describes the failure of an exchange to the alert conditions, as `#exception`, and to the alert
/// events.
///
/// @param at when the failure was observed, according to the client clock
/// @param message the exception message, possibly `null`
public record ExceptionContext(Instant at, String message){

}
