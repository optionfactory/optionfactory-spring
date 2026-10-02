package net.optionfactory.spring.marshaling.jackson.quirks.text;

import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

/// Handles [Quirks.Rename]: see the annotation for the caveat on field and setter properties.
public class RenameQuirkHandler implements QuirkHandler<Quirks.Rename> {

    /// @return [Quirks.Rename]
    @Override
    public Class<Quirks.Rename> annotation() {
        return Quirks.Rename.class;
    }

    /// @param ann the annotation, with the json name
    /// @param bpw the writer of the property
    /// @return a copy of the writer under the json name
    @Override
    public BeanPropertyWriter serialization(Quirks.Rename ann, BeanPropertyWriter bpw) {
        final var originalName = bpw.getName();
        return bpw.rename(new NameTransformer() {
            @Override
            public String transform(String name) {
                return ann.value();
            }

            @Override
            public String reverse(String transformed) {
                return originalName;
            }
        });
    }

    /// @param ann the annotation, with the json name
    /// @param sbp the property
    /// @return a copy of the property under the json name
    @Override
    public SettableBeanProperty deserialization(Quirks.Rename ann, SettableBeanProperty sbp) {
        return sbp.withSimpleName(ann.value());
    }
}