# optionfactory-spring/validators

`jakarta.validation` based validators for emails, `MultipartFile`s, IBANs, phone numbers, and tax codes.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>validators</artifactId>
</dependency>
```

## Usage

Apply the annotations to your DTO fields:

```java
public record MyDto(
    @StrictEmail 
    String email,
    
    @MultipartFileMaxSize(1024 * 1024) 
    @MultipartFileContentType("application/pdf")
    MultipartFile file,
    
    @Iban 
    String iban,
    
    @PhoneNumber 
    String phone,
    
    @ItalianTaxCode 
    String taxCode
) {}
```


