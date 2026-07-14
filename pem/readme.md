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


