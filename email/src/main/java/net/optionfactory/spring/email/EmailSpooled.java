package net.optionfactory.spring.email;

/// The application event published when an email has been written to the spool, so that a
/// [ScheduledEmailSender] can send it right away rather than at its next scheduled run.
///
/// It is published by [EmailMessage.Builder#marshalToSpool()] when the spooling configuration
/// carries a publisher. It carries no data: the sender always processes the whole spool.
public class EmailSpooled {
}
