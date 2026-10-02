package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

public class XsltRedactorTest {

    private static ByteArrayResource xml(String xml) {
        return new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void matchedAttributesAndElementsAreRedacted() {
        final var redactor = XsltRedactor.Factory.INSTANCE.create(Map.of(), Map.of("@password", "redacted"), Map.of("//password", "redacted"));
        final var input = """
        <request>
            <a password="secret" other="kept">a</a>
            <password kind="plain">secret<nested>also secret</nested></password>
        </request>
        """;
        final var output = redactor.redact(xml(input));
        Assertions.assertEquals("<request><a password=\"redacted\" other=\"kept\">a</a><password kind=\"plain\">redacted</password></request>", output, "matched attributes must be redacted, matched elements must have their whole content replaced, their attributes kept");
    }

    @Test
    public void theDocumentIsCompactedAndLosesItsDeclaration() {
        final var redactor = XsltRedactor.Factory.INSTANCE.create(Map.of(), Map.of(), Map.of());
        final var input = """
        <?xml version="1.0" encoding="UTF-8"?>
        <a>
            <b>  some
               text  </b>
        </a>
        """;
        Assertions.assertEquals("<a><b>some text</b></a>", redactor.redact(xml(input)), "the declaration and whitespace-only text must be dropped, and the spaces of text normalized");
    }

    @Test
    public void patternPrefixesMatchTheNamespaceWhateverTheDocumentPrefix() {
        final var redactor = XsltRedactor.Factory.INSTANCE.create(Map.of("s", "urn:secrets"), Map.of(), Map.of("//s:password", "R"));
        final var input = """
        <r xmlns:x="urn:secrets"><x:password>secret</x:password><password>public</password></r>
        """;
        Assertions.assertEquals("<r xmlns:x=\"urn:secrets\"><x:password>R</x:password><password>public</password></r>", redactor.redact(xml(input)), "only the element in the configured namespace must be redacted");
    }

    @Test
    public void anInvalidPatternFailsTheCreation() {
        Assertions.assertThrows(IllegalStateException.class, () -> XsltRedactor.Factory.INSTANCE.create(Map.of(), Map.of(), Map.of("//[", "R")), "a pattern that does not compile must fail the creation");
        Assertions.assertThrows(IllegalStateException.class, () -> XsltRedactor.Factory.INSTANCE.create(Map.of(), Map.of(), Map.of("//undeclared:a", "R")), "an undeclared prefix must fail the creation");
    }

    @Test
    public void malformedXmlCannotBeRedacted() {
        final var redactor = XsltRedactor.Factory.INSTANCE.create(Map.of(), Map.of(), Map.of());
        Assertions.assertThrows(IllegalStateException.class, () -> redactor.redact(xml("<a><b></a>")), "a malformed document must fail the redaction");
    }

    @Test
    public void redactRejectsDoctypeBasedXxe() {
        final var redactor = XsltRedactor.Factory.INSTANCE.create(Map.of(), Map.of(), Map.of());
        final var xxe = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE r [
          <!ENTITY xxe SYSTEM "file:///etc/passwd" >
        ]>
        <request>&xxe;</request>
        """;
        Assertions.assertThrows(IllegalStateException.class, () ->
                redactor.redact(xml(xxe)), "a document with a doctype must be rejected");
    }
}
