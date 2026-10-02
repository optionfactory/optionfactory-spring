package net.optionfactory.spring.email;

/// Transforms the html body of an [EmailMessage] once it has been rendered, e.g. the
/// [net.optionfactory.spring.email.inliner.CssInliner] moving css rules into `style` attributes.
///
/// The postprocessor configured on a builder is invoked by [EmailMessage.Builder#build()] whether
/// or not the message has an html body: for a message with a text body only it receives `null`.
/// Whatever it returns becomes the html body, so it should return `null` for a `null` input; one
/// that throws instead fails the build of every text-only message.
public interface HtmlBodyPostprocessor {

    /// @param body the rendered html body, `null` when the message has no html body
    /// @return the html body to send
    String postprocess(String body);
}
