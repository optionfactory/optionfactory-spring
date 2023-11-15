package net.optionfactory.spring.upstream.soap;

import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.xml.namespace.QName;

public interface SoapHeaderWriter {

    public static SoapHeaderWriter NONE = null;

    public void write(SOAPHeader header);

    public static class WssUsernameToken implements SoapHeaderWriter {

        public static final String WSU_NAMESPACE_URI = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
        public static final String WSSE_NAMESPACE_URI = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
        public static final String PASSWORD_TYPE_TEXT_ATTRIBUTE_VALUE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";
        private final String username;
        private final String password;

        public WssUsernameToken(String username, String password) {
            this.username = username;
            this.password = password;
        }

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
