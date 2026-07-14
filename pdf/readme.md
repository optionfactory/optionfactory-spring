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

### Signing a PDF

The module also provides `Pkcs7PdfSigner` for digital signatures.

```java
Pkcs7PdfSigner signer = new Pkcs7PdfSigner(keystore, "alias", "password");
byte[] signedPdf = signer.sign(pdfInputStream, new SignatureInfo(...));
```


