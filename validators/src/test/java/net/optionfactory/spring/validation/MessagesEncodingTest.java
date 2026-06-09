package net.optionfactory.spring.validation;

import jakarta.validation.Validation;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.optionfactory.spring.validation.emails.StrictEmail;
import net.optionfactory.spring.validation.files.ByteArrayMultipartFile;
import net.optionfactory.spring.validation.files.MultipartFilenameMaxLength;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

public class MessagesEncodingTest {

    
    public record BeanWithEmail(@StrictEmail String email){
    }
    
    @Test
    public void french() {
        final var validator = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.FRENCH, false))
                .buildValidatorFactory()
                .getValidator();

        final var bean = new BeanWithEmail("");
        final var violations = validator.validate(bean);
        Assertions.assertEquals(List.of("Spécifiez une adresse e-mail valide"), violations.stream().map(cv -> cv.getMessage()).toList());        
    }
    
    @Test
    public void spanish() {
        final var validator = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.of("es"), false))
                .buildValidatorFactory()
                .getValidator();

        final var bean = new BeanWithEmail("");
        final var violations = validator.validate(bean);
        Assertions.assertEquals(List.of("Especifique una dirección de correo electrónico válida"), violations.stream().map(cv -> cv.getMessage()).toList());        
    }
    
    public record BeanWithMultipartFile(@MultipartFilenameMaxLength(1) MultipartFile file){
    
    }
    
    @Test
    public void italian() {
        final var validator = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
                .buildValidatorFactory()
                .getValidator();

        final var bean = new BeanWithMultipartFile(ByteArrayMultipartFile.empty("aaaa", "image/svg"));
        final var violations = validator.validate(bean);
        Assertions.assertEquals(List.of("Il nome del file è troppo lungo"), violations.stream().map(cv -> cv.getMessage()).toList());        
    }
}
