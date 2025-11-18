package net.optionfactory.spring.marshaling.jackson.quirks;

import java.lang.annotation.Annotation;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

public interface QuirkHandler<A extends Annotation> {

    Class<A> annotation();

    BeanPropertyWriter serialization(A ann, BeanPropertyWriter bpw);

    SettableBeanProperty deserialization(A ann, SettableBeanProperty sbp);

}
