package net.optionfactory.spring.pem.parsing;

import java.security.cert.X509Certificate;

public record KeyAndCertificates(String alias, PrivateKeyHolder key, X509Certificate[] certs) {

}
