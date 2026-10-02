package net.optionfactory.spring.email;

/// Transforms the html body of an [EmailMessage] once it has been rendered, e.g. the
/// [net.optionfactory.spring.email.inliner.CssInliner] moving css rules into `style` attributes.
///
/// The postprocessor configured on a builder is invoked by [EmailMessage.Builder#build()] only
/// when the message has an html body, and whatever it returns becomes the html body: a message
/// with a text body only is built without invoking it.
public interface HtmlBodyPostprocessor {

    /// @param body the rendered html body, never `null`
    /// @return the html body to send
    String postprocess(String body);
}
