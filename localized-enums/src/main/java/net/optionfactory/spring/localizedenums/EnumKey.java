package net.optionfactory.spring.localizedenums;

import java.util.Objects;

public class EnumKey {

    public String category;
    public String name;

    public static EnumKey of(String category, String name) {
        final var ek = new EnumKey();
        ek.category = category;
        ek.name = name;
        return ek;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, name);
    }

    @Override
    public boolean equals(Object rhs) {
        if (rhs instanceof EnumKey == false) {
            return false;
        }
        final var other = (EnumKey) rhs;
        return Objects.equals(this.category, other.category)
                && Objects.equals(this.name, other.name);

    }

    public LocalizedEnumResponse toLabel(String value) {
        return LocalizedEnumResponse.of(category, name, value);
    }
}
