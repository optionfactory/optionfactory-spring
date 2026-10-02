package net.optionfactory.spring.pem.parsing;

import java.security.cert.X509Certificate;

/// The keystore material associated with an alias: a single PEM entry once unmarshaled, or the
/// merge of all the entries sharing the alias.
///
/// @param alias the alias of the material
/// @param key the private key, `null` when the alias holds certificates only
/// @param certs the certificates in file order, never `null` but possibly empty; when there is a
/// key they are its certificate chain
public record KeyAndCertificates(String alias, PrivateKeyHolder key, X509Certificate[] certs) {

}
