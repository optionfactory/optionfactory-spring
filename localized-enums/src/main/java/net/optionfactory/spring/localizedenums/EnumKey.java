package net.optionfactory.spring.localizedenums;

public record EnumKey(String category, String name) {

    public static EnumKey of(String category, String name) {
        return new EnumKey(category, name);
    }

    public LocalizedEnumResponse toLabel(String value) {
        return LocalizedEnumResponse.of(category, name, value);
    }
}
