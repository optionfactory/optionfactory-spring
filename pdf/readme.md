# optionfactory-spring/pdf

Simplified PDF generations with [Thymeleaf](https://www.thymeleaf.org/) + [openhtmltopdf](https://github.com/openhtmltopdf/openhtmltopdf) + [pdfbox](https://pdfbox.apache.org/)

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>pdf</artifactId>
</dependency>
```

## Usage

### Rendering a PDF

```java
ThymeleafToPdfRenderer renderer = new ThymeleafToPdfRenderer(
    templateEngine, 
    List.of(new PdfFontInfo("/fonts/Roboto.ttf", "Roboto", 400, FontStyle.NORMAL, true)), 
    Optional.of("My App")
);

Context context = new Context();
context.setVariable("title", "Hello World");

Resource pdf = renderer.render("templates/my-report", context);
```

An XHTML document produced elsewhere (an XSL transformation, another template engine) is rendered as it is, its text
never evaluated as Thymeleaf expressions:

```java
Resource pdf = renderer.renderXhtml(xhtml);
```

### Signing a PDF

`PdfSigner` adds a detached PKCS#7 signature (built by `Pkcs7PdfSigner`) to a PDF, as an incremental update. It takes
the signing key, RSA of at least 2048 bits or a 256 bit EC key, and its certificate chain, the signer's certificate
first; an unsupported key or an empty chain is rejected by the constructor.

```java
KeyStore keystore = Pem.keyStore(pemStream);
PrivateKey key = (PrivateKey) keystore.getKey("signer", "password".toCharArray());
Certificate[] certificates = keystore.getCertificateChain("signer");
X509Certificate[] chain = Arrays.copyOf(certificates, certificates.length, X509Certificate[].class);

PdfSigner signer = new PdfSigner(key, chain);
TemporaryFileSystemResource signed = signer.sign(pdf, new SignatureInfo(
    "ACME", "Invoice", "Milano", ZonedDateTime.now(), SignatureInfo.CommitmentType.PROOF_OF_ORIGIN
));
```

`pdf` is a spring `Resource`, such as the one returned by `ThymeleafToPdfRenderer.render`. The signed document is
buffered in a temporary file, deleted once the returned resource has been read. A loaded `PDDocument` can be signed with
`signer.sign(document, signatureInfo)` instead, followed by `document.saveIncremental(outputStream)`.


