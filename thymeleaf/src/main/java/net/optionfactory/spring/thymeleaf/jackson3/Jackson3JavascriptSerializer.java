package net.optionfactory.spring.thymeleaf.jackson3;

import java.io.Writer;
import java.util.function.Consumer;
import org.thymeleaf.standard.serializer.IStandardJavaScriptSerializer;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces JacksonStandardJavaScriptSerializer for Jackson 3.
 * @author rferranti
 */
public class Jackson3JavascriptSerializer implements IStandardJavaScriptSerializer {

    private final JsonMapper mapper;

    public Jackson3JavascriptSerializer(Consumer<JsonMapper.Builder> customizer) {
        final var jsonFactory = JsonFactory.builder().characterEscapes(new Jackson3ThymeleafCharacterEscapes()).build();
        final var builder = JsonMapper.builder(jsonFactory)
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .enable(JsonWriteFeature.ESCAPE_NON_ASCII);
        customizer.accept(builder);
        this.mapper = builder.build();
    }
    
    @Override
    public void serializeValue(Object object, Writer writer) {
        mapper.writeValue(writer, object);
    }

}
