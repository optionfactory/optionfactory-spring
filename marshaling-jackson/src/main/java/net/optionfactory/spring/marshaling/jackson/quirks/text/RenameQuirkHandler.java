package net.optionfactory.spring.marshaling.jackson.quirks.text;

import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

public class RenameQuirkHandler implements QuirkHandler<Quirks.Rename> {

    @Override
    public Class<Quirks.Rename> annotation() {
        return Quirks.Rename.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Rename ann, BeanPropertyWriter bpw) {
        return bpw.rename(new NameTransformer() {
            @Override
            public String transform(String name) {
                return ann.value();
            }

            @Override
            public String reverse(String transformed) {
                throw new UnsupportedOperationException("unused.");
            }
        });
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Rename ann, SettableBeanProperty sbp) {
        return sbp.withSimpleName(ann.value());

    }

}
