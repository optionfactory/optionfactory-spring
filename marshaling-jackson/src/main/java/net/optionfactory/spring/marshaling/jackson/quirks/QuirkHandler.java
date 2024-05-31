package net.optionfactory.spring.marshaling.jackson.quirks;

import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import java.lang.annotation.Annotation;

public interface QuirkHandler<A extends Annotation> {

    Class<A> annotation();

    BeanPropertyWriter serialization(A ann, BeanPropertyWriter bpw);

    SettableBeanProperty deserialization(A ann, SettableBeanProperty sbp);

}
