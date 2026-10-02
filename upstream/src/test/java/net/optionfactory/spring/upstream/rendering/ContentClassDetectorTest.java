package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.optionfactory.spring.upstream.rendering.ContentClassDetector.ContentClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class ContentClassDetectorTest {

    private static final byte[] WITH_NUL = {'a', 0, 'b'};

    @Test
    public void anEmptyPayloadIsText() {
        Assertions.assertEquals(ContentClass.TEXT, ContentClassDetector.detect(MediaType.APPLICATION_OCTET_STREAM, new byte[0]), "an empty payload must be text whatever its media type");
    }

    @Test
    public void textMediaTypesWinOverTheContent() {
        Assertions.assertEquals(ContentClass.TEXT, ContentClassDetector.detect(MediaType.TEXT_PLAIN, WITH_NUL), "a text/* media type must be text");
        Assertions.assertEquals(ContentClass.TEXT, ContentClassDetector.detect(MediaType.APPLICATION_PROBLEM_JSON, WITH_NUL), "a +json media type must be text");
        Assertions.assertEquals(ContentClass.TEXT, ContentClassDetector.detect(MediaType.parseMediaType("application/soap+xml"), WITH_NUL), "a +xml media type must be text");
        Assertions.assertEquals(ContentClass.TEXT, ContentClassDetector.detect(MediaType.APPLICATION_FORM_URLENCODED, WITH_NUL), "a form media type must be text");
    }

    @Test
    public void otherPayloadsAreBinaryWhenTheyHoldControlCharacters() {
        Assertions.assertEquals(ContentClass.BINARY, ContentClassDetector.detect(MediaType.APPLICATION_OCTET_STREAM, WITH_NUL), "a NUL byte must make the payload binary");
        Assertions.assertEquals(ContentClass.BINARY, ContentClassDetector.detect(null, new byte[]{'a', 0x1B}), "a control character must make a payload of unknown type binary");
    }

    @Test
    public void otherPayloadsAreTextWhenTheyLookLikeText() {
        final var content = "caffè\tlatte\r\n".getBytes(StandardCharsets.UTF_8);
        Assertions.assertEquals(ContentClass.TEXT, ContentClassDetector.detect(MediaType.APPLICATION_OCTET_STREAM, content), "tabs, line breaks and non-ASCII bytes must not make a payload binary");
    }

    @Test
    public void onlyTheFirst1024BytesAreInspected() {
        final var content = new byte[2048];
        Arrays.fill(content, (byte) 'a');
        content[1500] = 0;
        Assertions.assertEquals(ContentClass.TEXT, ContentClassDetector.detect(null, content), "a control character beyond the first 1024 bytes must not be seen");
    }
}
