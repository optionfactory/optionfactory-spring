# 0015. Outbound email goes through a filesystem spool, at most once

Status: accepted

## Context

Sending email inside a request or a transaction couples the request to an SMTP
server's availability and latency, and a retry after a failure that happened
after the message left can send it twice.

## Decision

Producing an email and delivering it are separate. A message is rendered
(thymeleaf, css inlined by `CssInliner`) and written as an `.eml` file to the
spool directory (`EmailPaths`). `EmailSender.processSpool()`, scheduled by
`ScheduledEmailSender` and woken early by `EmailSpooled` events, sends each
file and moves it to the sent directory; a file still in the spool (sending
kept failing) after `deadAfter` is moved to the dead directory instead of being
retried. Moves are atomic. A file that was
sent but cannot be archived is deleted rather than left in the spool: delivery
is at most once. Event-driven producers buffer their events and spool them in
batches (`BufferedScheduledSpooler`).

## Consequences

- A request never waits on SMTP, and a mail server outage only grows the
  spool.
- A spool belongs to one sending process on one machine; it is not a queue to
  share between instances.
- The spool, sent and dead directories must be on durable storage: a spooled
  email exists only as its file until it is sent, so a spool on an ephemeral
  disk (a container's writable layer, a `tmpfs`) loses every email still in it
  when the instance is replaced.
- A crash between sending and archiving loses the archive copy, never sends
  twice.
- A placebo mode lets non-production environments run the whole pipeline
  without sending.
