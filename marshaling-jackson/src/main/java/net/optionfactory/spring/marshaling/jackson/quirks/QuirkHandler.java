package net.optionfactory.spring.marshaling.jackson.quirks;

import java.lang.annotation.Annotation;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

/// Applies the quirk of one annotation to the bean properties carrying it, registered through
/// [Quirks.Builder#add].
///
/// The handler is invoked once per annotated property, while jackson builds the serializer or
/// deserializer of the bean, never per value: it adapts the property by assigning a custom
/// serializer or deserializer, or by renaming it. Throwing rejects the annotation's placement,
/// conventionally with an `IllegalStateException`, and fails the construction of the
/// (de)serializer. A handler is shared by every mapper the module is registered on, and should be
/// stateless.
///
/// ```java
/// public class UpperQuirkHandler implements QuirkHandler<Upper> {
///     public Class<Upper> annotation() { return Upper.class; }
///     public BeanPropertyWriter serialization(Upper ann, BeanPropertyWriter bpw) {
///         bpw.assignSerializer(new UpperSerializer());
///         return bpw;
///     }
///     public SettableBeanProperty deserialization(Upper ann, SettableBeanProperty sbp) {
///         return sbp.withValueDeserializer(new UpperDeserializer());
///     }
/// }
/// ```
///
/// @param <A> the annotation type
public interface QuirkHandler<A extends Annotation> {

    /// @return the annotation selecting the properties this handler applies to, which must be
    /// retained at runtime
    Class<A> annotation();

    /// Adapts the serialization of an annotated property.
    ///
    /// @param ann the annotation found on the property
    /// @param bpw the property writer, as left by the handlers applied before this one
    /// @return the writer to use: the same one, possibly with a serializer assigned, or a
    /// replacement such as a renamed copy
    BeanPropertyWriter serialization(A ann, BeanPropertyWriter bpw);

    /// Adapts the deserialization of an annotated property.
    ///
    /// @param ann the annotation found on the property
    /// @param sbp the property, as left by the handlers applied before this one
    /// @return the property to use, typically a copy with another deserializer or name
    SettableBeanProperty deserialization(A ann, SettableBeanProperty sbp);

}
