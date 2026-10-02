package net.optionfactory.spring.email;

import org.springframework.core.io.InputStreamSource;

/// A resource embedded in the html body of an [EmailMessage] and referenced from it by content id,
/// typically an image shown inline as `<img src="cid:logo">`.
///
/// Content ids are only marshalled alongside an html body: a message with a text body alone drops
/// them. Like an [AttachmentSource], the content is read only when marshalling, possibly more than once.
///
/// @param source provides the resource content
/// @param id the content id the html body references, without the `cid:` scheme
/// @param mimeType the content type of the resource, e.g. `image/png`
public record CidSource(InputStreamSource source, String id, String mimeType) {

    /// @param source provides the resource content
    /// @param id the content id the html body references, without the `cid:` scheme
    /// @param mimeType the content type of the resource
    /// @return the cid source
    public static CidSource of(InputStreamSource source, String id, String mimeType) {
        return new CidSource(source, id, mimeType);
    }

}
