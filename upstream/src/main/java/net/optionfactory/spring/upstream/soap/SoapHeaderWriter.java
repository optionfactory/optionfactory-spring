package net.optionfactory.spring.upstream.soap;

import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.xml.namespace.QName;

/// Writes the SOAP header of every outgoing message, e.g. to authenticate it.
///
/// Passed to the `soap` methods of `UpstreamBuilder`; [SoapJaxbHttpMessageConverter] calls it for
/// each request it writes, before marshalling the body.
public interface SoapHeaderWriter {

    /// No header writer: messages are sent with an empty header. A readable `null` for the `soap`
    /// builder methods.
    public static SoapHeaderWriter NONE = null;

    /// @param header the header of the message being written, to add elements to
    public void write(SOAPHeader header);

    /// Writes a WS-Security `UsernameToken` with a plain text password (the `PasswordText` type).
    ///
    /// The `Security` element is marked `mustUnderstand`, and the token id is derived from the
    /// credentials, so it is the same on every message. The password travels in clear: use it over
    /// https only.
    public static class WssUsernameToken implements SoapHeaderWriter {

        /// The WS-Security utility namespace, of the `wsu:Id` attribute.
        public static final String WSU_NAMESPACE_URI = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
        /// The WS-Security extension namespace, of the `Security` element.
        public static final String WSSE_NAMESPACE_URI = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
        /// The `Type` of a plain text password.
        public static final String PASSWORD_TYPE_TEXT_ATTRIBUTE_VALUE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";
        private final String username;
        private final String password;

        /// @param username the user name
        /// @param password the password, sent in clear
        public WssUsernameToken(String username, String password) {
            this.username = username;
            this.password = password;
        }

        /// @param header the header to add the `wsse:Security` element to
        /// @throws IllegalStateException when the elements cannot be added
        @Override
        public void write(SOAPHeader header) {
            try {
                final var usernameTokenId = String.format("UsernameToken-%s", UUID.nameUUIDFromBytes(String.format("%s:%s", username, password).getBytes(StandardCharsets.UTF_8)));
                final var sec = header.addChildElement("Security", "wsse", WSSE_NAMESPACE_URI).addNamespaceDeclaration("wsu", WSU_NAMESPACE_URI);
                sec.addAttribute(new QName(header.getElementQName().getNamespaceURI(), "mustUnderstand", header.getElementQName().getPrefix()), "1");
                final var token = sec.addChildElement("UsernameToken", "wsse").addAttribute(new QName(WSU_NAMESPACE_URI, "Id", "wsu"), usernameTokenId);
                token.addChildElement("Username", "wsse").addTextNode(username);
                token.addChildElement("Password", "wsse").addAttribute(new QName("Type"), PASSWORD_TYPE_TEXT_ATTRIBUTE_VALUE).addTextNode(password);
            } catch (SOAPException ex) {
                throw new IllegalStateException(ex);
            }

        }
    }
}
