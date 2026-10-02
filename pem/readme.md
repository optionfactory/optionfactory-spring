# optionfactory-spring/pem

PEM based `Keystore`s and security providers.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>pem</artifactId>
</dependency>
```

## Usage

### Loading a KeyStore from PEM

```java
try (InputStream is = new FileInputStream("my-certs.pem")) {
    KeyStore ks = Pem.keyStore(is);
}
```

### Loading a Private Key

```java
try (InputStream is = new FileInputStream("key.pem")) {
    PrivateKey key = Pem.privateKey(is, "passphrase".toCharArray());
}
```

### Loading a Certificate

```java
try (InputStream is = new FileInputStream("cert.pem")) {
    X509Certificate cert = Pem.certificate(is);
}
```



### The `PEM` KeyStore type

`Pem.keyStore` passes a `PemProvider` instance explicitly, so it needs no JVM configuration. To get a PEM keystore
through `KeyStore.getInstance("PEM")` instead, the provider has to be installed, either at runtime:

```java
Security.addProvider(new PemProvider());
```

or with a `security.provider.<n>=PEM` entry in the JVM security properties (`java.security`, or a file passed with
`-Djava.security.properties`). The JDK resolves the `PEM` name with a `ServiceLoader` of the system class loader, which
finds the provider through the jar's `META-INF/services/java.security.Provider` registration; the jar must therefore be
on the class path the JVM is launched with. The registration alone installs nothing.
